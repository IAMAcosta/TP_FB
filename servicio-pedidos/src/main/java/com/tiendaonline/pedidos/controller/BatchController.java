package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.batch.ExpirarPedidosLauncher;
import com.tiendaonline.pedidos.dto.ErrorResponse;
import com.tiendaonline.pedidos.dto.JobExecutionResumenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/batch")
@Tag(name = "Batch")
public class BatchController {

    private final ExpirarPedidosLauncher launcher;

    public BatchController(ExpirarPedidosLauncher launcher) {
        this.launcher = launcher;
    }

    @PostMapping("/expirar-pedidos")
    @Operation(summary = "Ejecuta a mano el job de expiración de pedidos (para la demo)",
            description = "Pasa a EXPIRADO los pedidos CREADO con más de 24 horas y libera su stock en Inventario. "
                    + "Es el mismo job que corre todas las noches. Devuelve el resumen de la corrida.")
    @ApiResponse(responseCode = "200", description = "Resumen de la corrida",
            content = @Content(schema = @Schema(implementation = JobExecutionResumenResponse.class)))
    @ApiResponse(responseCode = "409", description = "El job ya se está ejecutando", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Error al ejecutar el job", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<?> ejecutarExpiracionManual() {
        try {
            JobExecutionResumenResponse resumen = launcher.ejecutar("MANUAL_DEMO");
            return ResponseEntity.ok(resumen);
        } catch (JobExecutionAlreadyRunningException e) {
            // TP-036: mismo formato de error que el resto de la API (ErrorResponse)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of(HttpStatus.CONFLICT, "El job de expiración ya se encuentra en ejecución"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Error al ejecutar el job de expiración: " + e.getMessage()));
        }
    }
}