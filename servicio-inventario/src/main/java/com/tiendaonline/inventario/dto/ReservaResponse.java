package com.tiendaonline.inventario.dto;

import com.tiendaonline.inventario.entity.Reserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Reserva de stock creada")
public record ReservaResponse(
        Integer id,
        Integer productoId,
        Integer pedidoId,
        Integer cantidad,
        String estado,
        LocalDateTime fechaCreacion
) {
    public static ReservaResponse from(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                reserva.getProducto().getId(),
                reserva.getPedidoId(),
                reserva.getCantidad(),
                reserva.getEstado().name(),
                reserva.getFechaCreacion());
    }
}
