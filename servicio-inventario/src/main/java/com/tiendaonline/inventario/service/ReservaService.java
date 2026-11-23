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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
            throw new StockInsuficienteException(
                    producto.getId(), producto.getCantidadDisponible(), request.cantidad());
        }

        producto.descontarStock(request.cantidad());

        Reserva reserva = reservaRepository.save(
                new Reserva(producto, request.pedidoId(), request.cantidad()));

        return ReservaResponse.from(reserva);
    }

    @Transactional
    public ReservaLiberarResponse liberar(ReservaLiberarRequest request) {
        List<Reserva> activas = reservaRepository.buscarActivasParaActualizar(request.pedidoId());

        int liberadas = 0;
        for (Reserva reserva : activas) {
            Integer productoId = reserva.getProducto().getId();
            productoRepository.buscarParaActualizar(productoId)
                    .orElseThrow(() -> new ProductoNoEncontradoException(productoId));

            if (reserva.liberar()) {
                liberadas++;
            }
        }

        return new ReservaLiberarResponse(request.pedidoId(), liberadas);
    }

    private ReservaResponse reservaRepetida(Reserva existente, ReservaCrearRequest request) {
        if (existente.getEstado() == EstadoReserva.LIBERADA) {
            throw new ReservaConflictoException("La reserva del pedido " + request.pedidoId()
                    + " para el producto " + request.productoId() + " ya fue liberada");
        }
        if (!existente.getCantidad().equals(request.cantidad())) {
            throw new ReservaConflictoException("El pedido " + request.pedidoId()
                    + " ya tiene una reserva del producto " + request.productoId()
                    + " por " + existente.getCantidad() + " unidades");
        }
        return ReservaResponse.from(existente);
    }
}
