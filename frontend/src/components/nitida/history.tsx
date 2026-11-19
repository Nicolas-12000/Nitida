"use client";
import { ArrowLeftRight, ChevronDown, Gauge, ShieldCheck, ShieldQuestion, ShieldX, Wrench, FilePen, Flag } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useState } from "react";
import { Badge, CopyButton, Skeleton } from "@/components/ui/primitives";
import { EvidenceBadge } from "@/components/nitida/status";
import { CONDITION_LABEL, ENTRY_LABEL, JOB_LABEL, date, km, shortHash } from "@/lib/format";
import type { EvidenceLevel, HistoryEntry, HistoryView, Verification } from "@/lib/types";

const EASE = [0.2, 0.8, 0.2, 1] as const;

/** Sello de verificación en sus tres estados (DESIGN.MD §6.4). Nunca aparece mientras se verifica. */
export function VerificationSeal({
  verification, size = "lg", detail, audience = "public",
}: { verification: Verification; size?: "lg" | "md"; detail?: string; audience?: "public" | "workshop" }) {
  const reduce = useReducedMotion();
  const big = size === "lg";
  const cfg = {
    VALID: {
      icon: ShieldCheck, circle: "bg-verified text-on-verified", title: "Historial verificado", line: detail,
    },
    INVALID: {
      icon: ShieldX, circle: "bg-danger-soft text-danger-ink border-2 border-danger", title: "No pudimos comprobar este historial",
      line: audience === "workshop" && verification.seq != null
        ? `La cadena no coincide desde el registro #${verification.seq}. Puede que se haya modificado fuera de Nítida.`
        : "Puede que haya sido modificado fuera de Nítida. Pida al taller una copia actualizada.",
    },
    UNVERIFIABLE: {
      icon: ShieldQuestion, circle: "bg-attention-soft text-attention-ink", title: "Verificación no disponible en este momento",
      line: "No la presentamos como válida hasta poder comprobarla. Intente más tarde.",
    },
  }[verification.status];
  const Icon = cfg.icon;
  return (
    <motion.div
      initial={reduce ? false : { opacity: 0, scale: 0.92 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.26, ease: EASE }}
      className={`flex items-center gap-4 ${big ? "flex-col text-center" : ""}`}
      role="status"
    >
      <span className={`relative flex shrink-0 items-center justify-center rounded-full ${cfg.circle} ${big ? "size-22" : "size-11"}`}>
        {verification.status === "VALID" && <span aria-hidden className="absolute -inset-2 rounded-full bg-verified opacity-[0.28]" />}
        <Icon aria-hidden className={`relative ${big ? "size-11" : "size-6"}`} strokeWidth={1.75} />
      </span>
      <div>
        <p className={`${big ? "text-title" : "text-heading font-medium"} font-semibold text-ink`}>{cfg.title}</p>
        {cfg.line && <p className="mt-1 text-body-sm text-ink-muted">{cfg.line}</p>}
      </div>
    </motion.div>
  );
}

export function SealSkeleton({ size = "lg" }: { size?: "lg" | "md" }) {
  const big = size === "lg";
  return (
    <div aria-hidden className={`flex items-center gap-4 ${big ? "flex-col" : ""}`}>
      <Skeleton className={`rounded-full! ${big ? "size-22" : "size-11"}`} />
      <div className={`flex flex-col gap-2 ${big ? "items-center" : ""}`}>
        <Skeleton className={`h-6 ${big ? "w-60" : "w-44"}`} />
        <Skeleton className={`h-3.5 ${big ? "w-48" : "w-56"}`} />
      </div>
    </div>
  );
}

export function sealDetail(h: HistoryView): string {
  const signed = h.entries.length;
  const jumps = mileageJumps(h.entries);
  return `${signed} de ${signed} registros firmados · ${jumps ? `${jumps} cambio${jumps > 1 ? "s" : ""} de odómetro declarado${jumps > 1 ? "s" : ""}` : "sin saltos de km"}`;
}

function mileageJumps(entries: HistoryEntry[]) {
  return entries.filter((e) => e.type === "ODOMETER_CHANGE").length;
}

const ICON = { INITIAL_REGISTRATION: Flag, SERVICE: Wrench, AMENDMENT: FilePen, ODOMETER_CHANGE: Gauge, OWNERSHIP_TRANSFER: ArrowLeftRight };

