// 文件路径: src/main/java/com/campus/market/mapper/OrderLogMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.OrderLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单日志 Mapper。日志只增不改，无需额外方法。
 */
@Mapper
public interface OrderLogMapper extends BaseMapper<OrderLog> {
}
