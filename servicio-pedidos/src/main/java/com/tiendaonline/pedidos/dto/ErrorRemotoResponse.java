package com.tiendaonline.pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorRemotoResponse(String mensaje) {
}