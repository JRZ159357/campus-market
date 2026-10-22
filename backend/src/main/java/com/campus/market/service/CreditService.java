// 文件路径: src/main/java/com/campus/market/service/CreditService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.CreditAdjustDTO;
import com.campus.market.vo.CreditLogVO;
import com.campus.market.vo.CreditUserVO;
import com.campus.market.vo.CreditVO;
import com.campus.market.vo.ReviewStatVO;

/**
 * 信誉分服务。
 * <p>
 * 「加多少分」这件事只能有一个入口：评价、举报、管理员调整三条链路最终都调
 * {@link #addScore}，由它统一做 0-150 截断、写流水、返回变动结果。
 * 如果每条链路各自 UPDATE sys_user.credit_score，迟早会出现
 * 「评价加了 2 分但流水记了 +2、实际被截断成 +1」这种对不上的账。
 */
public interface CreditService {

    /** 用户信誉分详情（分数、等级、统计、最近评价与变动） */
    CreditVO detail(Long userId);

    /** 商品维度的评价统计（平均分、好评数），商品详情页用 */
    ReviewStatVO statOfProduct(Long productId);

    /**
     * 管理端按关键词搜用户（昵称 / 账号 / 手机号），带出当前信誉分与等级。
     * <p>
     * 信誉分调整页需要先定位到人，而用户管理模块还没做，所以这里提供一个
     * 「只读的用户查询」入口，不涉及任何用户资料修改。
     */
    IPage<CreditUserVO> pageUsers(String keyword, Long pageNum, Long pageSize);

    /** 信誉分变动记录分页；type 为 null 查全部，传 3 只见管理员调整 */
    IPage<CreditLogVO> logs(Long userId, Integer type, Long pageNum, Long pageSize);

    /** 管理员调整信誉分（需要原因，写流水） */
    CreditVO adjust(Long userId, CreditAdjustDTO dto, Long operatorId);

    /**
     * 修改信誉分并写流水。所有分数变动的唯一入口。
     *
     * @param userId     被调整用户
     * @param delta      期望变动值（实际生效值可能因上下限截断而不同）
     * @param type       变动类型，见 CreditLogType
     * @param reason     变动原因
     * @param relatedId  关联业务ID（评价ID / 举报ID）
     * @param operatorId 操作人，系统自动变动传 null
     * @return 实际生效的变动值
     */
    int addScore(Long userId, int delta, int type, String reason, Long relatedId, Long operatorId);

    /**
     * 只留痕、不改分。用于「违规评价被删除」这类需要审计但明确不动分数的操作。
     * <p>
     * 与 addScore(delta=0) 的区别：addScore 在变动为 0 时不会写流水（避免 3 星评价刷屏），
     * 而删除评价必须留下一条记录证明"谁在什么时候删了哪条评价"。
     */
    void audit(Long userId, int type, String reason, Long relatedId, Long operatorId);
}
