// 文件路径: src/main/java/com/campus/market/vo/AddressVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 地址展示对象。既用于地址列表，也作为订单里的地址快照结构。
 */
@Data
@Schema(description = "地址")
public class AddressVO {

    @Schema(description = "地址ID")
    private Long id;

    @Schema(description = "收货人")
    private String receiverName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "是否默认地址")
    private Boolean isDefault;
}
