package com.tiendaonline.pedidos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

@Schema(description = "Datos para crear un pedido")
public record CrearPedidoRequest(

        @Schema(description = "Id del cliente en servicio-clientes", example = "5")
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor a 0")
        Integer clienteId,

        @Schema(description = "Ítems del pedido (sin productos repetidos)")
        @NotEmpty(message = "debe tener al menos un ítem")
        List<@NotNull(message = "no puede ser nulo") @Valid ItemPedidoRequest> items
) {
}