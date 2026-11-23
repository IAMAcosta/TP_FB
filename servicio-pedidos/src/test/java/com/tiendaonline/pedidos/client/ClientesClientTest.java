package com.tiendaonline.pedidos.client;

import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class ClientesClientTest {

    private static ClientAndServer mockServer;
    private static int puerto;

    private ClientesClient client;

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
        client = new ClientesClient(RestClient.builder(), "http://localhost:" + puerto, 500);
    }

    @Test
    void existeDevuelveTrueSiClientesRespondeOk() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/5")).respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON).withBody("{\"id\":5,\"nombre\":\"Ana\"}"));
        assertThat(client.existe(5)).isTrue();
    }

    @Test
    void existeDevuelveFalseSiClientesRespondeNotFound() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/99"))
                .respond(response().withStatusCode(404));

        assertThat(client.existe(99)).isFalse();
    }

    @Test
    void existeLanzaServicioNoDisponibleSiClientesRespondeError500() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/5")).respond(response().withStatusCode(500));

        assertThatThrownBy(() -> client.existe(5)).isInstanceOf(ServicioNoDisponibleException.class).hasMessageContaining("servicio-clientes").hasMessageContaining("500");
    }

    @Test
    void existeLanzaServicioNoDisponibleSiHayTimeout() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/5")).respond(response().withStatusCode(200).withDelay(TimeUnit.MILLISECONDS, 1500));

        assertThatThrownBy(() -> client.existe(5)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void existeLanzaServicioNoDisponibleSiNadieEscucha() throws IOException {
        ClientesClient sinServidor = new ClientesClient(RestClient.builder(), "http://localhost:" + puertoLibre(), 500);

        assertThatThrownBy(() -> sinServidor.existe(5))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }
}