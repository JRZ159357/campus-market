// 文件路径: src/main/java/com/campus/market/service/support/ReviewAssembler.java
package com.campus.market.service.support;

import com.campus.market.common.CreditLevel;
import com.campus.market.common.ReviewRule;
import com.campus.market.common.ReviewType;
import com.campus.market.entity.Product;
import com.campus.market.entity.Review;
import com.campus.market.entity.SysUser;
import com.campus.market.entity.TradeOrder;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.vo.ReviewVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评价实体 → 视图对象的装配器。
 * <p>
 * 为什么单独抽一个组件，而不是在 ReviewServiceImpl 里写一个私有方法：
 * 信誉分详情（CreditService）也要展示「最近评价」，如果它去调 ReviewService，
 * 而 ReviewService 又要调 CreditService 加信誉分，两者就形成循环依赖。
 * 把「怎么把 review 变成前端能用的 VO」下放到装配器，两个 Service 都依赖它，
 * 依赖关系就变成了单向的 → 装配器（无业务）。
 * <p>
 * 装配器只做三件事：批量补用户 / 商品信息、匿名脱敏、算查看者能否追评与回复。
 * 任何业务校验都不在这里，避免它变成第二个 Service。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAssembler {

    private final SysUserMapper sysUserMapper;

    private final ProductMapper productMapper;

    private final TradeOrderMapper tradeOrderMapper;

    private final ObjectMapper objectMapper;

    /**
     * 批量装配。
     *
     * @param reviews       评价实体列表
     * @param viewerId      当前查看者，用于计算 canFollow / canReply，可为 null
     * @param maskAnonymous 是否对匿名评价脱敏。管理端传 false——管理员需要看到真实评价人
     *                      才能判断是否违规，但 isAnonymous 标记仍然保留
     */
    public List<ReviewVO> toVOList(List<Review> reviews, Long viewerId, boolean maskAnonymous) {
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次把用到的用户、商品、订单全部捞出来，避免 N+1
        Set<Long> userIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> orderIds = new HashSet<>();
        for (Review review : reviews) {
            userIds.add(review.getReviewerId());
            userIds.add(review.getRevieweeId());
            productIds.add(review.getProductId());
            orderIds.add(review.getOrderId());
        }
        Map<Long, SysUser> users = loadUsers(userIds);
        Map<Long, Product> products = loadProducts(productIds);
        Map<Long, TradeOrder> orders = loadOrders(orderIds);

        List<ReviewVO> list = new ArrayList<>(reviews.size());
        for (Review review : reviews) {
            list.add(toVO(review, viewerId, maskAnonymous, users, products, orders));
        }
        return list;
    }

    /**
     * 单个装配（内部走 {@link #toVOList}，保证两条路径行为一致）。
     */
    public ReviewVO toVO(Review review, Long viewerId, boolean maskAnonymous) {
        List<ReviewVO> list = toVOList(List.of(review), viewerId, maskAnonymous);
        return list.isEmpty() ? null : list.get(0);
    }

    private ReviewVO toVO(Review review, Long viewerId, boolean maskAnonymous,
                          Map<Long, SysUser> users, Map<Long, Product> products,
                          Map<Long, TradeOrder> orders) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderId(review.getOrderId());
        vo.setOrderNo(review.getOrderNo());
        vo.setProductId(review.getProductId());
        Product product = products.get(review.getProductId());
        vo.setProductTitle(product != null ? product.getTitle() : "商品已删除");

        vo.setReviewerId(review.getReviewerId());
        SysUser reviewer = users.get(review.getReviewerId());
        boolean anonymous = review.getIsAnonymous() != null && review.getIsAnonymous() == 1;
        vo.setIsAnonymous(anonymous);
        if (anonymous && maskAnonymous) {
            // 脱敏必须发生在服务端：只把真实昵称下发、让前端"不要显示"，
            // 任何人抓一次接口就能看到实名，匿名就失去意义
            vo.setReviewerNickname(ReviewRule.ANONYMOUS_NICKNAME);
            vo.setReviewerAvatar(null);
        } else if (reviewer != null) {
            vo.setReviewerNickname(reviewer.getNickname());
            vo.setReviewerAvatar(reviewer.getAvatar());
        } else {
            vo.setReviewerNickname("用户已注销");
        }

        vo.setRevieweeId(review.getRevieweeId());
        SysUser reviewee = users.get(review.getRevieweeId());
        if (reviewee != null) {
            vo.setRevieweeNickname(reviewee.getNickname());
            int score = reviewee.getCreditScore() == null ? 0 : reviewee.getCreditScore();
            CreditLevel level = CreditLevel.of(score);
            vo.setRevieweeScore(score);
            vo.setRevieweeLevel(level.name());
            vo.setRevieweeLevelLabel(level.getLabel());
            vo.setRevieweeLevelColor(level.getColor());
        }

        vo.setReviewType(review.getReviewType());
        vo.setReviewTypeLabel(ReviewType.label(review.getReviewType()));
        vo.setStars(review.getStars());
        vo.setDescStars(review.getDescStars());
        vo.setShipStars(review.getShipStars());
        vo.setAttitudeStars(review.getAttitudeStars());
        vo.setPayStars(review.getPayStars());
        vo.setContent(review.getContent());
        vo.setImages(parseImages(review.getImages()));
        vo.setReplyContent(review.getReplyContent());
        vo.setReplyTime(review.getReplyTime());
        vo.setFollowContent(review.getFollowContent());
        vo.setFollowImages(parseImages(review.getFollowImages()));
        vo.setFollowTime(review.getFollowTime());
        vo.setCreateTime(review.getCreateTime());

        vo.setCanFollow(canFollow(review, viewerId, orders));
        vo.setCanReply(canReply(review, viewerId));
        return vo;
    }

    /**
     * 查看者能否追评：必须是评价人本人、尚未追评、且仍在「完成时间 + 7 天」内。
     * <p>
     * 用订单的 finish_time 而不是评价的 create_time 计算窗口：
     * 用户可能拖到最后一天才评价，如果按评价时间算，追评窗口会白白延后。
     */
    public boolean canFollow(Review review, Long viewerId, Map<Long, TradeOrder> orders) {
        if (viewerId == null || !viewerId.equals(review.getReviewerId())) {
            return false;
        }
        if (StringUtils.hasText(review.getFollowContent())) {
            return false;
        }
        TradeOrder order = orders.get(review.getOrderId());
        LocalDateTime base = order != null && order.getFinishTime() != null
                ? order.getFinishTime() : review.getCreateTime();
        return base != null && LocalDateTime.now().isBefore(base.plusDays(ReviewRule.FOLLOW_WINDOW_DAYS));
    }

    /**
     * 查看者能否回复：只有「被评价的卖家」能回复「买家评卖家」的评价，且只能回一次。
     */
    public boolean canReply(Review review, Long viewerId) {
        return viewerId != null
                && viewerId.equals(review.getRevieweeId())
                && ReviewType.BUYER_TO_SELLER == review.getReviewType()
                && !StringUtils.hasText(review.getReplyContent());
    }

    /**
     * JSON 数组字符串 → List。解析失败返回空列表而不是抛异常：
     * 历史脏数据不应该让整页评价 500。
     */
    public List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("评价图片 JSON 解析失败：{}", json);
            return Collections.emptyList();
        }
    }

    /**
     * List → JSON 数组字符串，空列表统一存成 "[]"（而不是 null），
     * 这样 SQL 里判空只需要一个 `images <> '[]'` 条件就够。
     */
    public String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Map<Long, SysUser> loadUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Product> loadProducts(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, TradeOrder> loadOrders(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        // 订单可能已被逻辑删除（极少见），查不到就退回按评价时间算窗口
        return tradeOrderMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TradeOrder::getId, Function.identity(), (a, b) -> a));
    }
}
