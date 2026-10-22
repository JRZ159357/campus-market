// 文件路径: src/main/java/com/campus/market/service/impl/BannerServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.ResultCode;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.dto.BannerQueryDTO;
import com.campus.market.dto.BannerSaveDTO;
import com.campus.market.dto.BannerSortDTO;
import com.campus.market.entity.Banner;
import com.campus.market.mapper.BannerMapper;
import com.campus.market.service.BannerService;
import com.campus.market.service.HomeCacheService;
import com.campus.market.vo.BannerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮播图业务实现。
 * <p>
 * 规则：
 * 1) 跳转类型与跳转值必须匹配：商品/分类要求数字ID，外链要求 http(s) 地址；
 * 2) 生效时间晚于失效时间直接拒绝，避免配置出一张永远不展示的图；
 * 3) 小程序只取「启用 + 时间窗口内」的数据，过期轮播图无需人工下架。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    /** 展示位置：首页轮播。当前只有一个位置，常量先立在这里，加位置时只改查询条件 */
    private static final int POSITION_HOME = 1;

    private final HomeCacheService homeCacheService;

    @Override
    public IPage<BannerVO> pageBanners(BannerQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;

        Page<Banner> page = this.page(new Page<>(pageNum, pageSize), Wrappers.<Banner>lambdaQuery()
                .eq(query.getStatus() != null, Banner::getStatus, query.getStatus())
                .like(keyword != null, Banner::getTitle, keyword)
                .orderByAsc(Banner::getSort)
                .orderByDesc(Banner::getId));

        return page.convert(BannerServiceImpl::toVO);
    }

    @Override
    public List<BannerVO> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return this.lambdaQuery()
                .eq(Banner::getPosition, POSITION_HOME)
                .eq(Banner::getStatus, 1)
                .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, now))
                .and(w -> w.isNull(Banner::getEndTime).or().ge(Banner::getEndTime, now))
                .orderByAsc(Banner::getSort)
                .orderByDesc(Banner::getId)
                .list()
                .stream()
                .map(BannerServiceImpl::toVO)
                .toList();
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_CREATE, desc = "新增轮播图")
    @Transactional(rollbackFor = Exception.class)
    public BannerVO create(BannerSaveDTO dto) {
        validate(dto);
        Banner banner = new Banner();
        copy(dto, banner);
        banner.setPosition(POSITION_HOME);
        banner.setSort(dto.getSort() != null ? dto.getSort() : nextSort());
        banner.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : 1);
        this.save(banner);
        homeCacheService.evictIndex();
        log.info("新增轮播图: id={}, title={}, linkType={}", banner.getId(), banner.getTitle(), banner.getLinkType());
        return toVO(banner);
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_UPDATE, targetIndex = 1, desc = "编辑轮播图")
    @Transactional(rollbackFor = Exception.class)
    public void updateBanner(Long id, BannerSaveDTO dto) {
        Banner exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("轮播图不存在或已被删除");
        }
        validate(dto);

        Banner update = new Banner();
        update.setId(id);
        copy(dto, update);
        update.setSort(dto.getSort() != null ? dto.getSort() : exists.getSort());
        update.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : exists.getStatus());
        this.updateById(update);

        // 清空有效期需要显式置 null（updateById 会忽略 null 字段）
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            this.update(Wrappers.<Banner>lambdaUpdate()
                    .eq(Banner::getId, id)
                    .set(dto.getStartTime() == null, Banner::getStartTime, null)
                    .set(dto.getEndTime() == null, Banner::getEndTime, null));
        }
        homeCacheService.evictIndex();
        log.info("编辑轮播图: id={}, title={}", id, dto.getTitle());
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_DELETE, targetIndex = 1, desc = "删除轮播图")
    public void deleteBanner(Long id) {
        Banner exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("轮播图不存在或已被删除");
        }
        this.removeById(id);
        homeCacheService.evictIndex();
        log.info("删除轮播图: id={}, title={}", id, exists.getTitle());
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_STATUS, targetIndex = 1, desc = "启用/禁用轮播图")
    public void updateStatus(Long id, Integer status) {
        Banner exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("轮播图不存在或已被删除");
        }
        Banner update = new Banner();
        update.setId(id);
        update.setStatus(normalizeStatus(status));
        this.updateById(update);
        homeCacheService.evictIndex();
    }

    @Override
    @OpLog(module = OpLogModule.HOME, action = OpLogModule.ACTION_SORT, desc = "轮播图批量排序")
    @Transactional(rollbackFor = Exception.class)
    public void sortBatch(List<BannerSortDTO.Item> items) {
        List<Long> ids = items.stream().map(BannerSortDTO.Item::getId).distinct().toList();
        // 拖拽期间别的管理员可能刚好删了一张图，静默跳过会让「保存成功」变成假话
        long exists = this.count(Wrappers.<Banner>lambdaQuery().in(Banner::getId, ids));
        if (exists != ids.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "存在已被删除的轮播图，请刷新后重试");
        }
        for (BannerSortDTO.Item item : items) {
            this.update(Wrappers.<Banner>lambdaUpdate()
                    .eq(Banner::getId, item.getId())
                    .set(Banner::getSort, item.getSort()));
        }
        homeCacheService.evictIndex();
        log.info("轮播图批量排序: {} 条", items.size());
    }

    // ==================== 内部方法 ====================

    private void validate(BannerSaveDTO dto) {
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "失效时间必须晚于生效时间");
        }
        String value = StringUtils.hasText(dto.getLinkValue()) ? dto.getLinkValue().trim() : null;
        switch (dto.getLinkType()) {
            case 1 -> {
                if (value == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "请填写要跳转的商品ID");
                }
                parseId(value, "商品ID");
            }
            case 2 -> {
                if (value == null) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "请填写要跳转的分类ID");
                }
                parseId(value, "分类ID");
            }
            case 3 -> {
                if (value == null || !(value.startsWith("http://") || value.startsWith("https://"))) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "外部链接需以 http:// 或 https:// 开头");
                }
            }
            // 4 无跳转：纯展示型海报，link_value 一律写 null（见 copy）
            case 4 -> {
            }
            default -> throw new BusinessException(ResultCode.PARAM_ERROR,
                    "跳转类型取值为1商品 2分类 3外部链接 4无跳转");
        }
    }

    private void parseId(String value, String label) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, label + "必须是正整数");
        }
    }

    private void copy(BannerSaveDTO dto, Banner banner) {
        banner.setTitle(dto.getTitle().trim());
        banner.setImageUrl(dto.getImageUrl().trim());
        banner.setLinkType(dto.getLinkType());
        // 无跳转类型必须把 link_value 清空：否则编辑一张图从「商品详情」改成「无跳转」后，
        // 库里还留着上一个商品ID，将来若有人改回类型1就会跳到一个莫名其妙的地方
        banner.setLinkValue(Integer.valueOf(4).equals(dto.getLinkType()) || !StringUtils.hasText(dto.getLinkValue())
                ? null
                : dto.getLinkValue().trim());
        banner.setStartTime(dto.getStartTime());
        banner.setEndTime(dto.getEndTime());
    }

    private int nextSort() {
        Banner last = this.lambdaQuery().orderByDesc(Banner::getSort).last("limit 1").one();
        return last == null || last.getSort() == null ? 1 : last.getSort() + 1;
    }

    private int normalizeStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("状态取值为0禁用、1启用");
        }
        return status;
    }

    private static BannerVO toVO(Banner banner) {
        BannerVO vo = new BannerVO();
        BeanUtils.copyProperties(banner, vo);
        vo.setLinkTypeLabel(linkTypeLabel(banner.getLinkType()));
        vo.setEffective(isEffective(banner));
        return vo;
    }

    private static String linkTypeLabel(Integer linkType) {
        if (linkType == null) {
            return "未知";
        }
        return switch (linkType) {
            case 1 -> "商品详情";
            case 2 -> "分类页";
            case 3 -> "外部链接";
            case 4 -> "无跳转";
            default -> "未知";
        };
    }

    private static boolean isEffective(Banner banner) {
        if (!Integer.valueOf(1).equals(banner.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean started = banner.getStartTime() == null || !banner.getStartTime().isAfter(now);
        boolean notExpired = banner.getEndTime() == null || !banner.getEndTime().isBefore(now);
        return started && notExpired;
    }
}
