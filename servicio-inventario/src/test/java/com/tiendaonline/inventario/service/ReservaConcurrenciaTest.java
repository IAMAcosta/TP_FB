package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ReservaCrearRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarRequest;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.repository.ProductoRepository;
import com.tiendaonline.inventario.repository.ReservaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ReservaConcurrenciaTest {

    private static final int PEDIDO_A = 900001;
    private static final int PEDIDO_B = 900002;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    private Producto producto;

    @AfterEach
    void limpiar() {
        reservaRepository.deleteAll(reservaRepository.findByPedidoId(PEDIDO_A));
        reservaRepository.deleteAll(reservaRepository.findByPedidoId(PEDIDO_B));
        if (producto != null) {
            productoRepository.deleteById(producto.getId());
        }
    }

    @Test
    void dosReservasAlMismoTiempoPorElUltimoProductoSoloUnaPasa() throws InterruptedException {
        producto = productoRepository.save(new Producto("SKU-CONC-" + System.nanoTime(), "Ultimo mouse", 1));
        AtomicInteger exitosas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();

        Thread pedidoA = new Thread(() -> intentarReservar(PEDIDO_A, exitosas, rechazadas));
        Thread pedidoB = new Thread(() -> intentarReservar(PEDIDO_B, exitosas, rechazadas));
        pedidoA.start();
        pedidoB.start();
        pedidoA.join();
        pedidoB.join();

        assertEquals(1, exitosas.get());
        assertEquals(1, rechazadas.get());
        assertEquals(0, productoRepository.findById(producto.getId()).orElseThrow().getCantidadDisponible());
    }

    @Test
    void dosLiberacionesAlMismoTiempoDevuelvenElStockUnaSolaVez() throws InterruptedException {
        producto = productoRepository.save(new Producto("SKU-CONC-" + System.nanoTime(), "Mouse", 5));
        reservaService.reservar(new ReservaCrearRequest(producto.getId(), PEDIDO_A, 3));
        AtomicInteger liberadas = new AtomicInteger();

        Thread primera = new Thread(() -> liberadas.addAndGet(
                reservaService.liberar(new ReservaLiberarRequest(PEDIDO_A)).reservasLiberadas()));
        Thread segunda = new Thread(() -> liberadas.addAndGet(
                reservaService.liberar(new ReservaLiberarRequest(PEDIDO_A)).reservasLiberadas()));
        primera.start();
        segunda.start();
        primera.join();
        segunda.join();

        assertEquals(1, liberadas.get());
        assertEquals(5, productoRepository.findById(producto.getId()).orElseThrow().getCantidadDisponible());
    }

    private void intentarReservar(int pedidoId, AtomicInteger exitosas, AtomicInteger rechazadas) {
        try {
            reservaService.reservar(new ReservaCrearRequest(producto.getId(), pedidoId, 1));
            exitosas.incrementAndGet();
        } catch (RuntimeException e) {
            rechazadas.incrementAndGet();
        }
    }
}
