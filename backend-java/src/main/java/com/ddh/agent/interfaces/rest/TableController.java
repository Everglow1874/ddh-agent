
package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.TableAppService;
import com.ddh.agent.interfaces.dto.request.ColumnCreateRequest;
import com.ddh.agent.interfaces.dto.request.ColumnUpdateRequest;
import com.ddh.agent.interfaces.dto.request.TableUpdateRequest;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
        return tableAppService.importTable(file, scope, description, userId);
    }

    /** 下载导入模板：format=xlsx|csv（默认 xlsx） */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = tableAppService.buildTemplate(csv ? "csv" : "xlsx");
        String filename = "表字段导入模板." + (csv ? "csv" : "xlsx");
        String encoded;
        try {
            encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = csv ? "table_template.csv" : "table_template.xlsx";
        }
        MediaType type = csv
            ? new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)
            : MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded)
            .contentType(type)
            .body(body);
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