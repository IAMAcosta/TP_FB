package com.tiendaonline.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de liberar las reservas de un pedido")
public record ReservaLiberarResponse(
        Integer pedidoId,
        Integer reservasLiberadas
) {
}
