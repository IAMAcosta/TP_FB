package com.tiendaonline.inventario.controller;

import com.tiendaonline.inventario.dto.ReservaLiberarResponse;
import com.tiendaonline.inventario.dto.ReservaResponse;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.exception.ReservaConflictoException;
import com.tiendaonline.inventario.exception.StockInsuficienteException;
import com.tiendaonline.inventario.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
class ReservaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservaService reservaService;

    @Test
    void postReservaValidaDevuelve201() throws Exception {

        when(reservaService.reservar(any()))
                .thenReturn(new ReservaResponse(1, 5, 21, 3, "ACTIVA", LocalDateTime.of(2026, 11, 21, 10, 0)));

        String json = """
                {
                  "productoId": 5,
                  "pedidoId": 21,
                  "cantidad": 3
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.productoId").value(5))
                .andExpect(jsonPath("$.pedidoId").value(21))
                .andExpect(jsonPath("$.cantidad").value(3))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    void postReservaConCantidadCeroDevuelve400() throws Exception {

        String json = """
                {
                  "productoId": 5,
                  "pedidoId": 21,
                  "cantidad": 0
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("cantidad: debe ser mayor a 0"));

        verify(reservaService, never()).reservar(any());
    }

    @Test
    void postReservaSinProductoIdDevuelve400() throws Exception {

        String json = """
                {
                  "pedidoId": 21,
                  "cantidad": 3
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("productoId: es obligatorio"));

        verify(reservaService, never()).reservar(any());
    }

    @Test
    void postReservaConJsonMalEscritoDevuelve400() throws Exception {

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\": 5,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"));

        verify(reservaService, never()).reservar(any());
    }

    @Test
    void postReservaSinStockSuficienteDevuelve409() throws Exception {

        when(reservaService.reservar(any()))
                .thenThrow(new StockInsuficienteException(5, 2, 3));

        String json = """
                {
                  "productoId": 5,
                  "pedidoId": 22,
                  "cantidad": 3
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value("Stock insuficiente para el producto 5: disponible 2, solicitado 3"));
    }

    @Test
    void postReservaDeProductoInexistenteDevuelve404() throws Exception {

        when(reservaService.reservar(any()))
                .thenThrow(new ProductoNoEncontradoException(99));

        String json = """
                {
                  "productoId": 99,
                  "pedidoId": 21,
                  "cantidad": 1
                }
                """;

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un producto con id 99"));
    }

    @Test
    void postReservaRepetidaConOtraCantidadDevuelve409() throws Exception {

        when(reservaService.reservar(any()))
                .thenThrow(new ReservaConflictoException("El pedido 21 ya tiene una reserva del producto 5 por 3 unidades"));

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\": 5, \"pedidoId\": 21, \"cantidad\": 4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El pedido 21 ya tiene una reserva del producto 5 por 3 unidades"));
    }

    @Test
    void postReservaYaLiberadaDevuelve409() throws Exception {

        when(reservaService.reservar(any()))
                .thenThrow(new ReservaConflictoException("La reserva del pedido 21 para el producto 5 ya fue liberada"));

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\": 5, \"pedidoId\": 21, \"cantidad\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value("La reserva del pedido 21 para el producto 5 ya fue liberada"));
    }

    @Test
    void postLiberacionDevuelve200ConLaCantidadDeReservasLiberadas() throws Exception {

        when(reservaService.liberar(any()))
                .thenReturn(new ReservaLiberarResponse(21, 2));

        mockMvc.perform(post("/reservas/liberacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\": 21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidoId").value(21))
                .andExpect(jsonPath("$.reservasLiberadas").value(2));
    }

    @Test
    void postLiberacionSinPedidoIdDevuelve400() throws Exception {

        mockMvc.perform(post("/reservas/liberacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("pedidoId: es obligatorio"));

        verify(reservaService, never()).liberar(any());
    }

    @Test
    void postLiberacionConPedidoIdCeroDevuelve400() throws Exception {

        mockMvc.perform(post("/reservas/liberacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("pedidoId: debe ser mayor a 0"));

        verify(reservaService, never()).liberar(any());
    }
}