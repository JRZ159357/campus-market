// 文件路径: src/main/java/com/campus/market/service/impl/UserServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.RedisKey;
import com.campus.market.common.RoleConst;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.UserService;
import com.campus.market.vo.UserInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 用户业务实现。用户信息缓存用于拦截器每请求校验角色与状态，避免高频打库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements UserService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public SysUser getCached(Long userId) {
        String key = RedisKey.USER_PROFILE + userId;
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, SysUser.class);
            }
        } catch (Exception e) {
            log.warn("读取用户缓存失败，回源数据库。userId={}", userId, e);
        }

        SysUser user = this.getById(userId);
        if (user != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(user), CACHE_TTL);
            } catch (Exception e) {
                log.warn("写入用户缓存失败。userId={}", userId, e);
            }
        }
        return user;
    }

    @Override
    public void evictCache(Long userId) {
        if (userId == null) {
            return;
        }
        redis.delete(RedisKey.USER_PROFILE + userId);
    }

    @Override
    public UserInfoVO getProfile(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(user, vo);
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setHasAccount(org.springframework.util.StringUtils.hasText(user.getUsername()));
        vo.setRegisterTypeLabel(Integer.valueOf(2).equals(user.getRegisterType()) ? "账号密码注册" : "微信注册");
        return vo;
    }

    @Override
    public SysUser getByOpenId(String openId) {
        return this.lambdaQuery().eq(SysUser::getOpenId, openId).one();
    }
}
