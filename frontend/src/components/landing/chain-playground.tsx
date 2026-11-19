"use client";
import { AnimatePresence, motion } from "motion/react";
import { Link2, RotateCcw, ShieldCheck, ShieldX } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { GENESIS, canonical, linkHash } from "@/lib/chain";
import { km } from "@/lib/format";

interface Rec { seq: number; date: string; type: string; mileage: number; work: string }

const ORIGINAL: Rec[] = [
  { seq: 1, date: "2026-03-14", type: "Registro inicial", mileage: 12_300, work: "Moto recibida por primera vez" },
  { seq: 2, date: "2026-05-22", type: "Servicio", mileage: 15_120, work: "Cambio de aceite y kit de arrastre" },
  { seq: 3, date: "2026-09-18", type: "Servicio", mileage: 18_420, work: "Pastillas de freno delanteras" },
];

const content = (r: Rec) => ({ seq: r.seq, occurredAt: r.date, type: r.type, mileage: r.mileage, work: r.work });

async function chainOf(recs: Rec[]) {
  const out: string[] = [];
  let prev = GENESIS;
  for (const r of recs) {
    prev = await linkHash(prev, content(r));
    out.push(prev);
  }
  return out;
}

/**
 * Laboratorio de la portada: la persona altera un registro y ve, con SHA-256 real en su navegador,
 * por qué la verificación detecta el cambio. Explica el mecanismo del historial sin tecnicismos de más.
 */
