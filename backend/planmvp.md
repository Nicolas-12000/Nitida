# Nítida backend — plan del MVP y estado

> **Para agentes:** usar `superpowers:executing-plans` o `superpowers:subagent-driven-development`. Cada tarea empieza con su prueba fallando (TDD), termina con la prueba en verde y cierra con un commit.

**Contexto:** proyecto final de *Pruebas y Seguridad*. **Este repositorio entrega el backend**; el frontend (Next.js, spec §12) lo hace otra persona contra el contrato de la sección «Contrato con el frontend».

**Meta:** backend del MVP de Nítida, un SaaS para talleres de motos con historial firmado y verificable, con pipeline DevSecOps completo y evidencia de calidad: pruebas, mutación, ArchUnit, OWASP Top 10:2025 y SonarQube.

**Arquitectura:** monolito modular con Spring Modulith, Java 21 y Spring Boot 4.1. Cada caso de uso tiene un orquestador `*UseCase`. HTTP solo con `RouterFunction` y un handler delgado. El dominio no depende de nada externo. Cada build lo verifica con `ApplicationModules.verify()` y ArchUnit.

**Stack:** PostgreSQL + Flyway, Spring Security (sesión JDBC, CSRF, Argon2id), Ed25519 (JDK), JCS (RFC 8785), JUnit 5, jqwik, PIT, JaCoCo, ArchUnit, SonarQube, Testcontainers.

**Spec:** [`../Nitida-especificacion.md`](../Nitida-especificacion.md). Manda en negocio, seguridad y API.

## Restricciones globales (de la spec)

- El código va en inglés y los textos para la persona en español. Mapa de nombres: spec §4.1.
- `[REGLA DURA]` PII nunca en el historial (RN-22). Autorización en el backend. Fail closed en firma y verificación. Un serial rechazado no revela la moto ni el taller (RN-94). Los logs no llevan datos personales ni secretos (§13.3).
- Dinero en pesos enteros (`long`): cada línea entre 0 y 50.000.000. Kilometraje entre 0 y 999.999.
- Errores en Problem Details (RFC 9457) con un `code` estable en inglés. Un recurso de otro taller responde `404`.
- Java 21: la spec pedía 25 y se ajustó a 21 porque es el JDK instalado.

## Cómo trabajamos

- **Hasta T1 no hubo TDD estricto:** el código y sus pruebas salieron en el mismo commit. Aun así, las pruebas encontraron errores reales: fronteras de módulos, savepoints de JPA y el perfil de pruebas.
- **Desde T2 hacemos TDD:** prueba → verla fallar → mínimo código → verla pasar → commit. `CashdeskIntegrationTest` ya está escrita y en rojo.
- Usamos propiedades jqwik y modelos de referencia independientes (D4/D5) en las reglas con matemática o con máquina de estados.
- Antes de cada commit: `./gradlew build` en verde y la consola de VS Code (Problems) sin errores ni advertencias nuevas.

## Contrato con el frontend

El backend es la autoridad: el frontend repite las validaciones con zod solo para la experiencia de uso.

| Tema | Acuerdo |
|---|---|
| Base | Todo bajo `/api/v1`, mismo origen (Next reescribe `/api/*` hacia el backend). **Sin CORS.** |
| Sesión | Cookie de sesión `HttpOnly`, `Secure`, `SameSite=Lax` (Spring Session JDBC). Nada de JWT ni `localStorage`. |
| CSRF | Se lee la cookie `XSRF-TOKEN` y se reenvía en el encabezado `X-XSRF-TOKEN` en cada `POST`/`PATCH`/`DELETE`. Sin token → `403`. |
| Taller activo | Encabezado `X-Workshop-Id` en cada solicitud del taller; el backend lo valida contra las membresías del usuario. |
| Errores | Problem Details con `code`; el frontend traduce el `code` a un mensaje (DESIGN.md §9). Catálogo de `code` en `docs/api.md`. |
| Concurrencia | Las modificaciones envían `version`; si hay choque, el backend responde `409 VERSION_CONFLICT`. |
| Listados | Paginación por cursor. |
| Datos de prueba | `docker compose up` + perfil `dev` con semilla (T9), para que el frontend no dependa de nosotros. |

