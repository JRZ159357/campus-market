// 文件路径: src/main/java/com/campus/market/vo/DashboardDistributionVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘分布统计。
 * <p>
 * 六组分布对应六张图，但形状统一为「名称-数值」数组，
 * 前端一个通用组件即可渲染，新增一种分布不需要改组件。
 */
@Data
@Schema(description = "仪表盘分布统计")
public class DashboardDistributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户角色分布：普通用户/认证学生/客服/管理员")
    private List<NameValueVO> userRoleDist;

    @Schema(description = "商品分类分布")
    private List<NameValueVO> productCategoryDist;

    @Schema(description = "商品状态分布")
    private List<NameValueVO> productStatusDist;

    @Schema(description = "订单状态分布")
    private List<NameValueVO> orderStatusDist;

    @Schema(description = "信誉分区间分布：0-59/60-79/80-99/100-119/120-150")
    private List<NameValueVO> creditScoreDist;

    @Schema(description = "评价星级分布：1-5星")
    private List<NameValueVO> reviewStarDist;
}
