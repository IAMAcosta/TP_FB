package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.once;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExpirarPedidosBatchIntegrationTest {

    private static ClientAndServer mockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PedidoRepository pedidoRepository;

    private final List<Integer> idsCreados = new ArrayList<>();

    @DynamicPropertySource
    static void configurarPropiedades(DynamicPropertyRegistry registry) {
        if (mockServer == null) {
            mockServer = ClientAndServer.startClientAndServer(0);
        }
        registry.add("servicios.inventario.url", () -> "http://localhost:" + mockServer.getLocalPort());
        registry.add("batch.expiracion.scheduler-enabled", () -> "false");
    }

    @BeforeEach
    void resetMockServer() {
        mockServer.reset();
        idsCreados.clear();
    }

    @AfterEach
    void limpiarDatosDePrueba() {
        for (Integer id : idsCreados) {
            jdbcTemplate.update("DELETE FROM pedidos WHERE id = ?", id);
        }
    }

    @AfterAll
    static void detenerMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    void debeExpirarSoloPedidosVencidosLiberarStockYPermitirReejecucion() throws Exception {
        Integer idVencido = insertarPedido("CREADO", 25);
        Integer idReciente = insertarPedido("CREADO", 2);

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/pedido/" + idVencido + "/liberar")
        ).respond(response().withStatusCode(200));

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.origen").value("MANUAL_DEMO"))
                .andExpect(jsonPath("$.pedidosLeidos").value(1))
                .andExpect(jsonPath("$.pedidosExpirados").value(1))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(0));

        assertEquals(EstadoPedido.EXPIRADO, pedidoRepository.findById(idVencido).orElseThrow().getEstado());
        assertEquals(EstadoPedido.CREADO, pedidoRepository.findById(idReciente).orElseThrow().getEstado());
        mockServer.verify(
                request().withMethod("POST").withPath("/reservas/pedido/" + idVencido + "/liberar"),
                once()
        );

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(0))
                .andExpect(jsonPath("$.pedidosExpirados").value(0));
    }

    private Integer insertarPedido(String estado, int horasAtras) {
        Integer id = jdbcTemplate.queryForObject(
                "INSERT INTO pedidos (cliente_id, estado, monto_total, fecha_creacion, fecha_actualizacion) " +
                "VALUES (1, ?, 10000.00, NOW() - (? * INTERVAL '1 hour'), NOW() - (? * INTERVAL '1 hour')) " +
                "RETURNING id",
                Integer.class,
                estado, horasAtras, horasAtras
        );
        idsCreados.add(id);
        return id;
    }
}