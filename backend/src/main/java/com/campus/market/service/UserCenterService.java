// 文件路径: src/main/java/com/campus/market/service/UserCenterService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.UserAccountDeleteDTO;
import com.campus.market.dto.UserProfileUpdateDTO;
import com.campus.market.vo.ProductListVO;
import com.campus.market.vo.UserHomeVO;
import com.campus.market.vo.UserProfileVO;

/**
 * 个人中心与卖家主页聚合服务。
 * <p>
 * 本模块不新增核心业务表，全部数据来自前 7 个模块。
 * 这个服务的职责就是「把散落在 6 张表里的数字一次性聚齐」，
 * 让前端一个请求就能渲染整屏，不必串行请求七八个接口。
 * <p>
 * 与既有 Service 的依赖方向：
 * <ul>
 *   <li>依赖 {@link OrderService} 拿各状态订单数、{@link ReviewService} 拿待评价数与评价统计、
 *       {@link CreditService} 拿信誉分与等级、{@link ProductService} 拿在售商品；</li>
 *   <li>这些 Service 都不反向依赖本服务，因此不会形成循环依赖。</li>
 * </ul>
 */
public interface UserCenterService {

    /**
     * 个人中心聚合数据（需登录）。
     * <p>
     * 一次返回：用户信息 + 信誉分等级 + 六个统计数 + 各状态订单数。
     */
    UserProfileVO profile(Long userId);

    /**
     * 卖家主页数据（免登录，携带 token 时判定关注状态）。
     *
     * @param userId   目标用户ID
     * @param viewerId 当前查看者ID，未登录为 null
     */
    UserHomeVO home(Long userId, Long viewerId);

    /**
     * 卖家在售商品列表（分页，按发布时间倒序）。
     */
    IPage<ProductListVO> homeProducts(Long userId, Long pageNum, Long pageSize);

    /**
     * 修改个人资料。昵称、头像、性别、简介按「非空即更新」处理。
     */
    void updateProfile(Long userId, UserProfileUpdateDTO dto);

    /**
     * 修改手机号。手机号由微信授权码换取，不能由前端直接指定。
     */
    void updatePhone(Long userId, String code);

    /**
     * 注销账号（软删除）。要求密码正确、确认口令正确、角色为普通用户或认证学生。
     * <p>
     * 注销后该用户的全部在售商品立即下架。
     */
    void deleteAccount(Long userId, UserAccountDeleteDTO dto);
}
