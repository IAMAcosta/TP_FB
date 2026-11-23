package com.tiendaonline.pedidos.dto;

public record ReservaRequest(Integer pedidoId, Integer productoId, Integer cantidad) {
}