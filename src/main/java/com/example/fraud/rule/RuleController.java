package com.example.fraud.rule;

import com.example.fraud.event.EventDocument;
import com.example.fraud.search.SearchResponse;
import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;
    private final RuleResultsSearchService ruleResultsSearchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@RequestBody RuleRequest request) {
        return ruleService.create(TenantContext.getTenantId(), request);
    }

    @GetMapping
    public List<RuleResponse> list() {
        return ruleService.listByTenant(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    public RuleResponse getById(@PathVariable Long id) {
        return ruleService.getById(TenantContext.getTenantId(), id);
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @RequestBody RuleRequest request) {
        return ruleService.update(TenantContext.getTenantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @RequestParam(defaultValue = "false") boolean deleteIndex) {
        ruleService.delete(TenantContext.getTenantId(), id, deleteIndex);
    }

    @GetMapping("/{id}/results")
    public SearchResponse<EventDocument> results(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ruleResultsSearchService.search(
            TenantContext.getTenantId(), id, page, size, sort, direction);
    }
}