interface PartContent { name: string; brand: string; condition: string; quantity: number; evidenceLevel: EvidenceLevel; hasPhotoEvidence?: boolean }

/** Línea de tiempo: punto índigo por registro, menta en el último verificado. Las correcciones van con sangría bajo su original. */
export function HistoryTimeline({ history, onAmend }: { history: HistoryView; onAmend?: (seq: number) => void }) {
  const reduce = useReducedMotion();
  const amendments = new Map<number, HistoryEntry[]>();
  for (const e of history.entries) {
    if (e.type === "AMENDMENT") {
      const target = (e.content.amends as { seq: number } | null)?.seq;
      if (target != null) amendments.set(target, [...(amendments.get(target) ?? []), e]);
    }
  }
  const roots = history.entries.filter((e) => e.type !== "AMENDMENT").reverse();
  const lastSeq = history.entries.length ? history.entries[history.entries.length - 1].seq : -1;
  const valid = history.verification.status === "VALID";

  return (
    <ol className="relative">
      <span aria-hidden className="absolute top-3 bottom-3 left-[11px] w-px bg-border-strong" />
      {roots.map((e, i) => {
        const isLast = e.seq === lastSeq || (amendments.get(e.seq)?.some((a) => a.seq === lastSeq) ?? false);
        return (
          <motion.li
            key={e.seq}
            initial={reduce ? false : { opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.26, delay: i * 0.08, ease: EASE }}
            className="relative pb-6 pl-10 last:pb-0"
          >
            <span
              aria-hidden
              className={`absolute top-1.5 left-[7px] size-[9px] rounded-full ring-4 ring-surface ${isLast && valid ? "bg-verified" : "bg-primary"}`}
            />
            <EntryCard entry={e} onAmend={onAmend} />
            {amendments.get(e.seq)?.map((a) => (
              <div key={a.seq} className="mt-3 ml-2 border-l-2 border-border-strong pl-4">
                <EntryCard entry={a} />
              </div>
            ))}
          </motion.li>
        );
      })}
    </ol>
  );
}

