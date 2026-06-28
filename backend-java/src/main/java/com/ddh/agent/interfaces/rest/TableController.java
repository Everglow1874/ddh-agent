
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.TableAppService;
import com.ddh.agent.interfaces.dto.request.ColumnCreateRequest;
import com.ddh.agent.interfaces.dto.request.ColumnUpdateRequest;
import com.ddh.agent.interfaces.dto.request.TableUpdateRequest;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    @Autowired private TableAppService tableAppService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse importTable(@RequestParam("file") MultipartFile file,
                                     @RequestParam("scope") Integer scope,
                                     @RequestParam(value = "description", required = false) String description,
                                     Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return tableAppService.importCsv(file, scope, description, userId);
    }

    @GetMapping
    public List<TableResponse> listTables(
            @RequestParam(required = false) String scope,
            Authentication auth) {
        return tableAppService.listTables(scope, Long.valueOf(auth.getName()));
    }

    @GetMapping("/page")
    public PageResponse<TableResponse> listTablesPage(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        return tableAppService.listTablesPage(search, scope, Long.valueOf(auth.getName()), page, size);
    }

    @GetMapping("/{tableId}")
    public TableDetailResponse getTable(@PathVariable Long tableId) {
        return tableAppService.getTable(tableId);
    }

    @PutMapping("/{tableId}")
    public TableResponse updateTable(@PathVariable Long tableId,
                                     @RequestBody TableUpdateRequest req) {
        return tableAppService.updateTable(tableId, req);
    }

    @PostMapping("/{tableId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public void addColumn(@PathVariable Long tableId, @RequestBody ColumnCreateRequest req) {
        tableAppService.addColumn(tableId, req);
    }

    @PutMapping("/{tableId}/columns/{columnId}")
    public void updateColumn(@PathVariable Long tableId, @PathVariable Long columnId,
                             @RequestBody ColumnUpdateRequest req) {
        tableAppService.updateColumn(tableId, columnId, req);
    }

    @DeleteMapping("/{tableId}/columns/{columnId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteColumn(@PathVariable Long tableId, @PathVariable Long columnId) {
        tableAppService.deleteColumn(tableId, columnId);
    }

    @DeleteMapping("/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTable(@PathVariable Long tableId) {
        tableAppService.deleteTable(tableId);
    }
}