package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.ReservationItemResponse;
import cl.duoc.eventomax.catalog.dto.ReservationRequest;
import cl.duoc.eventomax.catalog.dto.ReservationResponse;
import cl.duoc.eventomax.catalog.exception.InsufficientInventoryException;
import cl.duoc.eventomax.catalog.service.InventoryReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InventoryReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReserveInventory() throws Exception {
        ReservationResponse response = new ReservationResponse(123L, List.of());
        when(reservationService.reserve(any(ReservationRequest.class))).thenReturn(response);

        String jsonRequest = "{ \"productionId\": 123, \"items\": [{ \"equipmentId\": 1, \"quantity\": 5 }] }";

        mockMvc.perform(post("/api/catalog/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn409OnInsufficientInventory() throws Exception {
        when(reservationService.reserve(any(ReservationRequest.class)))
                .thenThrow(new InsufficientInventoryException("Insufficient stock"));

        String jsonRequest = "{ \"productionId\": 123, \"items\": [{ \"equipmentId\": 1, \"quantity\": 500 }] }";

        mockMvc.perform(post("/api/catalog/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400OnInvalidPayload() throws Exception {
        String jsonRequest = "{ \"productionId\": 123 }";

        mockMvc.perform(post("/api/catalog/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetReservationsByProduction() throws Exception {
        ReservationItemResponse item = new ReservationItemResponse(1L, 10L, "Foco LED", 2, "RESERVED");
        when(reservationService.getReservationsByProduction(123L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/catalog/reservations/production/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentId").value(10))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].status").value("RESERVED"));
    }

    @Test
    void shouldCompensateReservations() throws Exception {
        ReservationItemResponse item = new ReservationItemResponse(1L, 10L, "Foco LED", 2, "COMPENSATED");
        when(reservationService.compensate(123L)).thenReturn(List.of(item));

        mockMvc.perform(post("/api/catalog/reservations/production/123/compensate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPENSATED"));
    }
}
