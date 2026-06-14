package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TableAssembler {

    public TableResponse toResponse(SourceTable t) {
        TableResponse r = new TableResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    public TableDetailResponse toDetailResponse(TableWithColumns t) {
        TableDetailResponse r = new TableDetailResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setColumns(t.getColumns().stream().map(this::toColumnResponse)
            .collect(Collectors.toList()));
        return r;
    }

    public ColumnResponse toColumnResponse(TableColumn c) {
        ColumnResponse r = new ColumnResponse();
        r.setId(c.getId());
        r.setColumnName(c.getColumnName());
        r.setDataType(c.getDataType());
        r.setComment(c.getComment());
        r.setSortOrder(c.getSortOrder());
        return r;
    }

    public TableDetailResponse toDetailResponseFromTable(SourceTable t, List<TableColumn> cols) {
        TableDetailResponse r = new TableDetailResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setDescription(t.getDescription());
        r.setScope(t.getScope());
        r.setOwnerId(t.getOwnerId());
        r.setColumns(cols.stream().map(this::toColumnResponse).collect(Collectors.toList()));
        return r;
    }
}