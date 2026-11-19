# API HTTP de Nítida — contrato para el frontend

Fuente de verdad en prosa: este archivo. El contrato máquina es **`docs/openapi.yaml`** (OpenAPI 3.0.3); el build lo copia al jar y, solo con el perfil `dev`, Swagger UI lo sirve en `/openapi.yaml`.

`ApiContractTest` y `OpenApiContractTest` fallan si una ruta del backend no está documentada (o sobra). Swagger UI: [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html) con `docker compose up` o `SPRING_PROFILES_ACTIVE=dev`. En pruebas, en el DAST del CI y sin ese perfil, esas rutas quedan cerradas. Las reglas de negocio están en [`../Nitida-especificacion.md`](../Nitida-especificacion.md); el backend es la autoridad y el frontend repite las validaciones solo para dar respuesta inmediata.

## Convenciones

| Tema | Acuerdo |
|---|---|
| Base | `/api/v1`, JSON, UTF-8. El frontend llama al **mismo origen** (Next reescribe `/api/*` hacia el backend). No hay CORS. |
| Sesión | Cookie `SESSION` (`HttpOnly`, `SameSite=Lax`, `Secure` salvo en desarrollo local por HTTP). Se crea con `POST /auth/login`. Sin token en `localStorage`. Expira a las 8 h de inactividad. |
| CSRF | Cada respuesta trae la cookie `XSRF-TOKEN` (legible por JS). En **todo** `POST`/`PATCH`/`PUT`/`DELETE` se reenvía su valor en el encabezado `X-XSRF-TOKEN`. Sin él: `403`. |
| Taller activo | Las rutas marcadas **T** exigen el encabezado `X-Workshop-Id: <uuid>`. Si la persona no es miembro de ese taller el backend responde `404` (nunca `403`, para no revelar que existe). |
| Roles | `O` dueño · `M` mecánico · `R` recepción · `A` administrador de la plataforma · `U` cualquier persona con sesión · `P` público sin sesión. La matriz completa (spec §3.1) la prueba `AccessMatrixTest`. |
| Errores | Problem Details (RFC 9457, `application/problem+json`): `{type, title, status, code, traceId}`. Se traduce el `code` (estable, en inglés) a un mensaje en el frontend; `title` está en español pero puede cambiar. Nunca hay trazas ni detalles internos. |
| Estados | `400` JSON o parámetro mal formado · `401` sin sesión · `403` rol sin permiso · `404` no existe **o es de otro taller** · `409` conflicto (`VERSION_CONFLICT`, estado) · `422` regla de negocio o validación · `423` taller suspendido · `429` demasiados intentos. |
| Versiones | Quien edita una orden, los ajustes del taller o entrega manda `version` (la última que leyó). Si cambió: `409 VERSION_CONFLICT`; se vuelve a leer y se reintenta. |
| Dinero | Pesos colombianos **enteros** (`long`), sin decimales. Línea entre 0 y 50.000.000. |
| Kilometraje | Entero entre 0 y 999.999. |
| Fechas | Instantes en ISO 8601 UTC (`2026-11-19T15:00:00Z`). Los días de caja son `AAAA-MM-DD` en `America/Bogota`. El frontend formatea con `Intl` `es-CO`. |
| Códigos secretos | `claimCode`, `token` de invitación, de link y de traspaso se devuelven **una sola vez**; el backend guarda solo su hash. Mostrarlos y no volver a pedirlos. |
| Listados | `GET /orders` y `GET /cashdesk/alerts` paginan por cursor: `?limit=` (1–100, 50 por defecto) y `?cursor=`; la respuesta sigue siendo un arreglo y, si hay más, el encabezado `X-Next-Cursor` trae el cursor de la siguiente página (opaco: no se arma ni se reutiliza). Más recientes primero. Los demás listados devuelven todo lo abierto (recepciones) o un máximo fijo (clientes 20). |

## Endpoints

Columnas: **Ctx** `T` = lleva `X-Workshop-Id` · **Roles** quién puede.

