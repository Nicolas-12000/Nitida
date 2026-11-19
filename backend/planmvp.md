# Nítida backend — plan del MVP y estado

> **Para agentes:** usar `superpowers:executing-plans` o `superpowers:subagent-driven-development`. Cada tarea abre con su prueba que falla (TDD), cierra con la prueba en verde y un commit.

**Meta:** backend del MVP de Nítida: SaaS de taller de motos con historial firmado y verificable, con pipeline DevSecOps completo.

**Arquitectura:** monolito modular (Spring Modulith) en Java 21 y Spring Boot 4.1; un orquestador `*UseCase` por caso de uso; HTTP solo con `RouterFunction` + handler delgado; dominio sin dependencias hacia afuera. Verificado en cada build con `ApplicationModules.verify()` y ArchUnit.

**Stack:** PostgreSQL + Flyway, Spring Security (sesión JDBC, CSRF, Argon2id), Ed25519 (JDK), JCS (RFC 8785), JUnit 5, jqwik, PIT, JaCoCo, Testcontainers (al final).

**Spec:** [`../Nitida-especificacion.md`](../Nitida-especificacion.md) (manda en negocio, seguridad y API).

## Restricciones globales (de la spec)

- Código en inglés; textos para la persona en español. Mapa de nombres: spec §4.1.
- `[REGLA DURA]` PII nunca en el historial (RN-22); autorización en el backend; fail closed en firma y verificación; el serial rechazado no revela moto ni taller (RN-94); logs sin datos personales ni secretos.
- Dinero en pesos enteros (`long`), línea 0..50.000.000; km 0..999.999.
- Errores: Problem Details (RFC 9457) con `code` estable en inglés. 404 para recursos de otro taller.
- Java 21 (la spec se ajustó de 25 a 21 porque es el JDK instalado).

## Cómo trabajamos (honesto sobre TDD)

- **Lo hecho hasta ahora no fue TDD estricto.** Se siguió el plan de iteraciones de la spec §17 *de memoria*, sin este archivo. El código de cada módulo se escribió y **sus pruebas salieron en el mismo commit**, no antes. Las pruebas sí encontraron errores reales (violaciones de frontera de módulos, savepoints de JPA, perfil de pruebas).
- **Desde aquí:** cada tarea nueva empieza escribiendo la prueba, verla fallar, implementar lo mínimo, verla pasar, commit.
- Propiedades jqwik y modelos de referencia independientes (D4/D5) para las reglas con matemática o máquina de estados.

## Estado por iteración de la spec

| It. | Módulos | Estado |
|---|---|---|
| 0 | Esqueleto, Gradle verificado, CI, módulos + ArchUnit | **Hecha** (CI en `.github/workflows/`) |
| 1 | `identity`, `workshops` | **Hecha** (RLS pendiente, ver T7) |
| 2 | `motorcycles`, `intake`, `files` | **Hecha** |
| 3 | `orders`, `catalog` (parte 1) | **Código y pruebas de dominio hechos**; falta prueba de integración HTTP (T1) |
| 4 | `history` | **Hecha** (JCS, cadena, firma, verificación, enmiendas, odómetro, links, concurrencia) |
| 5 | `cashdesk`, seriales globales | Seriales **hechos**; `cashdesk` **pendiente** (T2) |
| 6 | Portal del propietario | Tablas y `issueClaimCode` hechos; casos de uso **pendientes** (T3) |

Estado de calidad al último commit: ~200 pruebas en verde, cobertura de líneas ~92 %, mutación PIT 77 % (umbral 70), `Modulith verify` y reglas ArchUnit en verde.

## Tareas pendientes (en orden)

### T1 — Integración HTTP de `orders` (entrega → historial)
- **Prueba primero** (`OrdersIntegrationTest`): flujo recepción → OT → repuesto con código → serial → completar → pago → entrega; la entrega devuelve el código de reclamación **una sola vez**, aparece una entrada `SERVICE` y el link público da `VALID`.
- Casos: regresión de km → `422 MILEAGE_REGRESSION`; mecánico no registra pagos (`403`); recepción no abre OT (`403`); OT de otro taller `404`; serial duplicado en otra moto → `200 accepted=false` y evento `SerialRejected`; cancelar libera seriales.
- Commit: `test(orders): end-to-end order flow and access matrix`.

