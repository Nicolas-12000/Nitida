# 15. Una entrega ya ocurrida se registra aunque el taller se suspenda antes

**Estado:** aceptada

**Contexto.** Entre el pago y el listener de `OrderDelivered` un administrador puede suspender el taller. La spec bloquea registros nuevos de un taller suspendido (RN-04, `423`), pero no dice qué pasa con un evento que ya se publicó.

**Decisión.** La entrada se registra igual. La suspensión corta las llamadas nuevas del taller; no deshace una entrega que ya se comprometió. El contenido firmado lleva `workshopVerified` según el estado en el momento de registrar, así que si ya está suspendido queda en `false`. Si la cadena todavía no tiene `INITIAL_REGISTRATION`, el listener falla y el reintento (`EventResubmission`) lo vuelve a intentar cuando la cadena exista.

**Consecuencias.** Un taller suspendido no puede abrir órdenes ni recepciones, y aun así su última entrega queda en el historial, marcada como no verificada si la suspensión ganó la carrera.
