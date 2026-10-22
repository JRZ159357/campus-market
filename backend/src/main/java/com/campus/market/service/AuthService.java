// 文件路径: src/main/java/com/campus/market/service/AuthService.java
package com.campus.market.service;

import com.campus.market.dto.AccountLoginDTO;
import com.campus.market.dto.AccountRegisterDTO;
import com.campus.market.dto.AdminLoginDTO;
import com.campus.market.dto.BindAccountDTO;
import com.campus.market.dto.ChangePasswordDTO;
import com.campus.market.dto.WxLoginDTO;
import com.campus.market.vo.LoginVO;
import com.campus.market.vo.UserInfoVO;

/**
 * 认证业务接口：微信登录、账号注册登录、管理端登录、账号绑定、改密、退出。
 * 所有登录方式签发同一套 JWT（只携带 userId），角色与状态由服务端每次读取。
 */
public interface AuthService {

    /**
     * 小程序微信登录。openid 不存在时自动注册为普通用户。
     */
    LoginVO wxLogin(WxLoginDTO dto);

    /**
     * 账号密码注册，默认角色为普通用户，成功后直接返回 token（注册即登录）。
     */
    LoginVO register(AccountRegisterDTO dto);

    /**
     * 账号密码登录，全部角色通用。
     */
    LoginVO accountLogin(AccountLoginDTO dto);

    /**
     * 管理端账号密码登录，仅 admin / customer_service 可登录。
     */
    LoginVO adminLogin(AdminLoginDTO dto);

    /**
     * 微信用户绑定账号密码，绑定后可用账号密码登录。
     */
    void bindAccount(Long userId, BindAccountDTO dto);

    /**
     * 修改当前账号密码。
     */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /**
     * 账号是否可注册（小程序注册页实时校验）。
     */
    boolean isUsernameAvailable(String username);

    /**
     * 绑定手机号。
     */
    void bindPhone(Long userId, String code);

    /**
     * 退出登录，删除 Redis token 白名单。
     */
    void logout(Long userId);

    /**
     * 当前登录用户信息。
     */
    UserInfoVO me(Long userId);
}