export function ChainPlayground() {
  const [recs, setRecs] = useState<Rec[]>(ORIGINAL);
  const [sealed, setSealed] = useState<string[] | null>(null);
  const [current, setCurrent] = useState<string[] | null>(null);
  const [show, setShow] = useState(1);

  useEffect(() => {
    // Hash de cada registro "al firmarse": se calcula una vez con los datos originales.
    void chainOf(ORIGINAL).then(setSealed);
  }, []);
  useEffect(() => {
    let alive = true;
    void chainOf(recs).then((h) => alive && setCurrent(h));
    return () => {
      alive = false;
    };
  }, [recs]);

  const brokenAt = useMemo(() => {
    if (!sealed || !current) return -1;
    return current.findIndex((h, i) => h !== sealed[i]);
  }, [sealed, current]);
  const tampered = brokenAt >= 0;

  const edit = (i: number, patch: Partial<Rec>) => setRecs((rs) => rs.map((r, k) => (k === i ? { ...r, ...patch } : r)));

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-10">
      <ol className="flex flex-col">
        {recs.map((r, i) => {
          const bad = tampered && i >= brokenAt;
          const origin = tampered && i === brokenAt;
          return (
            <li key={r.seq} className="relative">
              {i > 0 && (
                <div className="flex h-10 items-center pl-6" aria-hidden>
                  <motion.span animate={{ color: bad ? "var(--danger)" : "var(--primary)" }} className="flex items-center gap-2 text-caption">
                    <Link2 className="size-4" strokeWidth={2} />
                    <span className="font-mono">usa el hash de #{r.seq - 1}</span>
                  </motion.span>
                </div>
              )}
              <motion.div
                animate={{ borderColor: origin ? "var(--danger)" : bad ? "color-mix(in oklch, var(--danger) 40%, var(--border))" : "var(--border)" }}
                transition={{ duration: 0.18 }}
                className="card border-2 p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-body-sm font-medium text-ink">
                    <span className="mr-2 inline-flex size-6 items-center justify-center rounded-full bg-surface-sunken font-mono text-caption">{r.seq}</span>
                    {r.type} · <span className="tabular text-ink-muted">{r.date}</span>
                  </p>
                  <AnimatePresence mode="wait">
                    {origin ? (
                      <motion.span key="o" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="inline-flex h-6 items-center gap-1 rounded-xs bg-danger-soft px-2 text-caption font-medium text-danger-ink">
                        <ShieldX aria-hidden className="size-3.5" strokeWidth={1.75} /> Alterado
                      </motion.span>
                    ) : bad ? (
                      <motion.span key="b" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="inline-flex h-6 items-center rounded-xs bg-danger-soft px-2 text-caption font-medium text-danger-ink">
                        Cadena rota
                      </motion.span>
                    ) : null}
                  </AnimatePresence>
                </div>
                <div className="mt-3 grid grid-cols-[minmax(0,1fr)_140px] gap-3">
                  <label className="flex flex-col gap-1 text-caption text-ink-muted">
                    Trabajo
                    <input value={r.work} onChange={(e) => edit(i, { work: e.target.value })} className="h-10 rounded-md border border-border-input bg-surface px-3 text-body-sm text-ink focus:border-primary focus:outline-none" />
                  </label>
                  <label className="flex flex-col gap-1 text-caption text-ink-muted">
                    Kilometraje
                    <input inputMode="numeric" value={r.mileage} onChange={(e) => edit(i, { mileage: Number(e.target.value.replace(/\D/g, "").slice(0, 6) || 0) })}
                      className="h-10 rounded-md border border-border-input bg-surface px-3 text-body-sm text-ink tabular focus:border-primary focus:outline-none" />
                  </label>
                </div>
                <dl className="mt-3 grid grid-cols-[minmax(0,1fr)] gap-1 font-mono text-caption">
                  <div className="flex gap-2"><dt className="w-24 shrink-0 text-ink-muted">firmado</dt><dd className="min-w-0 truncate text-ink-muted">{sealed?.[i] ?? "…"}</dd></div>
                  <div className="flex gap-2"><dt className="w-24 shrink-0 text-ink-muted">calculado</dt>
                    <motion.dd key={current?.[i]} initial={{ opacity: 0.4 }} animate={{ opacity: 1 }} className={`min-w-0 truncate ${bad ? "text-danger-ink" : "text-verified-ink"}`}>{current?.[i] ?? "…"}</motion.dd>
                  </div>
                </dl>
              </motion.div>
            </li>
          );
        })}
      </ol>

      <div className="lg:sticky lg:top-24 lg:self-start">
        <motion.div
          key={tampered ? "bad" : "ok"}
          initial={{ opacity: 0, scale: 0.92 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.26, ease: [0.2, 0.8, 0.2, 1] }}
          className={`rounded-xl border p-6 text-center ${tampered ? "border-danger/40 bg-danger-soft" : "border-verified/40 bg-verified-soft"}`}
          role="status"
          aria-live="polite"
        >
          <span className={`relative mx-auto flex size-22 items-center justify-center rounded-full ${tampered ? "border-2 border-danger bg-surface text-danger-ink" : "bg-verified text-on-verified"}`}>
            {!tampered && <span aria-hidden className="absolute -inset-2 rounded-full bg-verified opacity-[0.28]" />}
            {tampered ? <ShieldX aria-hidden className="relative size-11" strokeWidth={1.75} /> : <ShieldCheck aria-hidden className="relative size-11" strokeWidth={1.75} />}
          </span>
          <p className={`mt-4 text-title font-semibold ${tampered ? "text-danger-ink" : "text-verified-ink"}`}>
            {tampered ? "No pudimos comprobar este historial" : "Historial verificado"}
          </p>
          <p className={`mt-1 text-body-sm ${tampered ? "text-danger-ink" : "text-verified-ink"}`}>
            {tampered
              ? `El registro #${brokenAt + 1} ya no coincide con lo que se firmó${brokenAt < recs.length - 1 ? `, y por eso tampoco los siguientes` : ""}.`
              : `3 de 3 registros firmados · último en ${km(recs[2].mileage)}`}
          </p>
          {tampered && (
            <button type="button" onClick={() => setRecs(ORIGINAL)} className="mt-4 inline-flex h-10 items-center gap-2 rounded-md border border-border-strong bg-surface px-4 text-body-sm font-medium text-ink hover:bg-surface-sunken">
              <RotateCcw aria-hidden className="size-4" strokeWidth={1.75} /> Restaurar los datos firmados
            </button>
          )}
        </motion.div>

        <div className="mt-5 flex flex-col gap-2">
          {[
            { t: "Huella", d: "Cada registro se convierte en un código de 64 caracteres (SHA-256). Cambie una letra y el código cambia por completo." },
            { t: "Eslabón", d: "La huella de cada registro incluye la del anterior. Alterar uno rompe todos los que siguen." },
            { t: "Firma", d: "El taller firma cada huella con su llave (Ed25519). Así se sabe qué taller registró qué y cuándo." },
          ].map((x, i) => (
            <button key={x.t} type="button" onClick={() => setShow(i)} aria-expanded={show === i}
              className={`rounded-md border px-4 py-3 text-left transition-colors ${show === i ? "border-primary bg-primary-soft" : "border-border bg-surface hover:bg-surface-sunken"}`}>
              <span className={`text-body-sm font-medium ${show === i ? "text-primary-soft-ink" : "text-ink"}`}>{i + 1}. {x.t}</span>
              <AnimatePresence initial={false}>
                {show === i && (
                  <motion.span initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="block overflow-hidden text-body-sm text-primary-soft-ink">
                    <span className="block pt-1">{x.d}</span>
                  </motion.span>
                )}
              </AnimatePresence>
            </button>
          ))}
        </div>
        <p className="mt-4 text-caption text-ink-muted">
          Los cálculos se hacen aquí mismo, en su navegador. Contenido de ejemplo del registro #1: <code className="font-mono break-all">{canonical(content(recs[0]))}</code>
        </p>
      </div>
    </div>
  );
}
