package com.tiendaonline.pedidos.service;

import com.tiendaonline.pedidos.client.ClientesClient;
import com.tiendaonline.pedidos.client.InventarioClient;
import com.tiendaonline.pedidos.dto.CrearPedidoRequest;
import com.tiendaonline.pedidos.dto.ItemPedidoRequest;
import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.dto.ReservaResponse;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.exception.ClienteNoEncontradoException;
import com.tiendaonline.pedidos.exception.PedidoInvalidoException;
import com.tiendaonline.pedidos.exception.ServicioNoDisponibleException;
import com.tiendaonline.pedidos.exception.StockInsuficienteException;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceCrearTest {

    private static final int PEDIDO_ID = 15;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ClientesClient clientesClient;

    @Mock
    private InventarioClient inventarioClient;

    @InjectMocks
    private PedidoService pedidoService;

    private final AtomicReference<Pedido> guardado = new AtomicReference<>();

    private CrearPedidoRequest solicitud() {
        return new CrearPedidoRequest(5, List.of(
                new ItemPedidoRequest(101, 2, new BigDecimal("10.5")),
                new ItemPedidoRequest(102, 3, new BigDecimal("0.10"))));
    }

    private ReservaResponse reservaActiva(int productoId, int cantidad) {
        return new ReservaResponse(1, PEDIDO_ID, productoId, cantidad, "ACTIVA");
    }

    /** Simula el guardado: asigna el id como haría la base de datos. */
    private void simularGuardadoConId() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacion -> {
            Pedido pedido = invocacion.getArgument(0);
            pedido.setId(PEDIDO_ID);
            guardado.set(pedido);
            return pedido;
        });
    }

    @BeforeEach
    void limpiar() {
        guardado.set(null);
    }

    @Test
    void creaElPedidoCalculaElTotalYReservaCadaItem() {
        when(clientesClient.existe(5)).thenReturn(true);
        simularGuardadoConId();

        PedidoResponse respuesta = pedidoService.crearPedido(solicitud());

        assertThat(respuesta.id()).isEqualTo(PEDIDO_ID);
        assertThat(respuesta.clienteId()).isEqualTo(5);
        assertThat(respuesta.estado()).isEqualTo(EstadoPedido.CREADO);
        // 2 * 10.50 + 3 * 0.10 = 21.30
        assertThat(respuesta.montoTotal()).isEqualByComparingTo("21.30");
        assertThat(respuesta.items()).hasSize(2);

        InOrder orden = inOrder(clientesClient, pedidoRepository, inventarioClient);
        orden.verify(clientesClient).existe(5);
        orden.verify(pedidoRepository).save(any(Pedido.class));
        orden.verify(inventarioClient).reservar(PEDIDO_ID, 101, 2);
        orden.verify(inventarioClient).reservar(PEDIDO_ID, 102, 3);
        verify(inventarioClient, never()).liberar(any());
        verify(pedidoRepository, never()).deleteById(any());
    }

    @Test
    void siElClienteNoExisteNoCreaNadaNiReserva() {
        when(clientesClient.existe(5)).thenReturn(false);

        assertThatThrownBy(() -> pedidoService.crearPedido(solicitud()))
                .isInstanceOf(ClienteNoEncontradoException.class)
                .hasMessageContaining("5");

        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void siClientesNoEstaDisponibleNoCreaNada() {
        when(clientesClient.existe(5))
                .thenThrow(new ServicioNoDisponibleException("servicio-clientes", "caído"));

        assertThatThrownBy(() -> pedidoService.crearPedido(solicitud()))
                .isInstanceOf(ServicioNoDisponibleException.class);

        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void rechazaProductosRepetidosSinLlamarANadie() {
        CrearPedidoRequest repetidos = new CrearPedidoRequest(5, List.of(
                new ItemPedidoRequest(101, 1, new BigDecimal("10.00")),
                new ItemPedidoRequest(101, 2, new BigDecimal("10.00"))));

        assertThatThrownBy(() -> pedidoService.crearPedido(repetidos))
                .isInstanceOf(PedidoInvalidoException.class)
                .hasMessageContaining("101");

        verifyNoInteractions(clientesClient, inventarioClient, pedidoRepository);
    }

    @Test
    void siFaltaStockEnUnItemLiberaLoReservadoYBorraElPedido() {
        when(clientesClient.existe(5)).thenReturn(true);
        simularGuardadoConId();
        when(inventarioClient.reservar(PEDIDO_ID, 101, 2)).thenReturn(reservaActiva(101, 2));
        when(inventarioClient.reservar(PEDIDO_ID, 102, 3))
                .thenThrow(new StockInsuficienteException(102, 3));

        assertThatThrownBy(() -> pedidoService.crearPedido(solicitud()))
                .isInstanceOf(StockInsuficienteException.class);

        InOrder orden = inOrder(inventarioClient, pedidoRepository);
        orden.verify(inventarioClient).reservar(PEDIDO_ID, 101, 2);
        orden.verify(inventarioClient).reservar(PEDIDO_ID, 102, 3);
        orden.verify(inventarioClient).liberar(PEDIDO_ID);
        orden.verify(pedidoRepository).deleteById(PEDIDO_ID);
    }

    @Test
    void siInventarioNoEstaDisponibleTambienCompensa() {
        when(clientesClient.existe(5)).thenReturn(true);
        simularGuardadoConId();
        when(inventarioClient.reservar(PEDIDO_ID, 101, 2))
                .thenThrow(new ServicioNoDisponibleException("servicio-inventario", "timeout"));

        assertThatThrownBy(() -> pedidoService.crearPedido(solicitud()))
                .isInstanceOf(ServicioNoDisponibleException.class);

        verify(inventarioClient).liberar(PEDIDO_ID);
        verify(pedidoRepository).deleteById(PEDIDO_ID);
        verify(inventarioClient, never()).reservar(PEDIDO_ID, 102, 3);
    }

    @Test
    void siNoSePuedeLiberarElPedidoQuedaCanceladoYSeInformaElErrorOriginal() {
        when(clientesClient.existe(5)).thenReturn(true);
        simularGuardadoConId();
        when(inventarioClient.reservar(PEDIDO_ID, 101, 2)).thenReturn(reservaActiva(101, 2));
        when(inventarioClient.reservar(PEDIDO_ID, 102, 3))
                .thenThrow(new StockInsuficienteException(102, 3));
        doThrow(new ServicioNoDisponibleException("servicio-inventario", "caído"))
                .when(inventarioClient).liberar(PEDIDO_ID);
        when(pedidoRepository.findById(PEDIDO_ID)).thenAnswer(i -> Optional.of(guardado.get()));

        assertThatThrownBy(() -> pedidoService.crearPedido(solicitud()))
                .isInstanceOf(StockInsuficienteException.class);

        verify(pedidoRepository, never()).deleteById(any());
        verify(pedidoRepository, times(2)).save(any(Pedido.class));
        assertThat(guardado.get().getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }
}