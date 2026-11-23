package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.dto.CrearPedidoRequest;
import com.tiendaonline.pedidos.dto.ItemPedidoResponse;
import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.exception.ClienteNoEncontradoException;
import com.tiendaonline.pedidos.exception.PedidoInvalidoException;
import com.tiendaonline.pedidos.exception.ProductoNoEncontradoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.exception.StockInsuficienteException;
import com.tiendaonline.pedidos.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidoControllerCrearTest {

    private static final String JSON_VALIDO = """
            {"clienteId": 5, "items": [{"productoId": 101, "cantidad": 2, "precioUnitario": 10.50}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PedidoService pedidoService;

    private PedidoResponse respuesta() {
        return new PedidoResponse(15, 5, EstadoPedido.CREADO, new BigDecimal("21.00"),
                LocalDateTime.of(2026, 11, 20, 10, 0), LocalDateTime.of(2026, 11, 20, 10, 0),
                List.of(new ItemPedidoResponse(101, 2, new BigDecimal("10.50"), new BigDecimal("21.00"))));
    }

    private org.springframework.test.web.servlet.ResultActions postear(String json) throws Exception {
        return mockMvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void crearDevuelve201ConLocationYElPedido() throws Exception {
        when(pedidoService.crearPedido(any(CrearPedidoRequest.class))).thenReturn(respuesta());

        postear(JSON_VALIDO)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/pedidos/15")))
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.montoTotal").value(21.00));
    }

    @Test
    void sinItemsDevuelve400() throws Exception {
        postear("{\"clienteId\": 5, \"items\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("items")));

        verifyNoInteractions(pedidoService);
    }

    @Test
    void sinClienteDevuelve400() throws Exception {
        postear("{\"items\": [{\"productoId\": 101, \"cantidad\": 2, \"precioUnitario\": 10.50}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("clienteId")));
    }

    @Test
    void cantidadCeroDevuelve400() throws Exception {
        postear("{\"clienteId\": 5, \"items\": [{\"productoId\": 101, \"cantidad\": 0, \"precioUnitario\": 10.50}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("cantidad")));
    }

    @Test
    void precioNegativoDevuelve400() throws Exception {
        postear("{\"clienteId\": 5, \"items\": [{\"productoId\": 101, \"cantidad\": 1, \"precioUnitario\": -1}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("precioUnitario")));
    }

    @Test
    void jsonInvalidoDevuelve400() throws Exception {
        postear("{esto no es json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void productosRepetidosDevuelve400() throws Exception {
        when(pedidoService.crearPedido(any())).thenThrow(new PedidoInvalidoException("El producto 101 está repetido"));

        postear(JSON_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("repetido")));
    }

    @Test
    void clienteInexistenteDevuelve404() throws Exception {
        when(pedidoService.crearPedido(any())).thenThrow(new ClienteNoEncontradoException(5));

        postear(JSON_VALIDO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el cliente 5"));
    }

    @Test
    void productoInexistenteDevuelve404() throws Exception {
        when(pedidoService.crearPedido(any())).thenThrow(new ProductoNoEncontradoException(101));

        postear(JSON_VALIDO).andExpect(status().isNotFound());
    }

    @Test
    void stockInsuficienteDevuelve409() throws Exception {
        when(pedidoService.crearPedido(any())).thenThrow(new StockInsuficienteException(101, 2));

        postear(JSON_VALIDO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("Stock insuficiente")));
    }

    @Test
    void servicioNoDisponibleDevuelve503() throws Exception {
        when(pedidoService.crearPedido(any()))
                .thenThrow(new ServicioNoDisponibleException("servicio-inventario", "timeout"));

        postear(JSON_VALIDO)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}