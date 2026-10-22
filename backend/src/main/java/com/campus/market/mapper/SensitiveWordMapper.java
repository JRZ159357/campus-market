// 文件路径: src/main/java/com/campus/market/mapper/SensitiveWordMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper。
 * <p>
 * 没有自定义 SQL：本模块的查询都是「按类型 / 状态筛选 + 分页」，
 * MyBatis-Plus 的 LambdaQueryWrapper 已经能表达，
 * 再写一份 XML 只会多一个需要同步维护的地方。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
