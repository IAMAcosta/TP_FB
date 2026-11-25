# TiendaOnline — Sistema de Gestión de Pedidos, Inventario y Clientes

Trabajo Práctico Final de **Fundamentos de Desarrollo Backend** (UTN FRSF, 2026).
El enunciado está en [`docs/TP 2026.pdf`](./docs/TP%202026.pdf).

Backend de una tienda online separado en tres microservicios. Cada uno tiene su propia base de
datos PostgreSQL y su esquema versionado con Flyway. No comparten código ni base: se comunican
por HTTP.

## Estructura del repositorio

```
TP_FB/
├── servicio-clientes/     # Clientes (registro, consulta, flag mask-pii, pedidos del cliente)
├── servicio-inventario/   # Productos, stock y reservas
├── servicio-pedidos/      # Pedidos (creación con reserva de stock, cancelación, job batch de expiración)
├── observabilidad/        # Loki, Prometheus y Grafana (docker compose) + dashboards
└── docs/                  # Enunciado del TP
```

Es un proyecto Gradle multi-módulo: el `settings.gradle` y el `build.gradle` de la raíz declaran
los tres servicios y la configuración común (Spring Boot 3.3, Java 17).

## Servicios, puertos y bases de datos

| Servicio | Puerto | Base de datos | Llama a |
|---|---|---|---|
| servicio-clientes | 8081 | `clientes_db` | servicio-pedidos (pedidos de un cliente) |
| servicio-inventario | 8082 | `inventario_db` | — |
| servicio-pedidos | 8083 | `pedidos_db` | servicio-clientes (existe el cliente) y servicio-inventario (reservar / liberar stock) |

Las tres bases viven en la misma instancia local de PostgreSQL (puerto `5432`, usuario
`postgres`, contraseña `postgres`, solo para entorno local).

---

## 1. Requisitos

