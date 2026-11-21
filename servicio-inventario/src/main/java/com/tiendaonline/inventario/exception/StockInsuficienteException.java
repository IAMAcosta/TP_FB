package com.tiendaonline.inventario.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(Integer productoId, Integer disponible, Integer solicitado) {
        super("Stock insuficiente para el producto " + productoId
                + ": disponible " + disponible + ", solicitado " + solicitado);
    }
}
