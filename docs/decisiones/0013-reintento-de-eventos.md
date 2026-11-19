# 13. Las publicaciones incompletas se reintentan

**Estado:** aceptada

**Contexto.** Un listener que falla (por ejemplo, un corte de la base justo después de entregar) deja la publicación incompleta y nadie la reenviaba: la entrada del historial se perdía.

**Decisión.** `EventResubmission` reenvía cada 5 minutos lo que lleva más de 2 minutos sin completar; al reiniciar se republica lo pendiente.

**Consecuencias.** Requiere listeners idempotentes (RN-64, `dedupe_key`). Con varias instancias hay que apagar `republish-outstanding-events-on-restart`.
