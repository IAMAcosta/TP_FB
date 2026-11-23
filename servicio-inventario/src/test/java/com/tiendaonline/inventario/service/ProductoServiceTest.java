package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ProductoStockResponse;
import com.tiendaonline.inventario.entity.Producto;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoService productoService;

    @Test
    void consultarStockDeProductoExistenteDevuelveSusDatos() {
        Producto producto = new Producto("SKU-1", "Mouse", 10);
        producto.setId(1);

        when(productoRepository.findById(1)).thenReturn(Optional.of(producto));

        ProductoStockResponse respuesta = productoService.consultarStock(1);

        assertEquals(1, respuesta.id());
        assertEquals("SKU-1", respuesta.sku());
        assertEquals("Mouse", respuesta.nombre());
        assertEquals(10, respuesta.cantidadDisponible());
    }

    @Test
    void consultarStockDeProductoSinStockDevuelveCero() {
        Producto producto = new Producto("SKU-2", "Teclado", 0);
        producto.setId(2);

        when(productoRepository.findById(2)).thenReturn(Optional.of(producto));

        ProductoStockResponse respuesta = productoService.consultarStock(2);

        assertEquals(0, respuesta.cantidadDisponible());
    }

    @Test
    void consultarStockDeProductoInexistenteLanzaExcepcion() {
        when(productoRepository.findById(99)).thenReturn(Optional.empty());

        ProductoNoEncontradoException exception = assertThrows(ProductoNoEncontradoException.class, () -> {
            productoService.consultarStock(99);
        });

        assertEquals("No existe un producto con id 99", exception.getMessage());
    }
}
