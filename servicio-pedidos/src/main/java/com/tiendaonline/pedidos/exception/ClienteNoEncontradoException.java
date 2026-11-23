package com.tiendaonline.pedidos.exception;

public class ClienteNoEncontradoException extends RuntimeException {

    public ClienteNoEncontradoException(Integer clienteId) {
        super("No existe el cliente " + clienteId);
    }
}