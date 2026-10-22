// 文件路径: src/main/java/com/campus/market/service/ViewCountService.java
package com.campus.market.service;

/**
 * 商品浏览量计数。
 * <p>
 * 商品详情是读多写多的接口，每次详情页都 UPDATE 一次 product 会让
 * 同一行的行锁竞争变大、并产生大量 binlog。这里改为：
 * 详情页只对 Redis Hash 做 HINCRBY，定时任务批量累加回 MySQL。
 * 详情接口返回给前端的浏览量 = 库中值 + 尚未回写的增量，保证用户看到的是实时数。
 */
public interface ViewCountService {

    /** 浏览量 +1（只写 Redis） */
    void increment(Long productId);

    /** 尚未回写 MySQL 的增量 */
    int pending(Long productId);

    /** 把 Redis 增量批量刷回 MySQL，返回本次回写的商品数 */
    int flush();

    /** 清理某个商品的增量（商品删除时调用） */
    void clear(Long productId);
}
