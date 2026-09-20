package cl.duoc.eventomax.catalog.service;

import cl.duoc.eventomax.catalog.controller.ResourceNotFoundException;
import cl.duoc.eventomax.catalog.domain.Service;
import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ServiceRepository repository;

    @InjectMocks
    private CatalogService catalogService;

    @Test
    void updateService_updatesEditableFields() {
        Service service = new Service();
        service.setId(1L);
        service.setName("Original");
        service.setDescription("Original desc");
        service.setRate(BigDecimal.valueOf(100));
        service.setActive(true);

        ServiceRequest request = new ServiceRequest(
                "Updated",
                "Updated desc",
                BigDecimal.valueOf(250),
                false);

        when(repository.findById(1L)).thenReturn(Optional.of(service));
        when(repository.save(service)).thenReturn(service);

        ServiceResponse response = catalogService.updateService(1L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Updated");
        assertThat(response.description()).isEqualTo("Updated desc");
        assertThat(response.rate()).isEqualByComparingTo("250.00");
        assertThat(response.active()).isFalse();
        verify(repository).save(service);
    }

    @Test
    void updateService_missingId_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ServiceRequest request = new ServiceRequest(
                "Updated",
                "Updated desc",
                BigDecimal.valueOf(250),
                true);

        assertThrows(ResourceNotFoundException.class,
                () -> catalogService.updateService(99L, request));
    }
}
