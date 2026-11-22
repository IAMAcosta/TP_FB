package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class PedidosVencidosReaderConfig {

    static final int HORAS_PARA_EXPIRAR = 24;
    static final int TAMANIO_PAGINA = 50;

    @Bean
    @StepScope
    public PedidosVencidosReader pedidosVencidosReader(
            PedidoRepository pedidoRepository,
            @Value("#{jobParameters['fechaEjecucion']}") LocalDateTime fechaEjecucion) {
        LocalDateTime limite = fechaEjecucion.minusHours(HORAS_PARA_EXPIRAR);
        return new PedidosVencidosReader(pedidoRepository, limite, TAMANIO_PAGINA);
    }
}
