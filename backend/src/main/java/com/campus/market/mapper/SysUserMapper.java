// 文件路径: src/main/java/com/campus/market/mapper/SysUserMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.AdminUserQueryDTO;
import com.campus.market.entity.SysUser;
import com.campus.market.vo.AdminUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper。单表操作用 Wrapper，联表查询再补同名 XML。
 */@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 收藏数增减。用 GREATEST 兜底，避免并发下计数被减成负数。
     */
    @Update("UPDATE sys_user SET favorite_count = GREATEST(favorite_count + #{delta}, 0) "
            + "WHERE id = #{userId} AND deleted = 0")
    int addFavoriteCount(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 关注数增减（我关注了多少人）。
     */
    @Update("UPDATE sys_user SET follow_count = GREATEST(follow_count + #{delta}, 0) "
            + "WHERE id = #{userId} AND deleted = 0")
    int addFollowCount(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 粉丝数增减（多少人关注我）。
     */
    @Update("UPDATE sys_user SET fans_count = GREATEST(fans_count + #{delta}, 0) "
            + "WHERE id = #{userId} AND deleted = 0")
    int addFansCount(@Param("userId") Long userId, @Param("delta") int delta);

    /**
     * 读取信誉分并加行锁。
     * <p>
     * 为什么不直接用 {@code credit_score = GREATEST(0, LEAST(150, credit_score + ?))} 一条 SQL 搞定：
     * 因为信誉分变动必须写一条流水，而流水要记录 before / after 两个快照值。
     * 先 SELECT ... FOR UPDATE 锁住这一行，再算新分数、写流水、更新分数，
     * 整段在一个事务里，才能保证流水里的 before 就是这次真正生效的起点值；
     * 否则并发两条评价会各自读到同一个旧值，流水和实际分数对不上。
     */
    @Select("SELECT credit_score FROM sys_user WHERE id = #{userId} FOR UPDATE")
    Integer selectScoreForUpdate(@Param("userId") Long userId);

    /**
     * 写入新的信誉分。调用前必须已通过 {@link #selectScoreForUpdate} 拿到行锁。
     */
    @Update("UPDATE sys_user SET credit_score = #{score} WHERE id = #{userId}")
    int updateCreditScore(@Param("userId") Long userId, @Param("score") int score);

    /**
     * 管理端用户列表（分页）。
     * <p>
     * 三个派生字段用子查询带出来，而不是先查用户表再逐条补：
     * <ul>
     *   <li><b>student_no</b>：学号在 student_auth 表里，一个用户可能有多条申请
     *       （被驳回后重新提交），取最近一条「已通过」的；</li>
     *   <li><b>publish_count</b>：发布数在 product 表，用子查询一次算完，
     *       避免在 Java 侧 N+1；</li>
     *   <li><b>关键词匹配学号</b>：用 EXISTS 子查询而不是 JOIN，
     *       防止一张表多条记录把主表行放大、导致分页总数虚高。</li>
     * </ul>
     */
    @Select("<script>"
            + "SELECT u.id AS userId, u.nickname, u.avatar, u.username, u.phone,"
            + " u.gender, u.intro,"
            + " u.role, u.status, u.student_verified AS studentVerified,"
            + " u.credit_score AS creditScore, u.create_time AS createTime,"
            + " u.last_login_time AS lastLoginTime,"
            + " (SELECT sa.student_no FROM student_auth sa WHERE sa.user_id = u.id"
            + "    AND sa.status = 1 AND sa.deleted = 0 ORDER BY sa.id DESC LIMIT 1) AS studentNo,"
            + " (SELECT COUNT(*) FROM product p WHERE p.seller_id = u.id AND p.deleted = 0) AS publishCount"
            + " FROM sys_user u WHERE u.deleted = 0"
            + "<if test='q.role != null and q.role != \"\"'> AND u.role = #{q.role}</if>"
            + "<if test='q.status != null'> AND u.status = #{q.status}</if>"
            + "<if test='q.studentVerified != null'> AND u.student_verified = #{q.studentVerified}</if>"
            + "<if test='q.minCredit != null'> AND u.credit_score &gt;= #{q.minCredit}</if>"
            + "<if test='q.maxCredit != null'> AND u.credit_score &lt;= #{q.maxCredit}</if>"
            + "<if test='q.startTime != null and q.startTime != \"\"'> AND u.create_time &gt;= #{q.startTime}</if>"
            + "<if test='q.endTime != null and q.endTime != \"\"'> AND u.create_time &lt;= #{q.endTime}</if>"
            + "<if test='q.keyword != null and q.keyword != \"\"'>"
            + "  AND (u.nickname LIKE CONCAT('%', #{q.keyword}, '%')"
            + "    OR u.username LIKE CONCAT('%', #{q.keyword}, '%')"
            + "    OR u.phone LIKE CONCAT('%', #{q.keyword}, '%')"
            + "    OR (LENGTH(#{q.keyword}) REGEXP '^[0-9]+$' AND u.id = #{q.keyword})"
            + "    OR EXISTS (SELECT 1 FROM student_auth sk WHERE sk.user_id = u.id"
            + "        AND sk.status = 1 AND sk.deleted = 0"
            + "        AND sk.student_no LIKE CONCAT('%', #{q.keyword}, '%')))"
            + "</if>"
            + " ORDER BY u.id DESC"
            + "</script>")
    IPage<AdminUserVO> selectAdminPage(IPage<AdminUserVO> page, @Param("q") AdminUserQueryDTO q);
}
