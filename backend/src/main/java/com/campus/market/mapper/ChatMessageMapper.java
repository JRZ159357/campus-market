// 文件路径: src/main/java/com/campus/market/mapper/ChatMessageMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消息 Mapper。分页只用游标，不做 offset 分页：
 * 聊天时新消息不断插入，offset 分页会因为数据前移而重复或漏掉消息。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 会话内历史消息（游标分页，倒序）。
     * cursor 为上一页最小 id，首次传空表示取最新的一页。
     */
    @Select("<script>"
            + "SELECT * FROM chat_message WHERE session_id = #{sessionId}"
            + "<if test='cursor != null'> AND id &lt; #{cursor}</if>"
            + " ORDER BY id DESC LIMIT #{size}"
            + "</script>")
    java.util.List<ChatMessage> selectHistory(@Param("sessionId") Long sessionId,
                                              @Param("cursor") Long cursor,
                                              @Param("size") int size);
}
