package com.tiendaonline.pedidos.service;

import com.tiendaonline.pedidos.client.ClientesClient;
import com.tiendaonline.pedidos.client.InventarioClient;
import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.ItemPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.exception.EstadoPedidoInvalidoException;
import com.tiendaonline.pedidos.exception.PedidoNoEncontradoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceCancelarTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ClientesClient clientesClient;

    @Mock
    private InventarioClient inventarioClient;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido(Integer id, EstadoPedido estado) {
        Pedido pedido = new Pedido(5);
        pedido.setId(id);
        pedido.setEstado(estado);
        pedido.setFechaCreacion(LocalDateTime.of(2026, 11, 20, 10, 0));
        pedido.setFechaActualizacion(LocalDateTime.of(2026, 11, 20, 10, 0));
        pedido.agregarItem(new ItemPedido(101, 2, new BigDecimal("10.50")));
        pedido.recalcularMontoTotal();
        return pedido;
    }

    @Test
    void cancelarPedidoCreadoLiberaElStockYLoPasaACancelado() {
        Pedido pedido = pedido(7, EstadoPedido.CREADO);
        when(pedidoRepository.findByIdConItems(7)).thenReturn(Optional.of(pedido));

        PedidoResponse respuesta = pedidoService.cancelar(7);

        verify(inventarioClient).liberar(7);
        verify(pedidoRepository).saveAndFlush(pedido);
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void cancelarPedidoConfirmadoTambienSePuede() {
        Pedido pedido = pedido(8, EstadoPedido.CONFIRMADO);
        when(pedidoRepository.findByIdConItems(8)).thenReturn(Optional.of(pedido));

        PedidoResponse respuesta = pedidoService.cancelar(8);

        verify(inventarioClient).liberar(8);
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void cancelarPedidoInexistenteLanzaNoEncontradoYNoLlamaAInventario() {
        when(pedidoRepository.findByIdConItems(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.cancelar(99))
                .isInstanceOf(PedidoNoEncontradoException.class);

        verifyNoInteractions(inventarioClient);
    }

    @Test
    void cancelarPedidoYaCanceladoLanzaEstadoInvalidoYNoLiberaDeNuevo() {
        when(pedidoRepository.findByIdConItems(7)).thenReturn(Optional.of(pedido(7, EstadoPedido.CANCELADO)));

        assertThatThrownBy(() -> pedidoService.cancelar(7))
                .isInstanceOf(EstadoPedidoInvalidoException.class)
                .hasMessage("El pedido 7 está CANCELADO y no se puede cancelar");

        verifyNoInteractions(inventarioClient);
    }

    @Test
    void cancelarPedidoExpiradoLanzaEstadoInvalido() {
        when(pedidoRepository.findByIdConItems(9)).thenReturn(Optional.of(pedido(9, EstadoPedido.EXPIRADO)));

        assertThatThrownBy(() -> pedidoService.cancelar(9))
                .isInstanceOf(EstadoPedidoInvalidoException.class)
                .hasMessage("El pedido 9 está EXPIRADO y no se puede cancelar");

        verifyNoInteractions(inventarioClient);
    }

    @Test
    void siInventarioNoRespondeElPedidoQuedaComoEstaba() {
        Pedido pedido = pedido(7, EstadoPedido.CREADO);
        when(pedidoRepository.findByIdConItems(7)).thenReturn(Optional.of(pedido));
        when(inventarioClient.liberar(7))
                .thenThrow(new ServicioNoDisponibleException("servicio-inventario", "respondió con estado 500"));

        assertThatThrownBy(() -> pedidoService.cancelar(7))
                .isInstanceOf(ServicioNoDisponibleException.class);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CREADO);
        verify(pedidoRepository, never()).saveAndFlush(any());
    }
}
