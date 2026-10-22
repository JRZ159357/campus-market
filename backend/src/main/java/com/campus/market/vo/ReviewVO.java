// 文件路径: src/main/java/com/campus/market/vo/ReviewVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价展示对象。
 * <p>
 * 匿名处理在 Service 层完成，不留给前端：如果后端把真实昵称发下去、只让前端
 * 判断 isAnonymous 后显示"匿名用户"，那么任何人抓一次接口就能看到实名信息——
 * 匿名就形同虚设。所以这里下发的 reviewerNickname / reviewerAvatar
 * 已经是脱敏后的值，前端无论怎么渲染都拿不到真实身份。
 * <p>
 * reviewerId 仍然保留：评价列表需要用它跳「用户主页」查看信誉分，
 * 而匿名只是不显示昵称，不是把评价人变成不可追溯的幽灵——
 * 平台内部仍需要知道这条评价是谁写的（举报处理要用）。
 */
@Data
@Schema(description = "评价信息")
public class ReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "评价ID")
    private Long id;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品标题")
    private String productTitle;

    @Schema(description = "商品封面")
    private String productCover;

    @Schema(description = "评价人ID")
    private Long reviewerId;

    @Schema(description = "评价人昵称（匿名时已脱敏为「匿名用户」）")
    private String reviewerNickname;

    @Schema(description = "评价人头像（匿名时为null）")
    private String reviewerAvatar;

    @Schema(description = "被评价人ID")
    private Long revieweeId;

    @Schema(description = "被评价人昵称")
    private String revieweeNickname;

    @Schema(description = "被评价人信誉分")
    private Integer revieweeScore;

    @Schema(description = "被评价人信誉等级编码")
    private String revieweeLevel;

    @Schema(description = "被评价人信誉等级名称")
    private String revieweeLevelLabel;

    @Schema(description = "被评价人等级颜色")
    private String revieweeLevelColor;

    @Schema(description = "评价方向:1买家评卖家 2卖家评买家")
    private Integer reviewType;

    @Schema(description = "评价方向名称")
    private String reviewTypeLabel;

    @Schema(description = "综合星级1-5")
    private Integer stars;

    @Schema(description = "描述相符1-5")
    private Integer descStars;

    @Schema(description = "发货速度1-5")
    private Integer shipStars;

    @Schema(description = "沟通态度1-5")
    private Integer attitudeStars;

    @Schema(description = "付款速度1-5")
    private Integer payStars;

    @Schema(description = "文字评价")
    private String content;

    @Schema(description = "图片列表")
    private List<String> images;

    @Schema(description = "是否匿名")
    private Boolean isAnonymous;

    @Schema(description = "卖家回复内容")
    private String replyContent;

    @Schema(description = "卖家回复时间")
    private LocalDateTime replyTime;

    @Schema(description = "追评内容")
    private String followContent;

    @Schema(description = "追评图片列表")
    private List<String> followImages;

    @Schema(description = "追评时间")
    private LocalDateTime followTime;

    @Schema(description = "评价时间")
    private LocalDateTime createTime;

    @Schema(description = "当前查看者是否还能追评")
    private Boolean canFollow;

    @Schema(description = "当前查看者是否还能回复（仅被评价的卖家）")
    private Boolean canReply;
}