function EntryCard({ entry: e, onAmend }: { entry: HistoryEntry; onAmend?: (seq: number) => void }) {
  const [open, setOpen] = useState(false);
  const c = e.content;
  const Icon = ICON[e.type];
  const jobs = (c.jobs as { type: string; description: string }[] | undefined) ?? [];
  const parts = (c.parts as PartContent[] | undefined) ?? [];
  const warranty = c.warranty as { days: number | null; km: number | null } | null | undefined;
  const corrected = c.corrected as { diagnosis?: string; mileage?: number } | undefined;
  const title =
    e.type === "SERVICE" && jobs.length ? jobs.map((j) => JOB_LABEL[j.type] ?? j.type).join(" · ") : ENTRY_LABEL[e.type];
  const hasMore = e.type === "SERVICE" && (parts.length > 0 || !!c.diagnosis);

  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <Icon aria-hidden className="size-4 text-ink-muted" strokeWidth={1.75} />
        <h3 className="text-body-sm font-medium text-ink">{title}</h3>
        {e.type === "AMENDMENT" && <Badge tone="neutral">Corrección de #{(c.amends as { seq: number }).seq}</Badge>}
        {e.type === "INITIAL_REGISTRATION" && c.mileageDeclared === true && <Badge tone="neutral">Km declarado</Badge>}
      </div>
      <p className="mt-0.5 text-caption text-ink-muted">
        <span className="tabular">{date(e.recordedAt)}</span>
        {typeof c.mileage === "number" && e.type !== "AMENDMENT" && <> · <span className="tabular">{km(c.mileage)}</span></>}
        {e.workshopName && <> · {e.workshopName}{e.municipality ? `, ${e.municipality}` : ""}</>}
        {e.workshopName && (e.workshopVerified ? " · taller verificado" : " · taller sin verificar")}
        {e.type === "OWNERSHIP_TRANSFER" && " · registrado por la plataforma"}
      </p>
      {(e.type === "AMENDMENT" || e.type === "ODOMETER_CHANGE") && typeof c.reason === "string" && (
        <p className="mt-1 text-body-sm text-ink">{c.reason}</p>
      )}
      {corrected && (
        <p className="mt-1 text-caption text-ink-muted">
          Corrige {corrected.mileage != null && <>kilometraje a <span className="tabular text-ink">{km(corrected.mileage)}</span></>}
          {corrected.mileage != null && corrected.diagnosis && " y "}
          {corrected.diagnosis && <>diagnóstico</>}
        </p>
      )}
      <div className="mt-1.5 flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-1 font-mono text-caption text-ink-muted">
          #{e.seq} · {shortHash(c.privateHash as string)}
          <CopyButton value={String(c.privateHash ?? "")} label="Copiar huella del registro" />
        </span>
        {hasMore && (
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            aria-expanded={open}
            className="inline-flex h-8 items-center gap-1 rounded-sm px-2 text-caption font-medium text-primary hover:bg-primary-soft"
          >
            {open ? "Ocultar detalle" : `Ver detalle${parts.length ? ` · ${parts.length} repuesto${parts.length > 1 ? "s" : ""}` : ""}`}
            <ChevronDown aria-hidden className={`size-3.5 transition-transform duration-[180ms] ${open ? "rotate-180" : ""}`} strokeWidth={1.75} />
          </button>
        )}
        {onAmend && e.type !== "AMENDMENT" && e.type !== "OWNERSHIP_TRANSFER" && (
          <button type="button" onClick={() => onAmend(e.seq)} className="inline-flex h-8 items-center rounded-sm px-2 text-caption font-medium text-ink-muted hover:bg-surface-sunken hover:text-ink">
            Corregir
          </button>
        )}
      </div>
      <AnimatePresence initial={false}>
        {open && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.18, ease: EASE }}
            className="overflow-hidden"
          >
            <div className="mt-2 rounded-md bg-surface-sunken p-3">
              {typeof c.diagnosis === "string" && <p className="text-body-sm text-ink">{c.diagnosis}</p>}
              {parts.length > 0 && (
                <ul className="mt-2 divide-y divide-border">
                  {parts.map((p, i) => (
                    <li key={i} className="flex flex-wrap items-center justify-between gap-2 py-2">
                      <span className="text-body-sm text-ink">
                        {p.quantity > 1 && <span className="tabular">{p.quantity} × </span>}
                        {p.name} <span className="text-ink-muted">· {p.brand} · {CONDITION_LABEL[p.condition] ?? p.condition}</span>
                      </span>
                      <EvidenceBadge level={p.evidenceLevel} />
                    </li>
                  ))}
                </ul>
              )}
              {warranty && (
                <p className="mt-2 text-caption text-ink-muted">
                  Garantía: {[warranty.days != null && `${warranty.days} días`, warranty.km != null && km(warranty.km)].filter(Boolean).join(" o ")}
                </p>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export function TimelineSkeleton() {
  return (
    <div aria-hidden className="relative flex flex-col gap-6 pl-10">
      <span className="absolute top-3 bottom-3 left-[11px] w-px bg-border" />
      {[0.6, 0.45, 0.55].map((w, i) => (
        <div key={i} className="relative flex flex-col gap-2">
          <span className="absolute top-1.5 -left-[33px] size-[9px] rounded-full bg-skeleton" />
          <Skeleton className="h-3.5" />
          <Skeleton className="h-3" />
          <Skeleton className="h-3 w-24" />
          <span className="sr-only">{w}</span>
        </div>
      ))}
    </div>
  );
}

/** Resumen de evidencia de repuestos, en lenguaje honesto (DESIGN.MD §1.8). */
export function EvidenceSummary({ history }: { history: HistoryView }) {
  const { partsTotal, partsWithEvidence } = history.evidence;
  const services = history.entries.filter((e) => e.type === "SERVICE").length;
  return (
    <dl className="grid grid-cols-3 gap-2 text-center">
      <div className="rounded-md bg-surface-sunken px-2 py-3">
        <dt className="text-caption text-ink-muted">Servicios</dt>
        <dd className="text-heading font-semibold tabular">{services}</dd>
      </div>
      <div className="rounded-md bg-surface-sunken px-2 py-3">
        <dt className="text-caption text-ink-muted">Repuestos con evidencia</dt>
        <dd className="text-heading font-semibold tabular">{partsWithEvidence}/{partsTotal}</dd>
      </div>
      <div className="rounded-md bg-surface-sunken px-2 py-3">
        <dt className="text-caption text-ink-muted">Propietarios previos</dt>
        <dd className="text-heading font-semibold tabular">{history.transfers}</dd>
      </div>
    </dl>
  );
}
