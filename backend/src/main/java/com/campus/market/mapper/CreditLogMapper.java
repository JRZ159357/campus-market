// 文件路径: src/main/java/com/campus/market/mapper/CreditLogMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.entity.CreditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 信誉分流水 Mapper。
 * <p>
 * 流水表只追加不修改，因此这里只有查询，没有任何 UPDATE / DELETE 方法——
 * 这是有意为之：少给一个方法，就少一次误用导致账本被改的机会。
 */
@Mapper
public interface CreditLogMapper extends BaseMapper<CreditLog> {

    /**
     * 某用户的信誉分流水（时间倒序），可按类型过滤。
     * type 为 null 时返回全部来源（用户侧的「分数明细」）；
     * 传 3 时只看人工调整（管理端「调整历史」）。
     */
    @Select("<script>"
            + "SELECT * FROM credit_log WHERE user_id = #{userId}"
            + "<if test='type != null'> AND type = #{type}</if>"
            + " ORDER BY create_time DESC, id DESC"
            + "</script>")
    IPage<CreditLog> selectPageByUser(IPage<CreditLog> page,
                                      @Param("userId") Long userId,
                                      @Param("type") Integer type);

    /**
     * 某用户最近的几条流水，用于信誉分详情页的「最近变动」。
     */
    @Select("SELECT * FROM credit_log WHERE user_id = #{userId}"
            + " ORDER BY id DESC LIMIT #{limit}")
    List<CreditLog> selectRecent(@Param("userId") Long userId, @Param("limit") int limit);
}
