// 文件路径: src/main/java/com/campus/market/mapper/NoticeMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.Notice;
import com.campus.market.vo.NoticeBroadcastVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统通知 Mapper。
 * 个人通知的增删改查全部能用条件构造器表达，这里只放管理端的公告聚合法：
 * 公告是「一次发布 = N 行」，管理端要看的是「一次发布」，所以聚合交给 SQL 的 GROUP BY 完成，
 * 不在内存里把全表捞出来分组。
 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    /**
     * 已发布公告分页。按 (title, content, create_time) 分组，
     * 代表 ID 取组内最小 id，接收人数与已读人数直接由聚合函数给出。
     */
    @Select("SELECT MIN(id) AS id, title, content, create_time,"
            + " COUNT(*) AS receiverCount, SUM(is_read = 1) AS readCount"
            + " FROM notice WHERE type = 3"
            + " GROUP BY title, content, create_time"
            + " ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<NoticeBroadcastVO> selectBroadcastPage(@Param("offset") long offset, @Param("size") long size);

    /**
     * 已发布公告的「发布次数」（分组数），用于分页总数。
     */
    @Select("SELECT COUNT(DISTINCT CONCAT_WS(0x1f, title, content, create_time)) FROM notice WHERE type = 3")
    long countBroadcastGroups();
}
