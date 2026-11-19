# 11. TOTP obligatorio para el administrador de plataforma

**Estado:** aceptada (reemplaza el aplazamiento que se había propuesto)

**Contexto.** La spec (§10) pide verificación en dos pasos para quien opera la plataforma: puede verificar o suspender talleres. Una cuenta de administrador comprometida con solo contraseña es el peor caso de suplantación.

**Decisión.** TOTP de RFC 6238 (HMAC-SHA1, 30 s, 6 dígitos, compatible con cualquier aplicación de autenticación), implementado con el JDK, sin librerías nuevas.
- El secreto se guarda sellado con AES-GCM y la KEK (`KeyCipher`, con el usuario como dato autenticado), nunca en claro; el último paso aceptado queda guardado y un código no sirve dos veces.
- Se acepta el paso actual y uno a cada lado (desfase del reloj). Los intentos fallidos cuentan para el bloqueo progresivo de la cuenta; la confirmación admite 5 intentos cada 15 minutos.
- Un administrador **sin TOTP activo inicia sesión como usuario normal**: sin el privilegio `PLATFORM_ADMIN`, `/admin/**` responde `403`, hasta que completa `/auth/totp/setup` y `/auth/totp/confirm`; entonces la misma sesión recibe el privilegio. Con TOTP activo, el login exige `totp` y responde `TOTP_REQUIRED` solo después de validar la contraseña.
- Es opcional para cualquier otra cuenta (la spec lo pide así para el dueño); una vez activo, se exige para esa cuenta.
- `nitida.admin.totp-required` (por defecto `true`) solo lo relaja el perfil `dev`, para que el frontend no necesite una aplicación de autenticación para explorar.

**Consecuencias.** Al confirmar, la respuesta trae 8 códigos de recuperación (una sola vez, solo su hash queda guardado). Cada uno sustituye al código de la app en el login y se quema al usarse. Si se pierden el dispositivo y los códigos, otro administrador o quien tenga acceso a la base desactiva TOTP (`totp_enabled = false`). No hay dispositivos de confianza. El límite de la confirmación es en memoria, por instancia.
