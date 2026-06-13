package com.ddh.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.agent.domain.model.table.TableColumn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TableColumnMapper extends BaseMapper<TableColumn> {
}
