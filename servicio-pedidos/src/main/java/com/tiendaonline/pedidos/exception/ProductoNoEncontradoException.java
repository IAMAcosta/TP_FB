package com.tiendaonline.pedidos.exception;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Integer productoId) {
        super("No existe el producto " + productoId);
    }
}