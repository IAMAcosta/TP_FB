package com.tiendaonline.inventario.dto;

import com.tiendaonline.inventario.entity.Producto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stock disponible de un producto")
public record ProductoStockResponse(
        Integer id,
        String sku,
        String nombre,
        Integer cantidadDisponible
) {
    public static ProductoStockResponse from(Producto producto) {
        return new ProductoStockResponse(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                producto.getCantidadDisponible());
    }
}
