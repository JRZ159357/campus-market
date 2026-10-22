// 文件路径: src/main/java/com/campus/market/vo/NameValueVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用「名称 - 数值」对。
 * <p>
 * 仪表盘的分布类图表（角色分布、分类分布、状态分布、星级分布…）
 * 全部是这个形状，前端拿去直接喂 ECharts 的 data 数组即可，
 * 不需要为每种分布各写一套转换逻辑。
 * <p>
 * 额外带一个 {@code label} 字段：{@code name} 存的是数据库里的原始码值
 * （如 role=user、status=1），{@code label} 存的是给人看的中文名
 * （普通用户、已上架）。前端画图用 name 保持唯一性，tooltip 用 label 展示可读文案。
 */
@Data
@Schema(description = "名称-数值对")
public class NameValueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "原始码值")
    private String name;

    @Schema(description = "中文展示名")
    private String label;

    @Schema(description = "数量")
    private Long value;

    public static NameValueVO of(String name, String label, Long value) {
        NameValueVO vo = new NameValueVO();
        vo.setName(name);
        vo.setLabel(label);
        vo.setValue(value == null ? 0L : value);
        return vo;
    }
}
