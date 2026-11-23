package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ReservaCrearRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarResponse;
import com.tiendaonline.inventario.dto.ReservaResponse;
import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.entity.Reserva;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.exception.ReservaConflictoException;
import com.tiendaonline.inventario.exception.StockInsuficienteException;
import com.tiendaonline.inventario.repository.ProductoRepository;
import com.tiendaonline.inventario.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ProductoRepository productoRepository;
    private final ReservaRepository reservaRepository;

    @Transactional
    public ReservaResponse reservar(ReservaCrearRequest request) {
        Producto producto = productoRepository.buscarParaActualizar(request.productoId())
                .orElseThrow(() -> new ProductoNoEncontradoException(request.productoId()));

        Optional<Reserva> existente = reservaRepository.findByPedidoIdAndProductoId(
                request.pedidoId(), request.productoId());
        if (existente.isPresent()) {
            return reservaRepetida(existente.get(), request);
        }

        if (!producto.tieneStock(request.cantidad())) {
            log.warn("Stock insuficiente para pedidoId={}, productoId={}: disponible={}, solicitado={}",
                    request.pedidoId(), producto.getId(), producto.getCantidadDisponible(), request.cantidad());
            throw new StockInsuficienteException(
                    producto.getId(), producto.getCantidadDisponible(), request.cantidad());
        }

        producto.descontarStock(request.cantidad());

        Reserva reserva = reservaRepository.save(
                new Reserva(producto, request.pedidoId(), request.cantidad()));

        log.info("Reserva creada: reservaId={}, pedidoId={}, productoId={}, cantidad={}, stockDisponible={}",
                reserva.getId(), request.pedidoId(), producto.getId(), request.cantidad(), producto.getCantidadDisponible());

        return ReservaResponse.from(reserva);
    }

    @Transactional
    public ReservaLiberarResponse liberar(ReservaLiberarRequest request) {
        List<Reserva> activas = reservaRepository.buscarActivasParaActualizar(request.pedidoId());

        int liberadas = 0;
        for (Reserva reserva : activas) {
            Integer productoId = reserva.getProducto().getId();
            Producto producto = productoRepository.buscarParaActualizar(productoId)
                    .orElseThrow(() -> new ProductoNoEncontradoException(productoId));

            if (reserva.liberar()) {
                liberadas++;
                log.debug("Reserva id={} liberada para pedidoId={}, productoId={}, cantidad={}, stockDisponible={}",
                        reserva.getId(), request.pedidoId(), productoId, reserva.getCantidad(), producto.getCantidadDisponible());
            }
        }

        log.info("Liberación de stock procesada para pedidoId={}: {} reserva(s) liberada(s)",
                request.pedidoId(), liberadas);

        return new ReservaLiberarResponse(request.pedidoId(), liberadas);
    }

    private ReservaResponse reservaRepetida(Reserva existente, ReservaCrearRequest request) {
        if (existente.getEstado() == EstadoReserva.LIBERADA) {
            log.warn("Conflicto de reserva para pedidoId={}, productoId={}: la reserva id={} ya fue liberada",
                    request.pedidoId(), request.productoId(), existente.getId());
            throw new ReservaConflictoException("La reserva del pedido " + request.pedidoId()
                    + " para el producto " + request.productoId() + " ya fue liberada");
        }
        if (!existente.getCantidad().equals(request.cantidad())) {
            log.warn("Conflicto de reserva para pedidoId={}, productoId={}: ya existe por {} unidad(es) y se solicitaron {}",
                    request.pedidoId(), request.productoId(), existente.getCantidad(), request.cantidad());
            throw new ReservaConflictoException("El pedido " + request.pedidoId()
                    + " ya tiene una reserva del producto " + request.productoId()
                    + " por " + existente.getCantidad() + " unidades");
        }
        log.info("Reserva idempotente reutilizada: reservaId={}, pedidoId={}, productoId={}, cantidad={}",
                existente.getId(), request.pedidoId(), request.productoId(), existente.getCantidad());
        return ReservaResponse.from(existente);
    }
}