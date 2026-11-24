package com.tiendaonline.pedidos.dto;

import java.time.LocalDateTime;

public record JobExecutionResumenResponse(
        Long executionId,
        String origen,
        String estado,
        long pedidosLeidos,
        long pedidosExpirados,
        long pedidosFiltrados,
        long pedidosOmitidosPorError,
        LocalDateTime inicio,
        LocalDateTime fin
) {}
