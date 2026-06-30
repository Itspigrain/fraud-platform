package com.example.fraud.schema;

import com.example.fraud.config.CacheInvalidationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock private EventSchemaRepository repository;
    @Mock private SchemaValidationService validationService;
    @Mock private SchemaIndexService indexService;
    @Mock private CacheInvalidationPublisher cacheInvalidationPublisher;

    private SchemaService service;

    @BeforeEach
    void setUp() {
        service = new SchemaService(repository, validationService, indexService, cacheInvalidationPublisher);
    }

    @Test
    void create_savesEntityAndCreatesIndex() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, "Customer ID")
        );
        var request = new SchemaRequest("purchase", "Purchase Event", "desc", fields);

        when(repository.existsByTenantIdAndEventType("tenant-1", "purchase")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            EventSchemaEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        SchemaResponse response = service.create("tenant-1", request);

        assertThat(response.eventType()).isEqualTo("purchase");
        assertThat(response.fields()).hasSize(1);
        verify(indexService).ensureTenantIndex(eq("tenant-1"), eq(fields));
        verify(validationService).validateSchemaDefinition(fields);
    }

    @Test
    void create_rejectsDuplicate() {
        var request = new SchemaRequest("purchase", "Purchase", null, List.of());
        when(repository.existsByTenantIdAndEventType("tenant-1", "purchase")).thenReturn(true);

        assertThatThrownBy(() -> service.create("tenant-1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void findSchema_returnsCachedAfterFirstLookup() {
        var entity = new EventSchemaEntity();
        entity.setTenantId("tenant-1");
        entity.setEventType("purchase");
        entity.setFieldsFromList(List.of());
        when(repository.findByTenantIdAndEventType("tenant-1", "purchase")).thenReturn(Optional.of(entity));

        var first = service.findSchema("tenant-1", "purchase");
        var second = service.findSchema("tenant-1", "purchase");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        verify(repository, times(1)).findByTenantIdAndEventType("tenant-1", "purchase");
    }
}
