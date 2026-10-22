// 文件路径: src/main/java/com/campus/market/service/impl/ProductServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.RedisKey;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.SensitiveCheckResult;
import com.campus.market.dto.ProductAuditDTO;
import com.campus.market.dto.ProductQueryDTO;
import com.campus.market.dto.ProductSaveDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.CategoryService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.FavoriteService;
import com.campus.market.service.FollowService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.HomeCacheService;
import com.campus.market.service.ProductService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.service.UserService;
import com.campus.market.service.ViewCountService;
import com.campus.market.vo.ProductDetailVO;
import com.campus.market.vo.ProductFeedVO;
import com.campus.market.vo.ProductListVO;
import com.campus.market.vo.ProductSellerVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品业务实现。
 * <p>
 * 关键规则：
 * 1) 发布需 role=student 且 student_verified=1，普通用户被拒绝（403）；
 * 2) 发布额度按"待审核 + 已上架"计数，防止用户靠堆积待审核商品绕过上限；
 * 3) 列表排序默认「认证学生优先」，依赖冗余字段 is_verified_seller，避免 JOIN 排序；
 * 4) 非在售商品只有卖家本人、管理员、客服可查看，防止下架商品被人翻出来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    /** 认证学生默认发布上限，与 sys_user.publish_limit 配合使用 */
    private static final int DEFAULT_PUBLISH_LIMIT = 20;

    /** 商品图片上限 */
    private static final int MAX_IMAGES = 9;

    /** 热门商品缓存时长：5 分钟，兼顾实时性与数据库压力 */
    private static final Duration HOT_CACHE_TTL = Duration.ofMinutes(5);

    /** 热门商品数量 */
    private static final int HOT_SIZE = 10;

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final ViewCountService viewCountService;
    private final FavoriteService favoriteService;
    private final FollowService followService;
    private final NoticeService noticeService;
    private final DashboardCacheService dashboardCacheService;
    private final HomeCacheService homeCacheService;
    private final SensitiveWordService sensitiveWordService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ==================== 卖家侧 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long sellerId, ProductSaveDTO dto) {
        SysUser seller = userService.getById(sellerId);
        if (seller == null) {
            throw new BusinessException("用户不存在");
        }
        if (!RoleConst.STUDENT.equals(seller.getRole()) || !Integer.valueOf(1).equals(seller.getStudentVerified())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅认证学生可发布商品，请先完成学生认证");
        }

        int limit = publishLimitOf(seller);
        long occupied = baseMapper.countOccupied(sellerId);
        if (occupied >= limit) {
            throw new BusinessException("你同时在架与待审核的商品已达 " + limit + " 件上限，请先下架部分商品");
        }

        Category category = categoryService.getEnabled(dto.getCategoryId());

        String title = dto.getTitle().trim();
        String description = StringUtils.hasText(dto.getDescription())
                ? dto.getDescription().trim() : null;

        /*
         * 敏感词过滤（模块11）。
         *
         * 放在「额度校验之后、写库之前」是有讲究的：
         *   - 放在额度校验之前，用户明明超了额度却先被告知「含违规词」，
         *     改完文案再提交又被告知超额，白改一次；
         *   - 放在写库之后，违规内容已经落库，还得靠事后清理兜底。
         *
         * 两级处理：拦截级直接抛异常（提示命中哪个词，让用户能改），
         * 警告级照常发布但打标记 + 通知管理员（机器判断不了语境的情况，
         * 例如「加微信」可能真的是约线下验货）。
         */
        SensitiveCheckResult sensitive = sensitiveWordService.check(title, description);
        if (sensitive.isBlocked()) {
            // 拦截即通知：用户在小程序里只看到一句 toast，划走就没了；
            // 通知中心留一条记录，他回头能看到「我那条为什么没发出去」。
            // 通知走独立事务（REQUIRES_NEW），不会随下面的异常一起回滚。
            notifySensitiveBlocked(sellerId, title, sensitive);
            throw new BusinessException(ResultCode.PARAM_ERROR, sensitive.blockMessage());
        }

        Product product = new Product();
        product.setSellerId(sellerId);
        product.setCategoryId(category.getId());
        product.setTitle(title);
        product.setDescription(description);
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setStatus(ProductStatus.PENDING);
        product.setViewCount(0);
        product.setFavoriteCount(0);
        product.setIsVerifiedSeller(1);
        product.setNeedManualReview(sensitive.isWarned() ? 1 : 0);
        product.setSensitiveHit(sensitive.isWarned() ? sensitive.joinWords() : null);
        this.save(product);
        saveImages(product.getId(), dto.getImages());

        // 警告级命中要让管理员看见：商品已经进了待审核队列，
        // 但如果不额外提醒，它就混在几十条普通待审核里被一键通过了
        if (sensitive.isWarned()) {
            notifyManualReview(product, sellerId, sensitive);
        }

        evictProductCaches();
        log.info("商品发布: productId={}, sellerId={}, categoryId={}, needManualReview={}",
                product.getId(), sellerId, category.getId(), product.getNeedManualReview());
        return product.getId();
    }

    /**
     * 敏感词拦截时给卖家留一条通知。
     * <p>
     * 只发通知不改任何业务数据；异常由内部吞掉——通知失败绝不能
     * 掩盖「内容违规」这个真正要告诉用户的结论。
     */
    private void notifySensitiveBlocked(Long sellerId, String title, SensitiveCheckResult sensitive) {
        try {
            noticeService.notifyAudit(sellerId, "商品发布被拦截",
                    "《" + title + "》未发布成功：内容包含违规词（" + sensitive.joinWords()
                            + "）。请修改标题或描述后重新提交。",
                    null);
        } catch (Exception e) {
            log.warn("敏感词拦截通知发送失败 sellerId={}：{}", sellerId, e.getMessage());
        }
    }

    /**
     * 命中警告级敏感词时通知管理员与卖家。
     * <p>
     * 两个通知的措辞刻意不同：给管理员的是「去看一眼」，
     * 给卖家的是「已发布，但可能被驳回」——如果给卖家也发
     * 「你的内容含敏感词」，他会以为发布失败了反复重试。
     */
    private void notifyManualReview(Product product, Long sellerId, SensitiveCheckResult sensitive) {
        try {
            noticeService.notifyAllAdmins("有商品命中敏感词，待人工复核",
                    "商品《" + product.getTitle() + "》发布时命中敏感词："
                            + sensitive.joinWords() + "，已自动标记待人工复核。",
                    product.getId());
        } catch (Exception e) {
            log.warn("敏感词复核通知发送失败(管理员) productId={}：{}", product.getId(), e.getMessage());
        }
        try {
            noticeService.notifyAudit(sellerId, "商品已提交审核",
                    "《" + product.getTitle() + "》已提交，平台会尽快审核。"
                            + "内容中可能含有需要人工确认的表述，审核时间可能稍长。",
                    product.getId());
        } catch (Exception e) {
            log.warn("敏感词复核通知发送失败(卖家) productId={}：{}", product.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long sellerId, Long id, ProductSaveDTO dto) {
        Product product = requireOwned(id, sellerId);
        int status = product.getStatus() == null ? -1 : product.getStatus();
        if (status != ProductStatus.PENDING && status != ProductStatus.OFFLINE) {
            throw new BusinessException("当前状态不支持编辑，请先下架商品后再修改");
        }
        Category category = categoryService.getEnabled(dto.getCategoryId());
        SysUser seller = userService.getById(sellerId);

        String title = dto.getTitle().trim();
        String description = StringUtils.hasText(dto.getDescription())
                ? dto.getDescription().trim() : null;

        /*
         * 编辑也要过一遍敏感词（模块11）。
         *
         * 只在发布时过滤是不够的：先发一个正常商品通过审核，
         * 再「编辑」把标题改成违禁内容——编辑会把状态打回待审核，
         * 但如果不在这里再检查一次，违规文案就已经躺在库里了。
         * 这条路径比发布更隐蔽，因为它不涉及图片上传，
         * 一个脚本几秒就能把全店商品标题改一遍。
         */
        SensitiveCheckResult sensitive = sensitiveWordService.check(title, description);
        if (sensitive.isBlocked()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, sensitive.blockMessage());
        }

        this.update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getCategoryId, category.getId())
                .set(Product::getTitle, title)
                .set(Product::getDescription, description)
                .set(Product::getPrice, dto.getPrice())
                .set(Product::getOriginalPrice, dto.getOriginalPrice())
                .set(Product::getIsVerifiedSeller, seller != null && Integer.valueOf(1).equals(seller.getStudentVerified()) ? 1 : 0)
                .set(Product::getStatus, ProductStatus.PENDING)
                // 重新提交后复核标记按本次内容重算，
                // 不能沿用上一次的（用户可能已经把敏感词删掉了）
                .set(Product::getNeedManualReview, sensitive.isWarned() ? 1 : 0)
                .set(Product::getSensitiveHit, sensitive.isWarned() ? sensitive.joinWords() : null)
                .set(Product::getAuditReason, null)
                .set(Product::getAuditorId, null)
                .set(Product::getAuditTime, null)
                .set(Product::getUpdateTime, LocalDateTime.now()));

        if (sensitive.isWarned()) {
            Product refreshed = new Product();
            refreshed.setId(id);
            refreshed.setTitle(title);
            notifyManualReview(refreshed, sellerId, sensitive);
        }

        replaceImages(id, dto.getImages());
        evictProductCaches();
        log.info("商品编辑后重新提交审核: productId={}, sellerId={}", id, sellerId);
    }

    @Override
    public void offline(Long sellerId, Long id) {
        Product product = requireOwned(id, sellerId);
        if (!Integer.valueOf(ProductStatus.ON_SALE).equals(product.getStatus())) {
            throw new BusinessException("只有已上架的商品可以下架");
        }
        this.update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .set(Product::getStatus, ProductStatus.OFFLINE)
                .set(Product::getUpdateTime, LocalDateTime.now()));
        evictHotCache();
        evictProductCaches();
        log.info("卖家下架商品: productId={}, sellerId={}", id, sellerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long sellerId, Long id) {
        Product product = requireOwned(id, sellerId);
        int status = product.getStatus() == null ? -1 : product.getStatus();
        if (status == ProductStatus.ON_SALE) {
            throw new BusinessException("请先下架商品后再删除");
        }
        if (status == ProductStatus.SOLD) {
            throw new BusinessException("已售出的商品不能删除，交易记录需保留");
        }
        this.removeById(id);
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, id));
        viewCountService.clear(id);
        evictHotCache();
        evictProductCaches();
        log.info("卖家删除商品: productId={}, sellerId={}", id, sellerId);
    }

    @Override
    public IPage<ProductListVO> myPage(Long sellerId, ProductQueryDTO query) {
        Page<Product> page = this.page(buildPage(query), Wrappers.<Product>lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(query.getStatus() != null, Product::getStatus, query.getStatus())
                .orderByDesc(Product::getCreateTime));
        return convertPage(page);
    }

    @Override
    public IPage<ProductListVO> pageBySeller(Long sellerId, Integer status, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<Product> page = this.page(new Page<>(current, size), Wrappers.<Product>lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreateTime)
                .orderByDesc(Product::getId));
        return convertPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offlineAllBySeller(Long sellerId) {
        // 只下架「待审核 / 已上架」两种状态：已售出与已下架的本来就不在架上，
        // 违规下架的商品保留状态，方便管理员追溯是哪一批被处理的
        // 用 baseMapper.update 而不是 this.update：后者返回 boolean，拿不到受影响行数
        int rows = baseMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .eq(Product::getSellerId, sellerId)
                .in(Product::getStatus, ProductStatus.OFFLINEABLE_STATUSES)
                .set(Product::getStatus, ProductStatus.OFFLINE)
                .set(Product::getUpdateTime, LocalDateTime.now()));
        if (rows > 0) {
            evictHotCache();
            // 批量下架改变了在售数与商品状态分布，仪表盘要跟着重算
            evictProductCaches();
        }
        return rows;
    }

    // ==================== 用户侧 ====================

    @Override
    public ProductDetailVO detail(Long id, Long viewerId, String viewerRole) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }

        boolean owner = viewerId != null && viewerId.equals(product.getSellerId());
        boolean privileged = RoleConst.ADMIN.equals(viewerRole) || RoleConst.CUSTOMER_SERVICE.equals(viewerRole);
        // 已售出商品对所有人可见：买家会从订单详情点回商品页，
        // 若这里拦住，历史订单里的商品卡片就成了死链。
        boolean sold = Integer.valueOf(ProductStatus.SOLD).equals(product.getStatus());
        if (!Integer.valueOf(ProductStatus.ON_SALE).equals(product.getStatus()) && !sold && !owner && !privileged) {
            throw new BusinessException("该商品已下架或正在审核中");
        }

        // 浏览数：卖家自己查看不计数；计数写 Redis，由定时任务批量回写 MySQL。
        // 展示值 = 库中值 + 尚未回写的增量，卖家与访客看到的是同一个总浏览量。
        int dbViews = product.getViewCount() == null ? 0 : product.getViewCount();
        if (!owner) {
            viewCountService.increment(id);
        }
        product.setViewCount(dbViews + viewCountService.pending(id));

        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setSellerId(product.getSellerId());
        vo.setCategoryId(product.getCategoryId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setOriginalPrice(product.getOriginalPrice());
        vo.setStatus(product.getStatus());
        vo.setStatusLabel(ProductStatus.label(product.getStatus()));
        vo.setViewCount(product.getViewCount());
        vo.setFavoriteCount(product.getFavoriteCount());
        // 收藏态：访客匿名访问（viewerId=null）固定 false，登录用户走 Redis 集合 O(1) 判断
        vo.setFavorited(viewerId != null && favoriteService.isFavorited(viewerId, id));
        vo.setIsVerifiedSeller(Integer.valueOf(1).equals(product.getIsVerifiedSeller()));
        vo.setAuditReason(product.getAuditReason());
        vo.setCreateTime(product.getCreateTime());
        vo.setIsOwner(owner);
        vo.setImages(listImageUrls(product.getId()));

        Category category = categoryMapper.selectById(product.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setSeller(buildSellerVO(product.getSellerId(), viewerId));
        return vo;
    }

    @Override
    public IPage<ProductListVO> pageQuery(ProductQueryDTO query) {
        LambdaQueryWrapper<Product> wrapper = buildFilter(query);
        wrapper.eq(Product::getStatus, ProductStatus.ON_SALE);
        applySort(wrapper, query.getSort());
        return convertPage(this.page(buildPage(query), wrapper));
    }

    @Override
    public IPage<ProductListVO> search(ProductQueryDTO query) {
        // 搜索与列表共用同一套过滤/排序，避免两处规则漂移；
        // keyword 为空时退化为普通列表（前端搜索页不会在空关键词时发请求）
        return pageQuery(query);
    }

    @Override
    public List<ProductListVO> hot() {
        // 热门商品在首页、分类页都会被请求，直接打库会造成重复扫描大表；
        // 缓存 5 分钟，并放入内存缓存失败时的降级路径（缓存异常不影响页面）
        try {
            String cached = redis.opsForValue().get(RedisKey.PRODUCT_HOT);
            if (StringUtils.hasText(cached)) {
                return objectMapper.readValue(cached, new TypeReference<List<ProductListVO>>() {
                });
            }
        } catch (Exception e) {
            log.warn("读取热门商品缓存失败，回源数据库: {}", e.getMessage());
        }

        // 加权排序：收藏意愿比浏览更强，权重取 3
        List<Product> records = baseMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .last("ORDER BY (view_count + favorite_count * 3) DESC, create_time DESC LIMIT " + HOT_SIZE));
        List<ProductListVO> vos = buildVOList(records);

        try {
            redis.opsForValue().set(RedisKey.PRODUCT_HOT, objectMapper.writeValueAsString(vos), HOT_CACHE_TTL);
        } catch (Exception e) {
            log.warn("写入热门商品缓存失败: {}", e.getMessage());
        }
        return vos;
    }

    @Override
    public List<ProductListVO> recommend(int limit) {
        int size = limit <= 0 ? 10 : Math.min(limit, 50);
        List<Product> records = this.lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .orderByDesc(Product::getIsVerifiedSeller)
                .orderByDesc(Product::getViewCount)
                .orderByDesc(Product::getCreateTime)
                .last("limit " + size)
                .list();
        return buildVOList(records);
    }

    @Override
    public List<ProductListVO> hotTop(int limit) {
        int size = limit <= 0 ? 10 : Math.min(limit, 20);
        // 与 hot() 同一套加权公式（收藏意愿比浏览更强，权重取 3），
        // 差别只在数量由调用方指定；写成两套公式迟早会演变成「首页热门」和
        // 「分类页热门」算出两份不同的榜单
        List<Product> records = baseMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .last("ORDER BY (view_count + favorite_count * 3) DESC, id DESC LIMIT " + size));
        return buildVOList(records);
    }

    @Override
    public List<ProductListVO> listOnSaleByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Product> found = this.lambdaQuery()
                .in(Product::getId, ids)
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .list()
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        // 按传入顺序重排：IN 查询的返回顺序由数据库决定，直接用它会让
        // 运营排好的「第 1 件」变成随机的一件
        List<Product> ordered = ids.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return buildVOList(ordered);
    }

    @Override
    public List<ProductListVO> latest(int limit) {
        int size = limit <= 0 ? 4 : Math.min(limit, 50);
        // 按 id 倒序而非 create_time：id 自增等价于发布顺序，
        // 且与 /feed 游标分页的排序一致，首页首屏数据可直接作为第一页，
        // 翻页时不会出现重复或漏项。
        List<Product> records = this.lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .orderByDesc(Product::getId)
                .last("limit " + size)
                .list();
        return buildVOList(records);
    }

    @Override
    public ProductFeedVO feed(Long cursor, Integer size, Long categoryId) {
        int limit = size == null || size < 1 ? 10 : Math.min(size, 50);
        List<Product> records = this.lambdaQuery()
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .lt(cursor != null && cursor > 0, Product::getId, cursor)
                .orderByDesc(Product::getId)
                // 多取一条用于判断是否还有下一页，避免额外 count 查询
                .last("limit " + (limit + 1))
                .list();

        boolean hasMore = records.size() > limit;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, limit));
        }
        ProductFeedVO vo = new ProductFeedVO();
        vo.setRecords(buildVOList(records));
        vo.setHasMore(hasMore);
        vo.setNextCursor(hasMore && !records.isEmpty() ? records.get(records.size() - 1).getId() : null);
        return vo;
    }

    // ==================== 管理端 ====================

    @Override
    public IPage<ProductListVO> auditPage(ProductQueryDTO query) {
        LambdaQueryWrapper<Product> wrapper = buildFilter(query);
        wrapper.eq(query.getStatus() != null, Product::getStatus, query.getStatus());
        // 命中警告级敏感词的商品单独成列（审核页的「待人工复核」Tab）
        wrapper.eq(query.getNeedManualReview() != null,
                Product::getNeedManualReview, query.getNeedManualReview());
        if (query.getStatus() == null) {
            // 未筛选状态时待审核排最前，方便审核人员处理
            wrapper.orderByAsc(Product::getStatus).orderByDesc(Product::getCreateTime);
        } else {
            wrapper.orderByDesc(Product::getCreateTime);
        }
        return convertPage(this.page(buildPage(query), wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, Long auditorId, ProductAuditDTO dto) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!Integer.valueOf(ProductStatus.PENDING).equals(product.getStatus())) {
            throw new BusinessException("该商品不在待审核状态，不能重复审核");
        }
        boolean pass = Integer.valueOf(1).equals(dto.getStatus());
        if (!pass && !StringUtils.hasText(dto.getAuditReason())) {
            throw new BusinessException("驳回时必须填写驳回原因");
        }

        this.update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getStatus, pass ? ProductStatus.ON_SALE : ProductStatus.OFFLINE)
                .set(Product::getAuditReason, pass ? null : dto.getAuditReason().trim())
                .set(Product::getAuditorId, auditorId)
                .set(Product::getAuditTime, LocalDateTime.now())
                // 复核标记在人工看过之后就该消失：它表示「还没人看过」，
                // 一直留着会让这个 Tab 越积越多，最后没人再点开它。
                // 命中的词（sensitive_hit）保留，作为事后追溯的依据。
                .set(Product::getNeedManualReview, 0)
                .set(Product::getUpdateTime, LocalDateTime.now()));
        evictHotCache();
        evictProductCaches();

        // 审核结果必须让卖家第一时间知道，否则他只能反复进「我的发布」刷新状态
        try {
            if (pass) {
                noticeService.notifyAudit(product.getSellerId(), "商品审核通过",
                        "《" + product.getTitle() + "》已通过审核并上架，同学们现在可以看到它了。",
                        product.getId());
            } else {
                noticeService.notifyAudit(product.getSellerId(), "商品审核未通过",
                        "《" + product.getTitle() + "》未通过审核：" + dto.getAuditReason().trim()
                                + "。修改后可重新提交审核。",
                        product.getId());
            }
        } catch (Exception e) {
            log.warn("商品审核通知发送失败 productId={}：{}", id, e.getMessage());
        }

        log.info("商品审核: productId={}, pass={}, auditorId={}", id, pass, auditorId);
    }

    @Override
    @OpLog(module = OpLogModule.PRODUCT, action = OpLogModule.ACTION_OFFLINE, targetIndex = 1,
            desc = "强制下架违规商品")
    public void forceOffline(Long id, Long auditorId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写强制下架原因");
        }
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!Integer.valueOf(ProductStatus.ON_SALE).equals(product.getStatus())) {
            throw new BusinessException("只有已上架的商品可以强制下架");
        }
        this.update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .set(Product::getStatus, ProductStatus.VIOLATION)
                .set(Product::getAuditReason, reason.trim())
                .set(Product::getAuditorId, auditorId)
                .set(Product::getAuditTime, LocalDateTime.now())
                .set(Product::getUpdateTime, LocalDateTime.now()));
        evictHotCache();
        evictProductCaches();
        log.warn("管理员强制下架商品: productId={}, reason={}, auditorId={}", id, reason, auditorId);
    }

    @Override
    @OpLog(module = OpLogModule.PRODUCT, action = OpLogModule.ACTION_OFFLINE, targetIndex = 1,
            desc = "举报成立，强制下架商品")
    public void forceOfflineByReport(Long id, Long operatorId, String reason) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!ProductStatus.OFFLINEABLE_STATUSES.contains(product.getStatus())) {
            throw new BusinessException("只有待审核或已上架的商品可以强制下架");
        }
        doForceOffline(product, operatorId, reason);
    }

    /**
     * 置为违规下架的共同实现。抽出来是因为「管理端强制下架」与
     * 「举报核实强制下架」的写库动作完全一致，只有入参校验不同。
     */
    private void doForceOffline(Product product, Long operatorId, String reason) {
        this.update(Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, product.getId())
                .set(Product::getStatus, ProductStatus.VIOLATION)
                .set(Product::getAuditReason, reason)
                .set(Product::getAuditorId, operatorId)
                .set(Product::getAuditTime, LocalDateTime.now())
                .set(Product::getUpdateTime, LocalDateTime.now()));
        evictHotCache();
        evictProductCaches();
        log.warn("强制下架商品: productId={}, reason={}, operatorId={}",
                product.getId(), reason, operatorId);
    }

    // ==================== 内部方法 ====================

    private Product requireOwned(Long id, Long sellerId) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return product;
    }

    private int publishLimitOf(SysUser seller) {
        Integer limit = seller.getPublishLimit();
        if (limit == null || limit <= 0) {
            return DEFAULT_PUBLISH_LIMIT;
        }
        return limit;
    }

    private void saveImages(Long productId, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int sort = 1;
        for (String url : urls) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            if (sort > MAX_IMAGES) {
                break;
            }
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setImageUrl(url.trim());
            image.setSort(sort++);
            productImageMapper.insert(image);
        }
    }

    private void replaceImages(Long productId, List<String> urls) {
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, productId));
        saveImages(productId, urls);
    }

    private List<String> listImageUrls(Long productId) {
        return productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSort)
                        .orderByAsc(ProductImage::getId))
                .stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());
    }

    private ProductSellerVO buildSellerVO(Long sellerId, Long viewerId) {
        SysUser seller = userService.getById(sellerId);
        ProductSellerVO vo = new ProductSellerVO();
        if (seller == null) {
            vo.setId(sellerId);
            vo.setNickname("已注销用户");
            vo.setCreditScore(0);
            vo.setStudentVerified(false);
            vo.setOnSaleCount(0L);
            vo.setFansCount(0);
            vo.setFollowed(false);
            return vo;
        }
        vo.setId(seller.getId());
        vo.setNickname(seller.getNickname());
        vo.setAvatar(seller.getAvatar());
        vo.setCreditScore(seller.getCreditScore());
        vo.setStudentVerified(Integer.valueOf(1).equals(seller.getStudentVerified()));
        vo.setFansCount(seller.getFansCount() == null ? 0 : seller.getFansCount());
        // 卖家本人看自己的商品不展示关注按钮，followed 恒为 false
        vo.setFollowed(viewerId != null && !viewerId.equals(sellerId)
                && followService.isFollowing(viewerId, sellerId));
        vo.setOnSaleCount(this.lambdaQuery()
                .eq(Product::getSellerId, sellerId)
                .eq(Product::getStatus, ProductStatus.ON_SALE)
                .count());
        return vo;
    }

    private LambdaQueryWrapper<Product> buildFilter(ProductQueryDTO query) {
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        BigDecimal minPrice = query.getMinPrice();
        BigDecimal maxPrice = query.getMaxPrice();
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "最低价格不能高于最高价格");
        }
        LocalDateTime start = parseTime(query.getStartTime(), false);
        LocalDateTime end = parseTime(query.getEndTime(), true);

        return Wrappers.<Product>lambdaQuery()
                .eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                .and(keyword != null, w -> w
                        .like(Product::getTitle, keyword)
                        .or()
                        .like(Product::getDescription, keyword))
                .ge(minPrice != null, Product::getPrice, minPrice)
                .le(maxPrice != null, Product::getPrice, maxPrice)
                .ge(start != null, Product::getCreateTime, start)
                .le(end != null, Product::getCreateTime, end);
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sort) {
        String key = StringUtils.hasText(sort) ? sort.trim() : "default";
        switch (key) {
            case "new" -> wrapper.orderByDesc(Product::getCreateTime);
            case "priceAsc" -> wrapper.orderByAsc(Product::getPrice).orderByDesc(Product::getCreateTime);
            case "priceDesc" -> wrapper.orderByDesc(Product::getPrice).orderByDesc(Product::getCreateTime);
            case "hot" -> wrapper.orderByDesc(Product::getViewCount).orderByDesc(Product::getCreateTime);
            // 综合排序：认证学生商品优先，其次按发布时间
            default -> wrapper.orderByDesc(Product::getIsVerifiedSeller).orderByDesc(Product::getCreateTime);
        }
    }

    private Page<Product> buildPage(ProductQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        return new Page<>(pageNum, pageSize);
    }

    /**
     * 批量拼装列表 VO：一次取图片、卖家、分类，避免 N+1 查询。
     */
    private IPage<ProductListVO> convertPage(IPage<Product> page) {
        Page<ProductListVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(buildVOList(page.getRecords()));
        return result;
    }

    /**
     * 商品可见性变化后，统一失效所有受影响的缓存（模块 12 收拢）。
     * <p>
     * 原先每个调用点直接写 dashboardCacheService.evictOnProductChange()，
     * 加了首页缓存之后就变成「每个点写两行」，漏一处就是一处脏数据。
     * 收拢成一个方法后，调用点只需要说「商品变了」，
     * 具体清了哪些缓存由这里决定。
     * <p>
     * 首页缓存本来 5 分钟后会自然过期，这里仍然主动清：
     * 审核通过一件商品后，管理员大概率立刻去小程序看效果，
     * 「等了五分钟才出现」会被当成 bug 报上来。
     */
    private void evictProductCaches() {
        dashboardCacheService.evictOnProductChange();
        homeCacheService.evictIndex();
    }

    private List<ProductListVO> buildVOList(List<Product> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = records.stream().map(Product::getId).toList();
        Map<Long, String> coverMap = new HashMap<>();
        productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                        .in(ProductImage::getProductId, productIds)
                        .orderByAsc(ProductImage::getSort)
                        .orderByAsc(ProductImage::getId))
                .forEach(img -> coverMap.putIfAbsent(img.getProductId(), img.getImageUrl()));

        List<Long> sellerIds = records.stream().map(Product::getSellerId).distinct().toList();
        Map<Long, SysUser> sellerMap = userService.listByIds(sellerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        List<Long> categoryIds = records.stream().map(Product::getCategoryId).distinct().toList();
        Map<Long, String> categoryMap = categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        return records.stream().map(product -> {
            ProductListVO vo = new ProductListVO();
            vo.setId(product.getId());
            vo.setSellerId(product.getSellerId());
            SysUser seller = sellerMap.get(product.getSellerId());
            if (seller != null) {
                vo.setSellerNickname(seller.getNickname());
                vo.setSellerAvatar(seller.getAvatar());
            }
            vo.setCategoryId(product.getCategoryId());
            vo.setCategoryName(categoryMap.get(product.getCategoryId()));
            vo.setTitle(product.getTitle());
            vo.setPrice(product.getPrice());
            vo.setOriginalPrice(product.getOriginalPrice());
            vo.setCover(coverMap.get(product.getId()));
            vo.setStatus(product.getStatus());
            vo.setStatusLabel(ProductStatus.label(product.getStatus()));
            vo.setViewCount(product.getViewCount());
            vo.setFavoriteCount(product.getFavoriteCount());
            vo.setIsVerifiedSeller(product.getIsVerifiedSeller());
            vo.setNeedManualReview(product.getNeedManualReview());
            vo.setSensitiveHit(product.getSensitiveHit());
            vo.setAuditReason(product.getAuditReason());
            vo.setCreateTime(product.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 热门缓存与商品状态强相关，状态变化后立即失效，避免首页继续展示已下架商品。
     */
    private void evictHotCache() {
        try {
            redis.delete(RedisKey.PRODUCT_HOT);
        } catch (Exception e) {
            log.warn("清理热门商品缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 解析日期参数：只传日期时，起始补 00:00:00，结束补 23:59:59。
     */
    private LocalDateTime parseTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(LocalTime.of(23, 59, 59)) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text, DATE_TIME_FMT);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "时间格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }
}
