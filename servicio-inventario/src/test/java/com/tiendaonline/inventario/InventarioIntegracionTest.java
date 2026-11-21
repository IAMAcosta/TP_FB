package com.tiendaonline.inventario;

import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.repository.ProductoRepository;
import com.tiendaonline.inventario.repository.ReservaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventarioIntegracionTest {

    private static final int PEDIDO_A = 910001;
    private static final int PEDIDO_B = 910002;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    private Producto producto;

    @AfterEach
    void limpiar() {
        reservaRepository.deleteAll(reservaRepository.findByPedidoId(PEDIDO_A));
        reservaRepository.deleteAll(reservaRepository.findByPedidoId(PEDIDO_B));
        if (producto != null) {
            productoRepository.deleteById(producto.getId());
        }
    }

    @Test
    void reservarRepetirYLiberarDejaElStockComoEstaba() throws Exception {
        producto = crearProducto(10);
        String url = "/productos/" + producto.getId() + "/stock";

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadDisponible").value(10));

        mockMvc.perform(reservar(PEDIDO_A, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        mockMvc.perform(reservar(PEDIDO_A, 3))
                .andExpect(status().isCreated());

        assertEquals(1, reservaRepository.findByPedidoId(PEDIDO_A).size());
        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.cantidadDisponible").value(7));

        mockMvc.perform(liberar(PEDIDO_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasLiberadas").value(1));

        mockMvc.perform(liberar(PEDIDO_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasLiberadas").value(0));

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$.cantidadDisponible").value(10));
    }

    @Test
    void reservarSinStockSuficienteDevuelve409YNoCambiaNada() throws Exception {
        producto = crearProducto(2);

        mockMvc.perform(reservar(PEDIDO_A, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/productos/" + producto.getId() + "/stock"))
                .andExpect(jsonPath("$.cantidadDisponible").value(2));
        assertEquals(0, reservaRepository.findByPedidoId(PEDIDO_A).size());
    }

    @Test
    void reservarConOtraCantidadOAlgoLiberadoDevuelve409() throws Exception {
        producto = crearProducto(10);

        mockMvc.perform(reservar(PEDIDO_B, 2))
                .andExpect(status().isCreated());

        mockMvc.perform(reservar(PEDIDO_B, 4))
                .andExpect(status().isConflict());

        mockMvc.perform(liberar(PEDIDO_B))
                .andExpect(status().isOk());

        mockMvc.perform(reservar(PEDIDO_B, 2))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/productos/" + producto.getId() + "/stock"))
                .andExpect(jsonPath("$.cantidadDisponible").value(10));
    }

    @Test
    void productoInexistenteDevuelve404AlConsultarYAlReservar() throws Exception {
        mockMvc.perform(get("/productos/" + Integer.MAX_VALUE + "/stock"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\": " + Integer.MAX_VALUE + ", \"pedidoId\": " + PEDIDO_A + ", \"cantidad\": 1}"))
                .andExpect(status().isNotFound());
    }

    private Producto crearProducto(int stock) {
        return productoRepository.save(new Producto("SKU-INT-" + System.nanoTime(), "Producto de prueba", stock));
    }

    private RequestBuilder reservar(int pedidoId, int cantidad) {
        return post("/reservas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productoId\": " + producto.getId()
                        + ", \"pedidoId\": " + pedidoId
                        + ", \"cantidad\": " + cantidad + "}");
    }

    private RequestBuilder liberar(int pedidoId) {
        return post("/reservas/liberacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pedidoId\": " + pedidoId + "}");
    }
}
