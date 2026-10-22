// 文件路径: src/main/java/com/campus/market/controller/FavoriteController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.service.FavoriteService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.FavoriteFeedVO;
import com.campus.market.vo.FavoriteStateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口。全部需要登录，未登录由拦截器统一返回 401。
 */
@Tag(name = "收藏")
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏 / 取消收藏（同一接口切换，返回最新收藏状态与收藏数）")
    @PostMapping("/{productId}")
    public Result<FavoriteStateVO> toggle(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        FavoriteStateVO state = favoriteService.toggle(UserContext.getUserId(), productId);
        return Result.ok(state.getFavorited() ? "收藏成功" : "已取消收藏", state);
    }

    @Operation(summary = "我的收藏（游标分页，按收藏时间倒序，带商品信息与最新状态）")
    @GetMapping("/my")
    public Result<FavoriteFeedVO> my(
            @Parameter(description = "游标：上一页最后一条收藏记录ID，首页不传")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "每页条数，默认10，最大50")
            @RequestParam(required = false) Integer size) {
        return Result.ok(favoriteService.my(UserContext.getUserId(), cursor, size));
    }

    @Operation(summary = "检查当前用户是否收藏该商品（Redis O(1) 判断）")
    @GetMapping("/check/{productId}")
    public Result<Boolean> check(
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        return Result.ok(favoriteService.isFavorited(UserContext.getUserId(), productId));
    }
}