### Autenticación y taller

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `POST /api/v1/auth/register` | | P | `{email, password (12–128, no común), displayName}` | `201` `{userId}`; `409 EMAIL_TAKEN`, `422 WEAK_PASSWORD / INVALID_EMAIL` |
| `POST /api/v1/auth/login` | | P | `{email, password, totp?}` | `200` igual que `/auth/me` + cookie `SESSION`; `401 TOTP_REQUIRED` si la cuenta tiene TOTP y falta `totp` (solo con la contraseña correcta); `401 INVALID_CREDENTIALS` (misma respuesta para usuario inexistente), `429 RATE_LIMITED` tras 5 fallos |
| `GET /api/v1/auth/me` | | U | — | `{id, email, displayName, platformAdmin, totpEnabled, totpSetupRequired, memberships:[{workshopId, role}]}`. `totpSetupRequired`: administrador que aún no activó TOTP (inicia sesión, pero sin privilegios de administrador) |
| `POST /api/v1/auth/totp/setup` | | U | — | `{secret, otpauthUri}`: secreto Base32 y enlace para el QR, **una sola vez**; `409 TOTP_ALREADY_ENABLED` |
| `POST /api/v1/auth/totp/confirm` | | U | `{code}` (6 dígitos) | `204`; activa TOTP y, si es administrador, le da sus privilegios en esta sesión; `422 INVALID_TOTP`, `409 TOTP_NOT_STARTED`, `429` tras 5 intentos |
| `POST /api/v1/workshops` | | U | `{name, documentNumber, address, municipality, phone, facadePhotoId?}` | `201` `{workshopId}`; quien lo crea queda como dueño |
| `GET /api/v1/workshops/mine` | | U | — | talleres de la persona |
| `GET /api/v1/workshops/{id}` | T | O M R | — | datos y ajustes del taller (`intakeAlertHours`, `cashTolerance`, `stalledDays`, `version`, estado) |
| `PATCH /api/v1/workshops/{id}/settings` | T | O | `{intakeAlertHours 1–168, cashTolerance ≥ 0, stalledDays 1–90, version}` | ajustes actualizados |
| `POST /api/v1/workshops/{id}/invitations` | T | O | `{email, role: MECHANIC\|FRONT_DESK}` | `201` `{token}` (una vez) |
| `GET /api/v1/workshops/{id}/members` | T | O | — | miembros |
| `DELETE /api/v1/workshops/{id}/members/{userId}` | T | O | — | `204`; `409 LAST_OWNER` si es el único dueño |
| `POST /api/v1/invitations/{token}/accept` | | U | — | `{workshopId}`; el correo debe coincidir con el de la invitación |
| `GET /api/v1/admin/workshops` | | A | `?status=` | talleres de la plataforma |
| `POST /api/v1/admin/workshops/{id}/status` | | A | `{status: VERIFIED\|SUSPENDED\|PENDING_VERIFICATION, evidence}` | `{status}` |
| `GET /api/v1/public/keys` | | P | — | `{keys:[…]}` llaves públicas Ed25519 (formato tipo JWKS) |

`POST /api/v1/auth/logout` lo atiende Spring Security: `204` y la sesión queda invalidada.

### Recepción, clientes y motos

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `POST /api/v1/intakes` | T | O M R | `{plate? , vin?, brand?, line?, modelYear?, displacementCc?, mileage, odometerPhotoId, motorcyclePhotoId}` (placa o VIN) | `201` `{intakeId, motorcycleId, newMotorcycle}`; `422 INVALID_PLATE / INVALID_VIN / MILEAGE_REGRESSION`, `409 INTAKE_ALREADY_OPEN` |
| `GET /api/v1/intakes` | T | O M R | — | recepciones abiertas `[{id, motorcycleId, plate, brand, line, mileage, createdAt}]` |
| `POST /api/v1/intakes/{id}/discard` | T | O M R | `{reason: CONSULTATION_ONLY\|CUSTOMER_WITHDREW\|ENTRY_ERROR}` | `204` |
| `POST /api/v1/customers` | T | O R | `{fullName, phone, nationalId?, email?, consentAccepted, policyVersion, consentChannel: IN_PERSON\|…}` | `201` `{customerId}`; `422 CONSENT_REQUIRED / STALE_POLICY` |
| `GET /api/v1/customers` | T | O M R | `?q=` (mínimo 2 caracteres, máx. 20 resultados) | `[{id, fullName, phone, nationalId, email}]`; `422 QUERY_TOO_SHORT` |
| `PATCH /api/v1/customers/{id}` | T | O R | `{fullName, phone, nationalId, email}` | cliente actualizado |
| `POST /api/v1/customers/{id}/erasure` | T | O | — | `204`; anonimiza (Ley 1581), el historial no cambia |
| `GET /api/v1/motorcycles/lookup` | T | O M R | `?plate=` o `?vin=` | moto o `404` |
| `PATCH /api/v1/motorcycles/{id}/plate` | T | O R | `{plate}` | `204`. RN-14: la moto conserva su id y la placa anterior queda en el historial de placas; solo un taller que la recibió (otro → `404`); `422 INVALID_PLATE`, `409 PLATE_IN_USE` |

