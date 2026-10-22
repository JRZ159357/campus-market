// 文件路径: src/main/java/com/campus/market/mapper/HomeConfigMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.HomeConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 首页配置 Mapper。只有整表读与按 key 更新两种用法，不需要自定义 SQL。
 */
@Mapper
public interface HomeConfigMapper extends BaseMapper<HomeConfig> {
}
