package com.tiendaonline.pedidos.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.batch.job.enabled=false", "spring.batch.jdbc.initialize-schema=never"})
class BatchMetadataTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void flywayCreoLasSeisTablasDeMetadatos() {
        List<String> tablas = jdbc.queryForList("select table_name from information_schema.tables " + "where table_schema = current_schema() and table_name like 'batch\\_%'",
                String.class);

        assertThat(tablas).containsExactlyInAnyOrder("batch_job_instance", "batch_job_execution", "batch_job_execution_params", "batch_step_execution", "batch_step_execution_context", "batch_job_execution_context");
    }

    @Test
    void flywayCreoLasTresSecuencias() {
        List<String> secuencias = jdbc.queryForList("select sequence_name from information_schema.sequences " + "where sequence_schema = current_schema() and sequence_name like 'batch\\_%'",
                String.class);

        assertThat(secuencias).containsExactlyInAnyOrder("batch_job_seq", "batch_job_execution_seq", "batch_step_execution_seq");
    }

    @Test
    void elJobRepositoryPuedeRegistrarUnaEjecucion() throws Exception {
        String nombre = "jobDePrueba-" + UUID.randomUUID();

        JobExecution ejecucion = jobRepository.createJobExecution(nombre, new JobParametersBuilder().addString("origen", "test").toJobParameters());

        assertThat(ejecucion.getId()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from batch_job_instance where job_name = ?", Integer.class, nombre)).isEqualTo(1);

        jdbc.update("delete from batch_job_execution_params where job_execution_id = ?", ejecucion.getId());
        jdbc.update("delete from batch_job_execution_context where job_execution_id = ?", ejecucion.getId());
        jdbc.update("delete from batch_job_execution where job_execution_id = ?", ejecucion.getId());
        jdbc.update("delete from batch_job_instance where job_name = ?", nombre);
    }
}