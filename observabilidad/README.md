# Observabilidad (TP-032)

Logs, traces y métricas de los tres servicios. Basado en el ejemplo de la cátedra
(rama `observabilidad` del repo `santiagomurchio/clientes`), adaptado a nuestros 3 servicios.

## Qué hay

| Pieza | Para qué |
|---|---|
| `micrometer-tracing-bridge-brave` + actuator | Cada request recibe un `traceId`, que aparece en todos sus logs |
| `logback-spring.xml` (en cada servicio) | Logs en consola, en `logs/<servicio>.json` y, con el perfil `loki`, en Grafana Loki |
| `TraceIdResponseFilter` (en cada servicio) | Devuelve el `traceId` en el header `X-Trace-Id`, para poder buscarlo después |
| Logs con `kv(...)` en los services | Eventos de negocio como campos filtrables, por ejemplo `clienteId`. Nunca datos sensibles (email, nombre) |
| `micrometer-registry-prometheus` | Métricas en `/actuator/prometheus`: las HTTP que mide Spring solo (método RED) y las propias de cada servicio |
| `docker-compose.yml` | Loki (logs), Prometheus (métricas) y Grafana (pantallas) |

## Cómo levantarlo

Hace falta **Docker Desktop** corriendo.

1. Stack de observabilidad (desde esta carpeta):

   ```bash
   cd observabilidad
   docker compose up -d
   ```

   Loki tarda unos 15 segundos en estar listo: <http://localhost:3100/ready> tiene que decir `ready`.

2. Los servicios con el perfil `loki` (cada uno en su terminal, desde la raíz del repo):

   ```bash
   ./gradlew :servicio-clientes:bootRun --args='--spring.profiles.active=loki'
   ./gradlew :servicio-inventario:bootRun --args='--spring.profiles.active=loki'
   ./gradlew :servicio-pedidos:bootRun --args='--spring.profiles.active=loki'
   ```

   Sin el perfil `loki` los servicios andan igual, pero los logs no llegan a Grafana
   (solo quedan en consola y en `logs/`).

| Qué | URL |
|---|---|
| Grafana | <http://localhost:3000> (usuario `admin`, contraseña `admin`) |
| Prometheus | <http://localhost:9090> (en *Status → Targets* los 3 servicios tienen que estar `UP`) |
| Métricas crudas de un servicio | <http://localhost:8081/actuator/prometheus> |

Para apagar el stack: `docker compose down` (con `-v` además borra los logs y métricas guardados).

## Demo: buscar logs y seguir un trace

1. Hacer un request, por ejemplo `POST http://localhost:8083/pedidos`.
2. En la respuesta, copiar el header **`X-Trace-Id`** (en Postman, pestaña *Headers*).
3. En Grafana, **Dashboards → TiendaOnline → TiendaOnline - Logs**, pegar el traceId en la
   variable de arriba. El último panel muestra todos los logs de esa request, de los tres servicios.

También desde **Explore** (datasource Loki):

| Objetivo | Consulta |
|---|---|
| Todos los logs | `{app=~"servicio-.*"}` |
| Los de un servicio | `{app="servicio-clientes"}` |
| Warnings y errores | `{app=~"servicio-.*", level=~"WARN\|ERROR"}` |
| Buscar un texto | `{app=~"servicio-.*"} \|= "no encontrado"` |
| Seguir una request | `{app=~"servicio-.*"} \| json \| traceId="<traceId>"` |
| Todo lo de un cliente | `{app="servicio-clientes"} \| json \| clienteId="1"` |

## Métricas

En **Dashboards → TiendaOnline → TiendaOnline - Métricas** (método RED: Rate, Errors, Duration):

- Requests por segundo de cada servicio, separadas por status (los 4xx/5xx son los errores).
- Latencia p95 por servicio.
- Contadores propios de clientes: `clientes_creados_total`, `clientes_encontrados_total` y
  `clientes_no_encontrados_total` (con el tag `operacion`).

Consultas PromQL útiles (en *Explore*, datasource Prometheus):

| Objetivo | Consulta |
|---|---|
| Clientes creados en la última hora | `increase(clientes_creados_total[1h])` |
| No encontrados por operación | `sum by (operacion) (increase(clientes_no_encontrados_total[1m]))` |
| Requests por segundo según servicio y status | `sum by (application, status) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))` |
| Latencia p95 por servicio | `histogram_quantile(0.95, sum by (application, le) (rate(http_server_requests_seconds_bucket{uri!~"/actuator.*"}[5m])))` |

## Logs de negocio en cada servicio

`servicio-clientes` ya loguea sus eventos (cliente creado, cliente no encontrado, email repetido,
pedidos consultados) con `kv("clienteId", id)`. Inventario y pedidos tienen que agregar los suyos
de la misma forma (con `@Slf4j` y `kv(...)`), por ejemplo "Reserva creada", "Stock insuficiente",
"Pedido creado". Sin esos logs, al seguir un trace en Grafana solo aparecen las líneas de clientes.

## Importante: trace entre servicios

Para que el mismo `traceId` llegue de un servicio al otro, el `RestClient` que hace la llamada
tiene que construirse con el `RestClient.Builder` que inyecta Spring (así Spring le agrega el
header de tracing). `PedidosClient` (clientes → pedidos) ya lo hace. Los clientes de
`servicio-pedidos` hacia clientes e inventario usan `RestClient.builder()` directo y hay que
cambiarlos para que reciban el builder por constructor; si no, cada servicio muestra un traceId
distinto.

## Problemas comunes

- **No aparece nada en Loki:** revisar que el servicio arrancó con el perfil `loki` (al arrancar
  dice `The following 1 profile is active: "loki"`) y que <http://localhost:3100/ready> responda.
- **En Prometheus un target aparece `DOWN`:** ese servicio no está corriendo en su puerto.
- **Puerto 3000, 3100 o 9090 ocupado:** cambiar el puerto de la izquierda en `docker-compose.yml`.
