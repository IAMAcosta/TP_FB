package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.ItemPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.batch.item.ExecutionContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PedidosVencidosReaderIntegracionTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestEntityManager em;

    private Pedido guardar(EstadoPedido estado) {
        Pedido pedido = new Pedido(3003);
        pedido.agregarItem(new ItemPedido(101, 1, new BigDecimal("10.00")));
        pedido.recalcularMontoTotal();
        pedido.setEstado(estado);
        return pedidoRepository.saveAndFlush(pedido);
    }

    private void cambiarFechaCreacion(Pedido pedido, LocalDateTime fecha) {
        em.getEntityManager()
                .createNativeQuery("update pedidos set fecha_creacion = :f where id = :id")
                .setParameter("f", fecha)
                .setParameter("id", pedido.getId())
                .executeUpdate();
    }

    @Test
    void leeTodosLosCreadosVencidosAunqueSeVayanMarcandoComoExpirados() {
        LocalDateTime ahora = LocalDateTime.now();
        Pedido viejo1 = guardar(EstadoPedido.CREADO);
        Pedido viejo2 = guardar(EstadoPedido.CREADO);
        Pedido viejo3 = guardar(EstadoPedido.CREADO);
        Pedido reciente = guardar(EstadoPedido.CREADO);
        Pedido viejoCancelado = guardar(EstadoPedido.CANCELADO);
        Pedido viejoConfirmado = guardar(EstadoPedido.CONFIRMADO);
        cambiarFechaCreacion(viejo1, ahora.minusHours(30));
        cambiarFechaCreacion(viejo2, ahora.minusHours(26));
        cambiarFechaCreacion(viejo3, ahora.minusHours(25));
        cambiarFechaCreacion(reciente, ahora.minusHours(23));
        cambiarFechaCreacion(viejoCancelado, ahora.minusHours(30));
        cambiarFechaCreacion(viejoConfirmado, ahora.minusHours(30));
        em.clear();

        PedidosVencidosReader reader = new PedidosVencidosReader(pedidoRepository, ahora.minusHours(24), 2);
        reader.open(new ExecutionContext());

        List<Integer> leidos = new ArrayList<>();
        Pedido pedido = reader.read();
        while (pedido != null) {
            leidos.add(pedido.getId());
            pedido.setEstado(EstadoPedido.EXPIRADO);
            pedidoRepository.saveAndFlush(pedido);
            pedido = reader.read();
        }

        assertThat(leidos)
                .contains(viejo1.getId(), viejo2.getId(), viejo3.getId())
                .doesNotContain(reciente.getId(), viejoCancelado.getId(), viejoConfirmado.getId())
                .isSorted()
                .doesNotHaveDuplicates();
    }
}
