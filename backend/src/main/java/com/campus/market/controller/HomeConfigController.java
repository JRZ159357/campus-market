// 文件路径: src/main/java/com/campus/market/controller/HomeConfigController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import io.swagger.v3.oas.annotations.Parameter;
import com.campus.market.dto.HomeCategorySortDTO;
import com.campus.market.dto.HomeConfigSaveDTO;
import com.campus.market.service.HomeConfigService;
import com.campus.market.vo.HomeCategorySortVO;
import com.campus.market.vo.HomeConfigVO;
import com.campus.market.vo.ProductListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 首页配置接口（管理端，仅管理员）。
 * <p>
 * 与 {@link HomeController} 共用 /api/home 前缀但分开成两个类：
 * 本类全部需要登录且仅管理员可写，HomeController 全部是免登录的读接口。
 * 混在一个类里就只能逐个方法加注解，而「这个类的接口是否对外开放」
 * 应该一眼看得出来——安全边界不该藏在每个方法上。
 */
@Tag(name = "首页配置（管理端）")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeConfigController {

    private final HomeConfigService homeConfigService;

    @Operation(summary = "获取首页配置（公告、热门推荐、新品数量）")
    @GetMapping("/config")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<HomeConfigVO> getConfig() {
        return Result.ok(homeConfigService.get());
    }

    @Operation(summary = "保存首页配置")
    @PutMapping("/config")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> saveConfig(@RequestBody @Valid HomeConfigSaveDTO dto) {
        homeConfigService.save(dto);
        return Result.ok("配置已保存，首页已刷新", null);
    }

    @Operation(summary = "获取金刚区分类配置（显示状态与顺序）")
    @GetMapping("/category/sort")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<List<HomeCategorySortVO>> getCategorySort() {
        return Result.ok(homeConfigService.listCategorySort());
    }

    @Operation(summary = "按ID预览手动选品商品（仅已上架，保持传入顺序）")
    @GetMapping("/product/picked")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<List<ProductListVO>> pickedProducts(
            @Parameter(description = "商品ID，逗号分隔") @RequestParam(required = false) List<Long> ids) {
        return Result.ok(homeConfigService.previewProducts(ids));
    }

    @Operation(summary = "保存金刚区分类配置（哪些显示、顺序）")
    @PutMapping("/category/sort")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> saveCategorySort(@RequestBody @Valid HomeCategorySortDTO dto) {
        homeConfigService.saveCategorySort(dto);
        return Result.ok("金刚区配置已保存", null);
    }
}
