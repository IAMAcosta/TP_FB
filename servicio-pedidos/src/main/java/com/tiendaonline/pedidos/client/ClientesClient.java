package com.tiendaonline.pedidos.client;

import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ClientesClient {

    private static final String SERVICIO = "servicio-clientes";

    private final RestClient restClient;

    public ClientesClient(RestClient.Builder builder, @Value("${servicios.clientes.url:http://localhost:8081}") String baseUrl, @Value("${servicios.timeout-ms:5000}") int timeoutMs) {
        this.restClient = RestClients.crear(builder, baseUrl, timeoutMs);
    }

    /**
     * Verifica que exista el cliente (GET /clientes/{id}).
     *
     * @return true si responde 2xx, false si responde 404
     * @throws ServicioNoDisponibleException si el servicio no responde, hace timeout o devuelve otro estado
     */
    public boolean existe(Integer clienteId) {
        try {
            return restClient.get()
                    .uri("/clientes/{id}", clienteId)
                    .exchange((request, response) -> {
                        int estado = response.getStatusCode().value();
                        if (response.getStatusCode().is2xxSuccessful()) {
                            return true;
                        }
                        if (estado == 404) {
                            return false;
                        }
                        throw new ServicioNoDisponibleException(SERVICIO, "respondió con estado " + estado);
                    });
        } catch (ResourceAccessException e) {
            throw new ServicioNoDisponibleException(SERVICIO, e);
        }
    }
}