Entregable: **`docs/api.md`** (T4), escrito a mano. Por cada endpoint de la spec §9 incluye método, ruta, roles (§3.1), cuerpo, respuesta y `code` posibles. Una prueba falla si aparece una ruta que no está documentada.

## Estado por iteración de la spec (§17)

| It. | Módulos | Estado |
|---|---|---|
| 0 | Esqueleto, Gradle, CI, módulos + ArchUnit | **Hecha** (`.github/workflows/`) |
| 1 | `identity`, `workshops` | **Hecha** (RLS pendiente → T8) |
| 2 | `motorcycles`, `intake`, `files` | **Hecha** |
| 3 | `orders`, `catalog` (parte 1) | **Hecha**, con la integración HTTP y la matriz de `orders` (commit 485403d) |
| 4 | `history` | **Hecha** (JCS, cadena, firma, verificación, enmiendas, odómetro, links, concurrencia) |
| 5 | `cashdesk`, seriales globales | **Hecha** (T2): cuadre, alertas F1–F6, listeners y scheduler |
| 6 | Portal del propietario | **Hecha en el back** (T3): reclamar, traspasar, historial y links del propietario. E2E Playwright = frontend |

Calidad al último commit: 395 pruebas en verde; cobertura JaCoCo 96,5 % de líneas y 82 % de ramas (el build exige 90 % y 75 %); mutación PIT 91 % (el build exige 85 %); `Modulith verify`, ArchUnit (arquitectura + seguridad) y matriz de acceso en verde.

## Checklist del MVP contra la spec

Marcado = hecho y con prueba. Orden sugerido para la entrega: RLS → contrato → semilla → documentos de seguridad → TOTP/ADR → umbrales.

### Funcionalidad (§5, §6)
- [x] UC-01…24: orquestadores y rutas de taller, recepción, OT, historial, caja F1–F6, catálogo y seriales, portal del propietario
- [x] Reglas núcleo: estados de OT, pagos y anulación, entrega con código de reclamación, cadena JCS/Ed25519, enmiendas, odómetro, links, cuadre por día de Bogotá, alertas, seriales globales, evidencia, taller suspendido o pendiente dentro del contenido firmado
- [x] RN-82 traspaso firmado por la identidad de plataforma (sin nombres); el dueño anterior no ve entradas posteriores
- [ ] RN-14 cambio de placa por API (hoy solo `Motorcycle.changePlate()` + `plate_history` en dominio)
- [ ] RN-22 diagnóstico libre puede llevar PII escrita por el mecánico: documentar y pedir aviso en la UI

### Aislamiento entre talleres (§8.2) — el hueco más grande
- [x] Barrera de aplicación: `WorkshopContext` validado contra membresías + `404` para recursos de otro taller (`AccessMatrixTest`)
- [ ] RLS en PostgreSQL: `ENABLE ROW LEVEL SECURITY` + políticas por `app.workshop_id`, `SET LOCAL` por transacción
- [ ] Usuario de aplicación distinto al dueño de las tablas
- [ ] Pruebas Testcontainers: aislamiento con IDs ajenos; concurrencia de seriales y entregas sobre Postgres real

### Contrato HTTP (§9)
- [x] `docs/api.md` escrito a mano + `ApiContractTest` (falla si una ruta falta o sobra)
- [x] Catálogo de `code` de Problem Details para el frontend
- [ ] Paginación por cursor en listados (hoy: últimos 200 alertas/órdenes, 20 clientes)
- [x] OpenAPI/Swagger solo en `dev` (trabajo aparte; ver decisión 9)

