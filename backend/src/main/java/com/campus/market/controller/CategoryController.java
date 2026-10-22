// 文件路径: src/main/java/com/campus/market/controller/CategoryController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.CategorySaveDTO;
import com.campus.market.service.CategoryService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品分类接口。小程序取启用分类（免登录），管理端做增删改查。
 */
@Tag(name = "商品分类")
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PublicApi
    @Operation(summary = "分类列表（免登录）。管理端传 all=true 可返回含禁用项的全部分类")
    @GetMapping("/list")
    public Result<List<CategoryVO>> list(
            @Parameter(description = "是否包含禁用分类，仅管理员生效")
            @RequestParam(value = "all", defaultValue = "false") boolean all) {
        return Result.ok(categoryService.listCategories(all, UserContext.getRole()));
    }

    @Operation(summary = "新增分类（管理端）")
    @PostMapping
    @RequireRole(RoleConst.ADMIN)
    public Result<CategoryVO> create(@RequestBody @Valid CategorySaveDTO dto) {
        return Result.ok("新增成功", categoryService.create(dto));
    }

    @Operation(summary = "编辑分类（管理端）")
    @PutMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> update(@Parameter(description = "分类ID") @PathVariable Long id,
                               @RequestBody @Valid CategorySaveDTO dto) {
        categoryService.updateCategory(id, dto);
        return Result.ok("保存成功", null);
    }

    @Operation(summary = "删除分类（管理端，分类下有商品时拒绝）")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.ok("删除成功", null);
    }

    @Operation(summary = "启用/禁用分类（管理端）")
    @PutMapping("/{id}/status")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateStatus(@Parameter(description = "分类ID") @PathVariable Long id,
                                     @Parameter(description = "状态:0禁用 1启用") @RequestParam Integer status) {
        categoryService.updateStatus(id, status);
        return Result.ok(Integer.valueOf(1).equals(status) ? "已启用" : "已禁用", null);
    }
}
