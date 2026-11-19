# 12. Los pagos de una orden se serializan con bloqueo de fila

**Estado:** aceptada

**Contexto.** Registrar un pago lee los pagos existentes y luego escribe uno; dos escritorios a la vez pasaban los dos la revisión (la prueba aceptó 6 pagos de $100.000 en una orden de $100.000).

**Decisión.** `OrderAccess.loadLocked` (`PESSIMISTIC_WRITE`) en registrar, anular y entregar.

**Consecuencias.** Los pagos de una misma orden esperan unos milisegundos entre sí; las órdenes distintas no se afectan.
