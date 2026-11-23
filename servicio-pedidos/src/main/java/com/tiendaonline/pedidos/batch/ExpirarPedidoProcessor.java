package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class ExpirarPedidoProcessor implements ItemProcessor<Pedido, Pedido> {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidoProcessor.class);

    @Override
    public Pedido process(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.CREADO) {
            log.warn("Pedido id={} omitido en processor porque su estado actual es {}",
                    pedido.getId(), pedido.getEstado());
            return null;
        }

        pedido.setEstado(EstadoPedido.EXPIRADO);
        pedido.setFechaActualizacion(LocalDateTime.now());

        log.debug("Pedido id={} marcado como EXPIRADO", pedido.getId());
        return pedido;
    }
}