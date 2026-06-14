package com.ddh.agent.domain.model.table;

import lombok.Data;
import java.util.List;

@Data
public class TableWithColumns {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private List<TableColumn> columns;
}
