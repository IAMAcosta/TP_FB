package com.tiendaonline.pedidos.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExpirarPedidosJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidosJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Iniciando corrida batch expirarPedidosJob (executionId={})", jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long leidos = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount).sum();
        long expirados = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount).sum();
        long fallidos = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount).sum();

        long duracionMs = (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null)
                ? Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis()
                : 0L;

        log.info("Resumen corrida expirarPedidosJob [executionId={}, estado={}, duracionMs={}, leidos={}, expirados={}, fallidos={}]",
                jobExecution.getId(),
                jobExecution.getStatus(),
                duracionMs,
                leidos,
                expirados,
                fallidos);
    }
}