package com.tiendaonline.inventario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "pedido_id", nullable = false)
    private Integer pedidoId;

    @Column(nullable = false)
    private Integer cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Reserva(Producto producto, Integer pedidoId, Integer cantidad) {
        this.producto = producto;
        this.pedidoId = pedidoId;
        this.cantidad = cantidad;
    }

    public boolean estaActiva() {
        return estado == EstadoReserva.ACTIVA;
    }

    public boolean liberar() {
        if (!estaActiva()) {
            return false;
        }
        estado = EstadoReserva.LIBERADA;
        producto.reponerStock(cantidad);
        return true;
    }

    @PrePersist
    void alCrear() {
        this.fechaCreacion = LocalDateTime.now();
    }
}