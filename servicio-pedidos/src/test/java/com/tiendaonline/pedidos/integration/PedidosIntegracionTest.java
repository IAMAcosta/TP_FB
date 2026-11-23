package com.tiendaonline.pedidos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PedidosIntegracionTest {

    private static final int CLIENTE = 987654;

    private static final int PUERTO = puertoLibre();
    private static final ClientAndServer mockServer = ClientAndServer.startClientAndServer(PUERTO);

    private static final String PEDIDO_DOS_ITEMS = """
            {"clienteId": %d, "items": [
              {"productoId": 101, "cantidad": 2, "precioUnitario": 10.50},
              {"productoId": 102, "cantidad": 3, "precioUnitario": 0.10}
            ]}
            """.formatted(CLIENTE);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("servicios.clientes.url", () -> "http://localhost:" + PUERTO);
        registry.add("servicios.inventario.url", () -> "http://localhost:" + PUERTO);
        registry.add("servicios.timeout-ms", () -> 2000);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PedidoRepository pedidoRepository;

    @AfterAll
    static void detenerMockServer() {
        mockServer.stop();
    }

    @BeforeEach
    void preparar() {
        mockServer.reset();
        limpiarPedidosDelCliente();
    }

    @AfterEach
    void limpiar() {
        limpiarPedidosDelCliente();
    }

    @Test
    void crearPedidoGuardaElPedidoCalculaElTotalYReservaCadaItem() {
        clienteExiste();
        reservaAceptada(101);
        reservaAceptada(102);

        ResponseEntity<JsonNode> respuesta = crear(PEDIDO_DOS_ITEMS);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(201);
        JsonNode cuerpo = respuesta.getBody();
        int pedidoId = cuerpo.get("id").asInt();
        assertThat(respuesta.getHeaders().getLocation()).isNotNull();
        assertThat(respuesta.getHeaders().getLocation().getPath()).endsWith("/pedidos/" + pedidoId);
        assertThat(cuerpo.get("estado").asText()).isEqualTo("CREADO");
        // 2 * 10.50 + 3 * 0.10 = 21.30
        assertThat(cuerpo.get("montoTotal").decimalValue()).isEqualByComparingTo("21.30");
        assertThat(cuerpo.get("items")).hasSize(2);

        Pedido guardado = pedidoRepository.findByIdConItems(pedidoId).orElseThrow();
        assertThat(guardado.getClienteId()).isEqualTo(CLIENTE);
        assertThat(guardado.getEstado()).isEqualTo(EstadoPedido.CREADO);
        assertThat(guardado.getMontoTotal()).isEqualByComparingTo("21.30");
        assertThat(guardado.getItems()).hasSize(2);

        mockServer.verify(request().withMethod("GET").withPath("/clientes/" + CLIENTE),
                VerificationTimes.exactly(1));
        mockServer.verify(request().withMethod("POST").withPath("/reservas").withBody(json("{\"pedidoId\":" + pedidoId + ",\"productoId\":101,\"cantidad\":2}")),
                VerificationTimes.exactly(1));
        mockServer.verify(request().withMethod("POST").withPath("/reservas").withBody(json("{\"pedidoId\":" + pedidoId + ",\"productoId\":102,\"cantidad\":3}")),
                VerificationTimes.exactly(1));
        mockServer.verify(request().withMethod("POST").withPath("/reservas/liberacion"),
                VerificationTimes.never());
    }

    @Test
    void despuesDeCrearSePuedeConsultarYListarElPedido() {
        clienteExiste();
        reservaAceptada(101);
        reservaAceptada(102);
        int pedidoId = crear(PEDIDO_DOS_ITEMS).getBody().get("id").asInt();

        ResponseEntity<JsonNode> porId = rest.getForEntity("/pedidos/" + pedidoId, JsonNode.class);
        ResponseEntity<JsonNode> porCliente = rest.getForEntity("/pedidos?id_cliente=" + CLIENTE, JsonNode.class);

        assertThat(porId.getStatusCode().value()).isEqualTo(200);
        assertThat(porId.getBody().get("clienteId").asInt()).isEqualTo(CLIENTE);
        assertThat(porId.getBody().get("items")).hasSize(2);
        assertThat(porCliente.getStatusCode().value()).isEqualTo(200);
        assertThat(porCliente.getBody()).hasSize(1);
        assertThat(porCliente.getBody().get(0).get("id").asInt()).isEqualTo(pedidoId);
    }

    @Test
    void consultarUnPedidoInexistenteDevuelve404() {
        ResponseEntity<JsonNode> respuesta = rest.getForEntity("/pedidos/999999999", JsonNode.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().get("status").asInt()).isEqualTo(404);
    }

    @Test
    void siElClienteNoExisteNoSeCreaNadaNiSeReserva() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/" + CLIENTE))
                .respond(response().withStatusCode(404));

        ResponseEntity<JsonNode> respuesta = crear(PEDIDO_DOS_ITEMS);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().get("mensaje").asText()).contains("No existe el cliente");
        assertThat(pedidosDelCliente()).isEmpty();
        mockServer.verify(request().withMethod("POST").withPath("/reservas"), VerificationTimes.never());
    }

    @Test
    void siServicioClientesEstaCaidoRespondeUn503SinCrearNada() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/" + CLIENTE))
                .respond(response().withStatusCode(500));

        ResponseEntity<JsonNode> respuesta = crear(PEDIDO_DOS_ITEMS);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        assertThat(pedidosDelCliente()).isEmpty();
        mockServer.verify(request().withMethod("POST").withPath("/reservas"), VerificationTimes.never());
    }

    @Test
    void siFaltaStockEnElSegundoItemLiberaLoReservadoYNoDejaElPedido() {
        clienteExiste();
        reservaAceptada(101);
        mockServer.when(request().withMethod("POST").withPath("/reservas").withBody(json("{\"productoId\":102}")))
                .respond(jsonResponse(409, "{\"status\":409,\"mensaje\":" + "\"Stock insuficiente para el producto 102: disponible 0, solicitado 3\"}"));
        liberacion(200, "{\"pedidoId\":1,\"reservasLiberadas\":1}");

        ResponseEntity<JsonNode> respuesta = crear(PEDIDO_DOS_ITEMS);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
        assertThat(respuesta.getBody().get("mensaje").asText())
                .isEqualTo("Stock insuficiente para el producto 102: disponible 0, solicitado 3");
        assertThat(pedidosDelCliente()).isEmpty();
        mockServer.verify(request().withMethod("POST").withPath("/reservas/liberacion"),
                VerificationTimes.exactly(1));
    }

    @Test
    void siInventarioNoRespondeYNoSePuedeLiberarElPedidoQuedaCancelado() {
        clienteExiste();
        mockServer.when(request().withMethod("POST").withPath("/reservas"))
                .respond(response().withStatusCode(500));
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion"))
                .respond(response().withStatusCode(500));

        ResponseEntity<JsonNode> respuesta = crear(PEDIDO_DOS_ITEMS);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(503);
        List<Pedido> pedidos = pedidosDelCliente();
        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.get(0).getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void unCuerpoInvalidoDevuelve400SinLlamarANingunServicio() {
        ResponseEntity<JsonNode> respuesta = crear("{\"clienteId\": " + CLIENTE + ", \"items\": []}");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody().get("mensaje").asText()).contains("items");
        assertThat(mockServer.retrieveRecordedRequests(request())).isEmpty();
        assertThat(pedidosDelCliente()).isEmpty();
    }

    @Test
    void unProductoRepetidoDevuelve400SinLlamarANingunServicio() {
        String repetido = """
                {"clienteId": %d, "items": [
                  {"productoId": 101, "cantidad": 1, "precioUnitario": 10.00},
                  {"productoId": 101, "cantidad": 2, "precioUnitario": 10.00}
                ]}
                """.formatted(CLIENTE);

        ResponseEntity<JsonNode> respuesta = crear(repetido);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody().get("mensaje").asText()).contains("101");
        assertThat(mockServer.retrieveRecordedRequests(request())).isEmpty();
    }

    private ResponseEntity<JsonNode> crear(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/pedidos", new HttpEntity<>(json, headers), JsonNode.class);
    }

    private List<Pedido> pedidosDelCliente() {
        return pedidoRepository.findByClienteIdOrderByFechaCreacionDesc(CLIENTE);
    }

    private void limpiarPedidosDelCliente() {
        pedidoRepository.deleteAll(pedidosDelCliente());
    }

    private static void clienteExiste() {
        mockServer.when(request().withMethod("GET").withPath("/clientes/" + CLIENTE)).respond(jsonResponse(200, "{\"id\":" + CLIENTE + ",\"nombre\":\"Ana\"}"));
    }

    private static void reservaAceptada(int productoId) {
        mockServer.when(request().withMethod("POST").withPath("/reservas").withBody(json("{\"productoId\":" + productoId + "}"))).respond(jsonResponse(201, "{\"id\":1,\"estado\":\"ACTIVA\"}"));
    }

    private static void liberacion(int estado, String cuerpo) {
        mockServer.when(request().withMethod("POST").withPath("/reservas/liberacion"))
                .respond(jsonResponse(estado, cuerpo));
    }

    private static HttpResponse jsonResponse(int estado, String cuerpo) {
        return response().withStatusCode(estado)
                .withHeader("Content-Type", "application/json")
                .withBody(cuerpo);
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No hay un puerto libre para el MockServer", e);
        }
    }
}