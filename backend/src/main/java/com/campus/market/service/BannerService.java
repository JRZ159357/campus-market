// 文件路径: src/main/java/com/campus/market/service/BannerService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.BannerQueryDTO;
import com.campus.market.dto.BannerSaveDTO;
import com.campus.market.dto.BannerSortDTO;
import com.campus.market.entity.Banner;
import com.campus.market.vo.BannerVO;

import java.util.List;

/**
 * 轮播图业务接口。
 */
public interface BannerService extends IService<Banner> {

    /**
     * 管理端分页列表。
     */
    IPage<BannerVO> pageBanners(BannerQueryDTO query);

    /**
     * 小程序首页轮播图：状态启用且在 [start_time, end_time] 窗口内。
     */
    List<BannerVO> listActive();

    /**
     * 新增轮播图。
     */
    BannerVO create(BannerSaveDTO dto);

    /**
     * 编辑轮播图。
     */
    void updateBanner(Long id, BannerSaveDTO dto);

    /**
     * 删除轮播图（逻辑删除）。
     */
    void deleteBanner(Long id);

    /**
     * 启用/禁用。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 批量排序（拖拽后一次保存）。
     * <p>
     * 只改 sort，不动其它字段：拖拽是纯位置调整，
     * 若沿用「整条 update」的写法，前端必须把标题、图片、有效期全部回传一遍，
     * 任何一个字段在列表里被截断或格式化过，都会在排序时被悄悄写回数据库。
     */
    void sortBatch(List<BannerSortDTO.Item> items);
}
