package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.client.InventarioClient;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpirarPedidoWriterTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private InventarioClient inventarioClient;

    @InjectMocks
    private ExpirarPedidoWriter writer;

    @Test
    void debeLiberarStockYGuardarPedidoCuandoInventarioRespondeOk() {
        Pedido pedido = new Pedido();
        ReflectionTestUtils.setField(pedido, "id", 10);
        pedido.setEstado(EstadoPedido.EXPIRADO);

        writer.write(new Chunk<>(List.of(pedido)));

        InOrder orden = inOrder(inventarioClient, pedidoRepository);
        orden.verify(inventarioClient).liberarReservaPorPedido(10);
        orden.verify(pedidoRepository).save(pedido);
    }

    @Test
    void noDebeGuardarPedidoSiFallaLiberacionDeStock() {
        Pedido pedido = new Pedido();
        ReflectionTestUtils.setField(pedido, "id", 11);
        pedido.setEstado(EstadoPedido.EXPIRADO);

        doThrow(new RestClientException("Inventario no disponible"))
                .when(inventarioClient).liberarReservaPorPedido(11);

        assertThrows(RestClientException.class, () -> writer.write(new Chunk<>(List.of(pedido))));

        verify(pedidoRepository, never()).save(any());
    }
}