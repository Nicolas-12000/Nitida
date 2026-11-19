"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Info } from "lucide-react";
import { api } from "@/lib/api";
import { isCode } from "@/lib/errors";
import { motoName } from "@/lib/format";
import { useQuery } from "@/lib/use-query";
import { Lockup } from "@/components/brand/logo";
import { ErrorBanner, Skeleton } from "@/components/ui/primitives";
import { PlateTag } from "@/components/nitida/status";
import { EvidenceSummary, HistoryTimeline, SealSkeleton, TimelineSkeleton, VerificationSeal, sealDetail } from "@/components/nitida/history";
import { BrokenLinkFigure } from "@/components/illustrations";
import type { HistoryView } from "@/lib/types";

/** Hoja pública /v/[token]: sin navegación de la app, una columna de 560 px pensada para abrirse desde WhatsApp. */
export function PublicHistory() {
  const { token } = useParams<{ token: string }>();
  const h = useQuery<HistoryView>(`public:${token}`, () => api.get(`/public/v/${encodeURIComponent(token)}`));
  const d = h.data;

  return (
    <div className="min-h-screen bg-canvas px-4 py-8">
      <div className="mx-auto max-w-[560px]">
        <header className="mb-6 flex items-center justify-between">
          <Link href="/" aria-label="Nítida"><Lockup size={28} /></Link>
          <span className="text-caption text-ink-muted">Historial de moto</span>
        </header>

        {h.error ? (
          isCode(h.error, "LINK_NOT_AVAILABLE", "NOT_FOUND") ? (
            <section className="rounded-xl border border-border bg-surface p-8 text-center shadow-card">
              <div className="flex justify-center"><BrokenLinkFigure /></div>
              <h1 className="mt-4 text-title font-semibold">Este enlace no está disponible</h1>
              <p className="mt-2 text-body-sm text-ink-muted">Puede haber vencido o haber sido revocado. Pida al dueño de la moto o al taller un enlace nuevo.</p>
            </section>
          ) : <ErrorBanner error={h.error} onRetry={h.refetch} />
        ) : (
          <>
            <section className="rounded-xl border border-border bg-surface p-6 shadow-card sm:p-8" aria-busy={!d}>
              <div className="mb-6 text-center">
                {d ? (
                  <>
                    <h1 className="text-display font-semibold">{motoName(d.motorcycle)}</h1>
                    <p className="mt-2 flex items-center justify-center gap-2 text-body-sm text-ink-muted">
                      <PlateTag plate={d.motorcycle.plate} size="sm" />
                      {[d.motorcycle.modelYear, d.motorcycle.displacementCc && `${d.motorcycle.displacementCc} cc`].filter(Boolean).join(" · ")}
                    </p>
                  </>
                ) : (
                  <div className="flex flex-col items-center gap-2"><Skeleton className="h-8 w-3/5" /><Skeleton className="h-3.5 w-2/5" /></div>
                )}
              </div>
              {d ? <VerificationSeal verification={d.verification} detail={sealDetail(d)} /> : <SealSkeleton />}
              {d && <div className="mt-6"><EvidenceSummary history={d} /></div>}
            </section>

            <section className="mt-4 rounded-xl border border-border bg-surface p-6 shadow-card sm:p-8">
              <h2 className="mb-5 text-heading font-semibold">Servicios registrados</h2>
              {d ? <HistoryTimeline history={d} /> : <TimelineSkeleton />}
            </section>

            <section className="mt-4 flex gap-3 rounded-md border border-border bg-surface-sunken p-4 text-body-sm text-ink-muted">
              <Info aria-hidden className="mt-0.5 size-5 shrink-0" strokeWidth={1.75} />
              <div>
                <p className="font-medium text-ink">Cómo leer esta hoja</p>
                <p className="mt-1">Cada registro está encadenado al anterior y firmado por el taller que lo hizo. “Historial verificado” significa que nada se alteró ni se borró después de firmarse.</p>
                <p className="mt-1">No muestra precios ni datos del dueño, y la placa aparece incompleta a propósito. Que un repuesto esté “escaneado” no prueba que sea original.</p>
              </div>
            </section>
          </>
        )}
        <footer className="mt-8 text-center text-caption text-ink-muted">Verificación hecha por Nítida en el momento de abrir esta página.</footer>
      </div>
    </div>
  );
}
