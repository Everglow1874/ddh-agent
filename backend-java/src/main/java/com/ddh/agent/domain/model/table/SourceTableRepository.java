package com.ddh.agent.domain.model.table;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.Optional;

public interface SourceTableRepository {
    Optional<SourceTable> findById(Long id);
    List<SourceTable> findVisible(Long currentUserId);
    IPage<SourceTable> findPageable(String search, Integer scope, Long currentUserId, int page, int size);
    SourceTable save(SourceTable table);
    void deleteById(Long id);

    Optional<TableColumn> findColumnById(Long id);
    List<TableColumn> findColumnsByTableId(Long tableId);
    TableColumn saveColumn(TableColumn column);
    void updateColumn(TableColumn column);
    void deleteColumnById(Long id);
    void saveColumns(List<TableColumn> columns);
    void deleteColumnsByTableId(Long tableId);

    List<TableWithColumns> findWithColumnsByProjectId(Long projectId);
    List<TableWithColumns> findWithColumnsByConversationId(Long conversationId);
}
