"use client";
import Link from "next/link";
import { motion } from "motion/react";
import { CheckCheck, ExternalLink, LoaderCircle, LockKeyhole, RotateCcw, Scale } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api, request } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { bogotaToday, cop, date, dateTime, relative } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/field";
import { Badge, Banner, Card, CardHeader, Dialog, EmptyState, ErrorBanner, PageHeader, Skeleton, SkeletonRows } from "@/components/ui/primitives";
import { ALERT_COPY, CashStatusBadge } from "@/components/nitida/status";
import { Tachometer } from "@/components/illustrations";
import type { CashCount, LeakAlert } from "@/lib/types";

export default function CajaPage() {
  const { can } = useSession();
  if (!can("WORKSHOP_OWNER")) {
    return <EmptyState icon={LockKeyhole} title="Solo el dueño del taller ve la caja" line="El cuadre de caja y las alertas son información del dueño. Si necesita algo, pídaselo." />;
  }
  return (
    <>
      <PageHeader title="Caja y alertas" subtitle="Cuadre el efectivo de cada día y revise los movimientos que merecen una mirada." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-6">
        <CashCountCard />
        <AlertsCard />
      </div>
    </>
  );
}

function CashCountCard() {
  const { workshop } = useSession();
  const days = Array.from({ length: 7 }, (_, i) => bogotaToday(-i));
  const [day, setDay] = useState(days[0]);
  const key = `cash:${workshop?.id}:${day}`;
  const cash = useQuery<CashCount>(key, () => api.get(`/cashdesk/counts/${day}`));
  const [counted, setCounted] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reopen, setReopen] = useState(false);
  const [justClosed, setJustClosed] = useState<string | null>(null);
  const c = cash.data;

  const close = async () => {
    if (counted === "") return setError("Escriba cuánto efectivo contó.");
    setBusy(true);
    setError(null);
    try {
      const r = await api.post<CashCount>(`/cashdesk/counts/${day}/close`, { counted: Number(counted) });
      setJustClosed(day);
      invalidate("cash:", "alerts");
      await cash.refetch();
      toast.success(r.status === "BALANCED" ? "Caja cuadrada" : "Cuadre cerrado con diferencia. Se creó una alerta.");
      setCounted("");
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Cuadre de caja" subtitle="Efectivo esperado: pagos en efectivo no anulados del día, hora de Bogotá." />
      <div className="flex gap-1 overflow-x-auto px-4 pb-3 [scrollbar-width:none] lg:px-5" role="radiogroup" aria-label="Día">
        {days.map((d, i) => (
          <button key={d} role="radio" aria-checked={d === day} onClick={() => { setDay(d); setError(null); }}
            className={`h-10 shrink-0 rounded-md px-3 text-caption font-medium ${d === day ? "bg-primary-soft text-primary-soft-ink" : "text-ink-muted hover:bg-surface-sunken"}`}>
            {i === 0 ? "Hoy" : i === 1 ? "Ayer" : date(d).replace(/ \d{4}$/, "")}{/* "17 nov" */}
          </button>
        ))}
      </div>
      <div className="border-t border-border px-4 py-5 lg:px-5">
        {cash.error ? <ErrorBanner error={cash.error} onRetry={cash.refetch} /> : !c ? (
          <div aria-busy="true"><Skeleton className="h-3 w-24" /><Skeleton className="mt-2 h-9 w-40" /><Skeleton className="mt-6 h-10" /></div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-caption text-ink-muted">Efectivo esperado · {date(c.day)}</p>
                <p className="text-display font-semibold tabular">{cop(c.expected)}</p>
              </div>
              <motion.span
                key={`${c.day}-${c.status}`}
                initial={justClosed === c.day && c.status === "BALANCED" ? { rotate: 6 } : false}
                animate={{ rotate: 0 }}
                transition={{ duration: 0.6, ease: [0.2, 0.8, 0.2, 1] }}
                className={`flex size-12 items-center justify-center rounded-full ${c.status === "BALANCED" ? "bg-verified-soft text-verified-ink" : c.status === "UNBALANCED" ? "bg-attention-soft text-attention-ink" : "bg-surface-sunken text-ink-muted"}`}
              >
                <Scale aria-hidden className="size-6" strokeWidth={1.75} />
              </motion.span>
            </div>
            {c.status === "OPEN" ? (
              <div className="mt-5 flex flex-col gap-3">
                {c.reopenReason && <Banner tone="neutral" title="Día reabierto">{c.reopenReason}</Banner>}
                <Input label="Efectivo contado" inputMode="numeric" prefix="$" placeholder={String(c.expected)} value={counted}
                  onChange={(e) => { setCounted(e.target.value.replace(/\D/g, "").slice(0, 9)); setError(null); }} error={error} className="tabular"
                  hint="Cuente billetes y monedas de la caja y escriba el total." />
                {counted !== "" && (
                  <p className="text-body-sm text-ink-muted">
                    Diferencia: <span className={`tabular font-medium ${Number(counted) === c.expected ? "text-verified-ink" : "text-attention-ink"}`}>{cop(Number(counted) - c.expected)}</span>
                  </p>
                )}
                <Button variant="primary" icon={CheckCheck} loading={busy} loadingText="Cerrando…" onClick={close}>Cerrar el día</Button>
              </div>
            ) : (
              <div className="mt-5 flex flex-col gap-3">
                <div className="flex flex-wrap items-center gap-2"><CashStatusBadge status={c.status} difference={c.difference} /><span className="text-caption text-ink-muted">Cerrado {dateTime(c.closedAt)}</span></div>
                <dl className="grid grid-cols-2 gap-2">
                  <div className="rounded-md bg-surface-sunken p-3"><dt className="text-caption text-ink-muted">Contado</dt><dd className="text-heading font-semibold tabular">{cop(c.counted)}</dd></div>
                  <div className="rounded-md bg-surface-sunken p-3"><dt className="text-caption text-ink-muted">Diferencia</dt><dd className="text-heading font-semibold tabular">{cop(c.difference)}</dd></div>
                </dl>
                <Button variant="ghost" icon={RotateCcw} className="self-start" onClick={() => setReopen(true)}>Reabrir el día</Button>
              </div>
            )}
          </>
        )}
      </div>
      <ReopenDialog open={reopen} day={day} onClose={() => setReopen(false)} onDone={() => { invalidate("cash:"); void cash.refetch(); }} />
    </Card>
  );
}

