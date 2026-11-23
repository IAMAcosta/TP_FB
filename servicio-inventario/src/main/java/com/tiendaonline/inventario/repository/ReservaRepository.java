package com.tiendaonline.inventario.repository;

import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Reserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Integer> {

    @EntityGraph(attributePaths = "producto")
    List<Reserva> findByPedidoIdAndEstado(Integer pedidoId, EstadoReserva estado);

    List<Reserva> findByPedidoId(Integer pedidoId);

    boolean existsByPedidoIdAndProductoIdAndEstado(Integer pedidoId, Integer productoId, EstadoReserva estado);

    Optional<Reserva> findByPedidoIdAndProductoId(Integer pedidoId, Integer productoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.pedidoId = :pedidoId"
            + " and r.estado = com.tiendaonline.inventario.entity.EstadoReserva.ACTIVA"
            + " order by r.producto.id")
    List<Reserva> buscarActivasParaActualizar(@Param("pedidoId") Integer pedidoId);
}
