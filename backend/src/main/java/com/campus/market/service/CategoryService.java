// 文件路径: src/main/java/com/campus/market/service/CategoryService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.CategorySaveDTO;
import com.campus.market.entity.Category;
import com.campus.market.vo.CategoryVO;

import java.util.List;

/**
 * 商品分类业务接口。
 */
public interface CategoryService extends IService<Category> {

    /**
     * 分类列表。all=true 且为管理员时返回含禁用项的全部分类，否则只返回启用项。
     */
    List<CategoryVO> listCategories(boolean all, String role);

    /**
     * 首页金刚区分类：已在金刚区配置里开启（is_home_show=1）且启用状态的分类，
     * 按 sort 升序。首页聚合专用，不返回禁用项。
     */
    List<CategoryVO> listHomeCategories();

    /**
     * 新增分类，排序值不传时追加到末尾。
     */
    CategoryVO create(CategorySaveDTO dto);

    /**
     * 编辑分类。
     */
    void updateCategory(Long id, CategorySaveDTO dto);

    /**
     * 删除分类。分类下仍有商品时拒绝删除。
     */
    void deleteCategory(Long id);

    /**
     * 启用 / 禁用分类。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取启用中的分类，不存在或已禁用时抛业务异常。发布商品时校验用。
     */
    Category getEnabled(Long id);
}
