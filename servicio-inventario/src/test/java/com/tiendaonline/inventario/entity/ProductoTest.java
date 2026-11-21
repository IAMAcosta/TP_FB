package com.tiendaonline.inventario.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductoTest {

    @Test
    void descontarStockRestaLaCantidad() {
        Producto producto = new Producto("SKU-1", "Mouse", 10);

        producto.descontarStock(4);

        assertThat(producto.getCantidadDisponible()).isEqualTo(6);
    }

    @Test
    void descontarTodoElStockEsValido() {
        Producto producto = new Producto("SKU-1", "Mouse", 3);

        producto.descontarStock(3);

        assertThat(producto.getCantidadDisponible()).isZero();
    }

    @Test
    void descontarMasDeLoDisponibleLanzaExcepcionYNoCambiaElStock() {
        Producto producto = new Producto("SKU-1", "Mouse", 3);

        assertThatThrownBy(() -> producto.descontarStock(4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(producto.getCantidadDisponible()).isEqualTo(3);
    }

    @Test
    void cantidadNoPositivaLanzaExcepcion() {
        Producto producto = new Producto("SKU-1", "Mouse", 3);

        assertThatThrownBy(() -> producto.descontarStock(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> producto.reponerStock(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reponerStockSumaLaCantidad() {
        Producto producto = new Producto("SKU-1", "Mouse", 3);

        producto.reponerStock(2);

        assertThat(producto.getCantidadDisponible()).isEqualTo(5);
    }

    @Test
    void liberarReservaReponeStockUnaSolaVez() {
        Producto producto = new Producto("SKU-1", "Mouse", 10);
        producto.descontarStock(4);
        Reserva reserva = new Reserva(producto, 7, 4);

        assertThat(reserva.liberar()).isTrue();
        assertThat(reserva.liberar()).isFalse();

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.LIBERADA);
        assertThat(producto.getCantidadDisponible()).isEqualTo(10);
    }
}