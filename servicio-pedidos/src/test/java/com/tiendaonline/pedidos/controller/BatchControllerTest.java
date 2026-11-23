package com.tiendaonline.pedidos.controller;

import com.tiendaonline.pedidos.batch.ExpirarPedidosLauncher;
import com.tiendaonline.pedidos.dto.JobExecutionResumenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BatchController.class)
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpirarPedidosLauncher launcher;

    @Test
    void ejecutarDevuelve200ConElResumen() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        when(launcher.ejecutar("MANUAL_DEMO"))
                .thenReturn(new JobExecutionResumenResponse(10L, "MANUAL_DEMO", "COMPLETED", 3, 2, 0, 1, ahora, ahora));

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETED"))
                .andExpect(jsonPath("$.pedidosLeidos").value(3))
                .andExpect(jsonPath("$.pedidosExpirados").value(2))
                .andExpect(jsonPath("$.pedidosOmitidosPorError").value(1));
    }

    @Test
    void siElJobYaEstaCorriendoDevuelve409ConFormatoDeError() throws Exception {
        when(launcher.ejecutar("MANUAL_DEMO"))
                .thenThrow(new JobExecutionAlreadyRunningException("ya esta corriendo"));

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensaje").value("El job de expiración ya se encuentra en ejecución"));
    }

    @Test
    void siFallaElJobDevuelve500ConFormatoDeError() throws Exception {
        when(launcher.ejecutar("MANUAL_DEMO"))
                .thenThrow(new IllegalStateException("se cayo la base"));

        mockMvc.perform(post("/batch/expirar-pedidos"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.mensaje").value(containsString("se cayo la base")));
    }
}