### Órdenes de trabajo, repuestos y pagos

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `POST /api/v1/orders` | T | O M | `{intakeId, customerId?, diagnosis (10–2000), mechanicId?, warrantyOfOrderId?}` | `201` `{orderId, motorcycleId, mileage}` |
| `GET /api/v1/orders` | T | O M R | `?status=OPEN,IN_PROGRESS,…&limit=1..100&cursor=` | tablero de órdenes |
| `GET /api/v1/orders/{id}` | T | O M R | — | detalle (trabajos, repuestos, pagos, `total`, `version`) |
| `PATCH /api/v1/orders/{id}` | T | O M | `{version, diagnosis?, laborCost?, warrantyDays?, warrantyKm?, jobs:[{type: OIL_CHANGE\|DRIVE_CHAIN_KIT\|BRAKES\|SUSPENSION\|ELECTRICAL\|ENGINE\|TIRES\|TUNE_UP\|OTHER, description}]}` | `204`; `409 VERSION_CONFLICT`, `409 ORDER_NOT_EDITABLE` |
| `POST /api/v1/orders/{id}/transitions` | T | O M | `{status, reason?, version}` (`OPEN→IN_PROGRESS→COMPLETED`; `CANCELLED` pide `reason`) | `{status}`; `422 INVALID_TRANSITION` |
| `POST /api/v1/orders/{id}/parts` | T | O M | `{name, reference?, brand, condition: OEM\|AFTERMARKET\|USED, quantity 1–999, unitPrice, productCode?, replacementPhotoId?}` | `201` `{partId}` |
| `DELETE /api/v1/orders/{id}/parts/{partId}` | T | O M | — | `204` |
| `POST /api/v1/orders/{id}/parts/{partId}/serial` | T | O M | `{serial, reactivationReason?}` | `{accepted, evidenceLevel, message}`. Un serial ya activo en otra moto responde `200 accepted=false` con mensaje genérico (no revela moto ni taller) |
| `POST /api/v1/orders/{id}/parts/{partId}/authenticity` | T | O M | `{photoId, method}` | `{evidenceLevel}` |
| `POST /api/v1/orders/{id}/photos` | T | O M | `{fileId, moment: BEFORE\|AFTER}` | `201` |
| `POST /api/v1/orders/{id}/payments` | T | O R | `{method: CASH\|TRANSFER\|CARD\|STORE_CREDIT, amount}` | `201` `{paymentId}`; `422 OVERPAYMENT` |
| `POST /api/v1/payments/{id}/void` | T | O R | `{reason}` | `204`; `409 PAYMENT_ALREADY_VOIDED` |
| `POST /api/v1/orders/{id}/delivery` | T | O R | `{version}` | `{orderId, total, claimCode, claimCodeExpiresAt}`. **El código sale una sola vez** (`Cache-Control: no-store`); se imprime en el recibo. `422 INCOMPLETE_PAYMENTS` |

### Catálogo, seriales y archivos

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `GET /api/v1/catalog/products/{code}` | T | O M | — | producto del catálogo (EAN/UPC con dígito verificador) o `404`; `422 INVALID_PRODUCT_CODE` |
| `POST /api/v1/catalog/products` | T | O M | `{code, name, brand, reference?}` | `201` producto propuesto |
| `POST /api/v1/serials/{id}/retire` | T | O M | `{reason (5–200)}` | `204` |
| `POST /api/v1/files` | T | O M R | multipart, campo `file` (JPEG/PNG/WebP, máx. 8 MB) | `201` `{fileId}`; el servidor re-codifica y quita EXIF |
| `GET /api/v1/files/{id}` | T | O M R | — | la imagen, solo si es del taller activo |