### T2 — `cashdesk` (UC-17..19)
- Tablas `cash_count`, `leak_alert` (migración `V12`). Casos de uso: `CloseCashCountUseCase`, `ReopenCashCountUseCase`, `ResolveLeakAlertUseCase`, `EvaluateScheduledAlertsUseCase` (cada 15 min: F1, F5).
- Listeners: `WorkOrderCancelled` → F2, `SerialRejected` → F6, desfase de caja → F3, F4 por `OrdersApi.inconsistentZeroTotalOrders`.
- **Pruebas primero:** P9 (esperado = Σ efectivo no anulado), límite tolerancia (= tolerancia balanced, +1 desbalanceado), día en `America/Bogota` (23:59:59 vs 00:00:00), umbral F1 (−1 min, exacto, +1 min), cuadre cerrado no se edita, reabrir con motivo auditado.

### T3 — Portal del propietario (UC-20, UC-21)
- `ClaimMotorcycleUseCase` (código de 8 caracteres, un uso, 30 días), `TransferOwnershipUseCase` / `AcceptOwnershipTransferUseCase` (entrada `OWNERSHIP_TRANSFER` sin nombres).
- **Pruebas primero:** un solo propietario activo (índice único), código usado/vencido/ inexistente indistinguibles, el dueño anterior pierde acceso a entradas futuras.

### T4 — Matriz de acceso rol × endpoint y seguridad OWASP
- Prueba parametrizada de toda la matriz de spec §3.1 (`2xx`/`403`/`404`).
- Un test por categoría de spec §13.2 (A01..A10); documento `docs/owasp-top10.md` que mapea control → prueba → archivo.

### T5 — PostgreSQL real: RLS, usuario sin privilegios (Docker al final)
- Migración Postgres-only (`db/postgres/`) con `ENABLE ROW LEVEL SECURITY` + políticas por `app.workshop_id`; usuario de aplicación distinto al dueño de las tablas; AOP que hace `SET LOCAL`.
- Pruebas Testcontainers: aislamiento entre talleres aun enviando IDs ajenos; concurrencia de seriales y entregas sobre Postgres real.

### T6 — Documentación y datos de desarrollo
- `README.md` (arranque, variables, honestidad de la firma: integridad ≠ no repudio), `docs/decisiones/` (ADRs: `OrderDelivered` en `shared.events`, serial por savepoint JDBC, límites en memoria en lugar de Bucket4j, SBOM con Trivy), `docs/amenazas.md`.
- Semilla de dev (2 talleres, 1 admin, 5 motos con historial) en perfil `dev`.

### T7 — Cierre de calidad
- Subir umbral PIT hacia 75–85 % (`history`), TOTP para admin (o ADR de aplazamiento), `./gradlew build` y pipeline completo en verde.

## Foco de revisión (lo que la spec implica y aún no prueba una tarea)

- Diagnóstico libre con datos personales escritos por el mecánico (RN-22): hoy no hay filtro; documentar y valorar un aviso en la UI.
- Entrega con taller suspendido entre el pago y el evento: la entrada se registra igual; confirmar el criterio en un ADR.
- `X-Forwarded-For` detrás de proxy: el límite por IP hoy usa `remoteAddr`.
- Reintento de listeners con la cadena sin `INITIAL_REGISTRATION` todavía: se reintenta, pero falta prueba explícita.
- Rotación de la KEK (`kek_version`) sin prueba de rotación.

## Decisiones ya tomadas que se apartan de la spec (a registrar como ADR)

1. `OrderDelivered` vive en `shared.events` para evitar el ciclo `orders ↔ history`.
2. `orders` cierra la recepción llamando a `IntakeApi` en la misma transacción (en vez de evento asíncrono).
3. `orders` libera seriales al cancelar llamando a `CatalogApi` (evita ciclo `catalog → orders`).
4. Ajustes de `workshops` como columnas tipadas, no `settings_jsonb`.
5. Contenido de historial guardado como texto canónico JCS (exactamente lo hasheado), no `jsonb`.
6. Índice único parcial de seriales y de propietario activo emulados con columna anulable + `UNIQUE` (portable H2/Postgres).
7. Límite de intentos en memoria propio (`RateLimiter`) en vez de Bucket4j; SBOM con Trivy en vez de plugin CycloneDX.
8. Invitaciones devuelven el token una vez al dueño (sin correo/Mailpit por ahora).
