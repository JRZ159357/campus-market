// 文件路径: src/main/java/com/campus/market/service/impl/UserCenterServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.CreditLevel;
import com.campus.market.common.CreditRule;
import com.campus.market.common.Gender;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.RedisKey;
import com.campus.market.common.ResultCode;
import com.campus.market.common.RoleConst;
import com.campus.market.common.UserRule;
import com.campus.market.dto.UserAccountDeleteDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.entity.StudentAuth;
import com.campus.market.entity.SysUser;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.StudentAuthMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.service.CreditService;
import com.campus.market.service.FollowService;
import com.campus.market.service.OrderService;
import com.campus.market.service.ProductService;
import com.campus.market.service.ReviewService;
import com.campus.market.service.UserCenterService;
import com.campus.market.service.UserService;
import com.campus.market.util.WechatApiClient;
import com.campus.market.vo.CreditVO;
import com.campus.market.vo.OrderCountVO;
import com.campus.market.vo.ProductListVO;
import com.campus.market.vo.ReviewVO;
import com.campus.market.vo.UserHomeVO;
import com.campus.market.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 个人中心与卖家主页聚合服务实现。
 * <p>
 * 聚合的关键取舍是「少查表、一次查齐」：
 * 个人中心首屏要展示发布数、已售数、在售数、收藏数、关注数、粉丝数、
 * 待评价数、六个状态订单数，如果前端串行调 8 个接口，
 * 弱网下首屏会分好几批出现，而且各接口返回的时刻不同，
 * 用户会看到「收藏数已更新但订单数还是旧的」这种割裂。
 * <p>
 * 依赖方向上，本服务依赖 OrderService / ReviewService / CreditService /
 * ProductService / FollowService，这些都是模块 1~7 的既有服务，
 * 它们都不反向依赖本服务，因此不存在循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCenterServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements UserCenterService {

    /** 卖家主页展示的最近评价条数 */
    private static final int HOME_REVIEW_SIZE = 5;

    private final UserService userService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final CreditService creditService;
    private final ProductService productService;
    private final FollowService followService;
    private final ProductMapper productMapper;
    private final StudentAuthMapper studentAuthMapper;
    private final WechatApiClient wechatApiClient;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    // ==================== 个人中心 ====================

    @Override
    public UserProfileVO profile(Long userId) {
        SysUser user = requireUser(userId);

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setGenderLabel(Gender.label(user.getGender()));
        vo.setIntro(user.getIntro());
        vo.setPhone(user.getPhone());
        vo.setUsername(user.getUsername());
        vo.setHasAccount(StringUtils.hasText(user.getUsername()));
        vo.setRole(user.getRole());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setStudentVerified(user.getStudentVerified());
        vo.setStudentNo(findStudentNo(userId));
        vo.setCreateTime(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());

        int score = user.getCreditScore() == null ? CreditRule.INITIAL_SCORE : user.getCreditScore();
        CreditLevel level = CreditLevel.of(score);
        vo.setCreditScore(score);
        vo.setCreditLevelLabel(level.getLabel());
        vo.setCreditLevelColor(level.getColor());
        vo.setCreditProgress(CreditLevel.progressOf(score));

        // 发布数 / 在售数 / 已售数：一次 GROUP BY 出全部
        long publish = 0;
        long onSale = 0;
        long sold = 0;
        for (Map<String, Object> row : productMapper.countGroupByStatus(userId)) {
            int status = toInt(row.get("status"));
            long cnt = toLong(row.get("cnt"));
            publish += cnt;
            if (status == ProductStatus.ON_SALE) {
                onSale = cnt;
            } else if (status == ProductStatus.SOLD) {
                sold = cnt;
            }
        }
        vo.setPublishCount(publish);
        vo.setOnSaleCount(onSale);
        vo.setSoldCount(sold);

        // 收藏 / 关注 / 粉丝：冗余在 sys_user 上，与收藏/关注表同事务增减
        vo.setFavoriteCount(toLong(user.getFavoriteCount()));
        vo.setFollowCount(toLong(user.getFollowCount()));
        vo.setFansCount(toLong(user.getFansCount()));

        // 待评价：与订单列表用同一套判定（完成且该方向还没评过）
        vo.setPendingReviewCount(reviewService.pendingCount(userId));

        // 各状态订单数：买家侧
        OrderCountVO orderCount = orderService.buyerCount(userId);
        vo.setOrderCount(orderCount);
        return vo;
    }

    // ==================== 卖家主页 ====================

    @Override
    public UserHomeVO home(Long userId, Long viewerId) {
        SysUser user = requireVisibleUser(userId);

        UserHomeVO vo = new UserHomeVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGenderLabel(Gender.label(user.getGender()));
        vo.setIntro(user.getIntro());
        vo.setRoleLabel(RoleConst.label(user.getRole()));
        vo.setStudentVerified(Integer.valueOf(1).equals(user.getStudentVerified()));
        vo.setFansCount(toLong(user.getFansCount()));
        vo.setCreateTime(user.getCreateTime());

        int score = user.getCreditScore() == null ? CreditRule.INITIAL_SCORE : user.getCreditScore();
        CreditLevel level = CreditLevel.of(score);
        vo.setCreditScore(score);
        vo.setCreditLevelLabel(level.getLabel());
        vo.setCreditLevelColor(level.getColor());

        long publish = 0;
        long onSale = 0;
        long sold = 0;
        for (Map<String, Object> row : productMapper.countGroupByStatus(userId)) {
            int status = toInt(row.get("status"));
            long cnt = toLong(row.get("cnt"));
            publish += cnt;
            if (status == ProductStatus.ON_SALE) {
                onSale = cnt;
            } else if (status == ProductStatus.SOLD) {
                sold = cnt;
            }
        }
        vo.setOnSaleCount(onSale);
        vo.setSoldCount(sold);

        // 评价统计与最近评价：直接复用信誉分详情里的同一份数据，
        // 免得「主页好评率」和「信誉分页好评率」两处算法不一致
        CreditVO credit = creditService.detail(userId);
        if (credit.getStat() != null) {
            vo.setReviewCount(credit.getStat().getTotal());
            vo.setGoodRate(credit.getStat().getGoodRate());
            vo.setAvgStars(credit.getStat().getAvgStars());
        } else {
            vo.setReviewCount(0L);
            vo.setGoodRate(0.0);
            vo.setAvgStars(0.0);
        }
        List<ReviewVO> recent = credit.getRecentReviews();
        vo.setRecentReviews(recent == null ? List.of()
                : recent.stream().limit(HOME_REVIEW_SIZE).toList());

        vo.setIsSelf(viewerId != null && viewerId.equals(userId));
        vo.setFollowed(viewerId != null && followService.isFollowing(viewerId, userId));
        return vo;
    }

    @Override
    public IPage<ProductListVO> homeProducts(Long userId, Long pageNum, Long pageSize) {
        requireVisibleUser(userId);
        // 只展示已上架商品：卖家主页是给别人看的，
        // 待审核 / 已下架 / 已售出都属于卖家自己的内部状态
        return productService.pageBySeller(userId, ProductStatus.ON_SALE, pageNum, pageSize);
    }

    // ==================== 资料设置 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UserProfileUpdateDTO dto) {
        SysUser user = requireUser(userId);

        SysUser update = new SysUser();
        update.setId(userId);
        boolean changed = false;

        if (StringUtils.hasText(dto.getNickname())) {
            String nickname = dto.getNickname().trim();
            if (nickname.length() > UserRule.NICKNAME_MAX_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "昵称不能超过 " + UserRule.NICKNAME_MAX_LENGTH + " 个字");
            }
            update.setNickname(nickname);
            changed = true;
        }
        if (dto.getAvatar() != null) {
            String avatar = dto.getAvatar().trim();
            update.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
            changed = true;
        }
        if (dto.getGender() != null) {
            if (!Gender.isValid(dto.getGender())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "性别取值不合法");
            }
            update.setGender(dto.getGender());
            changed = true;
        }
        if (dto.getIntro() != null) {
            String intro = dto.getIntro().trim();
            if (intro.length() > UserRule.INTRO_MAX_LENGTH) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "个人简介不能超过 " + UserRule.INTRO_MAX_LENGTH + " 个字");
            }
            update.setIntro(StringUtils.hasText(intro) ? intro : null);
            changed = true;
        }

        if (!changed) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "没有需要修改的内容");
        }
        baseMapper.updateById(update);
        // 用户信息有 30 分钟 Redis 缓存，不清的话个人中心会显示旧昵称与旧头像
        userService.evictCache(userId);
        log.info("用户 {} 修改个人资料", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePhone(Long userId, String code) {
        SysUser user = requireUser(userId);
        // 手机号只能由微信授权码换取：前端传一个手机号字符串直接改，
        // 等于允许任何人把账号手机号改成自己的
        String phone = wechatApiClient.getPhoneNumber(code);
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("手机号获取失败，请重试");
        }
        if (phone.equals(user.getPhone())) {
            throw new BusinessException("新手机号与当前手机号相同");
        }
        Long exists = baseMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(SysUser::getId, userId));
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已被其他账号绑定");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPhone(phone);
        baseMapper.updateById(update);
        userService.evictCache(userId);
        log.info("用户 {} 修改手机号", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, UserAccountDeleteDTO dto) {
        SysUser user = requireUser(userId);

        // 客服与管理员不能自助注销：他们的账号挂着管理权限，
        // 必须由另一位管理员先把角色降下来，否则「误操作注销」等于平台少一个管理员
        if (RoleConst.ADMIN.equals(user.getRole()) || RoleConst.CUSTOMER_SERVICE.equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "客服与管理员账号不能自助注销，请联系管理员移除角色后再注销");
        }
        if (!UserRule.ACCOUNT_DELETE_CONFIRM.equals(dto.getConfirmText().trim())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "请输入「" + UserRule.ACCOUNT_DELETE_CONFIRM + "」以确认注销");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "当前账号尚未设置密码，请先在「账号与安全」中设置密码后再注销");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密码不正确");
        }

        // 先下架全部在架商品：账号没了，商品还挂在平台上被别人点是最直接的资损
        int offline = productService.offlineAllBySeller(userId);
        // 再软删除账号。sys_user 用 @TableLogic，removeById 只会置 deleted=1，
        // 历史订单与评价里的 user_id 仍然指向这条记录，账目不丢
        removeById(userId);
        // 强制下线：Redis 里的 token 白名单必须清掉，
        // 否则用户手上的 token 在 token 过期前仍能通过鉴权
        redis.delete(RedisKey.LOGIN_TOKEN + userId);
        userService.evictCache(userId);
        log.info("用户 {} 注销账号，下架商品 {} 件", userId, offline);
    }

    // ==================== 内部方法 ====================

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        SysUser user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在或已注销");
        }
        return user;
    }

    /**
     * 取一个「对外可见」的用户：卖家主页与卖家商品列表用。
     * <p>
     * 与 requireUser 的区别是多了封禁校验：卖家主页是免登录接口，
     * 走的是软登录（不抛错、不阻断），所以拦截器里那条
     * 「封禁账号禁止访问」的判断对被查看者不生效——
     * 拦截器只管当前请求者。
     * <p>
     * 如果这里不额外校验，一个被封禁的卖家主页照样能被所有人看到：
     * 名字、信誉分、粉丝数全在，被封禁的人却还在挂着「认证学生」标签。
     * 商品已经随封禁下架了，主页却还亮着，前后端状态对不上。
     */
    private SysUser requireVisibleUser(Long userId) {
        SysUser user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该用户已被封禁，主页暂不可访问");
        }
        return user;
    }

    /**
     * 查该用户已通过认证的学号。
     * <p>
     * 一条记录都没有（从未申请）返回 null；申请了但还在审核中也不返回，
     * 因为学号属于个人敏感信息，没通过审核就不该展示在任何列表里。
     */
    private String findStudentNo(Long userId) {
        StudentAuth auth = studentAuthMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudentAuth>()
                .eq(StudentAuth::getUserId, userId)
                .eq(StudentAuth::getStatus, 1)
                .orderByDesc(StudentAuth::getId)
                .last("limit 1"));
        return auth == null ? null : auth.getStudentNo();
    }

    private static int toInt(Object value) {
        return value == null ? -1 : Integer.parseInt(String.valueOf(value));
    }

    private static long toLong(Object value) {
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}
