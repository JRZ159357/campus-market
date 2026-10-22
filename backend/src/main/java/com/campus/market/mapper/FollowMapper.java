// 文件路径: src/main/java/com/campus/market/mapper/FollowMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Follow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关注 Mapper。关系查询全部用 Wrapper 表达，保持与项目其余模块一致。
 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {
}
