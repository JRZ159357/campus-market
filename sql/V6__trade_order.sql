-- 文件路径: sql/V6__trade_order.sql
-- 模块5：交易与订单。三张表：address（收货/见面地址）、trade_order（订单）、order_log（订单操作日志）
-- 说明：
--   1) 项目统一使用 create_time / update_time，与 created_at / updated_at 语义一致；
--   2) trade_order.pay_deadline 单独存一列而不是在定时任务里算 DATE_ADD(create_time, 30 MINUTE)，
--      这样能走 (status, pay_deadline) 组合索引，扫描量只与待付款订单数相关；
--   3) trade_order.refund_from_status 记录申请退款前的状态（1待发货 / 2待收货），
--      卖家拒绝退款时据此回退，避免"只能退回到待发货"的业务错误；
--   4) 所有表均用 CREATE TABLE IF NOT EXISTS，脚本可重复执行且不会清空已有数据。

USE campus_market;

-- ---------------------------------------------------------------
-- 1. 收货 / 交易地址
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS address (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    receiver_name VARCHAR(20)     NOT NULL COMMENT '收货人 / 联系人姓名',
    phone         VARCHAR(20)     NOT NULL COMMENT '联系电话',
    address       VARCHAR(120)    NOT NULL COMMENT '详细地址，校园交易可填宿舍楼 / 教学楼 / 约定见面点',
    is_default    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否默认地址:0否 1是',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除:0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_user_default (user_id, deleted, is_default, id) COMMENT '我的地址列表 / 取默认地址'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '收货地址';

-- ---------------------------------------------------------------
-- 2. 订单
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_order (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no           VARCHAR(32)     NOT NULL COMMENT '订单号：yyyyMMddHHmmssSSS + 4位随机数',
    buyer_id           BIGINT UNSIGNED NOT NULL COMMENT '买家ID',
    seller_id          BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
    product_id         BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    product_snapshot   JSON            NOT NULL COMMENT '商品标题/主图/价格等快照，防止商品被改或删除后订单失忆',
    price              DECIMAL(10, 2)  NOT NULL COMMENT '成交价格（下单时的商品价格）',
    status             TINYINT         NOT NULL DEFAULT 0 COMMENT '状态:0待付款 1待发货 2待收货 3已完成 4已取消 5退款中 6已退款',
    refund_from_status TINYINT         NULL COMMENT '申请退款前的状态:1待发货 2待收货，拒绝退款时回退用',
    address_snapshot   JSON            NULL COMMENT '收货/见面地址快照',
    pay_deadline       DATETIME        NOT NULL COMMENT '支付截止时间，超时由定时任务自动取消',
    payment_time       DATETIME        NULL COMMENT '支付时间',
    ship_time          DATETIME        NULL COMMENT '发货时间',
    finish_time        DATETIME        NULL COMMENT '完成时间',
    cancel_time        DATETIME        NULL COMMENT '取消时间',
    refund_time        DATETIME        NULL COMMENT '退款完成时间',
    cancel_reason      VARCHAR(200)    NULL COMMENT '取消原因',
    refund_reason      VARCHAR(200)    NULL COMMENT '退款申请原因',
    reject_refund_reason VARCHAR(200)  NULL COMMENT '卖家拒绝退款的原因',
    create_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    update_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除:0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no) COMMENT '订单号唯一',
    KEY idx_buyer_status (buyer_id, status, id) COMMENT '我买到的：按买家+状态筛选并按id倒序',
    KEY idx_seller_status (seller_id, status, id) COMMENT '我卖出的：按卖家+状态筛选并按id倒序',
    KEY idx_status_deadline (status, pay_deadline) COMMENT '定时任务扫描超时未付款订单',
    KEY idx_product_status (product_id, status) COMMENT '判断商品是否已有进行中的订单',
    KEY idx_create_time (create_time) COMMENT '管理端按下单时间范围筛选'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '交易订单';

-- ---------------------------------------------------------------
-- 3. 订单操作日志（全生命周期留痕，答辩可展示）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_log (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    operator_id   BIGINT UNSIGNED NULL COMMENT '操作人ID，系统自动操作为 NULL',
    operator_role VARCHAR(20)     NOT NULL COMMENT '操作人角色:buyer/seller/admin/customer_service/system',
    action        VARCHAR(30)     NOT NULL COMMENT '动作:create/pay/ship/receive/cancel/apply_refund/agree_refund/reject_refund/cancel_refund/timeout_cancel/admin_cancel/admin_refund',
    from_status   TINYINT         NULL COMMENT '变更前状态',
    to_status     TINYINT         NULL COMMENT '变更后状态',
    remark        VARCHAR(255)    NULL COMMENT '备注：原因、金额、操作说明',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_order (order_id, id) COMMENT '按订单查时间线，按id升序即操作顺序'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '订单操作日志';