### Autenticación y sesión (§10)
- [x] Argon2id, sesión JDBC 8 h, CSRF por cookie + encabezado, rotación de sesión, bloqueo progresivo, logout
- [x] Cookie `SESSION` con `HttpOnly` + `SameSite=Lax` (estaba sin ellas; corregido y probado en `OwaspSecurityTest`)
- [ ] TOTP obligatorio para admin / opcional para dueño, o ADR de aplazamiento
- [ ] Envío de correo de invitaciones por Mailpit (hoy el token vuelve una vez al dueño: decisión 8)
- [ ] Recuperación de contraseña (citada en §10, sin UC en §6)
- [x] Límite por IP detrás de proxy: `server.forward-headers-strategy=native` (solo proxies internos); `ForwardedHeadersTest`
- [ ] Límite al registro de usuarios (cada registro calcula un Argon2id: es la forma más barata de saturar la CPU)

### Seguridad y arquitectura (§13)
- [x] ArchUnit de capas + reglas de seguridad (sin `System.out`, solo `SecureRandom`, handlers sin acceso directo a BD, sin SQL nativo armado a mano, listeners en `application`, eventos `record`), cada regla con violación a propósito que demuestra que muerde
- [x] Matriz rol × endpoint de §3.1 (`AccessMatrixTest`, 61 casos)
- [x] Pruebas OWASP en ejecución (`OwaspSecurityTest`): encabezados, Actuator cerrado, cookie, Argon2id y tokens en hash, inyección, historial solo-agregar, logs sin PII, errores genéricos
- [x] `docs/owasp-top10.md`: categoría → control → prueba → archivo
- [x] `docs/amenazas.md` (STRIDE §13.1)
- [x] Bugs de concurrencia y robustez encontrados con pruebas: pagos simultáneos que superaban el total (`PaymentRaceIntegrationTest`), eventos fallidos que nadie reintentaba (`EventResubmission`), claves del limitador que nunca se liberaban, serial corregido que seguía bloqueando al verdadero

### Pruebas y calidad (§15)
- [x] P1–P8, P11, P12; D3, D4, D5; límites de §15.3; concurrencia de entregas y seriales
- [ ] P9 como `@Property` jqwik (hoy: caminata aleatoria con semilla en integración). Cuesta porque la suma vive en una consulta SQL, no en una función pura
- [ ] P10 como `@Property` jqwik (seriales en varias motos); mismo motivo
- [ ] D1 verificador independiente en Python; D2 Ed25519 JDK vs BouncyCastle
- [x] PIT 91 % global (umbral del build 85 %), `history.domain` ≥ 85 %, alcance `shared` + todos los `*.domain` (incluye `orders` y `cashdesk`)
- [x] JaCoCo 96,5 % de líneas y 82 % de ramas (umbrales del build 90 % y 75 %)

### CI/CD (§14)
- [x] `gradle-wrapper.jar` versionado (Actions fallaba con `Unable to access jarfile`)
- [x] Workflow sin `hashFiles` en `if` de job; detección de cambios (backend/frontend) para no correr PIT ni la imagen en vano
- [x] Paso de Sonar que de verdad corre cuando hay `SONAR_TOKEN` (antes la condición siempre era falsa) con Quality Gate
- [x] Check único `CI passed` para exigir en la protección de rama
- [ ] Configurar `SONAR_TOKEN` (secret) y `SONAR_HOST_URL` (variable) en GitHub, y captura del Quality Gate en verde
- [ ] Pasar `actionlint` (no se pudo correr local: sin Docker en WSL)
- [x] CI sin duplicados: un solo SAST (CodeQL), sin `dependency-review` (Trivy + verificación de Gradle lo cubren), SBOM solo de la imagen y solo en `main`, Trivy sin el escáner de secretos (gitleaks ya lo hace), caché de capas de Docker con la capa de dependencias separada, caché de pnpm, clon superficial cuando no corre Sonar, Dependabot agrupado
- [x] Los hallazgos de Trivy sobre la imagen salen como una anotación cada uno (legibles en la interfaz y por API)
- [ ] Los PR de Dependabot de Gradle fallan porque `verification-metadata.xml` no se actualiza solo: hay que correr `./gradlew --write-verification-metadata sha256 help` en la rama

