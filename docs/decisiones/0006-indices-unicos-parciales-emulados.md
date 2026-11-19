# 6. Índices únicos parciales emulados con columna anulable

**Estado:** aceptada

**Contexto.** «Un serial activo» y «un propietario activo» son índices únicos parciales en PostgreSQL, que H2 (usado en pruebas) no soporta igual.

**Decisión.** Una columna (`active_serial`, `active_motorcycle`) que vale el identificador mientras está activo y `NULL` al retirarse, con `UNIQUE`.

**Consecuencias.** Funciona igual en H2 y PostgreSQL, y la restricción es la última barrera contra la carrera entre dos talleres. Si se pasa a PostgreSQL puro se puede cambiar por un índice parcial.
