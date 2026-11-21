package com.tiendaonline.inventario.repository;

import com.tiendaonline.inventario.entity.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Test
    void guardaUnProductoYLoEncuentraPorSku() {
        Producto guardado = productoRepository.saveAndFlush(new Producto("SKU-TEST-1", "Mouse", 10));

        assertThat(guardado.getId()).isNotNull();
        Producto leido = productoRepository.findBySku("SKU-TEST-1").orElseThrow();
        assertThat(leido.getNombre()).isEqualTo("Mouse");
        assertThat(leido.getCantidadDisponible()).isEqualTo(10);
    }

    @Test
    void findBySkuDevuelveVacioSiNoExiste() {
        assertThat(productoRepository.findBySku("NO-EXISTE")).isEmpty();
    }

    @Test
    void noPermiteDosProductosConElMismoSku() {
        productoRepository.saveAndFlush(new Producto("SKU-DUP", "Mouse", 1));

        assertThatThrownBy(() -> productoRepository.saveAndFlush(new Producto("SKU-DUP", "Teclado", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void labaseRechazaStockNegativo() {
        Producto producto = new Producto("SKU-NEG", "Mouse", 1);
        producto.setCantidadDisponible(-1);

        assertThatThrownBy(() -> productoRepository.saveAndFlush(producto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscarParaActualizarDevuelveElProductoConBloqueo() {
        Producto guardado = productoRepository.saveAndFlush(new Producto("SKU-LOCK", "Mouse", 5));

        assertThat(productoRepository.buscarParaActualizar(guardado.getId()))
                .get()
                .extracting(Producto::getSku)
                .isEqualTo("SKU-LOCK");
        assertThat(productoRepository.buscarParaActualizar(999999)).isEmpty();
    }
}