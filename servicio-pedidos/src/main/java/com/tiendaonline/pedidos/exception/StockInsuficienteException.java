package com.tiendaonline.pedidos.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(Integer productoId, Integer cantidad) {
        super("Stock insuficiente para el producto " + productoId + " (cantidad solicitada: " + cantidad + ")");
    }
}