package com.example.fraud.schema;

import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schemas")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaService schemaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaResponse create(@RequestBody SchemaRequest request) {
        return schemaService.create(TenantContext.getTenantId(), request);
    }

    @GetMapping
    public List<SchemaResponse> list() {
        return schemaService.listByTenant(TenantContext.getTenantId());
    }

    @GetMapping("/{eventType}")
    public SchemaResponse get(@PathVariable String eventType) {
        return schemaService.getByEventType(TenantContext.getTenantId(), eventType);
    }

    @PutMapping("/{eventType}")
    public SchemaResponse update(@PathVariable String eventType, @RequestBody SchemaRequest request) {
        return schemaService.update(TenantContext.getTenantId(), eventType, request);
    }

    @DeleteMapping("/{eventType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String eventType) {
        schemaService.delete(TenantContext.getTenantId(), eventType);
    }

    @GetMapping("/{eventType}/fields")
    public List<SchemaFieldDefinition> fields(@PathVariable String eventType) {
        return schemaService.getFieldDefinitions(TenantContext.getTenantId(), eventType);
    }
}
