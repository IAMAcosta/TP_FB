package com.tiendaonline.pedidos.service;

import com.tiendaonline.pedidos.client.ClientesClient;
import com.tiendaonline.pedidos.client.InventarioClient;
import com.tiendaonline.pedidos.dto.CrearPedidoRequest;
import com.tiendaonline.pedidos.dto.ItemPedidoRequest;
import com.tiendaonline.pedidos.dto.PedidoResponse;
import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.ItemPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.exception.ClienteNoEncontradoException;
import com.tiendaonline.pedidos.exception.EstadoPedidoInvalidoException;
import com.tiendaonline.pedidos.exception.PedidoInvalidoException;
import com.tiendaonline.pedidos.exception.PedidoNoEncontradoException;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ClientesClient clientesClient;
    private final InventarioClient inventarioClient;

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPorId(Integer id) {
        return pedidoRepository.findByIdConItems(id)
                .map(PedidoResponse::from)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPorCliente(Integer clienteId) {
        return pedidoRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId).stream()
                .map(PedidoResponse::from)
                .toList();
    }

    public PedidoResponse crearPedido(CrearPedidoRequest request) {
        validarProductosNoRepetidos(request);

        if (!clientesClient.existe(request.clienteId())) {
            throw new ClienteNoEncontradoException(request.clienteId());
        }

        Pedido pedido = pedidoRepository.save(construirPedido(request));

        try {
            for (ItemPedidoRequest item : request.items()) {
                inventarioClient.reservar(pedido.getId(), item.productoId(), item.cantidad());
            }
        } catch (RuntimeException e) {
            try {
                compensar(pedido.getId());
            } catch (RuntimeException errorDeCompensacion) {
                e.addSuppressed(errorDeCompensacion);
            }
            throw e;
        }

        return PedidoResponse.from(pedido);
    }

    @Transactional
    public PedidoResponse cancelar(Integer id) {
        Pedido pedido = pedidoRepository.findByIdConItems(id)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));

        if (!pedido.getEstado().puedeCancelarse()) {
            log.warn("No se puede cancelar el pedido {} porque está {}", id, pedido.getEstado());
            throw new EstadoPedidoInvalidoException(id, pedido.getEstado());
        }

        inventarioClient.liberar(id);

        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.saveAndFlush(pedido);
        log.info("Pedido {} cancelado", id);
        return PedidoResponse.from(pedido);
    }

    private void validarProductosNoRepetidos(CrearPedidoRequest request) {
        Set<Integer> vistos = new HashSet<>();
        for (ItemPedidoRequest item : request.items()) {
            if (!vistos.add(item.productoId())) {
                throw new PedidoInvalidoException("El producto " + item.productoId() + " está repetido. Agrupá sus cantidades en un solo ítem");
            }
        }
    }

    private Pedido construirPedido(CrearPedidoRequest request) {
        Pedido pedido = new Pedido(request.clienteId());
        for (ItemPedidoRequest item : request.items()) {
            pedido.agregarItem(new ItemPedido(
                    item.productoId(),
                    item.cantidad(),
                    item.precioUnitario().setScale(2, RoundingMode.HALF_UP)));
        }
        pedido.recalcularMontoTotal();
        return pedido;
    }

    private void compensar(Integer pedidoId) {
        try {
            inventarioClient.liberar(pedidoId);
            pedidoRepository.deleteById(pedidoId);
        } catch (RuntimeException ex) {
            log.error("No se pudo deshacer el pedido {}: queda CANCELADO para revisión manual", pedidoId, ex);
            pedidoRepository.findById(pedidoId).ifPresent(pedido -> {
                pedido.setEstado(EstadoPedido.CANCELADO);
                pedidoRepository.save(pedido);
            });
        }
    }
}