### Historial firmado y links públicos

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `GET /api/v1/motorcycles/{id}/history` | T | O M R | — | `{motorcycle, entries[], verification:{status: VALID\|INVALID\|UNVERIFIABLE, …}, evidence, transfers}`; solo motos que el taller recibió |
| `POST /api/v1/motorcycles/{id}/history/amendments` | T | O M | `{amendsSeq, reason, diagnosis?, mileage?}` | `201`; solo el taller que creó la entrada original |
| `POST /api/v1/motorcycles/{id}/history/odometer-changes` | T | O M | `{mileage, reason, photoId}` | `201` |
| `POST /api/v1/motorcycles/{id}/links` | T | O R | `{days? 1–365, por defecto 30}` | `201` `{linkId, token, expiresAt}` (token una vez) |
| `DELETE /api/v1/links/{id}` | T | O R | — | `204` |
| `GET /api/v1/public/v/{token}` | | P | — | historial verificado sin datos personales ni precios; `404 LINK_NOT_AVAILABLE` igual para token inexistente, vencido o revocado; `429` por IP |

### Caja y alertas (solo dueño)

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `GET /api/v1/cashdesk/counts/{day}` | T | O | — | `{day, expected, counted, difference, status: OPEN\|BALANCED\|UNBALANCED, closedAt, reopenReason}`; `expected` = efectivo no anulado del día |
| `POST /api/v1/cashdesk/counts/{day}/close` | T | O | `{counted}` | cuadre cerrado; `409 CASH_COUNT_CLOSED`, `422 FUTURE_DAY`; si hay desfase genera alerta F3 |
| `POST /api/v1/cashdesk/counts/{day}/reopen` | T | O | `{reason (10–500)}` | cuadre abierto; queda auditado |
| `GET /api/v1/cashdesk/alerts` | T | O | `?status=PENDING\|RESOLVED&limit=1..100&cursor=` | `[{id, type, referenceId, details, createdAt, resolvedAt, comment}]` con `type` F1…F6 |
| `POST /api/v1/cashdesk/alerts/{id}/resolve` | T | O | `{comment (5–500)}` | `204`; `409 ALERT_ALREADY_RESOLVED`. Las alertas no se borran |

### Portal del propietario (cualquier persona con sesión, sin taller)

| Ruta | Ctx | Roles | Cuerpo | Respuesta |
|---|---|---|---|---|
| `POST /api/v1/owner/claims` | | U | `{code}` (8 caracteres del recibo; no distingue mayúsculas) | `{motorcycleId}`; `404` igual para código usado, vencido o inexistente; `409 ALREADY_OWNED`; `429` tras 10 intentos |
| `GET /api/v1/owner/motorcycles` | | U | — | `[{motorcycleId, plate, brand, line, modelYear, active, since, until}]` (incluye las que ya vendió) |
| `GET /api/v1/owner/motorcycles/{id}/history` | | U | — | mismo formato que el del taller; quien ya vendió la moto no ve lo registrado después |
| `POST /api/v1/owner/motorcycles/{id}/links` | | U | `{days?}` | `201` `{linkId, token, expiresAt}`; solo el propietario activo |
| `POST /api/v1/owner/motorcycles/{id}/transfers` | | U | — | `201` `{code, expiresAt}` (7 días, una vez); solo el propietario activo |
| `POST /api/v1/owner/transfers/{code}/accept` | | U | — | `{motorcycleId}`; `422 SAME_OWNER`; agrega la entrada `OWNERSHIP_TRANSFER` (sin nombres) |

## Catálogo de `code` (Problem Details)

