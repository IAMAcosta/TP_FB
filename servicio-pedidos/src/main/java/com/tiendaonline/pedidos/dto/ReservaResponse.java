package com.tiendaonline.pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaResponse(Integer id, Integer pedidoId, Integer productoId, Integer cantidad, String estado) {
}