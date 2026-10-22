// 文件路径: src/main/java/com/campus/market/service/HomeCacheService.java
package com.campus.market.service;

import com.campus.market.vo.HomeIndexVO;

/**
 * 首页聚合缓存服务（模块 12）。
 * <p>
 * 为什么不把缓存读写直接写在 HomeServiceImpl 里：首页配置的写入口有三处
 * （轮播图增删改、配置保存、金刚区排序），而它们分别落在 BannerService 与
 * HomeConfigService 中。如果缓存逻辑放在 HomeServiceImpl，
 * 这两个服务就要反过来依赖 HomeServiceImpl，而 HomeServiceImpl 又依赖它们——
 * 循环依赖，Spring 启动直接失败。
 * <p>
 * 抽成独立服务后依赖是单向的：业务 Service → 本接口 → Redis。
 * 本服务只依赖 Redis 与 Jackson，不依赖任何业务 Service。
 * <p>
 * <b>只缓存一个 key</b>：整个首页聚合结果就是一次请求的全部内容，
 * 拆成「轮播图 key / 商品 key / 分类 key」只会让失效逻辑变复杂
 * （任何一处变更都要把所有相关 key 都删对），而收益为零——
 * 首页数据量小，一次反序列化就能全部返回。
 * <p>
 * <b>降级</b>：Redis 异常时读返回 null（走查库）、写与删静默失败，
 * 首页永远能打开。缓存是优化，不是功能依赖。
 */
public interface HomeCacheService {

    /**
     * 首页聚合缓存 TTL：5 分钟。
     * <p>
     * 取 5 分钟而不是更长，是因为「热门/新品」里含商品数据，
     * 商品上架后最多等 5 分钟就能出现在首页；
     * 而运营改配置、改轮播图是<b>主动失效</b>的，不受这个 TTL 影响。
     */
    long INDEX_TTL_SECONDS = 300L;

    /** 读首页聚合缓存，未命中或异常返回 null */
    HomeIndexVO getIndex();

    /** 写入首页聚合缓存 */
    void putIndex(HomeIndexVO vo);

    /** 清除首页聚合缓存。轮播图/配置/金刚区的任何写操作都要调 */
    void evictIndex();
}
