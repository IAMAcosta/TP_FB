package com.tiendaonline.clientes.service;

import com.statsig.sdk.Statsig;
import com.statsig.sdk.StatsigUser;
import com.tiendaonline.clientes.client.PedidosClient;
import com.tiendaonline.clientes.dto.ClienteCrearRequest;
import com.tiendaonline.clientes.dto.ClienteResponse;
import com.tiendaonline.clientes.dto.PedidoResponse;
import com.tiendaonline.clientes.entity.Cliente;
import com.tiendaonline.clientes.repository.ClienteRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;


/**
 * TP-032: los logs usan kv(...) (logstash-logback-encoder): en consola se ve "clienteId=5"
 * y en el JSON queda como un campo que se puede filtrar en Loki.
 * Nunca logueamos el nombre ni el email del cliente.
 *
 * Metricas propias (Micrometer), en /actuator/prometheus:
 *   clientes_creados_total
 *   clientes_encontrados_total{operacion="buscar"}
 *   clientes_no_encontrados_total{operacion="buscar|ordenes"}
 */
@Slf4j
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final EntityManager entityManager;
    private final FeatureFlagService featureFlagService;
    private final PedidosClient pedidosClient;
    private final MeterRegistry meterRegistry;
    private final Counter clientesCreados;

    public ClienteService(ClienteRepository clienteRepository,
                          EntityManager entityManager, FeatureFlagService featureFlagService, PedidosClient pedidosClient,
                          MeterRegistry meterRegistry) {
        this.clienteRepository = clienteRepository;
        this.entityManager = entityManager;
        this.featureFlagService = featureFlagService;
        this.pedidosClient = pedidosClient;
        this.meterRegistry = meterRegistry;
        this.clientesCreados = Counter.builder("clientes.creados")
                .description("Cantidad de clientes creados")
                .register(meterRegistry);
    }

    // un contador por operacion: el tag "operacion" permite separarlos en Grafana
    private void contarNoEncontrado(String operacion) {
        meterRegistry.counter("clientes.no.encontrados", "operacion", operacion).increment();
    }

    private void contarEncontrado(String operacion) {
        meterRegistry.counter("clientes.encontrados", "operacion", operacion).increment();
    }

    @Transactional
    public ClienteResponse registrarCliente(ClienteCrearRequest request) {

        // Comprobamos si el email ya esta registrado
        if (clienteRepository.existsByEmail(request.email())) {
            // el email no se loguea (dato sensible)
            log.warn("Intento de registrar un email ya registrado");
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El email ya esta registrado"
            );
        }

        // Creamos el cliente
        Cliente cliente = new Cliente(
                request.nombre(),
                request.email()
        );

        // Guardamos en la base de datos
        Cliente clienteGuardado = clienteRepository.saveAndFlush(cliente);

        // Recuperamos la fecha generada por PostgreSQL
        entityManager.refresh(clienteGuardado);

        clientesCreados.increment();
        log.info("Cliente creado {}", kv("clienteId", clienteGuardado.getId()));

        // Devolvemos los datos del cliente
        return aResponse(clienteGuardado);
    }

    public ClienteResponse buscarPorId(Integer id) {

        log.debug("Buscando cliente {}", kv("clienteId", id));

        //Comprobamos si el cliente existe
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> {
                    contarNoEncontrado("buscar");
                    log.warn("Cliente no encontrado {}", kv("clienteId", id));
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado");
                });

        contarEncontrado("buscar");


        boolean ocultarDatos = featureFlagService.debeEnmascarar(id);

        log.debug("Flag mask-pii evaluado {} {}", kv("clienteId", id), kv("enmascarar", ocultarDatos));

        if (!ocultarDatos) {
        // Devolvemos los datos del cliente
        return aResponse(cliente);
        }

        return new ClienteResponse(cliente.getId(), enmascararNombre(cliente.getNombre()), enmascararEmail(cliente.getEmail()), cliente.getFechaAlta());

    }

    public List<PedidoResponse> listarOrdenesDeCliente(Integer clienteId) {

        // cjequeamos que el cliente exista en la base de datos
        if (!clienteRepository.existsById(clienteId)) {
            contarNoEncontrado("ordenes");
            log.warn("Cliente no encontrado al listar pedidos {}", kv("clienteId", clienteId));
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cliente no encontrado"
            );
        }

        //consultamos los pedidos
        List<PedidoResponse> pedidos = pedidosClient.listarPorCliente(clienteId);
        log.info("Pedidos del cliente obtenidos {} {}", kv("clienteId", clienteId), kv("cantidad", pedidos.size()));
        return pedidos;
    }

    private String enmascararNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "****";
        }

        String[] palabras = nombre.trim().split("\\s+");
        StringBuilder resultado = new StringBuilder();

        for (String palabra : palabras) {
            if (resultado.length() > 0) {
                resultado.append(" ");
            }

            resultado.append(palabra.charAt(0)).append("***");
        }

        return resultado.toString();
    }


    private String enmascararEmail(String email) {
        if (email == null || email.isBlank()) {
            return "****";
        }

        int arroba = email.indexOf('@');
        int ultimoPunto = email.lastIndexOf('.');

        if (arroba <= 0 || ultimoPunto <= arroba + 1) {
            return "****";
        }

        String extension = email.substring(ultimoPunto);

        return "****@****" + extension;
    }



    private ClienteResponse aResponse(Cliente cliente) {
        return new ClienteResponse(
            cliente.getId(),
            cliente.getNombre(),
            cliente.getEmail(),
            cliente.getFechaAlta()
        );
    }


}