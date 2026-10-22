// 文件路径: src/main/java/com/campus/market/controller/ProductController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.ForceOfflineDTO;
import com.campus.market.dto.ProductAuditDTO;
import com.campus.market.dto.ProductQueryDTO;
import com.campus.market.dto.ProductSaveDTO;
import com.campus.market.service.ProductService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.ProductDetailVO;
import com.campus.market.vo.ProductFeedVO;
import com.campus.market.vo.ProductListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品接口：卖家发布/管理自己的商品，用户浏览，管理员审核。
 */
@Tag(name = "商品")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "发布商品（仅认证学生）")
    @PostMapping
    @RequireRole(RoleConst.STUDENT)
    public Result<Long> publish(@RequestBody @Valid ProductSaveDTO dto) {
        Long id = productService.publish(UserContext.getUserId(), dto);
        return Result.ok("发布成功，等待管理员审核", id);
    }

    @Operation(summary = "编辑商品（卖家本人，待审核/已下架状态可编辑，保存后重新进入待审核）")
    @PutMapping("/{id}")
    @RequireRole(RoleConst.STUDENT)
    public Result<Void> update(@Parameter(description = "商品ID") @PathVariable Long id,
                               @RequestBody @Valid ProductSaveDTO dto) {
        productService.updateProduct(UserContext.getUserId(), id, dto);
        return Result.ok("保存成功，已重新提交审核", null);
    }

    @Operation(summary = "下架商品（卖家本人）")
    @PutMapping("/{id}/offline")
    @RequireRole(RoleConst.STUDENT)
    public Result<Void> offline(@Parameter(description = "商品ID") @PathVariable Long id) {
        productService.offline(UserContext.getUserId(), id);
        return Result.ok("已下架", null);
    }

    @Operation(summary = "删除商品（卖家本人，已上架需先下架）")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.STUDENT)
    public Result<Void> delete(@Parameter(description = "商品ID") @PathVariable Long id) {
        productService.deleteProduct(UserContext.getUserId(), id);
        return Result.ok("删除成功", null);
    }

    @Operation(summary = "我的发布（分页，可按状态筛选）")
    @GetMapping("/my")
    @RequireRole(RoleConst.STUDENT)
    public Result<IPage<ProductListVO>> my(ProductQueryDTO query) {
        return Result.ok(productService.myPage(UserContext.getUserId(), query));
    }

    @PublicApi
    @Operation(summary = "商品详情（免登录）。非在售商品仅卖家本人与管理员可见")
    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@Parameter(description = "商品ID") @PathVariable Long id) {
        return Result.ok(productService.detail(id, UserContext.getUserIdOrNull(), UserContext.getRole()));
    }

    @PublicApi
    @Operation(summary = "商品列表（免登录）。支持分类、关键词、价格区间、排序")
    @GetMapping("/page")
    public Result<IPage<ProductListVO>> page(ProductQueryDTO query) {
        return Result.ok(productService.pageQuery(query));
    }

    @PublicApi
    @Operation(summary = "关键词搜索（标题+描述模糊匹配，支持分类、价格区间、排序）")
    @GetMapping("/search")
    public Result<IPage<ProductListVO>> search(ProductQueryDTO query) {
        return Result.ok(productService.search(query));
    }

    @PublicApi
    @Operation(summary = "热门商品（浏览量+收藏量加权，取前10，Redis 缓存5分钟）")
    @GetMapping("/hot")
    public Result<List<ProductListVO>> hot() {
        return Result.ok(productService.hot());
    }

    @PublicApi
    @Operation(summary = "商品瀑布流（游标分页，首页/分类页使用，避免深分页）")
    @GetMapping("/feed")
    public Result<ProductFeedVO> feed(
            @Parameter(description = "游标：上一页最后一条商品ID，首页不传")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "每页条数，默认10，最大50")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "分类ID，不传为全部")
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(productService.feed(cursor, size, categoryId));
    }

    @Operation(summary = "商品审核列表（管理端，可按状态/关键词/分类/价格/时间筛选）")
    @GetMapping("/audit/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<ProductListVO>> auditPage(ProductQueryDTO query) {
        return Result.ok(productService.auditPage(query));
    }

    @Operation(summary = "审核商品（管理端，驳回需填原因）")
    @PutMapping("/{id}/audit")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> audit(@Parameter(description = "商品ID") @PathVariable Long id,
                              @RequestBody @Valid ProductAuditDTO dto) {
        productService.audit(id, UserContext.getUserId(), dto);
        return Result.ok(Integer.valueOf(1).equals(dto.getStatus()) ? "审核通过，商品已上架" : "已驳回", null);
    }

    @Operation(summary = "强制下架（管理端，状态置为违规下架）")
    @PutMapping("/{id}/force-offline")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> forceOffline(@Parameter(description = "商品ID") @PathVariable Long id,
                                    @RequestBody @Valid ForceOfflineDTO dto) {
        productService.forceOffline(id, UserContext.getUserId(), dto.getReason());
        return Result.ok("已强制下架", null);
    }
}
