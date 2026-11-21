package com.tiendaonline.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Pedido cuyas reservas se quieren liberar")
public record ReservaLiberarRequest(

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer pedidoId
) {
}
