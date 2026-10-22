// 文件路径: src/main/java/com/campus/market/controller/BannerController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.BannerQueryDTO;
import com.campus.market.dto.BannerSaveDTO;
import com.campus.market.dto.BannerSortDTO;
import com.campus.market.service.BannerService;
import com.campus.market.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 轮播图管理接口（管理端）。
 * <p>
 * 路径有两套前缀：{@code /api/banner} 是模块 3 就定下的老路径，
 * {@code /api/home/banner} 是模块 12 把首页相关配置归到 /api/home 下的新路径。
 * 两套都保留是刻意的——老路径在已交付的接口文档、小程序端说明和
 * 模块 3 的回归测试里都出现过，为了「目录整齐」把它们全部改掉，
 * 收益只是好看，风险是某个没被搜到的调用方悄悄 404。
 * 同一个 Controller 挂两个前缀，没有重复实现，也没有版本分叉。
 */
@Tag(name = "轮播图管理")
@RestController
@RequestMapping({"/api/banner", "/api/home/banner"})
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "轮播图分页列表（管理端）")
    @GetMapping("/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<BannerVO>> page(BannerQueryDTO query) {
        return Result.ok(bannerService.pageBanners(query));
    }

    @Operation(summary = "新增轮播图（管理端）")
    @PostMapping
    @RequireRole(RoleConst.ADMIN)
    public Result<BannerVO> create(@RequestBody @Valid BannerSaveDTO dto) {
        return Result.ok("新增成功", bannerService.create(dto));
    }

    @Operation(summary = "批量排序：拖拽后一次保存顺序（管理端）")
    @PutMapping("/sort")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> sort(@RequestBody @Valid BannerSortDTO dto) {
        bannerService.sortBatch(dto.getItems());
        return Result.ok("排序已保存", null);
    }

    @Operation(summary = "编辑轮播图（管理端）")
    @PutMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> update(@Parameter(description = "轮播图ID") @PathVariable Long id,
                               @RequestBody @Valid BannerSaveDTO dto) {
        bannerService.updateBanner(id, dto);
        return Result.ok("保存成功", null);
    }

    @Operation(summary = "删除轮播图（管理端）")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "轮播图ID") @PathVariable Long id) {
        bannerService.deleteBanner(id);
        return Result.ok("删除成功", null);
    }

    @Operation(summary = "启用/禁用轮播图（管理端）")
    @PutMapping("/{id}/status")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateStatus(@Parameter(description = "轮播图ID") @PathVariable Long id,
                                     @Parameter(description = "状态:0禁用 1启用") @RequestParam Integer status) {
        bannerService.updateStatus(id, status);
        return Result.ok(Integer.valueOf(1).equals(status) ? "已启用" : "已禁用", null);
    }
}
