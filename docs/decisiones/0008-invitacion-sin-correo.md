# 8. Las invitaciones viajan por correo

**Estado:** aceptada (reemplaza la devolución del token en la respuesta)

**Contexto.** La spec (§10) manda los correos de invitación y de recuperación por Mailpit en desarrollo. La primera versión le devolvía el token al dueño porque el correo no existía.

**Decisión.** `POST /workshops/{id}/invitations` responde `{invitationId, expiresAt}` y manda el código por SMTP (Mailpit en `docker compose`, puerto 1025). Solo se guarda el hash. Lo mismo hace la recuperación de contraseña: `POST /auth/password/forgot` responde `204` haya o no cuenta, y el código llega al correo si la cuenta existe.

**Consecuencias.** Sin Mailpit (o con `nitida.mail.mode=capture`, que es el perfil de pruebas) el código no sale del proceso. El dueño ya no puede copiarlo de la respuesta.
