# 3. `orders` libera los seriales llamando a `CatalogApi`

**Estado:** aceptada

**Contexto.** Al cancelar una orden sus seriales deben quedar retirados. Un evento que `catalog` escuche haría que `catalog` dependa de `orders`, y `orders` ya depende de `catalog`.

**Decisión.** `orders` llama a `CatalogApi.releaseForOrder` dentro de la misma transacción.

**Consecuencias.** El mismo mecanismo libera el serial que el mecánico corrige (`RegisterSerialUseCase`): un serial mal escrito deja de bloquear el verdadero.
