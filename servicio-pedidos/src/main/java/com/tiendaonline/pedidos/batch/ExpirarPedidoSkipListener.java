package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

@Component
public class ExpirarPedidoSkipListener implements SkipListener<Pedido, Pedido> {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidoSkipListener.class);

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("Fallo al leer pedido vencido en batch de expiración: {}", t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(Pedido pedido, Throwable t) {
        log.error("Fallo al procesar pedido id={}: {}", pedido.getId(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Pedido pedido, Throwable t) {
        log.error("Pedido id={} marcado como fallido (se reintentará en la próxima corrida). Causa: {}",
                pedido.getId(), t.getMessage());
    }
}