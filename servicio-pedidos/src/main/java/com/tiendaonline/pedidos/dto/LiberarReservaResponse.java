package com.tiendaonline.pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LiberarReservaResponse(Integer pedidoId, Integer reservasLiberadas) {
}