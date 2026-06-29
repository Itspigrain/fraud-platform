package com.example.fraud.tenant;

import com.example.fraud.schema.EventSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final EventSchemaRepository schemaRepository;

    @GetMapping
    public List<String> list() {
        return schemaRepository.findDistinctTenantIds();
    }
}
