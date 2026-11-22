package com.tiendaonline.clientes.observabilidad;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Devuelve el traceId de la request en el header de respuesta "X-Trace-Id" (igual al ejemplo de la catedra).
 *
 * Micrometer Tracing ya genera un traceId por request y lo pone en el MDC, asi que aparece
 * en todas las lineas de log de esa request. Este filtro solo lo expone al cliente
 * (Postman u otro servicio) para poder buscarlo despues en Grafana.
 *
 * Diferencia con el ejemplo: recibimos ObjectProvider en vez de Tracer porque desde Spring Boot 3.2
 * los tests con @WebMvcTest no tienen ningun Tracer, y con Tracer directo esos tests no levantan.
 */
@Component
public class TraceIdResponseFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceIdResponseFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null) {
            Span span = tracer.currentSpan();
            if (span != null) {
                response.setHeader(HEADER, span.context().traceId());
            }
        }
        chain.doFilter(request, response);
    }
}
