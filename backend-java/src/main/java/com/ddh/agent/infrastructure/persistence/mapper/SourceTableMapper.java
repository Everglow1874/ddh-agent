package com.ddh.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddh.agent.domain.model.table.SourceTable;
import com.ddh.agent.domain.model.table.TableWithColumns;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SourceTableMapper extends BaseMapper<SourceTable> {
    List<TableWithColumns> selectWithColumnsByProjectId(@Param("projectId") Long projectId);
    List<TableWithColumns> selectWithColumnsByConversationId(@Param("conversationId") Long conversationId);
}
