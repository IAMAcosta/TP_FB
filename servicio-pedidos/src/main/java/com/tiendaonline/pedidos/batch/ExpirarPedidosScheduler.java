package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.dto.JobExecutionResumenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "batch.expiracion.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExpirarPedidosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidosScheduler.class);

    private final ExpirarPedidosLauncher launcher;

    public ExpirarPedidosScheduler(ExpirarPedidosLauncher launcher) {
        this.launcher = launcher;
    }

    @Scheduled(
            cron = "${batch.expiracion.cron:0 0 2 * * *}",
            zone = "${batch.expiracion.zone:America/Argentina/Buenos_Aires}"
    )
    public void ejecutarExpiracionNocturna() {
        try {
            JobExecutionResumenResponse resumen = launcher.ejecutar("PROGRAMADO_NOCTURNO");
            log.info("Corrida nocturna finalizada: estado={}, leidos={}, expirados={}, omitidos={}",
                    resumen.estado(), resumen.pedidosLeidos(),
                    resumen.pedidosExpirados(), resumen.pedidosOmitidosPorError());
        } catch (Exception e) {
            log.error("Error durante la ejecución nocturna de expirarPedidosJob", e);
        }
    }
}