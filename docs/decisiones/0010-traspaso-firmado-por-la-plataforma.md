# 10. `OWNERSHIP_TRANSFER` lo firma la plataforma

**Estado:** aceptada

**Contexto.** Ningún taller hace un traspaso, y cada entrada del historial se firma con la llave de un taller.

**Decisión.** Identidad de plataforma (`WorkshopsApi.PLATFORM_ID`, migración `V13`) con su propia llave Ed25519 creada al arrancar.

**Consecuencias.** La entrada no lleva nombres ni identificadores de usuario (RN-22). `WorkshopsApi.all()` oculta la identidad de plataforma.