function ReopenDialog({ open, day, onClose, onDone }: { open: boolean; day: string; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={open} onClose={onClose} title={`¿Reabrir el cuadre del ${date(day)}?`}
      footer={<><Button variant="ghost" onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={RotateCcw} loading={busy} loadingText="Reabriendo…" onClick={async () => {
          if (reason.trim().length < 10) return setError("Explique el motivo (mínimo 10 caracteres).");
          setBusy(true);
          try {
            await api.post(`/cashdesk/counts/${day}/reopen`, { reason: reason.trim() });
            toast.success("Día reabierto");
            setReason("");
            onDone();
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Reabrir el día</Button></>}>
      <p className="mb-4">El cuadre anterior queda en la auditoría. Podrá contar y cerrar de nuevo.</p>
      <Textarea label="Motivo" placeholder="Apareció un pago en efectivo que no se había registrado" value={reason} onChange={(e) => { setReason(e.target.value); setError(null); }} error={error} />
    </Dialog>
  );
}

function AlertsCard() {
  const { workshop } = useSession();
  const [status, setStatus] = useState<"PENDING" | "RESOLVED">("PENDING");
  const [extra, setExtra] = useState<LeakAlert[]>([]);
  const [cursor, setCursor] = useState<string | null | undefined>(undefined);
  const [loadingMore, setLoadingMore] = useState(false);
  const [resolveFor, setResolveFor] = useState<LeakAlert | null>(null);
  const first = useQuery<{ items: LeakAlert[]; next: string | null }>(`alerts:${status}:${workshop?.id}:page`, async () => {
    const r = await request<LeakAlert[]>("GET", `/cashdesk/alerts?status=${status}&limit=10`);
    return { items: r.data, next: r.headers.get("X-Next-Cursor") };
  });
  const next = cursor === undefined ? first.data?.next : cursor;
  const items = [...(first.data?.items ?? []), ...extra];

  const switchTo = (s: "PENDING" | "RESOLVED") => {
    setStatus(s);
    setExtra([]);
    setCursor(undefined);
  };

  return (
    <Card>
      <div id="alertas" className="flex flex-wrap items-start justify-between gap-3 px-4 pt-4 pb-3 lg:px-5">
        <div>
          <h2 className="text-heading font-semibold">Alertas</h2>
          <p className="text-body-sm text-ink-muted">Describen un hecho, no acusan a nadie. Las alertas no se borran: se resuelven con un comentario.</p>
        </div>
        <div role="radiogroup" aria-label="Estado de las alertas" className="grid grid-cols-2 gap-1 rounded-md bg-surface-sunken p-1">
          {(["PENDING", "RESOLVED"] as const).map((s) => (
            <button key={s} role="radio" aria-checked={status === s} onClick={() => switchTo(s)} className={`relative h-9 rounded-sm px-3 text-caption font-medium ${status === s ? "text-ink" : "text-ink-muted"}`}>
              {status === s && <motion.span layoutId="alert-seg" className="absolute inset-0 rounded-sm bg-surface shadow-card" transition={{ duration: 0.18 }} />}
              <span className="relative">{s === "PENDING" ? "Pendientes" : "Resueltas"}</span>
            </button>
          ))}
        </div>
      </div>
      {first.error ? <div className="px-4 pb-4"><ErrorBanner error={first.error} onRetry={first.refetch} /></div> : first.loading ? (
        <SkeletonRows rows={4} label="Cargando alertas…" />
      ) : items.length === 0 ? (
        <EmptyState illustration={<Tachometer level={0.15} tone="verified" />} title={status === "PENDING" ? "Todo en orden" : "Sin alertas resueltas"} line={status === "PENDING" ? "No hay movimientos raros en caja ni en órdenes." : "Cuando resuelva una alerta, quedará aquí con su comentario."} />
      ) : (
        <ul className="divide-y divide-border border-t border-border">
          {items.map((a, i) => {
            const c = ALERT_COPY[a.type];
            return (
              <motion.li key={a.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04, duration: 0.26 }} className="flex gap-3 px-4 py-3 lg:px-5">
                <span className={`mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full ${c.tone === "danger" ? "bg-danger-soft text-danger-ink" : "bg-attention-soft text-attention-ink"}`}>
                  <c.icon aria-hidden className="size-4" strokeWidth={1.75} />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-body-sm font-medium text-ink">{c.title}</p>
                    <span className="font-mono text-caption text-ink-muted">{a.type.slice(0, 2)}</span>
                  </div>
                  <p className="text-body-sm text-ink-muted">{a.details}</p>
                  <p className="mt-0.5 text-caption text-ink-muted">{relative(a.createdAt)}</p>
                  {a.resolvedAt && (
                    <div className="mt-2 rounded-md bg-surface-sunken px-3 py-2 text-caption">
                      <Badge tone="verified" icon={CheckCheck}>Resuelta {relative(a.resolvedAt)}</Badge>
                      <p className="mt-1 text-ink">{a.comment}</p>
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {!a.resolvedAt && <Button size="sm" onClick={() => setResolveFor(a)}>Resolver</Button>}
                  {a.referenceId && (a.type === "F5_STALLED_ORDER" || a.type === "F2_CANCELLED_WITH_PARTS" || a.type === "F6_DUPLICATE_SERIAL" || a.type === "F4_ZERO_TOTAL_WITHOUT_WARRANTY") && (
                    <Link href={`/ordenes/${a.referenceId}`} className="inline-flex h-8 items-center gap-1 rounded-sm px-2 text-caption font-medium text-primary hover:bg-primary-soft">Ver orden <ExternalLink aria-hidden className="size-3.5" /></Link>
                  )}
                  {a.type === "F1_INTAKE_WITHOUT_ORDER" && <Link href="/recepcion" className="inline-flex h-8 items-center gap-1 rounded-sm px-2 text-caption font-medium text-primary hover:bg-primary-soft">Ver recepción</Link>}
                </div>
              </motion.li>
            );
          })}
        </ul>
      )}
      {next && (
        <div className="border-t border-border p-3 text-center">
          <Button variant="ghost" disabled={loadingMore} onClick={async () => {
            setLoadingMore(true);
            try {
              const r = await request<LeakAlert[]>("GET", `/cashdesk/alerts?status=${status}&limit=10&cursor=${encodeURIComponent(next)}`);
              setExtra((x) => [...x, ...r.data]);
              setCursor(r.headers.get("X-Next-Cursor"));
            } finally {
              setLoadingMore(false);
            }
          }}>{loadingMore && <LoaderCircle aria-hidden className="size-4 animate-spin" />}{loadingMore ? "Cargando…" : "Cargar más"}</Button>
        </div>
      )}
      <ResolveDialog alert={resolveFor} onClose={() => setResolveFor(null)} onDone={() => { invalidate("alerts"); setExtra([]); setCursor(undefined); }} />
    </Card>
  );
}

function ResolveDialog({ alert, onClose, onDone }: { alert: LeakAlert | null; onClose: () => void; onDone: () => void }) {
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={!!alert} onClose={onClose} title={alert ? `Resolver: ${ALERT_COPY[alert.type].title.toLowerCase()}` : ""}
      footer={<><Button variant="ghost" onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={CheckCheck} loading={busy} loadingText="Guardando…" onClick={async () => {
          if (comment.trim().length < 5) return setError("Escriba qué revisó (mínimo 5 caracteres).");
          setBusy(true);
          try {
            await api.post(`/cashdesk/alerts/${alert!.id}/resolve`, { comment: comment.trim() });
            toast.success("Alerta resuelta");
            setComment("");
            onDone();
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Marcar como resuelta</Button></>}>
      <p className="mb-4">{alert?.details}</p>
      <Textarea label="Comentario" placeholder="Revisé con recepción: el pago se registró como transferencia por error." value={comment} onChange={(e) => { setComment(e.target.value); setError(null); }} error={error} />
    </Dialog>
  );
}
