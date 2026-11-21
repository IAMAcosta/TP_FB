package com.tiendaonline.pedidos.client;

import com.tiendaonline.pedidos.dto.ReservaResponse;
import com.tiendaonline.pedidos.exception.ProductoNoEncontradoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.exception.StockInsuficienteException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

class InventarioClientTest {

    private static ClientAndServer mockServer;
    private static int puerto;

    private InventarioClient client;

    private static int puertoLibre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeAll
    static void iniciarMockServer() throws IOException {
        puerto = puertoLibre();
        mockServer = ClientAndServer.startClientAndServer(puerto);
    }

    @AfterAll
    static void detenerMockServer() {
        mockServer.stop();
    }

    @BeforeEach
    void preparar() {
        mockServer.reset();
        client = new InventarioClient("http://localhost:" + puerto, 500);
    }

    // ---------- reservar ----------

    @Test
    void reservarEnviaElCuerpoYDevuelveLaReserva() {
        mockServer.when(request().withMethod("POST").withPath("/reservas")
                        .withBody(json("{\"pedidoId\":10,\"productoId\":3,\"cantidad\":2}")))
                .respond(response().withStatusCode(201)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"id\":1,\"pedidoId\":10,\"productoId\":3,\"cantidad\":2,\"estado\":\"ACTIVA\",\"fechaCreacion\":\"2026-11-21T19:00:00\"}"));

        ReservaResponse reserva = client.reservar(10, 3, 2);

        assertThat(reserva.id()).isEqualTo(1);
        assertThat(reserva.productoId()).isEqualTo(3);
        assertThat(reserva.cantidad()).isEqualTo(2);
        assertThat(reserva.estado()).isEqualTo("ACTIVA");
    }

    @Test
    void reservarLanzaStockInsuficienteSiInventarioResponde409() {
        mockServer.when(request().withMethod("POST").withPath("/reservas"))
                .respond(response().withStatusCode(409));

        assertThatThrownBy(() -> client.reservar(10, 3, 500))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("producto 3");
    }

    @Test
    void reservarLanzaProductoNoEncontradoSiInventarioResponde404() {
        mockServer.when(request().withMethod("POST").withPath("/reservas"))
                .respond(response().withStatusCode(404));

        assertThatThrownBy(() -> client.reservar(10, 77, 1))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void reservarLanzaServicioNoDisponibleSiInventarioResponde500() {
        mockServer.when(request().withMethod("POST").withPath("/reservas"))
                .respond(response().withStatusCode(500));

        assertThatThrownBy(() -> client.reservar(10, 3, 1))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("servicio-inventario");
    }

    @Test
    void reservarLanzaServicioNoDisponibleSiHayTimeout() {
        mockServer.when(request().withMethod("POST").withPath("/reservas"))
                .respond(response().withStatusCode(201).withDelay(TimeUnit.MILLISECONDS, 1500));

        assertThatThrownBy(() -> client.reservar(10, 3, 1))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    // ---------- liberar ----------

    @Test
    void liberarEnviaElPedidoYDevuelveCuantasReservasLiberoInventario() {
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion")
                        .withBody(json("{\"pedidoId\":10}")))
                .respond(response().withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"pedidoId\":10,\"reservasLiberadas\":2}"));

        assertThat(client.liberar(10)).isEqualTo(2);

        mockServer.verify(request().withMethod("POST").withPath("/reservas/liberacion")
                .withBody(json("{\"pedidoId\":10}")));
    }

    @Test
    void liberarEsIdempotenteSiNoHabiaReservasActivas() {
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion"))
                .respond(response().withStatusCode(200)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"pedidoId\":10,\"reservasLiberadas\":0}"));

        assertThatCode(() -> assertThat(client.liberar(10)).isZero()).doesNotThrowAnyException();
    }

    @Test
    void liberarLanzaServicioNoDisponibleSiInventarioResponde500() {
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion"))
                .respond(response().withStatusCode(500));

        assertThatThrownBy(() -> client.liberar(10))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void liberarNoIgnoraUn404PorqueSeriaUnaRutaEquivocada() {
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion"))
                .respond(response().withStatusCode(404));

        assertThatThrownBy(() -> client.liberar(10))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("404");
    }

    @Test
    void liberarLanzaServicioNoDisponibleSiNadieEscucha() throws IOException {
        InventarioClient sinServidor = new InventarioClient("http://localhost:" + puertoLibre(), 500);

        assertThatThrownBy(() -> sinServidor.liberar(10))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }
}