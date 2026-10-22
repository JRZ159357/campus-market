// 文件路径: src/main/java/com/campus/market/controller/UserController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.dto.UserAccountDeleteDTO;
import com.campus.market.dto.UserPhoneUpdateDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.service.AuthService;
import com.campus.market.service.UserCenterService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.ProductListVO;
import com.campus.market.vo.UserHomeVO;
import com.campus.market.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人中心与卖家主页接口。
 * <p>
 * 路径规范：/api/user/{操作}。
 * <p>
 * 免登录与需登录的划分：
 * <ul>
 *   <li><b>卖家主页</b>（/home/{userId}、/products/{userId}）免登录——
 *       商品详情页、商品卡片都要能直接跳到卖家主页，不能要求先登录；</li>
 *   <li><b>个人中心与资料设置</b>全部需登录——
 *       这些操作只影响「我自己」，未登录时连「我是谁」都不知道。</li>
 * </ul>
 */
@Tag(name = "个人中心与卖家主页")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserCenterService userCenterService;
    private final AuthService authService;

    @Operation(summary = "个人中心聚合数据（用户信息 / 信誉分 / 统计数 / 各状态订单数）")
    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.ok(userCenterService.profile(UserContext.getUserId()));
    }

    @PublicApi
    @Operation(summary = "卖家主页数据（免登录，携带 token 时返回关注状态）")
    @GetMapping("/home/{userId}")
    public Result<UserHomeVO> home(@Parameter(description = "卖家用户ID") @PathVariable Long userId) {
        return Result.ok(userCenterService.home(userId, UserContext.getUserIdOrNull()));
    }

    @PublicApi
    @Operation(summary = "卖家在售商品列表（分页，按发布时间倒序）")
    @GetMapping("/products/{userId}")
    public Result<IPage<ProductListVO>> products(@Parameter(description = "卖家用户ID") @PathVariable Long userId,
                                                 @Parameter(description = "页码") @RequestParam(required = false) Long pageNum,
                                                 @Parameter(description = "每页条数") @RequestParam(required = false) Long pageSize) {
        return Result.ok(userCenterService.homeProducts(userId, pageNum, pageSize));
    }

    @Operation(summary = "修改个人资料（昵称 / 头像 / 性别 / 个人简介）")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto) {
        userCenterService.updateProfile(UserContext.getUserId(), dto);
        return Result.ok("资料已更新", null);
    }

    @Operation(summary = "修改手机号（需微信授权验证）")
    @PutMapping("/phone")
    public Result<Void> updatePhone(@RequestBody @Valid UserPhoneUpdateDTO dto) {
        userCenterService.updatePhone(UserContext.getUserId(), dto.getCode());
        return Result.ok("手机号已更新", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.getUserId());
        return Result.ok("已退出登录", null);
    }

    @Operation(summary = "注销账号（软删除，商品全部下架）")
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(@RequestBody @Valid UserAccountDeleteDTO dto) {
        userCenterService.deleteAccount(UserContext.getUserId(), dto);
        return Result.ok("账号已注销", null);
    }
}
