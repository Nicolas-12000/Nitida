# 5. El contenido del historial se guarda como texto canónico JCS

**Estado:** aceptada

**Contexto.** Lo que se firma son los bytes del JSON canónico (RFC 8785). Guardarlo como `jsonb` reordena claves y cambia espacios, y el hash dejaría de coincidir.

**Decisión.** Columna de texto con exactamente lo que se hasheó; el verificador reparsea, recanonicaliza y exige igualdad.

**Consecuencias.** No se puede consultar el contenido con operadores JSON de PostgreSQL; las vistas lo parsean en la aplicación.
