# Modelo de amenazas (STRIDE)

Resumen de la spec §13.1 con el estado real del backend. «Control» es lo que existe hoy; «Falta» lo que no.

| Amenaza | Ejemplo | Control | Prueba | Falta |
|---|---|---|---|---|
| **S**uplantación | Robo de sesión, fuerza bruta | Argon2id, bloqueo por cuenta e IP, cookie `HttpOnly` + `SameSite=Lax`, CSRF | `AuthIntegrationTest`, `OwaspSecurityTest`, `ForwardedHeadersTest` | TOTP para el administrador |
| **T**amper (manipulación) | Editar una entrada del historial en la base | Cadena de hashes, firma Ed25519, verificación que falla cerrado | `ChainTest`, `HistoryIntegrationTest` | Publicar las cabezas de cadena (fase 2): hoy quien controla la base y las llaves podría reescribir todo |
| Manipulación | Un taller crea registros falsos | Verificación de talleres, enmiendas visibles, evidencia fotográfica, `workshopVerified` dentro del contenido firmado | `HistoryIntegrationTest.entriesFromAVerifiedWorkshop…` | Reputación |
| **R**epudio | «Yo no registré eso» | Auditoría de solo-insertar, firma por taller | `HistoryIntegrationTest` | Firma desde el dispositivo del taller (WebAuthn): el servidor guarda las llaves |
| **I**nformación | Ver clientes de otro taller (IDOR) | `404` para recursos ajenos, filtro por taller en cada repositorio | `AccessMatrixTest`, `MotorcyclesIntegrationTest` | **RLS en PostgreSQL** (segunda barrera) |
| Información | GPS en fotos, PII en el historial | Las fotos se re-codifican sin EXIF; el historial no lleva clientes (RN-22) | `FilesIntegrationTest`, `ImageSanitizerTest` | El diagnóstico libre puede llevar PII que escriba el mecánico: aviso en la UI |
| Información | Averiguar dónde está una pieza por el rechazo de un serial | Mensaje genérico, sin moto ni taller; límite de intentos por usuario | `CatalogIntegrationTest`, `CashdeskIntegrationTest.aDuplicateSerialRaisesF6…` | — |
| **D**enegación | Fuerza bruta contra tokens de link; inundar el login o el registro | Tokens de 128 bits, límite por IP detrás del proxy, mismo `404` para token inexistente, vencido o revocado | `HistoryIntegrationTest.unknownExpiredAndRevokedLinks…`, `ForwardedHeadersTest`, `RateLimiterEvictionTest` | Límite al registro de usuarios; el límite es en memoria (una sola instancia) |
| **E**levación | El mecánico registra pagos o cuadra caja | Autorización por rol en cada caso de uso | `AccessMatrixTest` | — |
| Manipulación | Reusar una pieza con serial en otra moto | Unicidad global de seriales, índice único, alerta F6 | `CatalogIntegrationTest`, `SerialCorrectionIntegrationTest` | — |
| SSRF / Phishing | QR con URL maliciosa | El servidor nunca abre ni visita URLs del QR; solo guarda el método | — (no hay código que lo haga) | El frontend debe mostrar el dominio (RN-95) |
| Inyección | Contenido del código como vector | Validación estricta de formato y longitud; todo es dato | `OwaspSecurityTest.injectionPayloads…` | — |
| Concurrencia | Dos pagos simultáneos superan el total | Bloqueo de la fila de la orden al pagar, anular o entregar | `PaymentRaceIntegrationTest` | — |
