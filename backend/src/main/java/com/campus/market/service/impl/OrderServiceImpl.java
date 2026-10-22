// 文件路径: src/main/java/com/campus/market/service/impl/OrderServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.OperatorRole;
import com.campus.market.common.OrderAction;
import com.campus.market.common.OrderStatus;
import com.campus.market.common.ProductStatus;
import com.campus.market.common.RedisKey;
import com.campus.market.common.ResultCode;
import com.campus.market.common.ReviewRule;
import com.campus.market.common.RoleConst;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.dto.OrderQueryDTO;
import com.campus.market.entity.Address;
import com.campus.market.entity.Category;
import com.campus.market.entity.OrderLog;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductImage;
import com.campus.market.entity.Review;
import com.campus.market.entity.SysUser;
import com.campus.market.entity.TradeOrder;
import com.campus.market.mapper.CategoryMapper;
import com.campus.market.mapper.OrderLogMapper;
import com.campus.market.mapper.ProductImageMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ReviewMapper;
import com.campus.market.mapper.SysUserMapper;
import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.service.AddressService;
import com.campus.market.service.ChatService;
import com.campus.market.service.DashboardCacheService;
import com.campus.market.service.NoticeService;
import com.campus.market.service.OrderService;
import com.campus.market.vo.AddressVO;
import com.campus.market.vo.OrderCountVO;
import com.campus.market.vo.OrderCreateVO;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;
import com.campus.market.vo.OrderLogVO;
import com.campus.market.vo.OrderProductSnapshotVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单业务实现。
 * <p>
 * 三个关键设计（论文可展开写）：
 * <ol>
 *   <li><b>状态机集中在一处</b>：每个流转都是「身份校验 → 前置状态校验 → 条件更新」，
 *       条件更新形如 {@code WHERE id=? AND status=?}，等价于一次状态 CAS；
 *       并发重复点击时只有一个请求能更新成功，另一次得到明确提示而不是脏数据。</li>
 *   <li><b>防超卖</b>：付款时先 CAS 订单 0→1，再用 {@code UPDATE product SET status=3 WHERE id=? AND status=1}
 *       抢占商品；影响行数为 0 说明被他人买走，直接抛异常回滚整笔订单。</li>
 *   <li><b>快照</b>：商品与地址在创建订单时序列化为 JSON 冻结，之后卖家改价、改标题、
 *       删除商品都不影响历史订单展示。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder> implements OrderService {

    /** 订单号时间部分格式：精确到毫秒，配合 4 位随机数，冲突概率极低 */
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 商品快照里的描述截断长度，详情页只展示摘要 */
    private static final int SNAPSHOT_DESC_MAX = 120;

    /** 订单号冲突时的重试次数 */
    private static final int ORDER_NO_RETRY = 5;

    /** 已支付状态，取消 / 退款时需要恢复商品上架 */
    private static final List<Integer> PAID_STATUS =
            Arrays.asList(OrderStatus.PENDING_SHIP, OrderStatus.PENDING_RECEIVE, OrderStatus.REFUNDING);

    private final TradeOrderMapper tradeOrderMapper;
    private final OrderLogMapper orderLogMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final CategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;
    private final ReviewMapper reviewMapper;
    private final AddressService addressService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;
    private final ChatService chatService;
    private final DashboardCacheService dashboardCacheService;

    /** 待付款保留时长（分钟），默认 30 分钟 */
    @Value("${app.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    // ==================== 下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO create(Long buyerId, OrderCreateDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (buyerId.equals(product.getSellerId())) {
            throw new BusinessException("不能购买自己发布的商品");
        }
        if (!Integer.valueOf(ProductStatus.ON_SALE).equals(product.getStatus())) {
            throw new BusinessException("该商品已售出或已下架，无法下单");
        }

        // 同一商品只允许一笔进行中的订单；已超时但还没被定时任务清理的待付款单不算数，
        // 否则买家会因为一笔"僵尸订单"白等最多 5 分钟。
        boolean occupied = this.lambdaQuery()
                .eq(TradeOrder::getProductId, dto.getProductId())
                .and(w -> w.in(TradeOrder::getStatus,
                                Arrays.asList(OrderStatus.PENDING_SHIP,
                                        OrderStatus.PENDING_RECEIVE, OrderStatus.REFUNDING))
                        .or(x -> x.eq(TradeOrder::getStatus, OrderStatus.PENDING_PAY)
                                .gt(TradeOrder::getPayDeadline, LocalDateTime.now())))
                .count() > 0;
        if (occupied) {
            throw new BusinessException("该商品已有进行中的订单，请稍后再试");
        }

        Address address = addressService.getOwned(buyerId, dto.getAddressId());

        LocalDateTime now = LocalDateTime.now();
        TradeOrder order = new TradeOrder();
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(product.getId());
        order.setPrice(product.getPrice());
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setPayDeadline(now.plusMinutes(payTimeoutMinutes));
        order.setProductSnapshot(toJson(buildProductSnapshot(product)));
        order.setAddressSnapshot(toJson(buildAddressSnapshot(address)));
        insertWithOrderNo(order);

        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.CREATE,
                null, OrderStatus.PENDING_PAY,
                "下单成功，成交价 " + order.getPrice().toPlainString() + " 元");
        // 新建订单不经过 casStatus（下单是 INSERT 不是 UPDATE），
        // 仪表盘缓存的失效要在这里单独补一次，否则总订单数不会实时增长
        dashboardCacheService.evictOnOrderChange();
        log.info("创建订单: orderNo={}, buyerId={}, productId={}", order.getOrderNo(), buyerId, product.getId());

        notifyStatusChange(order, order.getSellerId(), "收到新订单",
                "买家拍下了《" + productTitleOf(order) + "》，成交价 "
                        + order.getPrice().toPlainString() + " 元，等待买家付款。",
                "买家已下单，成交价 " + order.getPrice().toPlainString() + " 元");

        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPrice(order.getPrice());
        vo.setPayDeadline(order.getPayDeadline());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long buyerId, String orderNo) {
        TradeOrder order = loadOrder(orderNo);
        assertBuyer(order, buyerId);
        expectStatus(order, OrderStatus.PENDING_PAY, "当前状态不可付款");
        if (order.getPayDeadline() != null && order.getPayDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("订单已超过付款时限，请重新下单");
        }

        boolean paid = casStatus(order.getId(), OrderStatus.PENDING_PAY, OrderStatus.PENDING_SHIP,
                w -> w.set(TradeOrder::getPaymentTime, LocalDateTime.now()));
        if (!paid) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        // 关键：抢占商品。影响行数为 0 表示商品已被其他买家买走或已被下架，
        // 抛异常让上面的订单状态更新一并回滚，不会出现"付了钱没货"。
        if (productMapper.markSold(order.getProductId()) == 0) {
            throw new BusinessException("手慢了，该商品已被其他同学买走");
        }
        evictHotCache();

        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.PAY,
                OrderStatus.PENDING_PAY, OrderStatus.PENDING_SHIP, "模拟支付成功");
        log.info("订单支付成功: orderNo={}, buyerId={}", orderNo, buyerId);

        notifyStatusChange(order, order.getSellerId(), "买家已付款",
                "订单 " + order.getOrderNo() + " 已付款，请尽快发货或与买家约定线下交付。",
                "买家已付款，等待卖家发货");
    }

    // ==================== 履约 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long sellerId, String orderNo) {
        TradeOrder order = loadOrder(orderNo);
        assertSeller(order, sellerId);
        expectStatus(order, OrderStatus.PENDING_SHIP, "仅待发货订单可发货");

        boolean ok = casStatus(order.getId(), OrderStatus.PENDING_SHIP, OrderStatus.PENDING_RECEIVE,
                w -> w.set(TradeOrder::getShipTime, LocalDateTime.now()));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        writeLog(order, sellerId, OperatorRole.SELLER, OrderAction.SHIP,
                OrderStatus.PENDING_SHIP, OrderStatus.PENDING_RECEIVE, "卖家已发货 / 已约定线下交付");

        notifyStatusChange(order, order.getBuyerId(), "卖家已发货",
                "订单 " + order.getOrderNo() + " 已发货，收到物品后请及时确认收货。",
                "卖家已发货，请注意查收");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long buyerId, String orderNo) {
        TradeOrder order = loadOrder(orderNo);
        assertBuyer(order, buyerId);
        expectStatus(order, OrderStatus.PENDING_RECEIVE, "仅待收货订单可确认收货");

        boolean ok = casStatus(order.getId(), OrderStatus.PENDING_RECEIVE, OrderStatus.FINISHED,
                w -> w.set(TradeOrder::getFinishTime, LocalDateTime.now()));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.RECEIVE,
                OrderStatus.PENDING_RECEIVE, OrderStatus.FINISHED, "交易完成");

        notifyStatusChange(order, order.getSellerId(), "买家已确认收货",
                "订单 " + order.getOrderNo() + " 已完成，成交金额 "
                        + order.getPrice().toPlainString() + " 元。",
                "买家已确认收货，交易完成");
    }

    // ==================== 取消与退款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long buyerId, String orderNo, String reason) {
        TradeOrder order = loadOrder(orderNo);
        assertBuyer(order, buyerId);
        expectStatus(order, OrderStatus.PENDING_PAY, "仅待付款订单可直接取消，已付款请申请退款");

        String text = StringUtils.hasText(reason) ? reason.trim() : "买家主动取消";
        boolean ok = casStatus(order.getId(), OrderStatus.PENDING_PAY, OrderStatus.CANCELED,
                w -> w.set(TradeOrder::getCancelTime, LocalDateTime.now())
                        .set(TradeOrder::getCancelReason, text));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        // 待付款订单未占用商品，无需恢复上架
        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.CANCEL,
                OrderStatus.PENDING_PAY, OrderStatus.CANCELED, text);
        log.info("买家取消订单: orderNo={}, reason={}", orderNo, text);

        notifyStatusChange(order, order.getSellerId(), "买家取消了订单",
                "订单 " + order.getOrderNo() + " 已被买家取消：" + text,
                "买家取消了订单：" + text);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(Long buyerId, String orderNo, String reason) {
        TradeOrder order = loadOrder(orderNo);
        assertBuyer(order, buyerId);
        int from = order.getStatus();
        if (from != OrderStatus.PENDING_SHIP && from != OrderStatus.PENDING_RECEIVE) {
            throw new BusinessException("仅待发货或待收货订单可申请退款");
        }

        String text = reason == null ? "" : reason.trim();
        if (text.length() < 5) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "退款原因至少 5 个字，便于卖家判断");
        }
        boolean ok = casStatus(order.getId(), from, OrderStatus.REFUNDING,
                w -> w.set(TradeOrder::getRefundFromStatus, from)
                        .set(TradeOrder::getRefundReason, text));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.APPLY_REFUND, from, OrderStatus.REFUNDING, text);

        notifyStatusChange(order, order.getSellerId(), "收到退款申请",
                "买家对订单 " + order.getOrderNo() + " 申请退款：" + text
                        + "。请在订单详情中处理。",
                "买家申请退款：" + text);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void agreeRefund(Long sellerId, String orderNo) {
        TradeOrder order = loadOrder(orderNo);
        assertSeller(order, sellerId);
        expectStatus(order, OrderStatus.REFUNDING, "该订单当前没有待处理的退款申请");

        boolean ok = casStatus(order.getId(), OrderStatus.REFUNDING, OrderStatus.REFUNDED,
                w -> w.set(TradeOrder::getRefundTime, LocalDateTime.now()));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        restoreProduct(order.getProductId());
        writeLog(order, sellerId, OperatorRole.SELLER, OrderAction.AGREE_REFUND,
                OrderStatus.REFUNDING, OrderStatus.REFUNDED, "卖家同意退款，商品已恢复上架");

        notifyStatusChange(order, order.getBuyerId(), "退款已完成",
                "卖家已同意退款，订单 " + order.getOrderNo() + " 退款完成，商品已重新上架。",
                "卖家同意退款，订单已完成退款");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRefund(Long sellerId, String orderNo, String reason) {
        TradeOrder order = loadOrder(orderNo);
        assertSeller(order, sellerId);
        expectStatus(order, OrderStatus.REFUNDING, "该订单当前没有待处理的退款申请");

        int back = backStatusOf(order);
        String text = reason == null ? "" : reason.trim();
        boolean ok = casStatus(order.getId(), OrderStatus.REFUNDING, back,
                w -> w.set(TradeOrder::getRejectRefundReason, text));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        writeLog(order, sellerId, OperatorRole.SELLER, OrderAction.REJECT_REFUND,
                OrderStatus.REFUNDING, back, "卖家拒绝退款：" + text);

        notifyStatusChange(order, order.getBuyerId(), "退款申请被拒绝",
                "卖家拒绝了订单 " + order.getOrderNo() + " 的退款申请：" + text
                        + "。如仍有争议可联系客服介入。",
                "卖家拒绝退款：" + text);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRefund(Long buyerId, String orderNo) {
        TradeOrder order = loadOrder(orderNo);
        assertBuyer(order, buyerId);
        expectStatus(order, OrderStatus.REFUNDING, "该订单当前没有待处理的退款申请");

        int back = backStatusOf(order);
        boolean ok = casStatus(order.getId(), OrderStatus.REFUNDING, back,
                w -> w.set(TradeOrder::getRefundReason, null));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        writeLog(order, buyerId, OperatorRole.BUYER, OrderAction.CANCEL_REFUND,
                OrderStatus.REFUNDING, back, "买家撤销退款申请，订单回到原状态");

        notifyStatusChange(order, order.getSellerId(), "买家撤销了退款申请",
                "订单 " + order.getOrderNo() + " 的退款申请已被买家撤销，订单回到原状态。",
                "买家撤销退款申请，订单回到原状态");
    }

    // ==================== 管理端干预 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminCancel(String orderNo, Long operatorId, String operatorRole, String reason) {
        TradeOrder order = loadOrder(orderNo);
        int from = order.getStatus();
        if (from != OrderStatus.PENDING_PAY && from != OrderStatus.PENDING_SHIP
                && from != OrderStatus.PENDING_RECEIVE && from != OrderStatus.REFUNDING) {
            throw new BusinessException("当前状态不可强制取消，已完成的订单只能走退款流程");
        }

        String text = "管理员强制取消：" + (reason == null ? "" : reason.trim());
        boolean ok = casStatus(order.getId(), from, OrderStatus.CANCELED,
                w -> w.set(TradeOrder::getCancelTime, LocalDateTime.now())
                        .set(TradeOrder::getCancelReason, text));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        if (PAID_STATUS.contains(from)) {
            restoreProduct(order.getProductId());
        }
        writeLog(order, operatorId, operatorRole, OrderAction.ADMIN_CANCEL,
                from, OrderStatus.CANCELED, text);
        log.warn("管理员强制取消订单: orderNo={}, operator={}, reason={}", orderNo, operatorId, text);

        notifyStatusChange(order, order.getBuyerId(), "订单已被平台取消",
                "订单 " + order.getOrderNo() + " 已由平台取消：" + text, "订单已被平台取消：" + text);
        notifyStatusChange(order, order.getSellerId(), "订单已被平台取消",
                "订单 " + order.getOrderNo() + " 已由平台取消：" + text, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminRefund(String orderNo, Long operatorId, String operatorRole, String reason) {
        TradeOrder order = loadOrder(orderNo);
        int from = order.getStatus();
        if (!PAID_STATUS.contains(from)) {
            throw new BusinessException("仅已支付的订单可强制退款，待付款订单请使用强制取消");
        }

        String text = reason == null ? "" : reason.trim();
        boolean ok = casStatus(order.getId(), from, OrderStatus.REFUNDED,
                w -> w.set(TradeOrder::getRefundTime, LocalDateTime.now())
                        .set(TradeOrder::getRejectRefundReason, null)
                        .set(TradeOrder::getRefundReason, StringUtils.hasText(order.getRefundReason())
                                ? order.getRefundReason() + "｜客服介入：" + text
                                : "客服介入退款：" + text));
        if (!ok) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        restoreProduct(order.getProductId());
        writeLog(order, operatorId, operatorRole, OrderAction.ADMIN_REFUND,
                from, OrderStatus.REFUNDED, "管理员强制退款：" + text);
        log.warn("管理员强制退款: orderNo={}, operator={}, reason={}", orderNo, operatorId, text);

        notifyStatusChange(order, order.getBuyerId(), "订单已被平台退款",
                "订单 " + order.getOrderNo() + " 已由平台退款：" + text, "订单已被平台退款：" + text);
        notifyStatusChange(order, order.getSellerId(), "订单已被平台退款",
                "订单 " + order.getOrderNo() + " 已由平台退款：" + text, null);
    }

    // ==================== 查询 ====================

    @Override
    public OrderDetailVO detail(String orderNo, Long viewerId, String viewerRole) {
        TradeOrder order = loadOrder(orderNo);
        boolean isBuyer = viewerId != null && viewerId.equals(order.getBuyerId());
        boolean isSeller = viewerId != null && viewerId.equals(order.getSellerId());
        boolean isAdmin = RoleConst.ADMIN.equals(viewerRole);
        boolean isStaff = isAdmin || RoleConst.CUSTOMER_SERVICE.equals(viewerRole);
        if (!isBuyer && !isSeller && !isStaff) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }

        Map<Long, SysUser> users = loadUsers(List.of(order.getBuyerId(), order.getSellerId()));
        int status = order.getStatus();

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(status);
        vo.setStatusLabel(OrderStatus.label(status));
        vo.setPrice(order.getPrice());
        vo.setBuyerId(order.getBuyerId());
        SysUser buyer = users.get(order.getBuyerId());
        if (buyer != null) {
            vo.setBuyerNickname(buyer.getNickname());
            vo.setBuyerAvatar(buyer.getAvatar());
        }
        vo.setSellerId(order.getSellerId());
        SysUser seller = users.get(order.getSellerId());
        if (seller != null) {
            vo.setSellerNickname(seller.getNickname());
            vo.setSellerAvatar(seller.getAvatar());
        }
        vo.setProduct(parseProductSnapshot(order.getProductSnapshot()));
        vo.setAddress(parseAddressSnapshot(order.getAddressSnapshot()));
        vo.setCreateTime(order.getCreateTime());
        vo.setPayDeadline(order.getPayDeadline());
        vo.setPaymentTime(order.getPaymentTime());
        vo.setShipTime(order.getShipTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setRefundTime(order.getRefundTime());
        vo.setCancelReason(order.getCancelReason());
        vo.setRefundReason(order.getRefundReason());
        vo.setRejectRefundReason(order.getRejectRefundReason());
        vo.setRefundFromStatus(order.getRefundFromStatus());
        vo.setLogs(buildLogs(order.getId()));

        vo.setIsBuyer(isBuyer);
        vo.setIsSeller(isSeller);
        // 可执行动作全部由服务端判定，前端只按布尔值渲染按钮
        vo.setCanPay(isBuyer && status == OrderStatus.PENDING_PAY);
        vo.setCanCancel(isBuyer && status == OrderStatus.PENDING_PAY);
        vo.setCanShip(isSeller && status == OrderStatus.PENDING_SHIP);
        vo.setCanReceive(isBuyer && status == OrderStatus.PENDING_RECEIVE);
        vo.setCanApplyRefund(isBuyer && (status == OrderStatus.PENDING_SHIP || status == OrderStatus.PENDING_RECEIVE));
        vo.setCanAgreeRefund(isSeller && status == OrderStatus.REFUNDING);
        vo.setCanRejectRefund(isSeller && status == OrderStatus.REFUNDING);
        vo.setCanCancelRefund(isBuyer && status == OrderStatus.REFUNDING);
        vo.setCanAdminCancel(isAdmin
                && (status == OrderStatus.PENDING_PAY || status == OrderStatus.PENDING_SHIP
                || status == OrderStatus.PENDING_RECEIVE || status == OrderStatus.REFUNDING));
        vo.setCanAdminRefund(isAdmin && PAID_STATUS.contains(status));
        fillReviewInfo(vo, order, viewerId);
        return vo;
    }

    /**
     * 填充评价相关字段：订单详情页要按这些布尔值决定显示「评价 / 查看评价 / 追评」。
     * <p>
     * 为什么不直接调 ReviewService：ReviewService 依赖 CreditService，
     * 而订单服务是更底层的业务，让它反向依赖评价服务会把依赖链绕成环。
     * 这里只需要「这个订单有没有我的评价」，一条按 orderId 的查询就够了，
     * 因此直接注入 ReviewMapper，比引入整个 Service 更轻。
     */
    private void fillReviewInfo(OrderDetailVO vo, TradeOrder order, Long viewerId) {
        vo.setBuyerReviewed(order.getBuyerReviewed());
        vo.setSellerReviewed(order.getSellerReviewed());
        vo.setCanReview(false);
        vo.setCanFollowReview(false);
        if (order.getFinishTime() != null) {
            vo.setReviewDeadline(order.getFinishTime().plusDays(ReviewRule.FOLLOW_WINDOW_DAYS));
        }
        boolean finished = order.getStatus() != null && order.getStatus() == OrderStatus.FINISHED;
        boolean participant = viewerId != null
                && (viewerId.equals(order.getBuyerId()) || viewerId.equals(order.getSellerId()));
        if (!finished || !participant) {
            return;
        }
        List<Review> reviews = reviewMapper.selectByOrderIds(List.of(order.getId()));
        Review mine = reviews.stream()
                .filter(review -> viewerId.equals(review.getReviewerId()))
                .findFirst()
                .orElse(null);
        if (mine == null) {
            vo.setCanReview(true);
            return;
        }
        vo.setMyReviewId(mine.getId());
        boolean withinWindow = order.getFinishTime() != null
                && LocalDateTime.now().isBefore(order.getFinishTime().plusDays(ReviewRule.FOLLOW_WINDOW_DAYS));
        vo.setCanFollowReview(withinWindow && !StringUtils.hasText(mine.getFollowContent()));
    }

    @Override
    public IPage<OrderListVO> buyerPage(Long buyerId, OrderQueryDTO query) {
        Page<TradeOrder> page = buildPage(query);
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getBuyerId, buyerId)
                .eq(query.getStatus() != null, TradeOrder::getStatus, query.getStatus())
                .orderByDesc(TradeOrder::getId);
        return convertPage(this.page(page, wrapper));
    }

    @Override
    public IPage<OrderListVO> sellerPage(Long sellerId, OrderQueryDTO query) {
        Page<TradeOrder> page = buildPage(query);
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getSellerId, sellerId)
                .eq(query.getStatus() != null, TradeOrder::getStatus, query.getStatus())
                .orderByDesc(TradeOrder::getId);
        return convertPage(this.page(page, wrapper));
    }

    @Override
    public IPage<OrderListVO> adminPage(OrderQueryDTO query) {
        Page<TradeOrder> page = buildPage(query);
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .like(StringUtils.hasText(query.getOrderNo()), TradeOrder::getOrderNo,
                        query.getOrderNo() == null ? null : query.getOrderNo().trim())
                .eq(query.getStatus() != null, TradeOrder::getStatus, query.getStatus())
                .ge(parseTime(query.getStartTime(), false) != null, TradeOrder::getCreateTime,
                        parseTime(query.getStartTime(), false))
                .le(parseTime(query.getEndTime(), true) != null, TradeOrder::getCreateTime,
                        parseTime(query.getEndTime(), true))
                .orderByDesc(TradeOrder::getId);

        // 昵称是 sys_user 的字段，先按昵称查出用户ID再过滤订单，
        // 避免订单表 JOIN 用户表导致的索引失效与分页 COUNT 变慢
        if (StringUtils.hasText(query.getBuyerKeyword())) {
            List<Long> ids = matchUserIds(query.getBuyerKeyword());
            if (ids.isEmpty()) {
                return emptyPage(page);
            }
            wrapper.in(TradeOrder::getBuyerId, ids);
        }
        if (StringUtils.hasText(query.getSellerKeyword())) {
            List<Long> ids = matchUserIds(query.getSellerKeyword());
            if (ids.isEmpty()) {
                return emptyPage(page);
            }
            wrapper.in(TradeOrder::getSellerId, ids);
        }
        return convertPage(this.page(page, wrapper));
    }

    @Override
    public OrderCountVO buyerCount(Long buyerId) {
        return OrderCountVO.from(tradeOrderMapper.countByBuyer(buyerId));
    }
    @Override
    public OrderCountVO adminCount() {
        return OrderCountVO.from(tradeOrderMapper.countAllGroupByStatus());
    }

    @Override
    public IPage<OrderListVO> pageByParticipant(Long userId, Long pageNum, Long pageSize) {
        long current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<TradeOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.<TradeOrder>lambdaQuery()
                .and(w -> w.eq(TradeOrder::getBuyerId, userId).or().eq(TradeOrder::getSellerId, userId))
                .orderByDesc(TradeOrder::getId);
        return convertPage(this.page(page, wrapper));
    }

    // ==================== 定时任务入口 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTimeoutOrder(Long orderId) {
        TradeOrder order = this.getById(orderId);
        if (order == null || !Integer.valueOf(OrderStatus.PENDING_PAY).equals(order.getStatus())) {
            return false;
        }
        boolean ok = casStatus(orderId, OrderStatus.PENDING_PAY, OrderStatus.CANCELED,
                w -> w.set(TradeOrder::getCancelTime, LocalDateTime.now())
                        .set(TradeOrder::getCancelReason, "超时未付款，系统自动取消"));
        if (!ok) {
            // 扫描与买家付款并发：CAS 失败直接跳过，不做任何补偿
            return false;
        }
        writeLog(order, null, OperatorRole.SYSTEM, OrderAction.TIMEOUT_CANCEL,
                OrderStatus.PENDING_PAY, OrderStatus.CANCELED,
                "下单后 " + payTimeoutMinutes + " 分钟内未付款，系统自动取消");
        log.info("超时订单自动取消: orderNo={}", order.getOrderNo());

        notifyStatusChange(order, order.getBuyerId(), "订单超时已取消",
                "订单 " + order.getOrderNo() + " 超过 " + payTimeoutMinutes
                        + " 分钟未付款，已自动取消，商品已重新上架。",
                "订单超时未付款，已自动取消");
        return true;
    }

    // ==================== 私有工具 ====================

    /**
     * 订单状态变更后的统一提醒：写一条系统通知（tabBar 角标可见），
     * 并尽量在买卖双方的会话里追加一条系统消息（没聊过天就不追加）。
     * <p>
     * 两个动作都包在 try-catch 里：通知属于附属功能，任何失败都不能把已经成功的
     * 下单 / 发货 / 退款流程变成失败。所有调用点都放在方法末尾（业务写库全部完成之后），
     * 这样即便外层事务最终回滚，也不会出现「订单没付成功却发了付款通知」。
     */
    private void notifyStatusChange(TradeOrder order, Long targetUserId,
                                    String title, String content, String chatText) {
        if (targetUserId != null) {
            try {
                noticeService.notifyOrder(targetUserId, title, content, order.getId());
            } catch (Exception e) {
                log.warn("订单通知发送失败 orderNo={}：{}", order.getOrderNo(), e.getMessage());
            }
        }
        if (chatText != null) {
            try {
                chatService.appendSystemMessage(order.getBuyerId(), order.getSellerId(),
                        order.getProductId(), chatText);
            } catch (Exception e) {
                log.warn("订单系统消息写入失败 orderNo={}：{}", order.getOrderNo(), e.getMessage());
            }
        }
    }

    /** 订单里冻结的商品标题，用于通知文案；历史数据缺失时退化为订单号 */
    private String productTitleOf(TradeOrder order) {
        OrderProductSnapshotVO snapshot = parseProductSnapshot(order.getProductSnapshot());
        return snapshot == null || snapshot.getTitle() == null ? "闲置物品" : snapshot.getTitle();
    }
    private TradeOrder loadOrder(String orderNo) {
        TradeOrder order = this.lambdaQuery().eq(TradeOrder::getOrderNo, orderNo).one();
        if (order == null) {
            throw new BusinessException("订单不存在或已被删除");
        }
        return order;
    }

    private void assertBuyer(TradeOrder order, Long userId) {
        if (userId == null || !userId.equals(order.getBuyerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家本人可以执行该操作");
        }
    }

    private void assertSeller(TradeOrder order, Long userId) {
        if (userId == null || !userId.equals(order.getSellerId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有卖家本人可以执行该操作");
        }
    }

    private void expectStatus(TradeOrder order, int expected, String message) {
        if (!Integer.valueOf(expected).equals(order.getStatus())) {
            throw new BusinessException(message + "（当前：" + OrderStatus.label(order.getStatus()) + "）");
        }
    }

    /**
     * 状态 CAS：只有当前状态仍等于 from 时才更新，返回值即影响行数。
     * 这是整套并发控制的核心，比先查后改少一个竞态窗口。
     * <p>
     * <b>仪表盘缓存失效也挂在这里</b>：订单的全部状态流转
     * （付款 / 发货 / 收货完成 / 取消 / 申请退款 / 同意退款 / 拒绝退款 / 撤销退款 /
     * 管理员强制取消与退款 / 超时自动取消）都经过这一个方法，
     * 在这里统一失效一次，就不必在十来个调用点各写一遍——
     * 漏掉一个调用点，仪表盘上的订单数就会一直停在旧值上，而且极难发现。
     */
    private boolean casStatus(Long orderId, int from, int to,
                              Consumer<LambdaUpdateWrapper<TradeOrder>> customizer) {
        LambdaUpdateWrapper<TradeOrder> wrapper = Wrappers.<TradeOrder>lambdaUpdate()
                .eq(TradeOrder::getId, orderId)
                .eq(TradeOrder::getStatus, from)
                .set(TradeOrder::getStatus, to);
        if (customizer != null) {
            customizer.accept(wrapper);
        }
        boolean updated = this.update(wrapper);
        if (updated) {
            // 只在真的改成功时失效：CAS 失败说明状态已被别人改过，
            // 这次请求没有产生任何数据变化，没必要让缓存失效
            dashboardCacheService.evictOnOrderChange();
        }
        return updated;
    }

    /**
     * 退款被拒绝 / 撤销时回到哪个状态。理论上一定是 1 或 2，
     * 兜底走待发货，避免因历史数据缺失把订单卡在退款中。
     */
    private int backStatusOf(TradeOrder order) {
        Integer from = order.getRefundFromStatus();
        if (from != null && (from == OrderStatus.PENDING_SHIP || from == OrderStatus.PENDING_RECEIVE)) {
            return from;
        }
        return OrderStatus.PENDING_SHIP;
    }

    private void restoreProduct(Long productId) {
        if (productId == null) {
            return;
        }
        int rows = productMapper.restoreOnSale(productId);
        if (rows > 0) {
            evictHotCache();
            log.info("商品恢复上架: productId={}", productId);
        }
    }

    private void evictHotCache() {
        try {
            redis.delete(RedisKey.PRODUCT_HOT);
        } catch (Exception e) {
            log.warn("清理热门商品缓存失败: {}", e.getMessage());
        }
    }

    private void insertWithOrderNo(TradeOrder order) {
        for (int attempt = 0; attempt < ORDER_NO_RETRY; attempt++) {
            order.setOrderNo(generateOrderNo());
            try {
                this.save(order);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("订单号冲突，重试第 {} 次: {}", attempt + 1, order.getOrderNo());
            }
        }
        throw new BusinessException("订单号生成冲突，请稍后重试");
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private OrderProductSnapshotVO buildProductSnapshot(Product product) {
        OrderProductSnapshotVO snapshot = new OrderProductSnapshotVO();
        snapshot.setProductId(product.getId());
        snapshot.setTitle(product.getTitle());
        snapshot.setCover(coverOf(product.getId()));
        snapshot.setPrice(product.getPrice());
        snapshot.setOriginalPrice(product.getOriginalPrice());
        snapshot.setDescription(truncate(product.getDescription(), SNAPSHOT_DESC_MAX));
        snapshot.setIsVerifiedSeller(Integer.valueOf(1).equals(product.getIsVerifiedSeller()));
        Category category = categoryMapper.selectById(product.getCategoryId());
        snapshot.setCategoryName(category == null ? null : category.getName());
        return snapshot;
    }

    private String coverOf(Long productId) {
        ProductImage cover = productImageMapper.selectOne(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, productId)
                .orderByAsc(ProductImage::getSort)
                .orderByAsc(ProductImage::getId)
                .last("limit 1"));
        return cover == null ? null : cover.getImageUrl();
    }

    private AddressVO buildAddressSnapshot(Address address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setPhone(address.getPhone());
        vo.setAddress(address.getAddress());
        vo.setIsDefault(Integer.valueOf(1).equals(address.getIsDefault()));
        return vo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("订单快照序列化失败", e);
            throw new BusinessException("订单创建失败，请稍后重试");
        }
    }

    private OrderProductSnapshotVO parseProductSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return new OrderProductSnapshotVO();
        }
        try {
            return objectMapper.readValue(json, OrderProductSnapshotVO.class);
        } catch (Exception e) {
            log.warn("商品快照解析失败: {}", e.getMessage());
            return new OrderProductSnapshotVO();
        }
    }

    private AddressVO parseAddressSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AddressVO.class);
        } catch (Exception e) {
            log.warn("地址快照解析失败: {}", e.getMessage());
            return null;
        }
    }

    private void writeLog(TradeOrder order, Long operatorId, String operatorRole, String action,
                          Integer fromStatus, Integer toStatus, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(order.getId());
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setRemark(remark);
        orderLogMapper.insert(log);
    }

    private List<OrderLogVO> buildLogs(Long orderId) {
        List<OrderLog> logs = orderLogMapper.selectList(Wrappers.<OrderLog>lambdaQuery()
                .eq(OrderLog::getOrderId, orderId)
                .orderByAsc(OrderLog::getId));
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> operatorIds = logs.stream()
                .map(OrderLog::getOperatorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> operators = loadUsers(operatorIds);

        List<OrderLogVO> result = new ArrayList<>(logs.size());
        for (OrderLog entity : logs) {
            OrderLogVO vo = new OrderLogVO();
            vo.setId(entity.getId());
            vo.setAction(entity.getAction());
            vo.setActionLabel(OrderAction.label(entity.getAction()));
            vo.setOperatorRole(entity.getOperatorRole());
            vo.setOperatorRoleLabel(OperatorRole.label(entity.getOperatorRole()));
            vo.setOperatorId(entity.getOperatorId());
            SysUser operator = entity.getOperatorId() == null ? null : operators.get(entity.getOperatorId());
            vo.setOperatorName(operator == null ? "系统" : operator.getNickname());
            vo.setFromStatus(entity.getFromStatus());
            vo.setToStatus(entity.getToStatus());
            vo.setRemark(entity.getRemark());
            vo.setCreateTime(entity.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private Map<Long, SysUser> loadUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    private List<Long> matchUserIds(String keyword) {
        String kw = keyword.trim();
        if (!StringUtils.hasText(kw)) {
            return Collections.emptyList();
        }
        return sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId)
                        .like(SysUser::getNickname, kw)
                        .last("limit 500"))
                .stream().map(SysUser::getId).toList();
    }

    private IPage<OrderListVO> convertPage(IPage<TradeOrder> page) {
        Page<OrderListVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<TradeOrder> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<Long> userIds = new ArrayList<>();
        records.forEach(o -> {
            userIds.add(o.getBuyerId());
            userIds.add(o.getSellerId());
        });
        Map<Long, SysUser> users = loadUsers(userIds);

        List<OrderListVO> list = new ArrayList<>(records.size());
        for (TradeOrder order : records) {
            OrderListVO vo = new OrderListVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus(order.getStatus());
            vo.setStatusLabel(OrderStatus.label(order.getStatus()));
            vo.setPrice(order.getPrice());
            vo.setBuyerId(order.getBuyerId());
            vo.setSellerId(order.getSellerId());
            vo.setCreateTime(order.getCreateTime());
            vo.setPayDeadline(order.getPayDeadline());

            OrderProductSnapshotVO snapshot = parseProductSnapshot(order.getProductSnapshot());
            vo.setProductId(snapshot.getProductId() == null ? order.getProductId() : snapshot.getProductId());
            vo.setProductTitle(snapshot.getTitle());
            vo.setProductCover(snapshot.getCover());

            SysUser buyer = users.get(order.getBuyerId());
            vo.setBuyerNickname(buyer == null ? "已注销用户" : buyer.getNickname());
            SysUser seller = users.get(order.getSellerId());
            vo.setSellerNickname(seller == null ? "已注销用户" : seller.getNickname());
            list.add(vo);
        }
        result.setRecords(list);
        return result;
    }

    private Page<TradeOrder> buildPage(OrderQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 10 : Math.min(query.getPageSize(), 100);
        return new Page<>(pageNum, pageSize);
    }

    private IPage<OrderListVO> emptyPage(Page<TradeOrder> page) {
        Page<OrderListVO> result = new Page<>(page.getCurrent(), page.getSize(), 0);
        result.setRecords(Collections.emptyList());
        return result;
    }

    /**
     * 解析日期参数：只传日期时，起始补 00:00:00，结束补 23:59:59。
     */
    private LocalDateTime parseTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            if (text.length() == 16) {
                text = text + (endOfDay ? ":59" : ":00");
            }
            return LocalDateTime.parse(text, DATE_TIME_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
