// 文件路径: src/main/java/com/campus/market/task/OrderTimeoutTask.java
package com.campus.market.task;

import com.campus.market.mapper.TradeOrderMapper;
import com.campus.market.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时未付款订单自动取消任务。
 * <p>
 * 为什么单独拆成一个 Component 而不是写成 OrderServiceImpl 里的私有方法？
 * Spring 的 @Transactional 依赖代理：同类内部直接调用 this.xxx() 不会开启事务，
 * 定时任务通过注入的 OrderService（代理对象）逐笔调用，才能保证
 * "改订单状态 + 写操作日志"在一个事务里，单笔失败不影响整批。
 * <p>
 * 单次最多处理 200 笔，避免一次扫描把所有待付款订单塞进一个长事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 单批处理上限 */
    private static final int BATCH_SIZE = 200;

    private final TradeOrderMapper tradeOrderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${app.order.timeout-scan-ms:300000}",
            initialDelayString = "${app.order.timeout-scan-ms:300000}")
    public void cancelTimeoutOrders() {
        List<Long> ids;
        try {
            ids = tradeOrderMapper.selectTimeoutIds(BATCH_SIZE);
        } catch (Exception e) {
            log.warn("扫描超时订单失败: {}", e.getMessage());
            return;
        }
        if (ids.isEmpty()) {
            return;
        }

        int canceled = 0;
        for (Long id : ids) {
            try {
                if (orderService.cancelTimeoutOrder(id)) {
                    canceled++;
                }
            } catch (Exception e) {
                // 单笔失败（如恰好被买家付款）只记日志，不阻断其余订单
                log.warn("超时订单取消失败: orderId={}, err={}", id, e.getMessage());
            }
        }
        log.info("超时订单扫描完成: 命中 {} 笔，成功取消 {} 笔", ids.size(), canceled);
    }
}
