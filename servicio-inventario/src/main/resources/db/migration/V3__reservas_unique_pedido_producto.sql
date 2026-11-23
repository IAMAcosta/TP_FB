ALTER TABLE reservas
    ADD CONSTRAINT uk_reservas_pedido_producto UNIQUE (pedido_id, producto_id);

-- Una sola reserva por producto dentro de un mismo pedido (TP-006 / TP-017).
-- Si un reintento llega dos veces al mismo tiempo, la base rechaza el duplicado.
