package com.tiendaonline.inventario.service;

import com.tiendaonline.inventario.dto.ProductoStockResponse;
import com.tiendaonline.inventario.exception.ProductoNoEncontradoException;
import com.tiendaonline.inventario.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public ProductoStockResponse consultarStock(Integer productoId) {
        return productoRepository.findById(productoId)
                .map(ProductoStockResponse::from)
                .orElseThrow(() -> new ProductoNoEncontradoException(productoId));
    }
}
