// 文件路径: src/main/java/com/campus/market/vo/CreditVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 用户信誉分详情。
 * <p>
 * 一次返回「分数 + 等级 + 统计 + 最近评价 + 最近变动」，
 * 卖家主页、个人中心、商品详情旁的卖家卡片都调这一个接口就能画完整块，
 * 不需要前端串三四个请求再拼装。
 * <p>
 * nextLevelScore / levels 让前端可以画出等级进度与全部档位说明，
 * 把「差/一般/良好/优秀/极佳」的阈值放在后端，避免前端硬编码数字后
 * 与后端规则改动不同步。
 */
@Data
@Schema(description = "信誉分详情")
public class CreditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "信誉分")
    private Integer score;

    @Schema(description = "等级编码：POOR/NORMAL/GOOD/EXCELLENT/PERFECT")
    private String level;

    @Schema(description = "等级名称：差/一般/良好/优秀/极佳")
    private String levelLabel;

    @Schema(description = "等级颜色")
    private String levelColor;

    @Schema(description = "等级图标")
    private String levelIcon;

    @Schema(description = "当前等级内进度 0-100")
    private Integer progress;

    @Schema(description = "下一等级名称，已封顶为null")
    private String nextLevelLabel;

    @Schema(description = "下一等级的起始分，已封顶为null")
    private Integer nextLevelScore;

    @Schema(description = "距离满分的差值")
    private Integer toMaxScore;

    @Schema(description = "评价统计")
    private ReviewStatVO stat;

    @Schema(description = "最近收到的评价")
    private List<ReviewVO> recentReviews;

    @Schema(description = "最近的分数变动")
    private List<CreditLogVO> recentLogs;

    @Schema(description = "全部等级档位说明")
    private List<LevelItem> levels;

    /**
     * 等级档位说明项，供前端渲染「差 0-59 / 一般 60-79 …」图例。
     */
    @Data
    @Schema(description = "等级档位")
    public static class LevelItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "等级编码")
        private String code;

        @Schema(description = "等级名称")
        private String label;

        @Schema(description = "起始分")
        private Integer min;

        @Schema(description = "结束分")
        private Integer max;

        @Schema(description = "颜色")
        private String color;
    }
}
