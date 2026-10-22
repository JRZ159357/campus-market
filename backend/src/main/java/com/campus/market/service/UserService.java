// 文件路径: src/main/java/com/campus/market/service/UserService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.entity.SysUser;
import com.campus.market.vo.UserInfoVO;

/**
 * 用户业务接口。
 */
public interface UserService extends IService<SysUser> {

    /**
     * 按 ID 查询用户，优先读 Redis 缓存（30 分钟），未命中回源数据库并回填。
     */
    SysUser getCached(Long userId);

    /**
     * 清除用户缓存。修改角色、封禁、改资料后必须调用。
     */
    void evictCache(Long userId);

    /**
     * 当前用户信息（个人中心用）。
     */
    UserInfoVO getProfile(Long userId);

    /**
     * 按 openid 查询小程序用户。
     */
    SysUser getByOpenId(String openId);
}
