# Billing API

API REST de facturación con **Java 17 + Spring Boot 3.5**: JWT, migraciones Flyway, pagos idempotentes, bloqueo optimista y errores en formato **Problem Details (RFC 9457)**.

[![CI](https://github.com/HazSy/billing-api/actions/workflows/ci.yml/badge.svg)](https://github.com/HazSy/billing-api/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Tests](https://img.shields.io/badge/tests-25%20passing-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

> Tercer proyecto del portfolio, centrado en el stack que domina la empresa española (Java + Spring). El objetivo no era otro CRUD: era resolver los tres problemas que de verdad rompen una API de facturación —dinero con redondeos, cobros duplicados y ediciones concurrentes.

---

## Qué demuestra

| Problema real | Cómo se resuelve aquí |
| :--- | :--- |
| **Dinero con decimales** | `BigDecimal` con escala 2 y `HALF_UP`; el IVA se redondea por línea y después se suma (así lo exige la factura), con tests que cubren los casos límite. |
| **Cobros duplicados por reintentos** | Cabecera `Idempotency-Key` + índice único en `payments`. Repetir la petición devuelve el pago original con `replayed: true`. |
| **Dos usuarios editando la misma factura** | `@Version` (bloqueo optimista): la segunda escritura recibe `409` en lugar de pisar datos. |
| **Esquema que se desincroniza** | Flyway manda; Hibernate solo valida (`ddl-auto: validate`). |
| **Errores que el cliente no puede interpretar** | `ProblemDetail` con `code`, `detail` y `errors` por campo. |
| **Seguridad casera** | Spring Security como *resource server* OAuth2 con JWT HS256 y roles por método (`@PreAuthorize`). |

## Endpoints

| Método | Ruta | Rol | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/token` | público | Devuelve un JWT (1 h) |
| `GET` | `/api/customers` | ADMIN, ACCOUNTANT | Lista clientes |
| `POST` | `/api/customers` | ADMIN, ACCOUNTANT | Crea cliente (email y CIF únicos) |
| `GET` | `/api/invoices?status=` | ADMIN, ACCOUNTANT | Lista facturas, filtro por estado |
| `GET` | `/api/invoices/{id}` | ADMIN, ACCOUNTANT | Detalle con pagos acumulados |
| `POST` | `/api/invoices` | ADMIN, ACCOUNTANT | Crea borrador y calcula totales |
| `POST` | `/api/invoices/{id}/issue` | ADMIN, ACCOUNTANT | Emite el borrador |
| `POST` | `/api/invoices/{id}/void` | **ADMIN** | Anula una factura no pagada |
| `POST` | `/api/invoices/{id}/payments` | ADMIN, ACCOUNTANT | Pago idempotente (`Idempotency-Key`) |
| `GET` | `/actuator/health` | público | Estado del servicio |
| `GET` | `/docs` | público | Swagger UI |

## Arrancar

Requisitos: **JDK 17+** y **Maven 3.9+**.

```bash
mvn spring-boot:run
# API en http://localhost:8080 · Swagger en http://localhost:8080/docs
```

Usuarios de demo (en memoria, solo para desarrollo):

| Usuario | Contraseña | Rol |
| :--- | :--- | :--- |
| `admin` | `admin12345` | ADMIN |
| `contable` | `contable12345` | ACCOUNTANT |

Prueba rápida:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Acme SL","email":"facturas@acme.es","taxId":"B12345678"}'
```

El archivo [`api.http`](api.http) reproduce el flujo completo desde VS Code (REST Client) o IntelliJ.

## Tests

```bash
mvn test     # 25 tests
```

- **6** de cálculo de importes (`InvoiceCalculatorTest`): redondeo, tipos de IVA mezclados, independencia del orden.
- **8** de la máquina de estados (`InvoiceTest`): emitir, anular, pagos parciales, sobrepago, borrador.
- **5** del servicio con Mockito (`InvoiceServiceTest`): numeración, cliente inexistente, idempotencia, clave reutilizada.
- **6** de integración (`BillingApiIntegrationTest`): Spring completo + H2 + Flyway, flujo real con JWT, 401, 404, 409 y 400.

## Decisiones técnicas

1. **El total se calcula, no se confía.** El servidor recalcula neto, IVA y total a partir de las líneas; el cliente nunca envía importes.
2. **Redondeo por línea.** Redondear al final daría un céntimo de diferencia con la factura impresa. Hay un test que fija ese comportamiento.
3. **Idempotencia en la base de datos**, no solo en memoria: el índice único de `idempotency_key` es la última línea de defensa ante dos peticiones simultáneas.
4. **Estados explícitos con métodos que validan.** `InvoiceStatus` responde `canBeIssued()`, `acceptsPayments()`…; la entidad lanza `ApiException` con el código HTTP correcto en lugar de dejar campos inconsistentes.
5. **H2 en modo PostgreSQL para desarrollo**, sin contenedores. El SQL de Flyway es compatible con PostgreSQL: cambiar `spring.datasource.url` basta para usar el motor real.
6. **Validación en el borde.** Jakarta Validation en los DTO `record`; los errores se agrupan por campo en la respuesta.

## Estructura

```text
src/main/java/dev/hazsy/billing/
├─ auth/          Emisión del token JWT
├─ config/        Seguridad, OpenAPI y propiedades tipadas
├─ common/        ApiException + manejador global de errores
├─ customer/      Entidad, repositorio y controlador
├─ invoice/       Dominio, cálculo puro, servicio y controlador
└─ payment/       Entidad y repositorio de pagos

src/main/resources/
├─ application.yml
└─ db/migration/V1__init.sql
```

## Despliegue

```bash
docker build -t billing-api .
docker run -p 8080:8080 -e JWT_SECRET="una-clave-de-al-menos-32-bytes" billing-api
```

En producción: `JWT_SECRET` por variable de entorno, PostgreSQL real y los usuarios en base de datos con el hash BCrypt.

## Hoja de ruta

- [ ] Testcontainers con PostgreSQL en un perfil de CI dedicado.
- [ ] Abonos (notas de crédito) enlazados a la factura original.
- [ ] Envío del PDF por correo al emitir.
- [ ] Paginación con `Pageable` en los listados.
- [ ] Métricas de negocio en Micrometer (facturas emitidas, tiempo medio de cobro).

## Licencia

MIT © Óscar López Pellicer
