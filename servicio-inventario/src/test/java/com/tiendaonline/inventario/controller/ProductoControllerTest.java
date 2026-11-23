package com.tiendaonline.inventario.controller;

import com.tiendaonline.inventario.dto.ProductoStockResponse;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductoService productoService;

    @Test
    void getStockDeProductoExistenteDevuelve200() throws Exception {

        when(productoService.consultarStock(1))
                .thenReturn(new ProductoStockResponse(1, "SKU-1", "Mouse", 10));

        mockMvc.perform(get("/productos/1/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.nombre").value("Mouse"))
                .andExpect(jsonPath("$.cantidadDisponible").value(10));
    }

    @Test
    void getStockDeProductoInexistenteDevuelve404() throws Exception {

        when(productoService.consultarStock(99))
                .thenThrow(new ProductoNoEncontradoException(99));

        mockMvc.perform(get("/productos/99/stock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("No existe un producto con id 99"));
    }

    @Test
    void getStockConIdNoNumericoDevuelve400() throws Exception {

        mockMvc.perform(get("/productos/abc/stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
