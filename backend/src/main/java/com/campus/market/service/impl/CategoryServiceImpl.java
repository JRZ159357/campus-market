// 文件路径: src/main/java/com/campus/market/service/impl/CategoryServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.CategorySaveDTO;
import com.campus.market.entity.Category;
import com.campus.market.entity.Product;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.service.CategoryService;
import com.campus.market.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品分类业务实现。
 * <p>
 * 规则：
 * 1) 分类名全局唯一（逻辑删除后释放）；
 * 2) 分类下仍有商品时禁止删除，避免商品详情取不到分类名；
 * 3) 只有一个列表接口，管理端传 all=true 时返回含禁用项的全部分类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final ProductMapper productMapper;

    @Override
    public List<CategoryVO> listCategories(boolean all, String role) {
        boolean includeDisabled = all && RoleConst.ADMIN.equals(role);
        List<Category> categories = this.lambdaQuery()
                .eq(!includeDisabled, Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)
                .list();
        Map<Long, Long> countMap = productCountMap();
        return categories.stream()
                .map(c -> toVO(c, countMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> listHomeCategories() {
        // 金刚区 = 启用 + 已开启首页展示，两者缺一不可：
        // 分类被禁用后即使配置里还开着，也不该出现在首页（点进去是空列表）
        List<Category> categories = this.lambdaQuery()
                .eq(Category::getStatus, 1)
                .eq(Category::getIsHomeShow, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)
                .list();
        Map<Long, Long> countMap = productCountMap();
        return categories.stream()
                .map(c -> toVO(c, countMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVO create(CategorySaveDTO dto) {
        String name = dto.getName().trim();
        ensureNameUnique(name, null);

        Category category = new Category();
        category.setName(name);
        category.setIcon(StringUtils.hasText(dto.getIcon()) ? dto.getIcon().trim() : null);
        category.setSort(dto.getSort() != null ? dto.getSort() : nextSort());
        category.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : 1);
        this.save(category);
        log.info("新增分类: id={}, name={}", category.getId(), name);
        return toVO(category, 0L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(Long id, CategorySaveDTO dto) {
        Category exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("分类不存在");
        }
        String name = dto.getName().trim();
        ensureNameUnique(name, id);

        Category update = new Category();
        update.setId(id);
        update.setName(name);
        update.setIcon(StringUtils.hasText(dto.getIcon()) ? dto.getIcon().trim() : null);
        update.setSort(dto.getSort());
        update.setStatus(dto.getStatus() != null ? normalizeStatus(dto.getStatus()) : null);
        // icon 允许清空，需要显式 set null
        this.update(update, Wrappers.<Category>lambdaUpdate()
                .eq(Category::getId, id)
                .set(!StringUtils.hasText(dto.getIcon()), Category::getIcon, null));
        log.info("编辑分类: id={}, name={}", id, name);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        Category exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("分类不存在");
        }
        Long count = productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getCategoryId, id));
        if (count != null && count > 0) {
            throw new BusinessException("该分类下还有 " + count + " 件商品，请先转移或删除后再删除分类");
        }
        this.removeById(id);
        log.info("删除分类: id={}, name={}", id, exists.getName());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Category exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException("分类不存在");
        }
        Category update = new Category();
        update.setId(id);
        update.setStatus(normalizeStatus(status));
        this.updateById(update);
    }

    @Override
    public Category getEnabled(Long id) {
        Category category = this.getById(id);
        if (category == null) {
            throw new BusinessException("商品分类不存在");
        }
        if (!Integer.valueOf(1).equals(category.getStatus())) {
            throw new BusinessException("该分类已停用，请选择其他分类");
        }
        return category;
    }

    private Map<Long, Long> productCountMap() {
        List<Map<String, Object>> rows = productMapper.countByCategory();
        Map<Long, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object cid = row.get("categoryId");
            Object cnt = row.get("cnt");
            if (cid instanceof Number c && cnt instanceof Number n) {
                map.put(c.longValue(), n.longValue());
            }
        }
        return map;
    }

    private void ensureNameUnique(String name, Long excludeId) {
        long count = this.lambdaQuery()
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId)
                .count();
        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类名称「" + name + "」已存在");
        }
    }

    private int nextSort() {
        Category last = this.lambdaQuery()
                .orderByDesc(Category::getSort)
                .last("limit 1")
                .one();
        return last == null || last.getSort() == null ? 1 : last.getSort() + 1;
    }

    private int normalizeStatus(Integer status) {
        if (status == null) {
            throw new BusinessException("分类状态不能为空");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("分类状态取值为0禁用、1启用");
        }
        return status;
    }

    private CategoryVO toVO(Category category, Long productCount) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);
        vo.setProductCount(productCount);
        return vo;
    }
}