### Documentación y datos (§16)
- [x] `README.md` (raíz): arranque, variables, cómo correr Sonar y PIT, firma = integridad, no no-repudio
- [x] `docs/decisiones/`: 13 ADRs
- [ ] Semilla del perfil `dev`: 2 talleres (uno verificado), 1 admin, 5 motos con historial

### Fuera del MVP (correcto que no estén)
Web Push, recordatorios por km, publicación de cabezas de cadena, mercado y precios, inventario de repuestos, catálogo compartido.

## Tareas pendientes (en orden)

### T0 — Consola limpia ✅
- `HttpStatus.UNPROCESSABLE_ENTITY` → `UNPROCESSABLE_CONTENT` y `isUnprocessableEntity()` → `isUnprocessableContent()` (ambos están obsoletos en Spring 7).
- Se quitaron los imports y campos sin uso (`AuthHandler`, `EntryContent`, `CreatePublicLinkUseCase.entries`, `VIN` en una prueba). Se agregó `Serial.getReason()`.
- Quedan solo los errores de `CashdeskIntegrationTest`, que es la prueba en rojo de T2.

### T2 — `cashdesk` (UC-17..19, spec §5.6) ✅
- Casos de uso: `CloseCashCountUseCase`, `ReopenCashCountUseCase`, `ResolveLeakAlertUseCase` y `EvaluateScheduledAlertsUseCase`, que corre cada 15 min con `@Scheduled` y genera F1 y F5.
- Listeners: `WorkOrderCancelled` → F2, desfase de caja → F3, `OrdersApi.inconsistentZeroTotalOrders()` → F4, `SerialRejected` → F6. Son idempotentes gracias a `dedupe_key`.
- Web: `CashdeskRoutes`/`CashdeskHandler` (rutas de §9). **Solo el dueño**: el mecánico y la recepción reciben `403`.
- **Pruebas:** P9 con jqwik (el esperado es Σ del efectivo no anulado). Límites de RN-52: diferencia igual a la tolerancia → BALANCED; tolerancia + 1 → UNBALANCED. Un pago a las 23:59:59 y otro a las 00:00:00 en `America/Bogota` caen en días distintos. RN-32: −1 min, exacto y +1 min. Un cuadre cerrado no se edita; al reabrirlo se exige un motivo y queda auditado.
- Commit: `feat(cashdesk): cash count, leak alerts F1–F6 and scheduler`.

### T3 — Portal del propietario (UC-20, UC-21, spec §5.9) ✅
- `ClaimMotorcycleUseCase`: código de 8 caracteres, un solo uso, vigencia de 30 días.
- `TransferOwnershipUseCase` y `AcceptOwnershipTransferUseCase`: generan una entrada `OWNERSHIP_TRANSFER` sin nombres.
- Rol `OWNER` en las rutas `/owner/**`.
- **Pruebas:** hay un solo propietario activo (índice único). Un código usado, vencido o inexistente da exactamente la misma respuesta. El dueño anterior pierde acceso a las entradas futuras.

### T4 — Contrato para el frontend ✅
- `docs/api.md` (ver «Contrato con el frontend»), con el catálogo completo de `code` → significado.
- **Prueba:** recorre los `RouterFunction` registrados y falla si alguna ruta no aparece en `docs/api.md`.
- Se le entrega al compañero del frontend junto con la colección de ejemplos (`curl`/HTTP file).

### T5 — Arquitectura: ArchUnit + Spring Modulith ✅
Ya existen en `ArchitectureTest`: `verify()`, sin `@RestController`, `@Transactional` solo en `application`, handlers sin transacción, dominio sin capas externas, handlers sin repositorios y nombres `*UseCase`.

