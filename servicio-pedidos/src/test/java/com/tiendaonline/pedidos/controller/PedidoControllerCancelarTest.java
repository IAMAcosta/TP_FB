package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.exception.EstadoPedidoInvalidoException;
import com.tiendaonline.pedidos.exception.PedidoNoEncontradoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidoControllerCancelarTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidoService;

    @Test
    void cancelarPedidoDevuelve200ConEstadoCancelado() throws Exception {
        when(pedidoService.cancelar(7)).thenReturn(new PedidoResponse(7, 5, EstadoPedido.CANCELADO,
                new BigDecimal("21.00"), LocalDateTime.of(2026, 11, 20, 10, 0),
                LocalDateTime.of(2026, 11, 22, 18, 0), List.of()));

        mockMvc.perform(post("/pedidos/7/cancelacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    @Test
    void cancelarPedidoInexistenteDevuelve404() throws Exception {
        when(pedidoService.cancelar(99)).thenThrow(new PedidoNoEncontradoException(99));

        mockMvc.perform(post("/pedidos/99/cancelacion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void cancelarPedidoYaCanceladoDevuelve409() throws Exception {
        when(pedidoService.cancelar(7))
                .thenThrow(new EstadoPedidoInvalidoException(7, EstadoPedido.CANCELADO));

        mockMvc.perform(post("/pedidos/7/cancelacion"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El pedido 7 está CANCELADO y no se puede cancelar"));
    }

    @Test
    void cancelarConInventarioCaidoDevuelve503() throws Exception {
        when(pedidoService.cancelar(7))
                .thenThrow(new ServicioNoDisponibleException("servicio-inventario", "respondió con estado 500"));

        mockMvc.perform(post("/pedidos/7/cancelacion"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void cancelarConIdNoNumericoDevuelve400() throws Exception {
        mockMvc.perform(post("/pedidos/abc/cancelacion"))
                .andExpect(status().isBadRequest());
    }
}
