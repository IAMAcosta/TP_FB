package com.tiendaonline.inventario.repository;

import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.entity.Reserva;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservaRepositoryTest {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TestEntityManager em;

    private Producto producto(String sku, int stock) {
        return productoRepository.saveAndFlush(new Producto(sku, "Producto " + sku, stock));
    }

    @Test
    void guardaUnaReservaActivaConFechaYLaBuscaPorPedido() {
        Producto producto = producto("SKU-R1", 10);

        Reserva guardada = reservaRepository.saveAndFlush(new Reserva(producto, 100, 3));
        em.clear();

        List<Reserva> reservas = reservaRepository.findByPedidoIdAndEstado(100, EstadoReserva.ACTIVA);
        assertThat(reservas).hasSize(1);
        Reserva leida = reservas.get(0);
        assertThat(leida.getId()).isEqualTo(guardada.getId());
        assertThat(leida.getCantidad()).isEqualTo(3);
        assertThat(leida.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
        assertThat(leida.getFechaCreacion()).isNotNull();
        assertThat(leida.getProducto().getSku()).isEqualTo("SKU-R1");
    }

    @Test
    void liberarCambiaElEstadoYLaReservaYaNoApareceComoActiva() {
        Producto producto = producto("SKU-R2", 10);
        producto.descontarStock(4);
        Reserva reserva = reservaRepository.saveAndFlush(new Reserva(producto, 200, 4));

        reserva.liberar();
        reservaRepository.saveAndFlush(reserva);
        em.clear();

        assertThat(reservaRepository.findByPedidoIdAndEstado(200, EstadoReserva.ACTIVA)).isEmpty();
        assertThat(reservaRepository.findByPedidoIdAndEstado(200, EstadoReserva.LIBERADA)).hasSize(1);
        assertThat(productoRepository.findById(producto.getId()).orElseThrow().getCantidadDisponible())
                .isEqualTo(10);
    }

    @Test
    void existsDetectaUnaReservaActivaDelMismoPedidoYProducto() {
        Producto producto = producto("SKU-R3", 10);
        reservaRepository.saveAndFlush(new Reserva(producto, 300, 1));

        assertThat(reservaRepository.existsByPedidoIdAndProductoIdAndEstado(300, producto.getId(), EstadoReserva.ACTIVA)).isTrue();
        assertThat(reservaRepository.existsByPedidoIdAndProductoIdAndEstado(301, producto.getId(), EstadoReserva.ACTIVA)).isFalse();
        assertThat(reservaRepository.existsByPedidoIdAndProductoIdAndEstado(300, producto.getId(), EstadoReserva.LIBERADA)).isFalse();
    }

    @Test
    void findByPedidoIdDevuelveTodasLasReservasDelPedido() {
        Producto a = producto("SKU-R4A", 10);
        Producto b = producto("SKU-R4B", 10);
        reservaRepository.save(new Reserva(a, 400, 1));
        reservaRepository.save(new Reserva(b, 400, 2));
        reservaRepository.save(new Reserva(a, 401, 1));
        em.flush();

        assertThat(reservaRepository.findByPedidoId(400)).hasSize(2);
        assertThat(reservaRepository.findByPedidoId(999)).isEmpty();
    }

    @Test
    void labaseRechazaCantidadCero() {
        Producto producto = producto("SKU-R5", 10);

        assertThatThrownBy(() -> reservaRepository.saveAndFlush(new Reserva(producto, 500, 0))).isInstanceOf(DataIntegrityViolationException.class);
    }
}