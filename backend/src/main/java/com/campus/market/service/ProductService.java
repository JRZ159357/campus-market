// 文件路径: src/main/java/com/campus/market/service/ProductService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.ProductAuditDTO;
import com.campus.market.dto.ProductQueryDTO;
import com.campus.market.dto.ProductSaveDTO;
import com.campus.market.entity.Product;
import com.campus.market.vo.ProductDetailVO;
import com.campus.market.vo.ProductFeedVO;
import com.campus.market.vo.ProductListVO;

import java.util.List;

/**
 * 商品业务接口。
 * 状态流转：0待审核 → 1已上架 → 2已下架 / 4违规下架。
 */
public interface ProductService extends IService<Product> {

    /**
     * 发布商品，返回商品ID。仅认证学生可调用，且受 publish_limit 限制。
     */
    Long publish(Long sellerId, ProductSaveDTO dto);

    /**
     * 编辑商品。仅卖家本人、且状态为待审核或已下架时可编辑；编辑后重新进入待审核。
     */
    void updateProduct(Long sellerId, Long id, ProductSaveDTO dto);

    /**
     * 卖家下架商品。
     */
    void offline(Long sellerId, Long id);

    /**
     * 卖家删除商品。已上架需先下架，已售出不可删除。
     */
    void deleteProduct(Long sellerId, Long id);

    /**
     * 我的发布，支持状态筛选。
     */
    IPage<ProductListVO> myPage(Long sellerId, ProductQueryDTO query);

    /**
     * 按卖家分页查商品。卖家主页与管理员用户详情共用。
     *
     * @param sellerId 卖家ID
     * @param status   商品状态，传 null 表示全部
     */
    IPage<ProductListVO> pageBySeller(Long sellerId, Integer status, Long pageNum, Long pageSize);

    /**
     * 把某卖家的所有「待审核 / 已上架」商品一次性下架。
     * <p>
     * 账号注销与管理员封禁都要用到：人已经不能交易了，
     * 留在架上的商品还在被别人点，是最直接的资损入口。
     */
    int offlineAllBySeller(Long sellerId);

    /**
     * 商品详情。非在售商品仅卖家本人、管理员、客服可见。
     */
    ProductDetailVO detail(Long id, Long viewerId, String viewerRole);

    /**
     * 商品列表（免登录），仅返回已上架商品。
     */
    IPage<ProductListVO> pageQuery(ProductQueryDTO query);

    /**
     * 关键词搜索：标题与描述模糊匹配，支持分类、价格区间、排序。
     */
    IPage<ProductListVO> search(ProductQueryDTO query);

    /**
     * 热门商品：浏览量 + 收藏量加权排序前 10，Redis 缓存 5 分钟。
     */
    List<ProductListVO> hot();

    /**
     * 猜你喜欢：已上架商品，认证学生优先。
     */
    List<ProductListVO> recommend(int limit);

    /**
     * 按热度取前 N 件：浏览量 + 收藏量×3 降序，仅已上架。
     * <p>
     * 与 {@link #hot()} 的差别只有两处：数量可指定（首页「热门推荐」自动模式要 1-20 件）、
     * 不走 Redis 缓存（首页聚合结果本身已被缓存，再套一层只会让失效更难保证）。
     */
    List<ProductListVO> hotTop(int limit);

    /**
     * 按给定 ID 顺序取已上架商品。手动选品的顺序是运营排的，不能按 id 或热度重排。
     * <p>
     * 不存在的 ID、已下架/已售出的商品会被静默跳过：
     * 商品生命周期不受首页配置控制，让配置去报错只会逼运营反复改配置。
     */
    List<ProductListVO> listOnSaleByIds(List<Long> ids);

    /**
     * 最新发布：已上架商品按发布时间倒序。
     */
    List<ProductListVO> latest(int limit);

    /**
     * 游标分页（瀑布流）：以 id 倒序取下一页，避免深分页。
     *
     * @param cursor 上一页最后一条的 id，首页传 null
     */
    ProductFeedVO feed(Long cursor, Integer size, Long categoryId);

    /**
     * 管理端商品列表，可按全部状态筛选。
     */
    IPage<ProductListVO> auditPage(ProductQueryDTO query);

    /**
     * 审核商品：通过 → 已上架；驳回 → 已下架并记录原因。
     */
    void audit(Long id, Long auditorId, ProductAuditDTO dto);

    /**
     * 管理员强制下架，状态置为违规下架。
     * <p>
     * 只允许对「已上架」的商品调用（见实现里的状态断言）。
     */
    void forceOffline(Long id, Long auditorId, String reason);

    /**
     * 举报核实后的强制下架：状态置为违规下架，但允许作用于「待审核」商品。
     * <p>
     * 与 {@link #forceOffline} 的差别只有一处：商品可能还卡在待审核就被举报了
     * （比如标题违规但审核还没轮到）。若复用 forceOffline 会抛「只有已上架的商品可以强制下架」，
     * 客服就只能干看着。两者都做「置违规下架」这一件事，所以共用同一个私有实现。
     */
    void forceOfflineByReport(Long id, Long operatorId, String reason);
}
