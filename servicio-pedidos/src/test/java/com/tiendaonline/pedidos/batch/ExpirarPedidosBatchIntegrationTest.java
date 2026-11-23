package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.verify.VerificationTimes;
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
import static org.mockserver.model.RegexBody.regex;
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
                        .withPath("/reservas/liberacion")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reservasLiberadas\": 1}")
        );

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
                request().withMethod("POST").withPath("/reservas/liberacion"),
                VerificationTimes.once()
        );

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(0))
                .andExpect(jsonPath("$.pedidosExpirados").value(0));
    }

    @Test
    void debeReportarLeidosExpiradosYFallidosYRetomarSoloPendientesSinDuplicarLiberacion() throws Exception {
        Integer idOk = insertarPedido("CREADO", 30);
        Integer idFalla = insertarPedido("CREADO", 28);

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
                        .withBody(regex(".*:\\s*" + idOk + "\\s*[},].*"))
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reservasLiberadas\": 1}")
        );

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
                        .withBody(regex(".*:\\s*" + idFalla + "\\s*[},].*"))
        ).respond(
                response().withStatusCode(503)
        );

        // Corrida 1: fallo parcial -> 2 leídos, 1 expirado, 1 fallido
        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(2))
                .andExpect(jsonPath("$.pedidosExpirados").value(1))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(1));

        assertEquals(EstadoPedido.EXPIRADO, pedidoRepository.findById(idOk).orElseThrow().getEstado());
        assertEquals(EstadoPedido.CREADO, pedidoRepository.findById(idFalla).orElseThrow().getEstado());

        // Se recupera servicio-inventario -> reinicio seguro
        mockServer.reset();
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reservasLiberadas\": 1}")
        );

        // Corrida 2: solo retoma idFalla (1 leído, 1 expirado, 0 fallidos) y NO vuelve a liberar idOk
        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(1))
                .andExpect(jsonPath("$.pedidosExpirados").value(1))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(0));

        assertEquals(EstadoPedido.EXPIRADO, pedidoRepository.findById(idFalla).orElseThrow().getEstado());

        mockServer.verify(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
                        .withBody(regex(".*:\\s*" + idOk + "\\s*[},].*")),
                VerificationTimes.exactly(0)
        );
        mockServer.verify(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
                        .withBody(regex(".*:\\s*" + idFalla + "\\s*[},].*")),
                VerificationTimes.once()
        );
    }

    @Test
    void debeReintentarFalloTransitorioYSoportarLiberacionIdempotenteSinDuplicar() throws Exception {
        Integer idVencido = insertarPedido("CREADO", 26);

        // Primer intento falla con 503; el reintento recibe 200 con reservasLiberadas = 0 (liberación idempotente)
        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion"),
                Times.exactly(1)
        ).respond(
                response().withStatusCode(503)
        );

        mockServer.when(
                request()
                        .withMethod("POST")
                        .withPath("/reservas/liberacion")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reservasLiberadas\": 0}")
        );

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(1))
                .andExpect(jsonPath("$.pedidosExpirados").value(1))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(0));

        assertEquals(EstadoPedido.EXPIRADO, pedidoRepository.findById(idVencido).orElseThrow().getEstado());
        mockServer.verify(
                request().withMethod("POST").withPath("/reservas/liberacion"),
                VerificationTimes.exactly(2)
        );

        // Corrida inmediata posterior: sin pendientes y cero llamadas adicionales a inventario
        mockServer.reset();
        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(0))
                .andExpect(jsonPath("$.pedidosExpirados").value(0))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(0));

        mockServer.verify(
                request().withMethod("POST").withPath("/reservas/liberacion"),
                VerificationTimes.exactly(0)
        );
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