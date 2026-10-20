package cl.duoc.eventomax.catalog.controller;

import cl.duoc.eventomax.catalog.dto.EquipmentRequest;
import cl.duoc.eventomax.catalog.dto.EquipmentResponse;
import cl.duoc.eventomax.catalog.dto.EquipmentUpdateRequest;
import cl.duoc.eventomax.catalog.service.EquipmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EquipmentService equipmentService;

    @InjectMocks
    private EquipmentController equipmentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(equipmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateEquipment() throws Exception {
        EquipmentResponse response = new EquipmentResponse(1L, "Proj", "Desc", true, 10, 0);
        when(equipmentService.createEquipment(any(EquipmentRequest.class))).thenReturn(response);

        String jsonRequest = "{ \"name\": \"Proj\", \"description\": \"Desc\", \"active\": true, \"initialAvailableQty\": 10 }";

        mockMvc.perform(post("/api/catalog/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Proj"));
    }

    @Test
    void shouldRejectNegativeInitialAvailableQty() throws Exception {
        String jsonRequest = "{ \"name\": \"Proj\", \"description\": \"Desc\", \"active\": true, \"initialAvailableQty\": -5 }";

        mockMvc.perform(post("/api/catalog/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListEquipment() throws Exception {
        EquipmentResponse response = new EquipmentResponse(1L, "Proj", "Desc", true, 10, 0);
        when(equipmentService.listEquipment()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/catalog/equipment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Proj"));
    }

    @Test
    void shouldGetEquipmentById() throws Exception {
        EquipmentResponse response = new EquipmentResponse(1L, "Proj", "Desc", true, 10, 0);
        when(equipmentService.getEquipmentById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/catalog/equipment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Proj"));
    }

    @Test
    void shouldUpdateEquipmentSuccessfully() throws Exception {
        EquipmentResponse response = new EquipmentResponse(1L, "Proj Updated", "New Desc", false, 15, 2);
        when(equipmentService.updateEquipment(eq(1L), any(EquipmentUpdateRequest.class))).thenReturn(response);

        String jsonRequest = "{ \"name\": \"Proj Updated\", \"description\": \"New Desc\", \"active\": false, \"availableQty\": 15 }";

        mockMvc.perform(put("/api/catalog/equipment/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Proj Updated"))
                .andExpect(jsonPath("$.description").value("New Desc"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.availableQty").value(15))
                .andExpect(jsonPath("$.reservedQty").value(2));
    }

    @Test
    void shouldRejectInvalidUpdatePayloadWithBlankName() throws Exception {
        String jsonRequest = "{ \"name\": \"   \", \"description\": \"Desc\", \"active\": true, \"availableQty\": 10 }";

        mockMvc.perform(put("/api/catalog/equipment/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidUpdatePayloadWithNegativeQty() throws Exception {
        String jsonRequest = "{ \"name\": \"Valid\", \"description\": \"Desc\", \"active\": true, \"availableQty\": -1 }";

        mockMvc.perform(put("/api/catalog/equipment/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentEquipment() throws Exception {
        when(equipmentService.updateEquipment(eq(999L), any(EquipmentUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Equipment not found"));

        String jsonRequest = "{ \"name\": \"Valid\", \"description\": \"Desc\", \"active\": true, \"availableQty\": 5 }";

        mockMvc.perform(put("/api/catalog/equipment/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
                .andExpect(status().isNotFound());
    }
}
