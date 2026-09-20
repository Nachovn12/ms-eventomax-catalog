package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ServiceRequest;
import cl.duoc.eventomax.catalog.dto.ServiceResponse;
import cl.duoc.eventomax.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ServiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CatalogService catalogService;

    @InjectMocks
    private ServiceController serviceController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(serviceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnServiceList() throws Exception {
        ServiceResponse response = new ServiceResponse(1L, "Service A", "Desc", BigDecimal.TEN, true);
        when(catalogService.listServices()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/catalog/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Service A"));
    }

    @Test
    void shouldReturnServiceById() throws Exception {
        ServiceResponse response = new ServiceResponse(1L, "Service A", "Desc", BigDecimal.TEN, true);
        when(catalogService.getServiceById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/catalog/services/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Service A"));
    }

    @Test
    void shouldReturn404WhenServiceNotFound() throws Exception {
        when(catalogService.getServiceById(99L)).thenThrow(new ResourceNotFoundException("Service not found"));

        mockMvc.perform(get("/api/catalog/services/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateService() throws Exception {
        ServiceResponse response = new ServiceResponse(1L, "New Service", "Desc", BigDecimal.valueOf(100), true);
        
        when(catalogService.createService(any(ServiceRequest.class))).thenReturn(response);

        String jsonRequest = "{ \"name\": \"New Service\", \"description\": \"Desc\", \"rate\": 100.0, \"active\": true }";

        mockMvc.perform(post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Service"));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        // Missing name and rate
        String invalidJson = "{ \"description\": \"Desc\", \"active\": true }";

        mockMvc.perform(post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEmptyName() throws Exception {
        String invalidJson = "{ \"name\": \"\", \"description\": \"Desc\", \"rate\": 100.0, \"active\": true }";

        mockMvc.perform(post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectNegativeRate() throws Exception {
        String invalidJson = "{ \"name\": \"Valid Name\", \"description\": \"Desc\", \"rate\": -5.0, \"active\": true }";

        mockMvc.perform(post("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateService() throws Exception {
        ServiceResponse response = new ServiceResponse(1L, "Updated Service", "Updated Desc", BigDecimal.valueOf(150), true);

        when(catalogService.updateService(eq(1L), any(ServiceRequest.class))).thenReturn(response);

        String jsonRequest = "{ \"name\": \"Updated Service\", \"description\": \"Updated Desc\", \"rate\": 150.0, \"active\": true }";

        mockMvc.perform(put("/api/catalog/services/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Service"));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentService() throws Exception {
        when(catalogService.updateService(eq(99L), any(ServiceRequest.class)))
                .thenThrow(new ResourceNotFoundException("Service not found"));

        String jsonRequest = "{ \"name\": \"Valid Name\", \"description\": \"Desc\", \"rate\": 100.0, \"active\": true }";

        mockMvc.perform(put("/api/catalog/services/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidUpdatePayload() throws Exception {
        String invalidJson = "{ \"description\": \"Desc\", \"active\": true }";

        mockMvc.perform(put("/api/catalog/services/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
