package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.batch.ExpirarPedidosLauncher;
import com.tiendaonline.pedidos.dto.JobExecutionResumenResponse;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final ExpirarPedidosLauncher launcher;

    public BatchController(ExpirarPedidosLauncher launcher) {
        this.launcher = launcher;
    }

    @PostMapping("/expirar-pedidos")
    public ResponseEntity<?> ejecutarExpiracionManual() {
        try {
            JobExecutionResumenResponse resumen = launcher.ejecutar("MANUAL_DEMO");
            return ResponseEntity.ok(resumen);
        } catch (JobExecutionAlreadyRunningException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El job de expiración ya se encuentra en ejecución"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al ejecutar el job de expiración: " + e.getMessage()));
        }
    }
}