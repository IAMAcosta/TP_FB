package com.tiendaonline.inventario.controller;

import com.tiendaonline.inventario.dto.ProductoStockResponse;
import com.tiendaonline.inventario.dto.ErrorResponse;
import com.tiendaonline.inventario.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
@Tag(name = "Productos")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping("/{id}/stock")
    @Operation(summary = "Consulta el stock disponible de un producto")
    @ApiResponse(responseCode = "200", description = "Stock del producto")
    @ApiResponse(responseCode = "404", description = "El producto no existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "El id no es un número", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ProductoStockResponse consultarStock(@PathVariable Integer id) {
        return productoService.consultarStock(id);
    }
}
