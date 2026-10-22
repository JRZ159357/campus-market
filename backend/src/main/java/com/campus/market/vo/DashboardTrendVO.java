// 文件路径: src/main/java/com/campus/market/vo/DashboardTrendVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘趋势数据。
 * <p>
 * 结构是「外层对象 + 每天一个点」而不是直接返回数组：
 * 后续要加「上一周期对比」或「统计区间」时，数组形式无处安放，
 * 外层对象可以直接加字段而不破坏前端解析。
 * <p>
 * 日期为 {@code yyyy-MM-dd} 字符串而非 LocalDate：
 * 图表的 X 轴直接用它做类目标签，转成 Date 反而多一层时区转换的坑。
 */
@Data
@Schema(description = "仪表盘趋势数据")
public class DashboardTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "统计天数(7或30)")
    private Integer days;

    @Schema(description = "按天数组，长度等于 days，缺失日期已补零")
    private List<Point> points;

    /**
     * 某一天的统计。
     */
    @Data
    @Schema(description = "趋势单日数据点")
    public static class Point implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "日期 yyyy-MM-dd")
        private String date;

        @Schema(description = "当日新增用户数")
        private Long newUsers;

        @Schema(description = "当日新增商品数")
        private Long newProducts;

        @Schema(description = "当日订单数")
        private Long orders;

        @Schema(description = "当日成交额(元)，按完成时间归集")
        private BigDecimal gmv;

        @Schema(description = "当日新增举报数")
        private Long reports;
    }
}
