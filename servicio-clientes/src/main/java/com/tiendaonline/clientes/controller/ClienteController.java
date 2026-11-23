package com.tiendaonline.clientes.controller;

import com.tiendaonline.clientes.dto.ClienteCrearRequest;
import com.tiendaonline.clientes.dto.ClienteResponse;
import com.tiendaonline.clientes.dto.ErrorResponse;
import com.tiendaonline.clientes.dto.PedidoResponse;
import com.tiendaonline.clientes.service.ClienteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Registra un cliente nuevo")
    @ApiResponse(responseCode = "201", description = "Cliente registrado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El email ya está registrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ClienteResponse> registrarCliente(
            @Valid @RequestBody ClienteCrearRequest request) {

        ClienteResponse respuesta = clienteService.registrarCliente(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un cliente por su id", description = "Si el flag mask-pii está activo, el nombre y el email vuelven enmascarados")
    @ApiResponse(responseCode = "200", description = "Datos del cliente")
    @ApiResponse(responseCode = "400", description = "El id no es un número", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El cliente no existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ClienteResponse> buscarPorId(
        @PathVariable Integer id) {
            return ResponseEntity.ok(
                clienteService.buscarPorId(id));
    }

    @GetMapping("/{id}/ordenes")
    @Operation(summary = "Lista los pedidos de un cliente", description = "Consulta los pedidos a servicio-pedidos")
    @ApiResponse(responseCode = "200", description = "Pedidos del cliente (puede ser una lista vacía)")
    @ApiResponse(responseCode = "400", description = "El id no es un número", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El cliente no existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "Servicio de pedidos respondió con error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Servicio de pedidos no disponible", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "Servicio de pedidos tardó demasiado en responder", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<PedidoResponse>> listarOrdenes(
            @PathVariable Integer id) {

        List<PedidoResponse> ordenes = clienteService.listarOrdenesDeCliente(id);

        return ResponseEntity.ok(ordenes);
    }

}