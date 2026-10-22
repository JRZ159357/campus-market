// 文件路径: src/main/java/com/campus/market/controller/AuthController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.common.annotation.PublicApi;
import com.campus.market.dto.AccountLoginDTO;
import com.campus.market.dto.AccountRegisterDTO;
import com.campus.market.dto.AdminLoginDTO;
import com.campus.market.dto.BindAccountDTO;
import com.campus.market.dto.BindPhoneDTO;
import com.campus.market.dto.ChangePasswordDTO;
import com.campus.market.dto.WxLoginDTO;
import com.campus.market.service.AuthService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.LoginVO;
import com.campus.market.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口。路径规范：/api/auth/{操作}。
 */
@Tag(name = "用户认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PublicApi
    @Operation(summary = "小程序微信登录（openid不存在时自动注册为普通用户）")
    @PostMapping("/wx-login")
    public Result<LoginVO> wxLogin(@RequestBody @Valid WxLoginDTO dto) {
        return Result.ok("登录成功", authService.wxLogin(dto));
    }

    @PublicApi
    @Operation(summary = "账号密码注册（默认普通用户，注册成功即登录）")
    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody @Valid AccountRegisterDTO dto) {
        return Result.ok("注册成功", authService.register(dto));
    }

    @PublicApi
    @Operation(summary = "账号密码登录（全角色通用，与微信登录同一套 token）")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid AccountLoginDTO dto) {
        return Result.ok("登录成功", authService.accountLogin(dto));
    }

    @PublicApi
    @Operation(summary = "管理端账号密码登录（仅管理员与客服）")
    @PostMapping("/admin-login")
    public Result<LoginVO> adminLogin(@RequestBody @Valid AdminLoginDTO dto) {
        return Result.ok("登录成功", authService.adminLogin(dto));
    }

    @PublicApi
    @Operation(summary = "校验账号是否可注册（注册页实时校验唯一性）")
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(
            @Parameter(description = "待校验的登录账号") @RequestParam("username") String username) {
        return Result.ok(authService.isUsernameAvailable(username));
    }

    @Operation(summary = "微信用户绑定账号密码（绑定后可用账号密码登录）")
    @PostMapping("/bind-account")
    public Result<Void> bindAccount(@RequestBody @Valid BindAccountDTO dto) {
        authService.bindAccount(UserContext.getUserId(), dto);
        return Result.ok("绑定成功", null);
    }

    @Operation(summary = "修改当前账号密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto) {
        authService.changePassword(UserContext.getUserId(), dto);
        return Result.ok("密码修改成功", null);
    }

    @Operation(summary = "绑定手机号")
    @PostMapping("/bind-phone")
    public Result<Void> bindPhone(@RequestBody @Valid BindPhoneDTO dto) {
        authService.bindPhone(UserContext.getUserId(), dto.getCode());
        return Result.ok("绑定成功", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.me(UserContext.getUserId()));
    }
}
