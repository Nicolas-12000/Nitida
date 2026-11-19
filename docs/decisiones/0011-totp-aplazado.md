# 11. TOTP aplazado a la fase 2

**Estado:** aceptada

**Contexto.** La spec (§10) lo marca obligatorio para el administrador «si alcanza el tiempo; si no, fase 2».

**Decisión.** No se implementa en el MVP. El administrador entra con correo y contraseña Argon2id, con bloqueo por cuenta e IP.

**Consecuencias.** Riesgo aceptado: una cuenta de administrador comprometida puede verificar o suspender talleres, pero no leer clientes. Mitigación: pocas cuentas de administrador y contraseñas largas.
