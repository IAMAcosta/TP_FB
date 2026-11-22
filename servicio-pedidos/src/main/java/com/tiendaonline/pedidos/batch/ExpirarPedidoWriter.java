package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.client.InventarioClient;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class ExpirarPedidoWriter implements ItemWriter<Pedido> {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidoWriter.class);

    private final PedidoRepository pedidoRepository;
    private final InventarioClient inventarioClient;

    public ExpirarPedidoWriter(PedidoRepository pedidoRepository, InventarioClient inventarioClient) {
        this.pedidoRepository = pedidoRepository;
        this.inventarioClient = inventarioClient;
    }

    @Override
    public void write(Chunk<? extends Pedido> chunk) {
        for (Pedido pedido : chunk.getItems()) {
            inventarioClient.liberarReservaPorPedido(pedido.getId());

            pedidoRepository.save(pedido);

            log.info("Pedido id={} expirado y reserva de stock liberada correctamente", pedido.getId());
        }
    }
}