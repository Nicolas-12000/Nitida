# 8. Las invitaciones devuelven el token al dueño

**Estado:** aceptada

**Contexto.** El correo (Mailpit en desarrollo) no está implementado todavía.

**Decisión.** El token se devuelve una sola vez al dueño, que lo comparte; solo se guarda su hash.

**Consecuencias.** Reemplazar por un envío de correo no cambia el contrato: el dueño dejaría de recibir el token.
