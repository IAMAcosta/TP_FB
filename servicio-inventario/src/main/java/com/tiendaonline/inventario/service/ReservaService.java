package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ReservaCrearRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarRequest;
import com.tiendaonline.inventario.dto.ReservaLiberarResponse;
import com.tiendaonline.inventario.dto.ReservaResponse;
import com.tiendaonline.inventario.entity.EstadoReserva;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.entity.Reserva;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.exception.StockInsuficienteException;
import com.tiendaonline.inventario.repository.ProductoRepository;
import com.tiendaonline.inventario.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ProductoRepository productoRepository;
    private final ReservaRepository reservaRepository;

    @Transactional
    public ReservaResponse reservar(ReservaCrearRequest request) {
        Producto producto = productoRepository.buscarParaActualizar(request.productoId())
                .orElseThrow(() -> new ProductoNoEncontradoException(request.productoId()));

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
        List<Reserva> activas = reservaRepository.findByPedidoIdAndEstado(
                request.pedidoId(), EstadoReserva.ACTIVA);

        int liberadas = 0;
        for (Reserva reserva : activas) {
            if (reserva.liberar()) {
                liberadas++;
            }
        }

        return new ReservaLiberarResponse(request.pedidoId(), liberadas);
    }
}
