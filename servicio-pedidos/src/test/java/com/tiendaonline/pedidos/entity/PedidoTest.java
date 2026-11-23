package com.tiendaonline.pedidos.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoTest {

    @Test
    void elTotalNoTieneErroresDePuntoFlotante() {
        Pedido pedido = new Pedido(5);
        pedido.agregarItem(new ItemPedido(1, 3, new BigDecimal("0.10")));
        pedido.agregarItem(new ItemPedido(2, 1, new BigDecimal("0.20")));

        assertThat(pedido.calcularMontoTotal()).isEqualTo(new BigDecimal("0.50"));
    }

    @Test
    void unPedidoSinItemsTieneTotalCero() {
        assertThat(new Pedido(5).calcularMontoTotal()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void elTotalSeRedondeaADosDecimalesHalfUp() {
        Pedido pedido = new Pedido(5);
        pedido.agregarItem(new ItemPedido(1, 1, new BigDecimal("1.005")));

        assertThat(pedido.calcularMontoTotal()).isEqualTo(new BigDecimal("1.01"));
    }

    @Test
    void recalcularMontoTotalActualizaElCampo() {
        Pedido pedido = new Pedido(5);
        pedido.agregarItem(new ItemPedido(1, 2, new BigDecimal("10.50")));

        pedido.recalcularMontoTotal();

        assertThat(pedido.getMontoTotal()).isEqualByComparingTo("21.00");
    }

    @Test
    void agregarUnItemEnlazaAmbosLadosDeLaRelacion() {
        Pedido pedido = new Pedido(5);
        ItemPedido item = new ItemPedido(1, 1, new BigDecimal("1.00"));

        pedido.agregarItem(item);

        assertThat(pedido.getItems()).containsExactly(item);
        assertThat(item.getPedido()).isSameAs(pedido);
    }

    @Test
    void unPedidoNuevoEmpiezaEnCreado() {
        assertThat(new Pedido(5).getEstado()).isEqualTo(EstadoPedido.CREADO);
    }
}