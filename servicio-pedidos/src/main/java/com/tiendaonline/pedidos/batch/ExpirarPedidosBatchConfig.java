package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
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
            ExpirarPedidoWriter writer,
            ExpirarPedidoSkipListener skipListener
    ) {
        return new StepBuilder("expirarPedidosStep", jobRepository)
                .<Pedido, Pedido>chunk(CHUNK_SIZE, transactionManager)
                .reader(pedidosExpirablesReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .processorNonTransactional()
                .retry(ServicioNoDisponibleException.class)
                .retry(RestClientException.class)
                .retryLimit(3)
                .skip(ServicioNoDisponibleException.class)
                .skip(RestClientException.class)
                .skipLimit(100)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job expirarPedidosJob(
            JobRepository jobRepository,
            Step expirarPedidosStep,
            ExpirarPedidosJobListener jobListener
    ) {
        return new JobBuilder("expirarPedidosJob", jobRepository)
                .listener(jobListener)
                .start(expirarPedidosStep)
                .build();
    }
}