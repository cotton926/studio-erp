package com.studio.erp.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.employee.entity.Dept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门 Mapper。只有下拉框和列表用到，全是单表查询。
 */
@Mapper
public interface DeptMapper extends BaseMapper<Dept> {
}
