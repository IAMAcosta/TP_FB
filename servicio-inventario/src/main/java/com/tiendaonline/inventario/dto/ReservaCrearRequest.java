package com.tiendaonline.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Datos para reservar stock de un producto para un pedido")
public record ReservaCrearRequest(

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer productoId,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer pedidoId,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer cantidad
) {
}
