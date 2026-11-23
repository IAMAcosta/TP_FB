package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PedidosVencidosReader implements ItemStreamReader<Pedido> {

    static final String CLAVE_ULTIMO_ID = "pedidosVencidos.ultimoId";

    private final PedidoRepository pedidoRepository;
    private final LocalDateTime limite;
    private final int tamanioPagina;

    private List<Pedido> pagina = new ArrayList<>();
    private int posicion = 0;
    private int ultimoId = 0;

    public PedidosVencidosReader(PedidoRepository pedidoRepository, LocalDateTime limite, int tamanioPagina) {
        this.pedidoRepository = pedidoRepository;
        this.limite = limite;
        this.tamanioPagina = tamanioPagina;
    }

    @Override
    public void open(ExecutionContext contexto) {
        if (contexto.containsKey(CLAVE_ULTIMO_ID)) {
            ultimoId = contexto.getInt(CLAVE_ULTIMO_ID);
            log.info("Reanudando lectura de pedidos vencidos desde el id {}", ultimoId);
        }
    }

    @Override
    public Pedido read() {
        if (posicion >= pagina.size()) {
            pagina = pedidoRepository.findByEstadoAndFechaCreacionBeforeAndIdGreaterThanOrderByIdAsc(
                    EstadoPedido.CREADO, limite, ultimoId, PageRequest.of(0, tamanioPagina));
            posicion = 0;
            if (pagina.isEmpty()) {
                return null;
            }
        }
        Pedido pedido = pagina.get(posicion);
        posicion++;
        ultimoId = pedido.getId();
        return pedido;
    }

    @Override
    public void update(ExecutionContext contexto) {
        contexto.putInt(CLAVE_ULTIMO_ID, ultimoId);
    }
}
