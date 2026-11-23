package com.tiendaonline.pedidos.batch;

import com.tiendaonline.pedidos.entity.EstadoPedido;
import com.tiendaonline.pedidos.entity.Pedido;
import com.tiendaonline.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidosVencidosReaderTest {

    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 11, 21, 3, 0);

    @Mock
    private PedidoRepository pedidoRepository;

    private Pedido pedido(Integer id) {
        Pedido pedido = new Pedido(5);
        pedido.setId(id);
        return pedido;
    }

    private void cuandoBuscaDesde(int ultimoId, int tamanio, List<Pedido> resultado) {
        when(pedidoRepository.findByEstadoAndFechaCreacionBeforeAndIdGreaterThanOrderByIdAsc(
                EstadoPedido.CREADO, LIMITE, ultimoId, PageRequest.of(0, tamanio)))
                .thenReturn(resultado);
    }

    @Test
    void leePaginaPorPaginaYTerminaConNull() {
        cuandoBuscaDesde(0, 2, List.of(pedido(3), pedido(8)));
        cuandoBuscaDesde(8, 2, List.of(pedido(12)));
        cuandoBuscaDesde(12, 2, List.of());
        PedidosVencidosReader reader = new PedidosVencidosReader(pedidoRepository, LIMITE, 2);
        reader.open(new ExecutionContext());

        assertThat(reader.read().getId()).isEqualTo(3);
        assertThat(reader.read().getId()).isEqualTo(8);
        assertThat(reader.read().getId()).isEqualTo(12);
        assertThat(reader.read()).isNull();
    }

    @Test
    void sinPedidosVencidosDevuelveNullEnLaPrimeraLectura() {
        cuandoBuscaDesde(0, 50, List.of());
        PedidosVencidosReader reader = new PedidosVencidosReader(pedidoRepository, LIMITE, 50);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isNull();
    }

    @Test
    void updateGuardaElUltimoIdLeido() {
        cuandoBuscaDesde(0, 50, List.of(pedido(4), pedido(9)));
        PedidosVencidosReader reader = new PedidosVencidosReader(pedidoRepository, LIMITE, 50);
        ExecutionContext contexto = new ExecutionContext();
        reader.open(contexto);

        reader.read();
        reader.read();
        reader.update(contexto);

        assertThat(contexto.getInt(PedidosVencidosReader.CLAVE_ULTIMO_ID)).isEqualTo(9);
    }

    @Test
    void alReiniciarSigueDesdeElUltimoIdGuardado() {
        cuandoBuscaDesde(9, 50, List.of(pedido(15)));
        ExecutionContext contexto = new ExecutionContext();
        contexto.putInt(PedidosVencidosReader.CLAVE_ULTIMO_ID, 9);
        PedidosVencidosReader reader = new PedidosVencidosReader(pedidoRepository, LIMITE, 50);

        reader.open(contexto);

        assertThat(reader.read().getId()).isEqualTo(15);
    }

    @Test
    void elLimiteEsLaFechaDeEjecucionMenos24Horas() {
        LocalDateTime fechaEjecucion = LocalDateTime.of(2026, 11, 22, 3, 0);
        PedidosVencidosReader reader = new PedidosVencidosReaderConfig()
                .pedidosVencidosReader(pedidoRepository, fechaEjecucion);
        reader.open(new ExecutionContext());

        reader.read();

        verify(pedidoRepository).findByEstadoAndFechaCreacionBeforeAndIdGreaterThanOrderByIdAsc(
                EstadoPedido.CREADO, LIMITE, 0, PageRequest.of(0, PedidosVencidosReaderConfig.TAMANIO_PAGINA));
    }
}
