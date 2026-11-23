package com.tiendaonline.pedidos.client;

import com.tiendaonline.pedidos.dto.ErrorRemotoResponse;
import com.tiendaonline.pedidos.dto.LiberarReservaRequest;
import com.tiendaonline.pedidos.dto.LiberarReservaResponse;
import com.tiendaonline.pedidos.dto.ReservaRequest;
import com.tiendaonline.pedidos.dto.ReservaResponse;
import com.tiendaonline.pedidos.exception.ProductoNoEncontradoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.exception.StockInsuficienteException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class InventarioClient {

    private static final String SERVICIO = "servicio-inventario";

    private final RestClient restClient;

    public InventarioClient(@Value("${servicios.inventario.url:http://localhost:8082}") String baseUrl,
                            @Value("${servicios.timeout-ms:5000}") int timeoutMs) {
        this.restClient = RestClients.crear(baseUrl, timeoutMs);
    }

    /**
     * Reserva stock de un ítem del pedido (POST /reservas)
     *
     * @throws StockInsuficienteException    si Inventario responde 409
     * @throws ProductoNoEncontradoException si Inventario responde 404
     * @throws ServicioNoDisponibleException si no responde, hace timeout o devuelve otro error
     */
    public ReservaResponse reservar(Integer pedidoId, Integer productoId, Integer cantidad) {
        ReservaRequest cuerpo = new ReservaRequest(pedidoId, productoId, cantidad);
        try {
            return restClient.post()
                    .uri("/reservas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .exchange((request, response) -> {
                        HttpStatusCode estado = response.getStatusCode();
                        if (estado.is2xxSuccessful()) {
                            return response.bodyTo(ReservaResponse.class);
                        }
                        if (estado.value() == 409) {
                            // 409 = falta stock o conflicto con una reserva existente: se propaga el motivo de Inventario
                            String motivo = null;
                            try {
                                ErrorRemotoResponse error = response.bodyTo(ErrorRemotoResponse.class);
                                motivo = error == null ? null : error.mensaje();
                            } catch (RuntimeException sinCuerpoLegible) {
                                // se usa el mensaje por defecto
                            }
                            throw motivo == null || motivo.isBlank()
                                    ? new StockInsuficienteException(productoId, cantidad)
                                    : new StockInsuficienteException(motivo);
                        }
                        if (estado.value() == 404) {
                            throw new ProductoNoEncontradoException(productoId);
                        }
                        throw new ServicioNoDisponibleException(SERVICIO, "respondió con estado " + estado.value());
                    });
        } catch (ResourceAccessException e) {
            throw new ServicioNoDisponibleException(SERVICIO, e);
        }
    }

    /**
     * Libera todas las reservas activas del pedido (POST /reservas/liberacion)
     * idempotente pq si no había reservas activas, Inventario responde 200 con reservasLiberadas = 0 entonces la compensación puede repetirse sin problema.
     *
     * @return cantidad de reservas que se liberaron
     * @throws ServicioNoDisponibleException si no responde, hace timeout o devuelve cualquier estado no 2xx
     */
    public int liberar(Integer pedidoId) {
        try {
            int liberadas = restClient.post()
                    .uri("/reservas/liberacion")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LiberarReservaRequest(pedidoId))
                    .exchange((request, response) -> {
                        HttpStatusCode estado = response.getStatusCode();
                        if (estado.is2xxSuccessful()) {
                            LiberarReservaResponse cuerpo = response.bodyTo(LiberarReservaResponse.class);
                            return cuerpo == null || cuerpo.reservasLiberadas() == null
                                    ? 0
                                    : cuerpo.reservasLiberadas();
                        }
                        throw new ServicioNoDisponibleException(SERVICIO, "respondió con estado " + estado.value());
                    });
            log.info("Inventario liberó {} reserva(s) del pedido {}", liberadas, pedidoId);
            return liberadas;
        } catch (ResourceAccessException e) {
            throw new ServicioNoDisponibleException(SERVICIO, e);
        }
    }
}