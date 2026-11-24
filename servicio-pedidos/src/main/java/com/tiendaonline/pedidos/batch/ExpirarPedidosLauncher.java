package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.dto.JobExecutionResumenResponse;
import com.tiendaonline.pedidos.exception.BatchEnEjecucionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExpirarPedidosLauncher {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidosLauncher.class);

    private final JobLauncher jobLauncher;
    private final Job expirarPedidosJob;
    private final AtomicBoolean enEjecucion = new AtomicBoolean(false); //Si justo a las 02:00 arranca el scheduler y alguien hace POST /batch/expirar-pedidos en paralelo, dos hilos podrían leer los mismos pedidos CREADO y mandar liberaciones duplicadas.

    public ExpirarPedidosLauncher(
            JobLauncher jobLauncher,
            @Qualifier("expirarPedidosJob") Job expirarPedidosJob
    ) {
        this.jobLauncher = jobLauncher;
        this.expirarPedidosJob = expirarPedidosJob;
    }

    public JobExecutionResumenResponse ejecutar(String origen) throws Exception {
        if (!enEjecucion.compareAndSet(false, true)) {
            log.warn("Ejecución rechazada (origen={}): ya hay una corrida de expirarPedidosJob en curso", origen);
            throw new BatchEnEjecucionException("Ya existe una corrida de expirarPedidosJob en ejecución");
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("origen", origen)
                    .toJobParameters();

            log.info("Iniciando expirarPedidosJob (origen={})", origen);
            JobExecution execution = jobLauncher.run(expirarPedidosJob, params);

            long leidos = execution.getStepExecutions().stream().mapToLong(StepExecution::getReadCount).sum();
            long expirados = execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
            long filtrados = execution.getStepExecutions().stream().mapToLong(StepExecution::getFilterCount).sum();
            long omitidos = execution.getStepExecutions().stream().mapToLong(StepExecution::getSkipCount).sum();

            return new JobExecutionResumenResponse(
                    execution.getId(),
                    origen,
                    execution.getStatus().name(),
                    leidos,
                    expirados,
                    filtrados,
                    omitidos,
                    execution.getStartTime(),
                    execution.getEndTime()
            );
        } finally {
            enEjecucion.set(false);
        }
    }
}