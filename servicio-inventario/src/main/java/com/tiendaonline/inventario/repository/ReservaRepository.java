package com.tiendaonline.inventario.repository;

import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Reserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Integer> {

    @EntityGraph(attributePaths = "producto")
    List<Reserva> findByPedidoIdAndEstado(Integer pedidoId, EstadoReserva estado);

    List<Reserva> findByPedidoId(Integer pedidoId);

    boolean existsByPedidoIdAndProductoIdAndEstado(Integer pedidoId, Integer productoId, EstadoReserva estado);
}