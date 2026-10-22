// 文件路径: src/main/java/com/campus/market/mapper/CategoryMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
