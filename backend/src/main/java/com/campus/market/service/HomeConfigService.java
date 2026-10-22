// 文件路径: src/main/java/com/campus/market/service/HomeConfigService.java
package com.campus.market.service;

import com.campus.market.dto.HomeCategorySortDTO;
import com.campus.market.dto.HomeConfigSaveDTO;
import com.campus.market.vo.HomeCategorySortVO;
import com.campus.market.vo.HomeConfigVO;
import com.campus.market.vo.ProductListVO;

import java.util.List;

/**
 * 首页配置业务接口（模块 12）。
 * <p>
 * 负责三件事：配置项的读写、金刚区（分类展示顺序）的读写、
 * 以及在写操作后清掉首页聚合缓存。
 */
public interface HomeConfigService {

    /**
     * 读取首页配置。缺失的项用代码里的默认值补齐，
     * 保证「表被清空 / 新环境没跑脚本」时首页依然能正常展示。
     */
    HomeConfigVO get();

    /**
     * 保存首页配置。全量覆盖 7 个配置项，保存后清除首页缓存。
     */
    void save(HomeConfigSaveDTO dto);

    /**
     * 金刚区配置列表：返回全部分类的首页展示状态与顺序，
     * 管理端左栏列表与右栏预览都用这一份数据。
     */
    List<HomeCategorySortVO> listCategorySort();

    /**
     * 保存金刚区配置（全量快照），保存后清除首页缓存。
     */
    void saveCategorySort(HomeCategorySortDTO dto);

    /**
     * 按 ID 顺序取已上架商品，供管理端「手动选品」回显卡片。
     * <p>
     * 不复用商品列表接口逐条查：选 20 件商品就要发 20 个请求，
     * 而且列表接口返回的是分页对象，前端还要自己拼装顺序。
     */
    List<ProductListVO> previewProducts(List<Long> ids);
}
