# 4. Ajustes del taller como columnas tipadas

**Estado:** aceptada

**Contexto.** La spec ejemplificaba `settings_jsonb`. Los tres ajustes (horas de alerta, tolerancia de caja, días de estancamiento) tienen rango y se validan en el dominio.

**Decisión.** Columnas `int`/`bigint` con su validación en `Workshop.updateSettings`.

**Consecuencias.** Más migraciones al agregar un ajuste, pero el esquema documenta los tipos y la base rechaza basura.
