"use client";
import { AnimatePresence, motion } from "motion/react";
import { Camera, CircleCheck, ClipboardList, Inbox, PackageCheck, Share2, Wrench, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { Badge } from "@/components/ui/primitives";
import { EvidenceBadge, OrderStatusBadge, PlateTag } from "@/components/nitida/status";
import { SealFigure, Tachometer } from "@/components/illustrations";

interface Step { id: string; icon: LucideIcon; title: string; who: string; text: string; points: string[] }

const STEPS: Step[] = [
  {
    id: "recepcion", icon: Inbox, title: "Recepción", who: "Recepción",
    text: "La moto entra con su placa, el kilometraje y dos fotos: la moto y el tablero. Si ya la conocemos, Nítida avisa si el kilometraje es menor que el último registrado.",
    points: ["Placa con formato colombiano (ABC 12D)", "Fotos re-codificadas y sin datos de ubicación", "Alerta si la moto espera sin orden"],
  },
  {
    id: "orden", icon: Wrench, title: "Orden de trabajo", who: "Mecánico",
    text: "El mecánico abre la orden con el diagnóstico, agrega trabajos y repuestos. Cada repuesto lleva su nivel de evidencia: manual, escaneado, serial único o sello revisado.",
    points: ["Escáner de códigos de barras con la cámara", "Un serial no puede estar activo en dos motos", "Si dos personas editan a la vez, nadie pisa al otro"],
  },
  {
    id: "entrega", icon: PackageCheck, title: "Cobro y entrega", who: "Recepción",
    text: "Se registran los pagos hasta completar el total. Al entregar, el servicio se firma en el historial y sale un código para que el dueño agregue la moto a su cuenta.",
    points: ["Efectivo, transferencia, tarjeta o fiado", "Pagos anulados quedan tachados, nunca borrados", "Cuadre de caja diario con alertas de descuadre"],
  },
  {
    id: "historial", icon: Share2, title: "Historial verificable", who: "Dueño de la moto",
    text: "El propietario ve todos los servicios de su moto, de cualquier taller en Nítida, y comparte un enlace al venderla. Quien lo abre ve el sello de verificación, sin precios ni datos personales.",
    points: ["Enlace con vencimiento, revocable", "Placa incompleta en la hoja pública", "Traspaso al nuevo dueño sin nombres en el historial"],
  },
];

export function FlowExplorer() {
  const [active, setActive] = useState(0);
  const s = STEPS[active];
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-10">
      <div role="tablist" aria-label="Pasos del flujo del taller" aria-orientation="vertical" className="relative flex flex-col gap-1">
        <span aria-hidden className="absolute top-6 bottom-6 left-[27px] w-px bg-border-strong" />
        {STEPS.map((st, i) => (
          <button
            key={st.id}
            role="tab"
            id={`tab-${st.id}`}
            aria-selected={active === i}
            aria-controls={`panel-${st.id}`}
            onClick={() => setActive(i)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") setActive((active + 1) % STEPS.length);
              if (e.key === "ArrowUp") setActive((active + STEPS.length - 1) % STEPS.length);
            }}
            className={`relative flex items-center gap-4 rounded-lg px-3 py-3 text-left transition-colors ${active === i ? "bg-surface shadow-card" : "hover:bg-surface/60"}`}
          >
            <span className={`relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full border-2 transition-colors ${active === i ? "border-primary bg-primary text-on-primary" : i < active ? "border-primary bg-surface text-primary" : "border-border-strong bg-surface text-ink-muted"}`}>
              <st.icon aria-hidden className="size-4" strokeWidth={1.75} />
            </span>
            <span>
              <span className={`block text-body font-medium ${active === i ? "text-ink" : "text-ink-muted"}`}>{st.title}</span>
              <span className="block text-caption text-ink-muted">{st.who}</span>
            </span>
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={s.id}
          id={`panel-${s.id}`}
          role="tabpanel"
          aria-labelledby={`tab-${s.id}`}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -4 }}
          transition={{ duration: 0.18, ease: [0.2, 0.8, 0.2, 1] }}
          className="grid grid-cols-1 gap-6 sm:grid-cols-2"
        >
          <div>
            <h3 className="text-heading font-semibold text-ink">{s.title}</h3>
            <p className="mt-2 text-body text-ink-muted">{s.text}</p>
            <ul className="mt-4 flex flex-col gap-2">
              {s.points.map((p) => (
                <li key={p} className="flex items-start gap-2 text-body-sm text-ink">
                  <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" strokeWidth={1.75} />{p}
                </li>
              ))}
            </ul>
          </div>
          <div className="card p-4" aria-hidden>
            <Preview id={s.id} />
          </div>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}

/** Vista previa con los componentes reales de la app y datos de ejemplo. */
function Preview({ id }: { id: string }) {
  if (id === "recepcion")
    return (
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-2"><PlateTag plate="KZT41F" /><span className="text-body-sm text-ink-muted">Yamaha FZ 2.0 150</span></div>
        <div className="flex items-center gap-3 rounded-md bg-surface-sunken p-3">
          <Tachometer size={64} level={0.75} />
          <div><p className="text-caption text-ink-muted">Kilometraje</p><p className="text-title font-semibold tabular">18.420 km</p></div>
        </div>
        <div className="grid grid-cols-2 gap-2">
          {["Moto", "Tablero"].map((t) => (
            <div key={t} className="flex h-16 items-center justify-center gap-1 rounded-md border border-dashed border-border-input bg-surface-sunken text-caption text-ink-muted"><Camera className="size-4" />{t}</div>
          ))}
        </div>
      </div>
    );
  if (id === "orden")
    return (
      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between"><span className="text-body-sm font-medium">Orden #1042</span><OrderStatusBadge status="IN_PROGRESS" /></div>
        <p className="text-body-sm text-ink-muted">Frenos delanteros cristalizados; se cambian pastillas.</p>
        <ul className="divide-y divide-border rounded-md border border-border">
          {([["Pastillas delanteras", "AUTHENTICITY_VERIFIED"], ["Aceite 20W-50", "SCANNED"], ["Bujía CR8E", "UNIQUE_SERIAL"], ["Tornillería", "MANUAL"]] as const).map(([n, l]) => (
            <li key={n} className="flex items-center justify-between gap-2 px-3 py-2 text-caption"><span className="text-ink">{n}</span><EvidenceBadge level={l} /></li>
          ))}
        </ul>
      </div>
    );
  if (id === "entrega")
    return (
      <div className="flex flex-col items-center gap-2 text-center">
        <svg viewBox="0 0 100 100" className="size-20"><SealFigure cx={50} cy={50} r={28} /></svg>
        <p className="text-body-sm font-medium">Moto entregada</p>
        <div className="w-full rounded-md bg-surface-sunken p-3">
          <p className="text-caption text-ink-muted">Código para el propietario</p>
          <p className="font-mono text-title font-semibold tracking-[0.2em]">K7M2QX9P</p>
        </div>
        <Badge tone="verified" icon={CircleCheck}>Pagado $ 245.000</Badge>
      </div>
    );
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-3">
        <span className="relative flex size-11 items-center justify-center rounded-full bg-verified text-on-verified">
          <span className="absolute -inset-1.5 rounded-full bg-verified opacity-[0.28]" />
          <CircleCheck className="relative size-6" strokeWidth={1.75} />
        </span>
        <div><p className="text-body-sm font-semibold">Historial verificado</p><p className="text-caption text-ink-muted">4 de 4 registros firmados</p></div>
      </div>
      <ol className="relative flex flex-col gap-3 pl-6">
        <span className="absolute top-1 bottom-1 left-[5px] w-px bg-border-strong" />
        {[["Frenos", "18 sep 2026 · 18.420 km"], ["Aceite y kit de arrastre", "22 may 2026 · 15.120 km"], ["Registro inicial", "14 mar 2026 · 12.300 km"]].map(([t, d], i) => (
          <li key={t} className="relative">
            <span className={`absolute top-1.5 -left-[22px] size-[9px] rounded-full ${i === 0 ? "bg-verified" : "bg-primary"}`} />
            <p className="text-caption font-medium text-ink">{t}</p>
            <p className="text-caption text-ink-muted">{d}</p>
          </li>
        ))}
      </ol>
      <div className="flex items-center gap-2 text-caption text-ink-muted"><ClipboardList className="size-4" /> Sin precios ni datos del dueño</div>
    </div>
  );
}
