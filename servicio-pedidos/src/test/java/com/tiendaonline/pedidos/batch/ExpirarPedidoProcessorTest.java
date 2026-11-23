package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExpirarPedidoProcessorTest {

    private final ExpirarPedidoProcessor processor = new ExpirarPedidoProcessor();

    @Test
    void debeCambiarEstadoAExpiradoCuandoEstaCreado() {
        Pedido pedido = new Pedido();
        ReflectionTestUtils.setField(pedido, "id", 1);
        pedido.setEstado(EstadoPedido.CREADO);
        LocalDateTime fechaAnterior = LocalDateTime.now().minusHours(25);
        pedido.setFechaActualizacion(fechaAnterior);

        Pedido resultado = processor.process(pedido);

        assertNotNull(resultado);
        assertEquals(EstadoPedido.EXPIRADO, resultado.getEstado());
        assertTrue(resultado.getFechaActualizacion().isAfter(fechaAnterior));
    }

    @Test
    void debeRetornarNullCuandoElPedidoNoEstaCreado() {
        Pedido pedido = new Pedido();
        ReflectionTestUtils.setField(pedido, "id", 2);
        pedido.setEstado(EstadoPedido.CONFIRMADO);

        Pedido resultado = processor.process(pedido);

        assertNull(resultado);
    }
}