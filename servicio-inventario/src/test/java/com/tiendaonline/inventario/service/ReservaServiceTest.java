package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ReservaCrearRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarResponse;
import com.tiendaonline.inventario.dto.ReservaResponse;
import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.entity.Reserva;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.exception.ReservaConflictoException;
import com.tiendaonline.inventario.exception.StockInsuficienteException;
import com.tiendaonline.inventario.repository.ProductoRepository;
import com.tiendaonline.inventario.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private ReservaService reservaService;

    @Test
    void reservarConStockSuficienteDescuentaStockYGuardaLaReserva() {
        Producto producto = new Producto("SKU-1", "Mouse", 10);
        producto.setId(5);
        Reserva guardada = new Reserva(producto, 21, 3);
        guardada.setId(1);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));
        when(reservaRepository.save(any())).thenReturn(guardada);

        ReservaResponse respuesta = reservaService.reservar(new ReservaCrearRequest(5, 21, 3));

        assertEquals(7, producto.getCantidadDisponible());
        assertEquals(1, respuesta.id());
        assertEquals(5, respuesta.productoId());
        assertEquals(21, respuesta.pedidoId());
        assertEquals(3, respuesta.cantidad());
        assertEquals("ACTIVA", respuesta.estado());
        verify(reservaRepository).save(any());
    }

    @Test
    void reservarTodoElStockDisponibleDejaElStockEnCero() {
        Producto producto = new Producto("SKU-1", "Mouse", 3);
        producto.setId(5);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));
        when(reservaRepository.save(any())).thenReturn(new Reserva(producto, 21, 3));

        reservaService.reservar(new ReservaCrearRequest(5, 21, 3));

        assertEquals(0, producto.getCantidadDisponible());
    }

    @Test
    void reservarSinStockSuficienteLanzaExcepcionYNoCambiaElStock() {
        Producto producto = new Producto("SKU-1", "Mouse", 2);
        producto.setId(5);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));

        StockInsuficienteException exception = assertThrows(StockInsuficienteException.class, () -> {
            reservaService.reservar(new ReservaCrearRequest(5, 22, 3));
        });

        assertEquals("Stock insuficiente para el producto 5: disponible 2, solicitado 3", exception.getMessage());
        assertEquals(2, producto.getCantidadDisponible());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservarProductoInexistenteLanzaExcepcionYNoGuardaNada() {
        when(productoRepository.buscarParaActualizar(99)).thenReturn(Optional.empty());

        assertThrows(ProductoNoEncontradoException.class, () -> {
            reservaService.reservar(new ReservaCrearRequest(99, 21, 1));
        });

        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservarRepetidoConLaMismaCantidadDevuelveLaReservaExistenteSinDescontarDeNuevo() {
        Producto producto = new Producto("SKU-1", "Mouse", 7);
        producto.setId(5);
        Reserva existente = new Reserva(producto, 21, 3);
        existente.setId(1);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));
        when(reservaRepository.findByPedidoIdAndProductoId(21, 5)).thenReturn(Optional.of(existente));

        ReservaResponse respuesta = reservaService.reservar(new ReservaCrearRequest(5, 21, 3));

        assertEquals(1, respuesta.id());
        assertEquals(7, producto.getCantidadDisponible());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservarRepetidoConOtraCantidadLanzaConflicto() {
        Producto producto = new Producto("SKU-1", "Mouse", 7);
        producto.setId(5);
        Reserva existente = new Reserva(producto, 21, 3);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));
        when(reservaRepository.findByPedidoIdAndProductoId(21, 5)).thenReturn(Optional.of(existente));

        ReservaConflictoException exception = assertThrows(ReservaConflictoException.class, () -> {
            reservaService.reservar(new ReservaCrearRequest(5, 21, 4));
        });

        assertEquals("El pedido 21 ya tiene una reserva del producto 5 por 3 unidades", exception.getMessage());
        assertEquals(7, producto.getCantidadDisponible());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void reservarDeNuevoUnaReservaLiberadaLanzaConflicto() {
        Producto producto = new Producto("SKU-1", "Mouse", 10);
        producto.setId(5);
        Reserva liberada = new Reserva(producto, 21, 3);
        liberada.setEstado(EstadoReserva.LIBERADA);

        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(producto));
        when(reservaRepository.findByPedidoIdAndProductoId(21, 5)).thenReturn(Optional.of(liberada));

        ReservaConflictoException exception = assertThrows(ReservaConflictoException.class, () -> {
            reservaService.reservar(new ReservaCrearRequest(5, 21, 3));
        });

        assertEquals("La reserva del pedido 21 para el producto 5 ya fue liberada", exception.getMessage());
        assertEquals(10, producto.getCantidadDisponible());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void liberarPedidoConReservasActivasDevuelveElStockYLasMarcaLiberadas() {
        Producto mouse = new Producto("SKU-1", "Mouse", 7);
        mouse.setId(5);
        Producto teclado = new Producto("SKU-2", "Teclado", 4);
        teclado.setId(6);
        Reserva reservaMouse = new Reserva(mouse, 21, 3);
        Reserva reservaTeclado = new Reserva(teclado, 21, 1);

        when(reservaRepository.buscarActivasParaActualizar(21))
                .thenReturn(List.of(reservaMouse, reservaTeclado));
        when(productoRepository.buscarParaActualizar(5)).thenReturn(Optional.of(mouse));
        when(productoRepository.buscarParaActualizar(6)).thenReturn(Optional.of(teclado));

        ReservaLiberarResponse respuesta = reservaService.liberar(new ReservaLiberarRequest(21));

        assertEquals(21, respuesta.pedidoId());
        assertEquals(2, respuesta.reservasLiberadas());
        assertEquals(10, mouse.getCantidadDisponible());
        assertEquals(5, teclado.getCantidadDisponible());
        assertEquals(EstadoReserva.LIBERADA, reservaMouse.getEstado());
        assertEquals(EstadoReserva.LIBERADA, reservaTeclado.getEstado());
    }

    @Test
    void liberarPedidoSinReservasActivasDevuelveCeroYNoCambiaNada() {
        when(reservaRepository.buscarActivasParaActualizar(21)).thenReturn(List.of());

        ReservaLiberarResponse respuesta = reservaService.liberar(new ReservaLiberarRequest(21));

        assertEquals(21, respuesta.pedidoId());
        assertEquals(0, respuesta.reservasLiberadas());
    }
}
