package com.studio.erp.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.auth.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。查询条件用 Wrapper 即可（单表、无联表需求）。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
