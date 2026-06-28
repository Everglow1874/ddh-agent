package com.ddh.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.agent.domain.model.relation.TableRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TableRelationMapper extends BaseMapper<TableRelation> {
}
