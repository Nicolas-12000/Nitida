# 2. `orders` cierra la recepción llamando a `IntakeApi`

**Estado:** aceptada

**Contexto.** La recepción debe quedar ligada a la orden en el mismo instante: una recepción libre dispara la alerta F1 y no puede abrirse otra orden con ella.

**Decisión.** Llamada síncrona en la misma transacción en lugar de un evento asíncrono.

**Consecuencias.** Si falla uno de los dos pasos, ninguno queda escrito. A cambio, `orders` conoce la API pública de `intake`.
