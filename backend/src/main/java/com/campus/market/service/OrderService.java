// 文件路径: src/main/java/com/campus/market/service/OrderService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.dto.OrderQueryDTO;
import com.campus.market.entity.TradeOrder;
import com.campus.market.vo.OrderCountVO;
import com.campus.market.vo.OrderCreateVO;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;

/**
 * 订单业务接口。
 * <p>
 * 状态机：0待付款 → 1待发货 → 2待收货 → 3已完成；
 * 0 可取消为 4；1/2 可申请退款为 5，5 可同意为 6、可拒绝回退到来源状态。
 * 每个流转方法都带"操作人身份校验 + 前置状态校验 + 状态 CAS"，
 * 非法流转一律抛业务异常而不是静默忽略。
 */
public interface OrderService extends IService<TradeOrder> {

    /**
     * 创建订单。校验商品在售、不能买自己的商品、同一商品不能有进行中的订单，
     * 并把商品与地址冻结成快照。
     */
    OrderCreateVO create(Long buyerId, OrderCreateDTO dto);

    /**
     * 买家模拟支付。0 → 1，同时用状态 CAS 把商品从"已上架"改为"已售出"，
     * CAS 失败说明被其他买家抢先，本单回滚。
     */
    void pay(Long buyerId, String orderNo);

    /**
     * 卖家发货。1 → 2。
     */
    void ship(Long sellerId, String orderNo);

    /**
     * 买家确认收货。2 → 3。
     */
    void receive(Long buyerId, String orderNo);

    /**
     * 买家取消订单。仅 0 状态可取消，4 已取消。
     */
    void cancel(Long buyerId, String orderNo, String reason);

    /**
     * 买家申请退款。1/2 → 5，记录来源状态供拒绝时回退。
     */
    void applyRefund(Long buyerId, String orderNo, String reason);

    /**
     * 卖家同意退款。5 → 6，商品恢复上架。
     */
    void agreeRefund(Long sellerId, String orderNo);

    /**
     * 卖家拒绝退款。5 → 申请退款前的状态。
     */
    void rejectRefund(Long sellerId, String orderNo, String reason);

    /**
     * 买家撤销退款申请。5 → 申请退款前的状态。
     */
    void cancelRefund(Long buyerId, String orderNo);

    /**
     * 管理员强制取消。0/1/2/5 → 4；已支付的订单会恢复商品上架。
     */
    void adminCancel(String orderNo, Long operatorId, String operatorRole, String reason);

    /**
     * 管理员强制退款。1/2/5 → 6，商品恢复上架。
     */
    void adminRefund(String orderNo, Long operatorId, String operatorRole, String reason);

    /**
     * 订单详情。买家、卖家、管理员、客服可查看，其余人 403。
     */
    OrderDetailVO detail(String orderNo, Long viewerId, String viewerRole);

    /**
     * 我买到的（分页），可按状态筛选。
     */
    IPage<OrderListVO> buyerPage(Long buyerId, OrderQueryDTO query);

    /**
     * 我卖出的（分页），可按状态筛选。
     */
    IPage<OrderListVO> sellerPage(Long sellerId, OrderQueryDTO query);

    /**
     * 管理端全部订单（分页），支持订单号、买卖家昵称、状态、下单时间范围。
     */
    IPage<OrderListVO> adminPage(OrderQueryDTO query);

    /**
     * 某用户参与的全部订单（作为买家或卖家），管理端用户详情用。
     */
    IPage<OrderListVO> pageByParticipant(Long userId, Long pageNum, Long pageSize);

    /**
     * 买家侧各状态订单数，供个人中心角标。
     */
    OrderCountVO buyerCount(Long buyerId);

    /**
     * 管理端各状态订单数，供标签页角标。
     */
    OrderCountVO adminCount();

    /**
     * 取消单笔超时未付款订单，返回是否真的取消了。
     * 独立成方法是为了让定时任务通过代理调用它，从而让 @Transactional 生效。
     */
    boolean cancelTimeoutOrder(Long orderId);
}
