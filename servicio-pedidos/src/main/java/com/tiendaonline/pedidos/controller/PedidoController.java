package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.dto.CrearPedidoRequest;
import com.tiendaonline.pedidos.dto.ErrorResponse;
import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @Operation(summary = "Crea un pedido", description = "Verifica el cliente, calcula el monto total y reserva el stock de cada ítem. " + "Si el cliente no existe o no hay stock suficiente, el pedido no se crea.")
    @ApiResponse(responseCode = "201", description = "Pedido creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El cliente o un producto no existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Stock insuficiente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Clientes o Inventario no disponible", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request) {
        PedidoResponse pedido = pedidoService.crearPedido(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(pedido.id()).toUri();
        return ResponseEntity.created(ubicacion).body(pedido);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un pedido por su id")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "404", description = "El pedido no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PedidoResponse obtener(@PathVariable Integer id) {
        return pedidoService.obtenerPorId(id);
    }

    @GetMapping
    @Operation(summary = "Lista los pedidos de un cliente", description = "Devuelve una lista vacía si el cliente no tiene pedidos.")
    @ApiResponse(responseCode = "200", description = "Pedidos del cliente")
    @ApiResponse(responseCode = "400", description = "Falta id_cliente o no es un número", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<PedidoResponse> listarPorCliente(@RequestParam("id_cliente") Integer idCliente) {
        return pedidoService.listarPorCliente(idCliente);
    }
}