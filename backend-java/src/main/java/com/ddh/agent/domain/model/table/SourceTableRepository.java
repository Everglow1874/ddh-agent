package com.ddh.agent.domain.model.table;

import java.util.List;
import java.util.Optional;

public interface SourceTableRepository {
    Optional<SourceTable> findById(Long id);
    List<SourceTable> findVisible(Long currentUserId);
    SourceTable save(SourceTable table);
    void deleteById(Long id);

    List<TableColumn> findColumnsByTableId(Long tableId);
    void saveColumns(List<TableColumn> columns);
    void deleteColumnsByTableId(Long tableId);

    List<TableWithColumns> findWithColumnsByProjectId(Long projectId);
    List<TableWithColumns> findWithColumnsByConversationId(Long conversationId);
}
