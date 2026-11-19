# 1. `OrderDelivered` vive en `shared.events`

**Estado:** aceptada

**Contexto.** `history` necesita el evento de entrega y `orders` lo publica; si el evento viviera en `orders`, `history` dependería de `orders` y `orders` ya depende de `history` para el kilometraje (ciclo que `ApplicationModules.verify()` rechaza).

**Decisión.** El evento está en `shared.events`, módulo abierto que ambos pueden usar.

**Consecuencias.** Los eventos de un solo módulo siguen en su paquete raíz. Un evento compartido por dos módulos va a `shared`.
