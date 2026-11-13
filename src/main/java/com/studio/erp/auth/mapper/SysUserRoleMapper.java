package com.studio.erp.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账号-角色关联表。
 *
 * <p>【为什么单独建一个 Mapper，而不是塞进 SysUserMapper 用 JdbcTemplate】
 * 这张表没有实体类（复合主键，用 MyBatis-Plus 反而别扭）。
 * 用注解 SQL 明确写清楚"整体覆盖"这个语义，比在 Service 里拼
 * {@code jdbcTemplate.update("DELETE ...")} 更容易被后来人看懂。
 */
@Mapper
public interface SysUserRoleMapper {

    /**
     * 【为什么先删后插，而不是 diff 出增删】
     * 一个账号最多五个角色，删掉重插的成本可以忽略；
     * 而 diff 要先把旧集合查出来再比对，中间任何一步出错（比如查出空集合）
     * 都会留下"多了一个角色"或"少了角色"的脏数据。
     * 整体覆盖在事务里是原子的：要么全换，要么一条没动。
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 批量插入角色。
     *
     * <p>用 foreach 拼成一条 INSERT，而不是循环单条插入：
     * 循环插入每次都要一次网络往返，五个角色就是五次；
     * 更要紧的是循环里如果某一条失败，前面已经插进去的会留下半套角色，
     * 而单条 INSERT 失败就是全部失败，配合事务不会产生中间状态。
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role (user_id, role_id) VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);
}