| Qué | Versión | Notas |
|---|---|---|
| JDK | **17** | Gradle busca un JDK 17 instalado. Si en la compu solo hay otro Java (21, 22…), el build falla. Se puede descargar de [Adoptium (Temurin 17)](https://adoptium.net/temurin/releases/?version=17). Pueden convivir varias versiones. |
| PostgreSQL | 16 o 17 | El **servidor**, no solo pgAdmin. Con la 17, Flyway muestra un aviso de "upgrade recommended" pero funciona igual. |
| pgAdmin 4 | cualquiera | Para crear las bases y cargar datos de prueba. |
| Git | — | En Windows recomendamos usar **Git Bash**. |
| Postman | opcional | Para probar los endpoints (también se puede usar Swagger). |
| Docker Desktop | — | **Solo para la observabilidad** (Loki, Prometheus, Grafana). Los servicios y PostgreSQL no corren en Docker. |
| Acceso al proyecto de Statsig | — | Para la clave del flag `mask-pii` (ver paso 3). |

Para comprobar que Gradle encuentra el JDK 17, desde la raíz del repo:

```bash
./gradlew -q javaToolchains
```

Tiene que aparecer una instalación con `Language Version: 17`.

## 2. Base de datos

1. Instalar el servidor PostgreSQL desde <https://www.postgresql.org/download/windows/>
   (instalador de EDB). Durante la instalación:
   - contraseña del superusuario: `postgres`
   - puerto: `5432`
2. Abrir pgAdmin, conectarse al servidor (`localhost`, puerto `5432`, usuario `postgres`).
3. Crear tres bases (clic derecho en **Databases → Create → Database...**):
   - `clientes_db`
   - `inventario_db`
   - `pedidos_db`

No hace falta crear tablas: cada servicio corre sus migraciones de Flyway al arrancar.

> Si tu PostgreSQL tiene otra contraseña, no cambies el `application.yml` (es compartido).
> Pasala al levantar el servicio con la variable `SPRING_DATASOURCE_PASSWORD`, por ejemplo en
> Git Bash: `SPRING_DATASOURCE_PASSWORD=miClave ./gradlew :servicio-clientes:bootRun`

## 3. Clave de Statsig (feature flag `mask-pii`)

`servicio-clientes` usa Statsig para el flag `mask-pii`, que enmascara el nombre y el email del
cliente. Al arrancar lee la clave de la variable de entorno **`STATSIG_SERVER_SECRET`**; si no
está, el servicio no levanta (`Falta configurar STATSIG_SERVER_SECRET`).

1. Pedir a un integrante del grupo que te invite al proyecto de Statsig.
2. En la consola de Statsig: **Project Settings → Keys & Environments** → copiar la
   **Server Secret Key** (empieza con `secret-`). No usar la `client-` ni la `console-`.
3. Cargarla como variable de entorno. Una de estas opciones:

   | Dónde | Cómo | Dura |
   |---|---|---|
   | Windows (recomendado) | Buscar *"Editar las variables de entorno de esta cuenta"* → **Nueva** → nombre `STATSIG_SERVER_SECRET`, valor la clave. Después cerrar y volver a abrir la terminal / VS Code. | Siempre |
   | PowerShell | `$env:STATSIG_SERVER_SECRET="secret-..."` | Esa terminal |
   | Git Bash | `export STATSIG_SERVER_SECRET="secret-..."` | Esa terminal |

**La clave nunca se sube al repositorio** (ni en `application.yml` ni en commits).

Para comprobar que quedó cargada: `echo $STATSIG_SERVER_SECRET` (Git Bash) o
`echo $env:STATSIG_SERVER_SECRET` (PowerShell).

## 4. Levantar los servicios

Cada servicio en **su propia terminal**, siempre desde la **raíz del repo** (donde está `gradlew`):

```bash
./gradlew :servicio-inventario:bootRun
./gradlew :servicio-clientes:bootRun
./gradlew :servicio-pedidos:bootRun
```

- En **CMD** se escribe sin `./`: `gradlew :servicio-clientes:bootRun`.
- El servicio está listo cuando aparece `Started Servicio...Application in X seconds`.
- La barra de Gradle se queda en `80% EXECUTING` mientras el servicio corre: es normal.
  Para apagarlo, **Ctrl + C** en esa terminal.

### Swagger (documentación de cada API)

| Servicio | URL |
|---|---|
| Clientes | <http://localhost:8081/swagger-ui/index.html> |
| Inventario | <http://localhost:8082/swagger-ui/index.html> |
| Pedidos | <http://localhost:8083/swagger-ui/index.html> |

## 5. Datos de prueba: productos

Inventario no tiene endpoint para dar de alta productos, así que para probar hay que cargarlos
a mano. En pgAdmin, sobre `inventario_db`, abrir **Query Tool** y ejecutar (después de haber
levantado inventario al menos una vez, para que Flyway cree las tablas):

```sql
INSERT INTO productos (sku, nombre, cantidad_disponible) VALUES
    ('SKU-001', 'Mouse inalámbrico', 50),
    ('SKU-002', 'Teclado mecánico', 20),
    ('SKU-003', 'Monitor 24 pulgadas', 5);

SELECT id, sku, nombre, cantidad_disponible FROM productos;
```

**Ojo con los ids:** no siempre son 1, 2 y 3. Los tests de inventario usan la misma base, crean
y borran productos, y PostgreSQL no reutiliza los números (pueden quedar 61, 62, 63…). Usar los
ids que devuelve el `SELECT` en todas las pruebas.

## 6. Endpoints

Todos los errores responden con el mismo formato:

```json
{ "timestamp": "2026-11-22T10:15:30", "status": 404, "error": "Not Found", "mensaje": "Cliente no encontrado" }
```

### servicio-clientes (8081)

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| POST | `/clientes` | Registra un cliente | 201, 400, 409 (email repetido) |
| GET | `/clientes/{id}` | Consulta un cliente. Con `mask-pii` activo, nombre y email vuelven enmascarados (`J*** P***`, `****@****.com`) | 200, 400, 404 |
| GET | `/clientes/{id}/ordenes` | Pedidos del cliente (consulta síncrona a servicio-pedidos) | 200, 400, 404, 502, 503, 504 |

### servicio-inventario (8082)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/productos/{id}/stock` | Stock disponible de un producto |
| POST | `/reservas` | Reserva unidades para un pedido (`productoId`, `pedidoId`, `cantidad`). 409 si no hay stock |
| POST | `/reservas/liberacion` | Libera las reservas activas de un pedido (`pedidoId`) |

### servicio-pedidos (8083)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/pedidos` | Crea un pedido: verifica el cliente, reserva el stock de cada ítem y calcula el total. 404 si el cliente o un producto no existe, 409 si no hay stock, 503 si clientes o inventario no responden |
| GET | `/pedidos/{id}` | Consulta un pedido |
| GET | `/pedidos?id_cliente={id}` | Pedidos de un cliente (lista vacía si no tiene) |
| POST | `/pedidos/{id}/cancelacion` | Cancela un pedido CREADO o CONFIRMADO y libera su stock. 409 si ya está CANCELADO o EXPIRADO, 503 si inventario no responde (en ese caso no se cancela) |
| POST | `/batch/expirar-pedidos` | Ejecuta a mano el job de expiración (ver sección 8) y devuelve el resumen de la corrida |

Swagger de cada servicio: `http://localhost:808X/swagger-ui/index.html` (ver sección 4).

## 7. Prueba rápida del flujo completo

Con los tres servicios levantados y los productos cargados. En los ejemplos, `61` y `62` son los
ids de los productos: reemplazarlos por los que devolvió el `SELECT` del paso 5.

1. **Registrar un cliente** — `POST http://localhost:8081/clientes`
   ```json
   { "nombre": "Juan Pérez", "email": "juan@mail.com" }
   ```
2. **Consultarlo** — `GET http://localhost:8081/clientes/1`
3. **Ver el stock** — `GET http://localhost:8082/productos/61/stock`
4. **Crear un pedido** — `POST http://localhost:8083/pedidos`
   ```json
   {
     "clienteId": 1,
     "items": [
       { "productoId": 61, "cantidad": 2, "precioUnitario": 10500.00 },
       { "productoId": 62, "cantidad": 1, "precioUnitario": 45000.00 }
     ]
   }
   ```
   Responde **201** con el pedido en estado `CREADO`. Anotar su `id`.
5. **Ver que bajó el stock** — `GET http://localhost:8082/productos/61/stock`
6. **Pedidos del cliente** — `GET http://localhost:8081/clientes/1/ordenes`
7. **Cancelar el pedido** — `POST http://localhost:8083/pedidos/{id}/cancelacion`
   → estado `CANCELADO`, y el stock del paso 5 vuelve a su valor original.
8. **Cancelarlo otra vez** → **409** (ya está cancelado).

### Probar el flag `mask-pii`

1. `GET http://localhost:8081/clientes/1` → nombre y email normales.
2. En la consola de Statsig, **Feature Gates → mask-pii**, activarlo.
3. Repetir el GET (puede tardar unos segundos) → `"nombre": "J*** P***"`, `"email": "****@****.com"`.

No hace falta reiniciar el servicio: el flag se evalúa en cada request.

## 8. Job batch de expiración de pedidos

Los pedidos que quedan en estado `CREADO` más de **24 horas** se pasan a `EXPIRADO` y se libera
su stock en inventario. Lo hace un job de Spring Batch (un step *chunk*: reader → processor →
writer) dentro de `servicio-pedidos`.

- **Automático:** corre todas las noches a las **02:00** (hora de Argentina).
  Se configura en `servicio-pedidos/src/main/resources/application.yml` (`batch.expiracion.cron`)
  y se puede apagar con `batch.expiracion.scheduler-enabled: false`.
- **Manual, para la demo:** `POST http://localhost:8083/batch/expirar-pedidos` (sin body).
  Responde el resumen de la corrida:

  ```json
  { "estado": "COMPLETED", "pedidosLeidos": 1, "pedidosExpirados": 1, "pedidosOmitidosPorError": 0, ... }
  ```

- **Si inventario falla** al liberar el stock de un pedido, el job **reintenta hasta 3 veces**
  en la misma corrida. Si sigue fallando, ese pedido se saltea (`pedidosOmitidosPorError`), queda
  en `CREADO` y se vuelve a intentar en la próxima corrida. Los demás pedidos se procesan igual.
- **Sin duplicar:** la liberación de stock es idempotente (si se repite, inventario responde
  `reservasLiberadas: 0` y no devuelve unidades dos veces), y una nueva corrida solo toma los pedidos
  que siguen pendientes.
- **Una corrida a la vez:** si ya hay una corrida en curso (por ejemplo, la nocturna) y se pide
  otra, se rechaza con **409**.
- Al terminar, el servicio deja en el log un resumen de la corrida, por ejemplo:
  `Resumen corrida expirarPedidosJob [executionId=12, estado=COMPLETED, duracionMs=85, leidos=2, expirados=1, fallidos=1]`.
- Spring Batch guarda el historial de ejecuciones en las tablas `BATCH_*` de `pedidos_db`
  (las crea Flyway con la migración `V2`).

Más detalle en `servicio-pedidos/docs/batch-expiracion.txt`.

### Probar la expiración

1. En pgAdmin, sobre `pedidos_db` → **Query Tool**, crear un pedido "viejo":
   ```sql
   INSERT INTO pedidos (cliente_id, estado, monto_total, fecha_creacion, fecha_actualizacion)
   VALUES (1, 'CREADO', 10000.00, NOW() - INTERVAL '30 hours', NOW() - INTERVAL '30 hours')
   RETURNING id;
   ```
2. `POST http://localhost:8083/batch/expirar-pedidos` → `pedidosLeidos: 1`, `pedidosExpirados: 1`.
3. `GET http://localhost:8083/pedidos/{id}` → `"estado": "EXPIRADO"`.

> Después de probar, no dejar pedidos `CREADO` con más de 24 h en `pedidos_db`: los tests del
> batch cuentan todos los pedidos vencidos de la base y fallarían.

## 9. Observabilidad: logs, trazas y métricas

Cada request recibe un **traceId** (Micrometer Tracing) que aparece en todas sus líneas de log y
viaja entre servicios. Los logs se guardan en JSON y se centralizan en **Loki**; las métricas las
junta **Prometheus**; **Grafana** muestra todo.

1. Abrir **Docker Desktop** (esperar a que diga *Engine running*) y levantar el stack:
   ```bash
   cd observabilidad
   docker compose up -d
   ```
2. Levantar los servicios con el perfil `loki` (sin él, los logs no llegan a Grafana):
   ```bash
   ./gradlew :servicio-clientes:bootRun --args='--spring.profiles.active=loki'
   ```
   (igual para inventario y pedidos).
3. Cada respuesta trae el header **`X-Trace-Id`**. Copiarlo y pegarlo en Grafana
   (<http://localhost:3000>, usuario `admin` / `admin`) → **Dashboards → TiendaOnline →
   TiendaOnline - Logs**: aparecen todos los logs de esa request, de todos los servicios.

| Qué | Dónde |
|---|---|
| Grafana | <http://localhost:3000> |
| Prometheus (los 3 servicios tienen que estar `UP` en *Status → Targets*) | <http://localhost:9090> |
| Logs en archivo | `servicio-xxx/logs/servicio-xxx.json` |
| Métricas de un servicio | `http://localhost:808X/actuator/prometheus` |

Guía completa, consultas de LogQL/PromQL y demo: [`observabilidad/README.md`](./observabilidad/README.md).

Para apagar el stack: `docker compose down` (desde la carpeta `observabilidad`).

## 10. Tests

Con PostgreSQL corriendo (algunos tests de integración y de repositorio usan la base real):

```bash
./gradlew test                         # los tres servicios
./gradlew :servicio-clientes:test      # uno solo
```

- `BUILD SUCCESSFUL` = todo en verde. El detalle queda en
  `servicio-xxx/build/reports/tests/test/index.html`.
- Los tests de `servicio-clientes` corren con el perfil `test`, así que **no** necesitan la clave
  de Statsig.
- Herramientas: JUnit 5 + Mockito (unitarios), MockMvc (`@WebMvcTest`), `@SpringBootTest` y
  MockServer (integración entre servicios y con inventario en el batch).
- Los tests de integración usan las **mismas bases locales** que los servicios: crean sus datos y
  los borran al terminar, pero por eso avanzan los ids (ver sección 5).

> En VS Code, el botón ▶ de "Run Test" puede fallar con `NoClassDefFoundError:
> org/junit/platform/engine/OutputDirectoryCreator`. Es un problema de versiones de la extensión,
> no del proyecto: correr los tests con Gradle desde la terminal.

## 11. Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `Cannot find a Java installation on your machine matching ... languageVersion=17` | No hay JDK 17 instalado | Instalar Temurin 17 (ver Requisitos) y reabrir la terminal |
| `"." no se reconoce como un comando` | Terminal CMD | Usar `gradlew ...` sin `./`, o usar Git Bash |
| `gradlew: command not found` / no encuentra el archivo | La terminal no está en la raíz del repo | `cd` a la carpeta `TP_FB` |
| `FATAL: no existe la base de datos "clientes_db"` | Falta crear la base, o hay dos servidores PostgreSQL y se creó en el que no usa el puerto 5432 | Crear las 3 bases en el servidor del puerto 5432 (en pgAdmin: clic derecho en el servidor → Properties → Connection) |
| pgAdmin: `connection timeout expired` | El servidor PostgreSQL no está instalado o está detenido | Instalar el servidor, o iniciar el servicio `postgresql-x64-NN` en *Servicios* de Windows |
| `Falta configurar STATSIG_SERVER_SECRET` | No está la variable de entorno | Ver paso 3 |
| Aparecen carpetas `bin/` como cambios en Git | Las genera la extensión de Java de VS Code | No commitearlas (agregar `bin/` al `.gitignore`) |
| Archivos "modificados" sin cambios reales | Fines de línea de Windows | `git config core.autocrlf true` |
| `404 No existe un producto con id 1` | Los ids de los productos no son 1, 2, 3 | Consultar los ids reales con `SELECT id, sku FROM productos;` (sección 5) |
| `405 Method Not Allowed` | Método HTTP equivocado (por ejemplo POST en vez de GET) | Revisar el método en Postman |
| `failed to connect to the docker API ... dockerDesktopLinuxEngine` | Docker Desktop no está abierto | Abrir Docker Desktop y esperar *Engine running* |
| En Prometheus un servicio aparece `DOWN` | Ese servicio no está corriendo | Levantarlo; Prometheus reintenta solo |
| No aparecen logs en Grafana | El servicio no arrancó con el perfil `loki` | Levantarlo con `--args='--spring.profiles.active=loki'` |
| `409` al llamar a `POST /batch/expirar-pedidos` | Ya hay una corrida del job en curso | Esperar a que termine y volver a llamar |
| Fallan los tests del batch con números distintos a los esperados | Quedaron pedidos `CREADO` viejos en `pedidos_db` | Borrarlos o pasarlos a `EXPIRADO` |