| `code` | Estado | Cuándo |
|---|---|---|
| `UNAUTHENTICATED` | 401 | Falta iniciar sesión |
| `FORBIDDEN` | 403 | Rol sin permiso, o CSRF ausente |
| `NOT_FOUND` | 404 | No existe o es de otro taller |
| `VERSION_CONFLICT` | 409 | Alguien modificó el recurso: releer y reintentar |
| `INTERNAL_ERROR` | 500 | Error inesperado; se muestra `traceId` para soporte |
| `RATE_LIMITED` | 429 | Demasiados intentos |
| `INVALID_LIMIT` · `INVALID_CURSOR` | 422 / 400 | Paginación: `limit` fuera de 1–100, o cursor mal formado (pedir la primera página) |
| `WORKSHOP_REQUIRED` / `INVALID_WORKSHOP_ID` | 400 | Falta o es inválido `X-Workshop-Id` |
| `WORKSHOP_SUSPENDED` | 423 | El taller suspendido no crea registros nuevos |
| `WEAK_PASSWORD` · `INVALID_EMAIL` · `EMAIL_TAKEN` · `INVALID_CREDENTIALS` | 422 / 409 / 401 | Registro e inicio de sesión |
| `TOTP_REQUIRED` · `INVALID_TOTP` · `TOTP_ALREADY_ENABLED` · `TOTP_NOT_STARTED` | 401 / 422 / 409 | Verificación en dos pasos: falta el código al iniciar sesión, código inválido al activar, ya activa, o falta pedir el QR |
| `INVALID_PLATE` · `PLATE_IN_USE` · `INVALID_VIN` · `PLATE_VIN_MISMATCH` · `IDENTIFIER_REQUIRED` · `INVALID_MODEL_YEAR` · `INVALID_DISPLACEMENT` | 422 | Identidad de la moto |
| `INVALID_MILEAGE` · `MILEAGE_REGRESSION` | 422 | Kilometraje fuera de rango o menor al anterior |
| `INTAKE_ALREADY_OPEN` · `INTAKE_NOT_OPEN` · `PHOTO_REQUIRED` · `INVALID_PHOTO` | 409 / 422 | Recepción |
| `CONSENT_REQUIRED` · `STALE_POLICY` · `INVALID_PHONE` · `INVALID_NATIONAL_ID` · `QUERY_TOO_SHORT` | 422 | Clientes |
| `INVALID_TRANSITION` · `ORDER_NOT_EDITABLE` · `JOBS_REQUIRED` · `INVALID_JOB` · `INVALID_WARRANTY` · `CUSTOMER_REQUIRED` · `DETAILS_REQUIRED` | 409 / 422 | Órdenes |
| `PERSONAL_DATA` | 422 | Un texto que se vuelve público en el historial (diagnóstico, descripción de trabajos, nombre de repuesto, motivos de enmienda y de cambio de tablero) lleva un correo, un teléfono o una cédula (8 o más dígitos seguidos). El frontend debe avisarlo al escribir, no solo traducir el error |
| `INVALID_PART` · `INVALID_QUANTITY` · `INVALID_AMOUNT` · `INVALID_SERIAL` · `SERIAL_NOT_ACTIVE` · `INVALID_PRODUCT_CODE` | 422 | Repuestos, seriales y catálogo |
| `INVALID_METHOD` · `OVERPAYMENT` · `INCOMPLETE_PAYMENTS` · `PAYMENT_ALREADY_VOIDED` | 422 / 409 | Pagos y entrega |
| `FILE_REQUIRED` · `FILE_TOO_LARGE` · `INVALID_IMAGE` | 422 | Archivos |
| `REASON_REQUIRED` · `INVALID_REASON` · `COMMENT_REQUIRED` · `NOTHING_TO_AMEND` · `CANNOT_AMEND_AMENDMENT` | 422 | Historial, anulaciones, alertas |
| `INVALID_EXPIRATION` · `LINK_NOT_AVAILABLE` | 422 / 404 | Links públicos |
| `COUNTED_REQUIRED` · `FUTURE_DAY` · `INVALID_DAY` · `CASH_COUNT_CLOSED` · `CASH_COUNT_NOT_CLOSED` · `ALERT_ALREADY_RESOLVED` | 422 / 400 / 409 | Caja y alertas |
| `ALREADY_OWNED` · `SAME_OWNER` | 409 / 422 | Portal del propietario |
| `LAST_OWNER` · `INVALID_ROLE` · `INVALID_SETTING` · `INVALID_DOCUMENT` · `INVALID_STATUS` | 409 / 422 | Talleres y miembros |

## Flujo recomendado para el frontend

1. `GET /auth/me` al cargar: si `401` va a `/login`; con `memberships` elige taller y fija `X-Workshop-Id`. Si `totpSetupRequired` es `true`, llevar al administrador a activar TOTP (`/auth/totp/setup` → mostrar el QR con `otpauthUri` → `/auth/totp/confirm`) antes de cualquier pantalla de `/admin`. En el login, un `401` con `code: TOTP_REQUIRED` es la señal para pedir el código de 6 dígitos y reenviar el mismo formulario con `totp`.
2. Recepción → orden → repuestos (escaneo: el valor leído se valida en el cliente y se vuelve a validar aquí) → completar → pagos → entrega.
3. En la entrega, mostrar y guardar `claimCode` en el recibo: no se puede volver a pedir.
4. Estados de verificación del historial: `VALID` (cadena íntegra), `INVALID` (alterada, con el `seq` del fallo), `UNVERIFIABLE` (no se pudo comprobar). Nunca mostrar `UNVERIFIABLE` como válido.
