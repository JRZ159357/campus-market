// 文件路径: src/main/java/com/campus/market/controller/FollowController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.service.FollowService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.FollowStateVO;
import com.campus.market.vo.UserFollowVO;
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
 * 关注接口。全部需要登录；不允许关注自己（服务层校验，返回 400）。
 */
@Tag(name = "关注")
@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "关注 / 取消关注（同一接口切换，返回最新关注状态与粉丝数）")
    @PostMapping("/{userId}")
    public Result<FollowStateVO> toggle(
            @Parameter(description = "被关注用户ID") @PathVariable Long userId) {
        FollowStateVO state = followService.toggle(UserContext.getUserId(), userId);
        return Result.ok(state.getFollowed() ? "关注成功" : "已取消关注", state);
    }

    @Operation(summary = "我关注的人（分页，含头像/昵称/认证标识/在售商品数）")
    @GetMapping("/following")
    public Result<IPage<UserFollowVO>> following(
            @Parameter(description = "页码，默认1") @RequestParam(required = false) Integer pageNum,
            @Parameter(description = "每页条数，默认10，最大100") @RequestParam(required = false) Integer pageSize) {
        return Result.ok(followService.following(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "关注我的人（粉丝列表，含是否已回关）")
    @GetMapping("/followers")
    public Result<IPage<UserFollowVO>> followers(
            @Parameter(description = "页码，默认1") @RequestParam(required = false) Integer pageNum,
            @Parameter(description = "每页条数，默认10，最大100") @RequestParam(required = false) Integer pageSize) {
        return Result.ok(followService.followers(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "检查当前用户是否关注该用户")
    @GetMapping("/check/{userId}")
    public Result<Boolean> check(
            @Parameter(description = "目标用户ID") @PathVariable Long userId) {
        return Result.ok(followService.isFollowing(UserContext.getUserId(), userId));
    }
}
