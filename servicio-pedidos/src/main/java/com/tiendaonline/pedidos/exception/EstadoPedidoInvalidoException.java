package com.tiendaonline.pedidos.exception;

import com.tiendaonline.pedidos.entity.EstadoPedido;

public class EstadoPedidoInvalidoException extends RuntimeException {

    public EstadoPedidoInvalidoException(Integer pedidoId, EstadoPedido estado) {
        super("El pedido " + pedidoId + " está " + estado + " y no se puede cancelar");
    }
}
