package com.tiendaonline.pedidos.exception;

public class ServicioNoDisponibleException extends RuntimeException {

    private final String servicio;

    public ServicioNoDisponibleException(String servicio, String detalle) {
        super("No se pudo completar la llamada a " + servicio + ": " + detalle);
        this.servicio = servicio;
    }

    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super("No se pudo contactar a " + servicio, causa);
        this.servicio = servicio;
    }

    public String getServicio() {
        return servicio;
    }
}