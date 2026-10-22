// 文件路径: src/main/java/com/campus/market/service/impl/ReviewServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.CreditLogType;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.common.CreditRule;
import com.campus.market.common.OrderStatus;
import com.campus.market.common.ResultCode;
import com.campus.market.common.ReviewRule;
import com.campus.market.common.ReviewType;
import com.campus.market.common.SensitiveCheckResult;
import com.campus.market.dto.ReviewFollowDTO;
import com.campus.market.dto.ReviewQueryDTO;
import com.campus.market.dto.ReviewReplyDTO;
import com.campus.market.dto.ReviewSubmitDTO;
import com.campus.market.entity.Product;
import com.campus.market.entity.Review;
import com.campus.market.entity.SysUser;
import com.campus.market.entity.TradeOrder;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.service.CreditService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.ReviewService;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.service.support.ReviewAssembler;
import com.campus.market.vo.OrderProductSnapshotVO;
import com.campus.market.vo.ReviewOrderVO;
import com.campus.market.vo.ReviewPendingVO;
import com.campus.market.vo.ReviewVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评价服务实现。
 * <p>
 * 关键设计：
 * <ul>
 *   <li><b>方向由订单决定</b>：客户端传的 reviewType 只用来核对，真正的方向
 *       来自「当前用户在订单里是买家还是卖家」。这样即使前端传错或恶意传反，
 *       也不会出现「卖家给自己加信誉分」这种漏洞。</li>
 *   <li><b>重复评价两道防线</b>：先用 countByOrderType 做友好提示，
 *       再让 uk_order_type 唯一索引兜住并发；订单上的 buyer_reviewed / seller_reviewed
 *       用 CAS（WHERE flag = 0）更新，任何一步失败整个事务回滚。</li>
 *   <li><b>追评窗口按订单完成时间算</b>：而不是按评价时间。
 *       用户可能拖到第 6 天才评价，如果按评价时间算，追评窗口就变成
 *       「完成后的第 6 到 13 天」，与需求「交易完成后 7 天内」不符。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final TradeOrderMapper tradeOrderMapper;

    private final SysUserMapper sysUserMapper;

    private final ProductMapper productMapper;

    private final CreditService creditService;

    private final ReviewAssembler reviewAssembler;

    private final DashboardCacheService dashboardCacheService;
    private final SensitiveWordService sensitiveWordService;
    private final NoticeService noticeService;

    private final ObjectMapper objectMapper;

    // ==================== 写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO submit(Long userId, ReviewSubmitDTO dto) {
        TradeOrder order = tradeOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.FINISHED) {
            throw new BusinessException("只有已完成的订单才能评价");
        }

        // 方向以订单角色为准，不信任前端传值
        boolean isBuyer = userId.equals(order.getBuyerId());
        boolean isSeller = userId.equals(order.getSellerId());
        if (!isBuyer && !isSeller) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权评价该订单");
        }
        int direction = isBuyer ? ReviewType.BUYER_TO_SELLER : ReviewType.SELLER_TO_BUYER;
        if (dto.getReviewType() != null && dto.getReviewType() != direction) {
            throw new BusinessException("评价方向与你在该订单中的角色不符");
        }
        Long revieweeId = isBuyer ? order.getSellerId() : order.getBuyerId();

        if (baseMapper.countByOrderType(order.getId(), direction) > 0) {
            throw new BusinessException("你已经评价过这笔订单了");
        }

        // 按方向校验维度齐全性：买家的三个维度、卖家的两个维度都是必填
        int stars;
        Integer descStars = null;
        Integer shipStars = null;
        Integer payStars = null;
        Integer attitudeStars = requireStar(dto.getAttitudeStars(), "沟通态度");
        if (direction == ReviewType.BUYER_TO_SELLER) {
            descStars = requireStar(dto.getDescStars(), "商品描述相符");
            shipStars = requireStar(dto.getShipStars(), "发货速度");
            stars = (int) Math.round((descStars + shipStars + attitudeStars) / 3.0);
        } else {
            payStars = requireStar(dto.getPayStars(), "付款速度");
            stars = (int) Math.round((payStars + attitudeStars) / 2.0);
        }

        /*
         * 敏感词过滤（模块11）。评价只有「拦截」一级生效：
         * 评价是用户对交易体验的表达，命中「加微信」这类警告级词
         * 多半只是「我们加了微信沟通」这种正常叙述，拦掉反而显得平台
         * 不让人说话；而辱骂与违禁品属于无论如何都不该出现的内容。
         */
        SensitiveCheckResult sensitive = sensitiveWordService.check(dto.getContent());
        if (sensitive.isBlocked()) {
            // 通知走独立事务，不会随下面的异常回滚；
            // 失败也不影响拦截结论（异常在 notifyXxx 内部已被吞掉）
            try {
                noticeService.notifyAudit(userId, "评价提交被拦截",
                        "你对该订单的评价未提交成功：内容包含违规词（" + sensitive.joinWords()
                                + "）。请修改后重新提交。", null);
            } catch (Exception e) {
                log.warn("评价敏感词拦截通知发送失败 userId={}：{}", userId, e.getMessage());
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, sensitive.blockMessage());
        }
        if (sensitive.isWarned()) {
            // 不拦截但要留痕：出现纠纷时能解释「这条评价当时为什么被标记过」
            log.info("评价命中警告级敏感词: orderId={}, reviewerId={}, words={}",
                    order.getId(), userId, sensitive.joinWords());
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setOrderNo(order.getOrderNo());
        review.setProductId(order.getProductId());
        review.setReviewerId(userId);
        review.setRevieweeId(revieweeId);
        review.setReviewType(direction);
        review.setStars(stars);
        review.setDescStars(descStars);
        review.setShipStars(shipStars);
        review.setAttitudeStars(attitudeStars);
        review.setPayStars(payStars);
        review.setContent(normalizeContent(dto.getContent()));
        review.setImages(normalizeImages(dto.getImages()));
        review.setIsAnonymous(Boolean.TRUE.equals(dto.getIsAnonymous()) ? 1 : 0);

        try {
            save(review);
        } catch (DuplicateKeyException e) {
            // 并发下两次提交同时通过预检查，唯一索引兜底
            throw new BusinessException("你已经评价过这笔订单了");
        }

        // CAS 更新订单标记：只有当前还是 0 才能改成 1，
        // 受影响行数为 0 说明已被另一条并发请求改过，整笔回滚
        if (!markOrderReviewed(order.getId(), direction)) {
            throw new BusinessException("你已经评价过这笔订单了");
        }

        // 立即更新对方信誉分。3 星为 0 分变动，不会写流水但也不会报错
        int delta = CreditRule.deltaOfStars(stars);
        creditService.addScore(revieweeId, delta, CreditLogType.REVIEW,
                buildCreditReason(direction, stars), review.getId(), null);

        // 新评价会让「评价星级分布」变化（信誉分那一路已由 CreditService 失效）
        dashboardCacheService.evictOnReviewChange();

        return reviewAssembler.toVO(review, userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO follow(Long userId, ReviewFollowDTO dto) {
        Review review = getById(dto.getReviewId());
        if (review == null) {
            throw new BusinessException("评价不存在或已被删除");
        }
        if (!userId.equals(review.getReviewerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能追评自己发表的评价");
        }
        if (StringUtils.hasText(review.getFollowContent())) {
            throw new BusinessException("该评价已经追评过了，不能重复追评");
        }
        String content = normalizeContent(dto.getContent());
        String images = normalizeImages(dto.getImages());
        if (!StringUtils.hasText(content) && "[]".equals(images)) {
            throw new BusinessException("追评内容和图片至少填一项");
        }
        TradeOrder order = tradeOrderMapper.selectById(review.getOrderId());
        LocalDateTime base = order != null && order.getFinishTime() != null
                ? order.getFinishTime() : review.getCreateTime();
        if (base == null || LocalDateTime.now().isAfter(base.plusDays(ReviewRule.FOLLOW_WINDOW_DAYS))) {
            throw new BusinessException("已超过追评期限（交易完成后 " + ReviewRule.FOLLOW_WINDOW_DAYS + " 天内可追评）");
        }

        // 带 isNull 条件更新：并发下只有一次能成功，后到的拿到 0 行
        UpdateWrapper<Review> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", review.getId())
                .isNull("follow_content")
                .set("follow_content", content)
                .set("follow_images", images)
                .set("follow_time", LocalDateTime.now());
        if (baseMapper.update(null, wrapper) == 0) {
            throw new BusinessException("该评价已经追评过了，不能重复追评");
        }
        return reviewAssembler.toVO(getById(review.getId()), userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO reply(Long userId, ReviewReplyDTO dto) {
        Review review = getById(dto.getReviewId());
        if (review == null) {
            throw new BusinessException("评价不存在或已被删除");
        }
        if (review.getReviewType() == null || review.getReviewType() != ReviewType.BUYER_TO_SELLER) {
            throw new BusinessException("只能回复买家对卖家的评价");
        }
        if (!userId.equals(review.getRevieweeId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有被评价的卖家可以回复");
        }
        if (StringUtils.hasText(review.getReplyContent())) {
            throw new BusinessException("该评价已经回复过了");
        }
        UpdateWrapper<Review> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", review.getId())
                .isNull("reply_content")
                .set("reply_content", dto.getContent().trim())
                .set("reply_time", LocalDateTime.now());
        if (baseMapper.update(null, wrapper) == 0) {
            throw new BusinessException("该评价已经回复过了");
        }
        return reviewAssembler.toVO(getById(review.getId()), userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = OpLogModule.REVIEW, action = OpLogModule.ACTION_DELETE, targetIndex = 1,
            desc = "删除违规评价")
    public void adminDelete(Long reviewId, Long operatorId) {
        Review review = getById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在或已被删除");
        }
        // 逻辑删除：保留原始内容供申诉复核，物理删除会让"到底删了什么"无从查证
        removeById(reviewId);
        // 明确不回退信誉分，但留一条 0 变动的审计流水，记录谁删了哪条评价
        creditService.audit(review.getRevieweeId(), CreditLogType.REVIEW_DELETED,
                "违规评价被管理员删除（信誉分不回退）：" + abbreviate(review.getContent()), reviewId, operatorId);
        // 评价被删，星级分布少一条
        dashboardCacheService.evictOnReviewChange();
    }

    // ==================== 查询 ====================

    @Override
    public ReviewVO detail(Long reviewId, Long viewerId) {
        Review review = getById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在或已被删除");
        }
        return reviewAssembler.toVO(review, viewerId, true);
    }

    @Override
    public IPage<ReviewVO> pageByProduct(Long productId, ReviewQueryDTO query) {
        ReviewQueryDTO condition = query == null ? new ReviewQueryDTO() : query;
        condition.setProductId(productId);
        // 商品页只展示「买家对商品/卖家」的评价。一笔订单会产出两行评价且共用 product_id，
        // 不锁方向的话卖家对买家的打分也会混进来，平均分和好评率都会失真。
        condition.setReviewType(ReviewType.BUYER_TO_SELLER);
        IPage<Review> page = baseMapper.selectPageByQuery(buildPage(condition), condition);
        return toVOPage(page, null, true);
    }

    @Override
    public IPage<ReviewVO> pageByUser(Long userId, ReviewQueryDTO query) {
        ReviewQueryDTO condition = query == null ? new ReviewQueryDTO() : query;
        condition.setRevieweeId(userId);
        IPage<Review> page = baseMapper.selectPageByQuery(buildPage(condition), condition);
        return toVOPage(page, null, true);
    }

    @Override
    public ReviewOrderVO orderReviews(Long orderId, Long viewerId) {
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        List<Review> reviews = baseMapper.selectByOrderIds(List.of(orderId));
        ReviewOrderVO vo = buildOrderVO(order, viewerId, reviews);

        // 查看者的方向、自己的那条评价、能否追评，统一在这里定，避免多处判断打架
        if (viewerId != null && viewerId.equals(order.getBuyerId())) {
            vo.setMyReviewType(ReviewType.BUYER_TO_SELLER);
            if (vo.getBuyerReview() != null) {
                vo.setMyReviewId(vo.getBuyerReview().getId());
                vo.setCanFollow(Boolean.TRUE.equals(vo.getBuyerReview().getCanFollow()));
            }
        } else if (viewerId != null && viewerId.equals(order.getSellerId())) {
            vo.setMyReviewType(ReviewType.SELLER_TO_BUYER);
            if (vo.getSellerReview() != null) {
                vo.setMyReviewId(vo.getSellerReview().getId());
                vo.setCanFollow(Boolean.TRUE.equals(vo.getSellerReview().getCanFollow()));
            }
        }
        return vo;
    }

    @Override
    public IPage<ReviewPendingVO> pending(Long userId, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        Page<TradeOrder> page = new Page<>(current, size);
        IPage<TradeOrder> orders = baseMapper.selectPendingOrders(page, userId);

        List<ReviewPendingVO> records = new ArrayList<>(orders.getRecords().size());
        for (TradeOrder order : orders.getRecords()) {
            ReviewPendingVO vo = new ReviewPendingVO();
            vo.setOrderId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setProductId(order.getProductId());
            vo.setPrice(order.getPrice());
            vo.setFinishTime(order.getFinishTime());
            OrderProductSnapshotVO product = parseSnapshot(order.getProductSnapshot());
            if (product != null) {
                vo.setProductTitle(product.getTitle());
                vo.setProductCover(product.getCover());
            }
            boolean isBuyer = userId.equals(order.getBuyerId());
            vo.setReviewType(isBuyer ? ReviewType.BUYER_TO_SELLER : ReviewType.SELLER_TO_BUYER);
            vo.setReviewTypeLabel(ReviewType.label(vo.getReviewType()));
            vo.setPeerId(isBuyer ? order.getSellerId() : order.getBuyerId());
            records.add(vo);
        }
        // 批量补对方昵称，避免 N+1
        List<Long> peerIds = records.stream().map(ReviewPendingVO::getPeerId).distinct().toList();
        if (!peerIds.isEmpty()) {
            Map<Long, SysUser> peers = sysUserMapper.selectBatchIds(peerIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
            for (ReviewPendingVO record : records) {
                SysUser peer = peers.get(record.getPeerId());
                record.setPeerNickname(peer == null ? "用户已注销" : peer.getNickname());
            }
        }
        Page<ReviewPendingVO> result = new Page<>(orders.getCurrent(), orders.getSize(), orders.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public long pendingCount(Long userId) {
        return baseMapper.countPendingOrders(userId);
    }

    @Override
    public IPage<ReviewVO> adminPage(ReviewQueryDTO query) {
        ReviewQueryDTO condition = query == null ? new ReviewQueryDTO() : query;
        // 昵称 / 商品标题是模糊匹配：先在小表上查出 ID 集合，再 IN 到评价表，
        // 命中 0 个直接返回空页，避免拿空集合去拼 IN () 造成全表扫描
        if (StringUtils.hasText(condition.getReviewerKeyword())) {
            List<Long> ids = findUserIdsByNickname(condition.getReviewerKeyword());
            if (ids.isEmpty()) {
                return emptyPage(condition);
            }
            condition.setReviewerIds(ids);
        }
        if (StringUtils.hasText(condition.getRevieweeKeyword())) {
            List<Long> ids = findUserIdsByNickname(condition.getRevieweeKeyword());
            if (ids.isEmpty()) {
                return emptyPage(condition);
            }
            condition.setRevieweeIds(ids);
        }
        if (StringUtils.hasText(condition.getProductKeyword())) {
            List<Long> ids = findProductIdsByTitle(condition.getProductKeyword());
            if (ids.isEmpty()) {
                return emptyPage(condition);
            }
            condition.setProductIds(ids);
        }
        IPage<Review> page = baseMapper.selectPageByQuery(buildPage(condition), condition);
        // 管理端不脱敏：管理员需要看到真实评价人才能判断是否违规
        return toVOPage(page, null, false);
    }

    // ==================== 内部方法 ====================

    private IPage<ReviewVO> toVOPage(IPage<Review> page, Long viewerId, boolean maskAnonymous) {
        Page<ReviewVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(reviewAssembler.toVOList(page.getRecords(), viewerId, maskAnonymous));
        return result;
    }

    private Page<Review> buildPage(ReviewQueryDTO query) {
        long current = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        return new Page<>(current, size);
    }

    private Page<ReviewVO> emptyPage(ReviewQueryDTO query) {
        long current = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        Page<ReviewVO> page = new Page<>(current, size, 0);
        page.setRecords(Collections.emptyList());
        return page;
    }

    /**
     * 把订单上的评价标记置 1，返回是否成功。
     * 用 CAS 而不是直接 set，是为了让「同一方向只评一次」在订单表这一侧也有约束，
     * 而不是完全依赖 review 表的唯一索引。
     */
    private boolean markOrderReviewed(Long orderId, int direction) {
        UpdateWrapper<TradeOrder> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", orderId);
        if (direction == ReviewType.BUYER_TO_SELLER) {
            wrapper.eq("buyer_reviewed", 0).set("buyer_reviewed", 1);
        } else {
            wrapper.eq("seller_reviewed", 0).set("seller_reviewed", 1);
        }
        return tradeOrderMapper.update(null, wrapper) > 0;
    }

    private int requireStar(Integer value, String name) {
        if (value == null) {
            throw new BusinessException(name + "评分不能为空");
        }
        if (value < 1 || value > 5) {
            throw new BusinessException(name + "评分必须在 1-5 之间");
        }
        return value;
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.length() > ReviewRule.MAX_CONTENT_LENGTH) {
            throw new BusinessException("评价内容最多 " + ReviewRule.MAX_CONTENT_LENGTH + " 字");
        }
        return trimmed;
    }

    private String normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(ReviewRule.MAX_IMAGES)
                .toList();
        return reviewAssembler.writeImages(cleaned);
    }

    private String buildCreditReason(int direction, int stars) {
        return direction == ReviewType.BUYER_TO_SELLER
                ? "收到买家评价 " + stars + " 星"
                : "收到卖家评价 " + stars + " 星";
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "无文字内容";
        }
        return text.length() <= 30 ? text : text.substring(0, 30) + "…";
    }

    private List<Long> findUserIdsByNickname(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId)
                .like(SysUser::getNickname, keyword)
                .last("LIMIT 200");
        return sysUserMapper.selectList(wrapper).stream().map(SysUser::getId).toList();
    }

    private List<Long> findProductIdsByTitle(String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .select(Product::getId)
                .like(Product::getTitle, keyword)
                .last("LIMIT 200");
        return productMapper.selectList(wrapper).stream().map(Product::getId).toList();
    }

    private ReviewOrderVO buildOrderVO(TradeOrder order, Long viewerId, List<Review> reviews) {
        ReviewOrderVO vo = new ReviewOrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setPrice(order.getPrice());
        vo.setFinishTime(order.getFinishTime());
        OrderProductSnapshotVO product = parseSnapshot(order.getProductSnapshot());
        if (product != null) {
            vo.setProductTitle(product.getTitle());
            vo.setProductCover(product.getCover());
        }
        if (order.getFinishTime() != null) {
            vo.setFollowDeadline(order.getFinishTime().plusDays(ReviewRule.FOLLOW_WINDOW_DAYS));
        }
        for (Review review : reviews) {
            ReviewVO reviewVO = reviewAssembler.toVO(review, viewerId, true);
            if (review.getReviewType() != null && review.getReviewType() == ReviewType.BUYER_TO_SELLER) {
                vo.setBuyerReview(reviewVO);
            } else {
                vo.setSellerReview(reviewVO);
            }
        }
        boolean participant = viewerId != null
                && (viewerId.equals(order.getBuyerId()) || viewerId.equals(order.getSellerId()));
        boolean finished = order.getStatus() != null && order.getStatus() == OrderStatus.FINISHED;
        boolean reviewedByMe = false;
        if (viewerId != null && viewerId.equals(order.getBuyerId())) {
            reviewedByMe = isFlagSet(order.getBuyerReviewed());
        } else if (viewerId != null && viewerId.equals(order.getSellerId())) {
            reviewedByMe = isFlagSet(order.getSellerReviewed());
        }
        vo.setCanReview(participant && finished && !reviewedByMe);
        vo.setCanFollow(false);
        return vo;
    }

    private boolean isFlagSet(Integer flag) {
        return flag != null && flag == 1;
    }

    private OrderProductSnapshotVO parseSnapshot(String snapshot) {
        if (!StringUtils.hasText(snapshot)) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot, OrderProductSnapshotVO.class);
        } catch (Exception e) {
            log.warn("商品快照解析失败：{}", snapshot);
            return null;
        }
    }
}
