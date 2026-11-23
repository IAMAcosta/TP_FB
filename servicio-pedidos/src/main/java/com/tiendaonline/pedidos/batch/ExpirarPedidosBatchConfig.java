package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
public class ExpirarPedidosBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidosBatchConfig.class);
    private static final int CHUNK_SIZE = 10;

    @Bean
    @StepScope
    public JpaCursorItemReader<Pedido> pedidosExpirablesReader(EntityManagerFactory entityManagerFactory) {
        LocalDateTime limite = LocalDateTime.now().minusHours(24);

        return new JpaCursorItemReaderBuilder<Pedido>()
                .name("pedidosExpirablesReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT p FROM Pedido p WHERE p.estado = :estado AND p.fechaCreacion < :limite ORDER BY p.id ASC")
                .parameterValues(Map.of(
                        "estado", EstadoPedido.CREADO,
                        "limite", limite
                ))
                .build();
    }

    @Bean
    public Step expirarPedidosStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Pedido> pedidosExpirablesReader,
            ExpirarPedidoProcessor processor,
            ExpirarPedidoWriter writer
    ) {
        return new StepBuilder("expirarPedidosStep", jobRepository)
                .<Pedido, Pedido>chunk(CHUNK_SIZE, transactionManager)
                .reader(pedidosExpirablesReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .skip(RestClientException.class)
                .skipLimit(100)
                .listener(resumenStepListener())
                .build();
    }

    @Bean
    public StepExecutionListener resumenStepListener() {
        return new StepExecutionListener() {
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info("=== RESUMEN CORRIDA EXPIRACIÓN DE PEDIDOS ===");
                log.info("Estado: {}", stepExecution.getStatus());
                log.info("Pedidos leídos: {}", stepExecution.getReadCount());
                log.info("Pedidos expirados y liberados: {}", stepExecution.getWriteCount());
                log.info("Pedidos filtrados: {}", stepExecution.getFilterCount());
                log.info("Pedidos omitidos por error (skip): {}", stepExecution.getSkipCount());
                return stepExecution.getExitStatus();
            }
        };
    }

    @Bean
    public Job expirarPedidosJob(JobRepository jobRepository, Step expirarPedidosStep) {
        return new JobBuilder("expirarPedidosJob", jobRepository)
                .start(expirarPedidosStep)
                .build();
    }
}