Reglas por agregar:
- `web` solo depende de `application` del propio módulo y de `shared.web`.
- Sin `System.out`/`System.err`/`printStackTrace` (A09); sin `java.util.Random` en el código de producción, solo `SecureRandom` (§13.3).
- Sin `java.sql.Statement`/`createNativeQuery` con concatenación; solo consultas parametrizadas (A05).
- Los listeners de eventos (`@ApplicationModuleListener`) viven en `application`.
- Los eventos públicos de cada módulo son `record` inmutables.

**Pruebas:** cada regla nueva lleva un caso de violación a propósito (una clase de fixture en `src/test`) que demuestra que la regla falla. Además, `Documenter` regenera `docs/modulos/*.puml`.

### T6 — OWASP Top 10:2025 (spec §13.2) — pruebas ✅, falta el documento
Entregable: `docs/owasp-top10.md`, con una tabla **categoría → control → prueba automática → archivo**. Debe haber al menos una prueba por categoría:

| Cat. | Prueba |
|---|---|
| A01 Broken Access Control | Matriz parametrizada **rol × endpoint** de §3.1 (`2xx`/`403`/`404`) que cubre todos los módulos; IDs de otro taller → `404`; el servidor nunca visita URLs leídas de un QR (RN-95) |
| A02 Security Misconfiguration | Encabezados de seguridad presentes; Actuator expone solo `health`; perfil `prod` sin credenciales por defecto |
| A03 Supply Chain | CI: dependency verification de Gradle, SBOM, Trivy (por SHA + checksum) y Grype; actions fijadas por SHA |
| A04 Cryptographic Failures | Hash Argon2id; tokens guardados solo como hash; comparación en tiempo constante; nonce único en AES-GCM |
| A05 Injection | Búsqueda de clientes y placa con `' OR 1=1 --` y unicode raro → sin efecto y `422`; regla ArchUnit de T5 |
| A06 Insecure Design | Enmiendas en lugar de ediciones; fail closed en la firma |
| A07 Authentication Failures | Bloqueo progresivo por cuenta e IP; rotación del ID de sesión al iniciar sesión; contraseña común o de menos de 12 caracteres rechazada |
| A08 Integrity Failures | Alterar un byte de la cadena → `INVALID`; idempotencia de `OrderDelivered` |
| A09 Logging & Alerting | Un appender de captura comprueba que tras login, registro y cliente los logs no contienen contraseña, correo, teléfono, cédula, token ni código; una verificación `INVALID` deja log y alerta |
| A10 Exceptional Conditions | Excepción interna forzada → Problem Details genérico sin stack; llave o BD caída al verificar → `UNVERIFIABLE` |

En CI: ZAP baseline contra `docker compose`, sin alertas altas.

### T7 — SonarQube
- `sonar-project.properties` y el paso de CI con `sonar.qualitygate.wait=true` **ya existen**. Faltan el servidor y la evidencia.
- Opción local: `docker run -d -p 9000:9000 sonarqube:community`, crear el token y luego `./gradlew test jacocoTestReport` + `sonar-scanner` (o la acción del CI con `SONAR_TOKEN`/`SONAR_HOST_URL`). Otra opción es SonarCloud, gratis para repos públicos.
- **Quality Gate meta:** 0 bugs, 0 vulnerabilidades, 100 % de hotspots revisados (con justificación en Sonar), cobertura de código nuevo ≥ 80 %, duplicación < 3 %, calificación A en mantenibilidad, confiabilidad y seguridad.
- Se corrigen los *code smells* críticos y mayores; los falsos positivos se marcan con justificación, nunca con un `//NOSONAR` sin explicación.
- Evidencia para la entrega: captura del Quality Gate en verde y un enlace en el README.

### T8 — PostgreSQL real: RLS y usuario sin privilegios (spec §8.2, §15.6)
- Migración solo para Postgres (`db/postgres/`) con `ENABLE ROW LEVEL SECURITY` y políticas por `app.workshop_id`. El usuario de la aplicación es distinto del dueño de las tablas. Un AOP hace `SET LOCAL`.
- Pruebas con Testcontainers: aislamiento entre talleres aun enviando IDs ajenos; concurrencia de seriales (exactamente uno gana, el otro recibe un rechazo genérico + F6) y de entregas (`seq` sin duplicados).

