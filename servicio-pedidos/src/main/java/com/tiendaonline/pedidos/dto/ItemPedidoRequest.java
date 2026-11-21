package com.tiendaonline.pedidos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Ítem del pedido a crear")
public record ItemPedidoRequest(

        @Schema(description = "Id del producto en servicio-inventario", example = "101")
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer productoId,

        @Schema(description = "Unidades a reservar", example = "2")
        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser mayor a 0")
        Integer cantidad,

        @Schema(description = "Precio por unidad", example = "10.50")
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.00", inclusive = false, message = "debe ser mayor a 0")
        @Digits(integer = 8, fraction = 2, message = "admite hasta 8 enteros y 2 decimales")
        BigDecimal precioUnitario
) {
}