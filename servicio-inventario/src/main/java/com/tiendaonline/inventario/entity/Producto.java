package com.tiendaonline.inventario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible = 0;

    public Producto(String sku, String nombre, Integer cantidadDisponible) {
        this.sku = sku;
        this.nombre = nombre;
        this.cantidadDisponible = cantidadDisponible;
    }

    public boolean tieneStock(int cantidad) {
        return cantidadDisponible >= cantidad;
    }

    /**
     * descuenta stock al reservar
     * @throws IllegalArgumentException si la cantidad no es positiva
     * @throws IllegalStateException    si no hay stock suficiente
     */
    public void descontarStock(int cantidad) {
        validarCantidad(cantidad);
        if (!tieneStock(cantidad)) {
            throw new IllegalStateException("Stock insuficiente: disponible " + cantidadDisponible
                    + ", solicitado " + cantidad);
        }
        cantidadDisponible -= cantidad;
    }

    public void reponerStock(int cantidad) {
        validarCantidad(cantidad);
        cantidadDisponible += cantidad;
    }

    private void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
    }
}