### T9 — Cierre de calidad y documentación
- PIT: el alcance de §15.4 suma `cashdesk.domain`. Umbral de 70 → **75 %** global y meta de **85 %** en `history`. Cada mutante sobreviviente lleva una prueba o una nota de por qué es equivalente.
- Diferenciales: D2 (JDK vs. BouncyCastle) y D3 (vectores de RFC 8785). D1 (verificador en Python) si alcanza el tiempo.
- TOTP para el admin, o un ADR que lo aplaza a la fase 2.
- `README.md`: arranque, variables (`.env.example`), cómo correr Sonar y PIT, y la nota honesta de que la firma da integridad, no no-repudio.
- `docs/decisiones/` con los ADRs de la lista de abajo y `docs/amenazas.md` (STRIDE §13.1).
- Semilla `dev`: 2 talleres (uno verificado y uno pendiente), 1 admin y 5 motos con historial.

## Definición de terminado de la entrega

- [ ] `./gradlew build` en verde, incluidos `Modulith verify` y todas las reglas ArchUnit.
- [ ] Consola de VS Code sin errores ni advertencias.
- [ ] Cobertura JaCoCo ≥ 85 % de líneas; PIT ≥ 75 % (85 % en `history`).
- [ ] Propiedades P1–P12 y límites de §15.3 cubiertos.
- [ ] Quality Gate de SonarQube en verde, con captura.
- [ ] `docs/owasp-top10.md` completo, con una prueba por categoría.
- [ ] Pipeline completo en verde: gitleaks, Semgrep/CodeQL, SBOM, Trivy + Grype, ZAP, cosign.
- [ ] `docs/api.md` entregado al frontend y la semilla `dev` funcionando con `docker compose up`.

## Foco de revisión (lo que la spec implica y ninguna tarea prueba todavía)

- El diagnóstico libre puede llevar datos personales escritos por el mecánico (RN-22). Hoy no hay filtro: documentarlo y pedirle al frontend un aviso en la UI.
- Si el taller queda suspendido entre el pago y el evento de entrega, la entrada se registra igual. Hay que confirmar el criterio en un ADR.
- `X-Forwarded-For` detrás de un proxy: el límite por IP hoy usa `remoteAddr`.
- Reintento de listeners cuando la cadena aún no tiene `INITIAL_REGISTRATION`: se reintenta, pero falta una prueba explícita.
- Rotación de la KEK (`kek_version`) sin prueba.

## Decisiones que se apartan de la spec (a registrar como ADR)

1. `OrderDelivered` vive en `shared.events` para evitar el ciclo `orders ↔ history`.
2. `orders` cierra la recepción llamando a `IntakeApi` en la misma transacción, en lugar de un evento asíncrono.
3. `orders` libera seriales al cancelar llamando a `CatalogApi`, para evitar el ciclo `catalog → orders`.
4. Los ajustes de `workshops` son columnas tipadas, no `settings_jsonb`.
5. El contenido del historial se guarda como texto canónico JCS (exactamente lo que se hashea), no como `jsonb`.
6. Los índices únicos parciales de seriales y de propietario activo se emulan con una columna anulable + `UNIQUE` (portable entre H2 y Postgres).
7. Límite de intentos en memoria propio (`RateLimiter`) en lugar de Bucket4j; SBOM con Trivy en lugar del plugin CycloneDX.
8. Las invitaciones le devuelven el token al dueño una sola vez (por ahora sin correo ni Mailpit).
9. Contrato HTTP en `docs/api.md` (prosa, comprobada por `ApiContractTest`); OpenAPI y Swagger UI solo con el perfil `dev`, cerrados en producción y en las pruebas.
10. `OWNERSHIP_TRANSFER` lo firma una identidad de plataforma (`WorkshopsApi.PLATFORM_ID`, migración `V13`, llave creada al arrancar), porque ningún taller hizo el traspaso.
