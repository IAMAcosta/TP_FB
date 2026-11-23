package com.tiendaonline.pedidos.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class RestClients {

    private RestClients() {
    }

    static RestClient crear(RestClient.Builder builder, String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}