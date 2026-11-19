"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowLeft, Banknote, CircleCheck, CircleSlash, History, PackageCheck, Pencil, Plus, Printer, RotateCcw, Save, Trash2, Wrench, X,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError, isCode, messageFor } from "@/lib/errors";
import { JOB_LABEL, cop, date, dateTime, km, motoName, shortId } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { personalDataWarning } from "@/lib/public-text";
import { Button } from "@/components/ui/button";
import { Input, Select, Textarea } from "@/components/ui/field";
import { Banner, Card, CardHeader, CopyButton, Dialog, ErrorBanner, PageHeader, Skeleton, EmptyState } from "@/components/ui/primitives";
import { OrderStatusBadge, PlateTag } from "@/components/nitida/status";
import { VerificationSeal, sealDetail } from "@/components/nitida/history";
import { SealFigure } from "@/components/illustrations";
import { BrokenLinkFigure } from "@/components/illustrations";
import { PartsSection } from "./parts";
import { PaymentsSection } from "./payments";
import type { DeliveryResult, HistoryView, Job, JobType, OrderStatus, OrderView } from "@/lib/types";

const STEPS: { status: OrderStatus; label: string }[] = [
  { status: "OPEN", label: "Abierta" },
  { status: "IN_PROGRESS", label: "En proceso" },
  { status: "COMPLETED", label: "Lista" },
  { status: "DELIVERED", label: "Entregada" },
];

export function OrderDetail() {
  const { id } = useParams<{ id: string }>();
  const { can } = useSession();
  const order = useQuery<OrderView>(`order:${id}`, () => api.get(`/orders/${id}`));
  const o = order.data;
  const history = useQuery<HistoryView>(o ? `history:${o.motorcycleId}` : null, () => api.get(`/motorcycles/${o!.motorcycleId}/history`));
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const [reasonFor, setReasonFor] = useState<"CANCELLED" | "REOPEN" | null>(null);
  const [deliverOpen, setDeliverOpen] = useState(false);
  const [delivered, setDelivered] = useState<DeliveryResult | null>(null);
  const [payOpen, setPayOpen] = useState(false);

  const refresh = async () => {
    invalidate("orders", `history:`);
    return order.refetch();
  };

  if (order.error) {
    return isCode(order.error, "NOT_FOUND") ? (
      <EmptyState illustration={<BrokenLinkFigure />} title="No encontramos esta orden" line="Puede que el enlace esté mal escrito o que la orden no sea de este taller." action={<Link href="/ordenes" className="font-medium text-primary">Ir a órdenes</Link>} />
    ) : <ErrorBanner error={order.error} onRetry={order.refetch} />;
  }
  if (!o) return <OrderSkeleton />;

  const plate = history.data?.motorcycle.plate;
  const editable = o.status === "OPEN" || o.status === "IN_PROGRESS" || o.status === "COMPLETED";
  const worker = can("WORKSHOP_OWNER", "MECHANIC");
  const cashier = can("WORKSHOP_OWNER", "FRONT_DESK");
  const paid = o.paid ?? 0;
  const fullyPaid = o.paid != null && paid === o.total;

  const transition = async (status: OrderStatus, reason?: string) => {
    setBusy(status);
    setActionError(null);
    try {
      await api.post(`/orders/${o.id}/transitions`, { status, reason, version: o.version });
      await refresh();
      toast.success({ IN_PROGRESS: reason ? "Orden reabierta" : "Trabajo empezado", COMPLETED: "Orden lista para entregar", CANCELLED: "Orden cancelada" }[status as "IN_PROGRESS"] ?? "Estado actualizado");
      setReasonFor(null);
    } catch (e) {
      setActionError(e);
      if (isCode(e, "VERSION_CONFLICT")) await order.refetch();
    } finally {
      setBusy(null);
    }
  };

  // Una sola acción principal por pantalla, según estado y rol.
  let primary: React.ReactNode = null;
  if (o.status === "OPEN" && worker) primary = <Button variant="primary" size="lg" icon={Wrench} loading={busy === "IN_PROGRESS"} loadingText="Empezando…" onClick={() => transition("IN_PROGRESS")}>Empezar trabajo</Button>;
  else if (o.status === "IN_PROGRESS" && worker) primary = <Button variant="primary" size="lg" icon={CircleCheck} loading={busy === "COMPLETED"} loadingText="Guardando…" onClick={() => transition("COMPLETED")}>Marcar lista para entregar</Button>;
  else if (o.status === "COMPLETED" && cashier) {
    primary = fullyPaid || o.total === 0
      ? <Button variant="primary" size="lg" icon={PackageCheck} onClick={() => setDeliverOpen(true)}>Entregar moto</Button>
      : <Button variant="primary" size="lg" icon={Banknote} onClick={() => setPayOpen(true)}>Registrar pago de {cop(o.total - paid)}</Button>;
  }

  const stepIndex = STEPS.findIndex((s) => s.status === o.status);

  return (
    <>
      <PageHeader
        back={<Link href="/ordenes" className="mb-2 inline-flex items-center gap-1 text-body-sm text-ink-muted hover:text-ink"><ArrowLeft aria-hidden className="size-4" /> Órdenes</Link>}
        title={<span className="flex flex-wrap items-center gap-3"><span className={o.status === "CANCELLED" ? "line-through" : ""}>Orden #{shortId(o.id)}</span><OrderStatusBadge status={o.status} /></span>}
        subtitle={<span className="flex flex-wrap items-center gap-2">{plate !== undefined && <PlateTag plate={plate} size="sm" />}{history.data && motoName(history.data.motorcycle)} · ingresó con <span className="tabular">{km(o.mileage)}</span></span>}
        action={<div className="hidden lg:block">{primary}</div>}
      />

      {o.status !== "CANCELLED" && (
        <ol className="mb-6 grid grid-cols-4 gap-1" aria-label="Avance de la orden">
          {STEPS.map((s, i) => (
            <li key={s.status} className="flex flex-col gap-1.5">
              <span className="h-1 overflow-hidden rounded-full bg-surface-sunken">
                <motion.span className={`block h-full ${i === 3 && stepIndex === 3 ? "bg-verified" : "bg-primary"}`} initial={false} animate={{ width: i <= stepIndex ? "100%" : "0%" }} transition={{ duration: 0.26, delay: i * 0.06 }} />
              </span>
              <span className={`text-caption ${i <= stepIndex ? "font-medium text-ink" : "text-ink-muted"}`} aria-current={i === stepIndex ? "step" : undefined}>{s.label}</span>
            </li>
          ))}
        </ol>
      )}

      {actionError != null && (
        <div className="mb-4">
          <Banner tone="danger" role="alert" action={isCode(actionError, "INCOMPLETE_PAYMENTS") ? <Button size="sm" onClick={() => setPayOpen(true)}>Registrar pago</Button> : undefined}>
            {messageFor(actionError)}
          </Banner>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
        <div className="flex min-w-0 flex-col gap-4">
          <WorkSection order={o} editable={editable && worker} onSaved={refresh} />
          <PartsSection order={o} editable={editable} onChange={refresh} />
          <PaymentsSection order={o} onChange={refresh} payOpen={payOpen} setPayOpen={setPayOpen} />
          {o.status === "DELIVERED" && (
            <Banner tone="verified" title={`Moto entregada el ${dateTime(o.deliveredAt)}`}>
              El servicio quedó firmado en el historial de la moto y ya no se puede editar. Las correcciones se hacen como enmienda desde el historial.
            </Banner>
          )}
          {worker && (o.status === "OPEN" || o.status === "IN_PROGRESS" || o.status === "COMPLETED") && (
            <div className="flex flex-wrap gap-2">
              {o.status === "COMPLETED" && <Button variant="ghost" icon={RotateCcw} onClick={() => setReasonFor("REOPEN")}>Reabrir trabajo</Button>}
              {o.status !== "COMPLETED" && <Button variant="ghost" icon={CircleSlash} onClick={() => setReasonFor("CANCELLED")}>Cancelar orden</Button>}
            </div>
          )}
        </div>

        <aside className="flex flex-col gap-4">
          <Card>
            <CardHeader title="Historial" action={<Link href={`/motos/${o.motorcycleId}`} className="inline-flex h-10 items-center gap-1 rounded-md px-2 text-body-sm font-medium text-primary hover:bg-primary-soft"><History aria-hidden className="size-4" /> Abrir</Link>} />
            <div className="px-4 pb-4 lg:px-5">
              {history.data ? (
                <>
                  <VerificationSeal verification={history.data.verification} size="md" detail={sealDetail(history.data)} audience="workshop" />
                  <ul className="mt-4 flex flex-col gap-2 border-t border-border pt-3">
                    {history.data.entries.slice(-3).reverse().map((e) => (
                      <li key={e.seq} className="flex items-center justify-between text-caption">
                        <span className="text-ink">#{e.seq} · {e.type === "SERVICE" ? "Servicio" : e.type === "INITIAL_REGISTRATION" ? "Registro inicial" : e.type === "AMENDMENT" ? "Corrección" : "Registro"}</span>
                        <span className="tabular text-ink-muted">{date(e.recordedAt)}{typeof e.content.mileage === "number" ? ` · ${km(e.content.mileage as number)}` : ""}</span>
                      </li>
                    ))}
                  </ul>
                </>
              ) : (
                <div className="flex items-center gap-4"><Skeleton className="size-11 rounded-full!" /><div className="flex-1"><Skeleton className="h-4 w-3/4" /><Skeleton className="mt-2 h-3 w-1/2" /></div></div>
              )}
            </div>
          </Card>
          {o.warrantyDays != null || o.warrantyKm != null ? (
            <Card className="px-4 py-4 lg:px-5">
              <p className="text-caption text-ink-muted">Garantía del trabajo</p>
              <p className="text-body font-medium text-ink">{[o.warrantyDays != null && `${o.warrantyDays} días`, o.warrantyKm != null && km(o.warrantyKm)].filter(Boolean).join(" o ")}</p>
            </Card>
          ) : null}
        </aside>
      </div>

      {primary && (
        <div className="fixed inset-x-0 bottom-[calc(56px+env(safe-area-inset-bottom))] z-20 border-t border-border bg-surface px-4 py-3 lg:hidden">{primary}</div>
      )}

      <ReasonDialog mode={reasonFor} busy={!!busy} onClose={() => setReasonFor(null)} onConfirm={(r) => transition(reasonFor === "CANCELLED" ? "CANCELLED" : "IN_PROGRESS", r)} />
      <DeliverDialog
        open={deliverOpen}
        order={o}
        plate={plate ?? null}
        onClose={() => setDeliverOpen(false)}
        onDone={async (r) => { setDeliverOpen(false); setDelivered(r); await refresh(); }}
        onError={(e) => { setDeliverOpen(false); setActionError(e); if (isCode(e, "VERSION_CONFLICT")) void order.refetch(); }}
      />
      <DeliveredDialog result={delivered} history={history.data} onClose={() => setDelivered(null)} />
    </>
  );
}

function WorkSection({ order: o, editable, onSaved }: { order: OrderView; editable: boolean; onSaved: () => Promise<unknown> }) {
  const [editing, setEditing] = useState(false);
  const [diagnosis, setDiagnosis] = useState(o.diagnosis);
  const [jobs, setJobs] = useState<Job[]>(o.jobs);
  const [labor, setLabor] = useState(String(o.laborCost || ""));
  const [wDays, setWDays] = useState(o.warrantyDays != null ? String(o.warrantyDays) : "");
  const [wKm, setWKm] = useState(o.warrantyKm != null ? String(o.warrantyKm) : "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const start = () => {
    setDiagnosis(o.diagnosis); setJobs(o.jobs.length ? o.jobs : [{ type: "OIL_CHANGE", description: "" }]);
    setLabor(String(o.laborCost || "")); setWDays(o.warrantyDays != null ? String(o.warrantyDays) : ""); setWKm(o.warrantyKm != null ? String(o.warrantyKm) : "");
    setError(null); setEditing(true);
  };

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.patch(`/orders/${o.id}`, {
        version: o.version, diagnosis: diagnosis.trim(), laborCost: Number(labor || 0),
        warrantyDays: wDays ? Number(wDays) : null, warrantyKm: wKm ? Number(wKm) : null,
        jobs: jobs.filter((j) => j.description.trim()).map((j) => ({ type: j.type, description: j.description.trim() })),
      });
      await onSaved();
      toast.success("Cambios guardados");
      setEditing(false);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Diagnóstico y trabajos" action={editable && !editing && <Button variant="ghost" size="sm" icon={Pencil} onClick={start}>Editar</Button>} />
      <AnimatePresence mode="wait" initial={false}>
        {!editing ? (
          <motion.div key="view" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.12 }} className="px-4 pb-4 lg:px-5">
            <p className="max-w-[70ch] text-body text-ink">{o.diagnosis}</p>
            {o.jobs.length > 0 ? (
              <ul className="mt-4 flex flex-col gap-2">
                {o.jobs.map((j, i) => (
                  <li key={i} className="flex items-start gap-3 rounded-md bg-surface-sunken px-3 py-2">
                    <Wrench aria-hidden className="mt-0.5 size-4 text-ink-muted" strokeWidth={1.75} />
                    <span className="text-body-sm"><span className="font-medium text-ink">{JOB_LABEL[j.type]}</span><span className="text-ink-muted"> · {j.description}</span></span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-body-sm text-ink-muted">Aún sin trabajos. Agregue al menos uno antes de marcar la orden como lista.</p>
            )}
            <dl className="mt-4 flex gap-6 border-t border-border pt-3">
              <div><dt className="text-caption text-ink-muted">Mano de obra</dt><dd className="text-body font-medium tabular">{cop(o.laborCost)}</dd></div>
              <div><dt className="text-caption text-ink-muted">Repuestos</dt><dd className="text-body font-medium tabular">{cop(o.total - o.laborCost)}</dd></div>
              <div><dt className="text-caption text-ink-muted">Total</dt><dd className="text-body font-semibold tabular">{cop(o.total)}</dd></div>
            </dl>
          </motion.div>
        ) : (
          <motion.div key="edit" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.12 }} className="flex flex-col gap-4 px-4 pb-4 lg:px-5">
            <Textarea label="Diagnóstico" value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} error={diagnosis.trim().length < 10 ? "Mínimo 10 caracteres." : null} warning={personalDataWarning(diagnosis)} />
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-2 text-body-sm font-medium">Trabajos</legend>
              {jobs.map((j, i) => (
                <div key={i} className="grid grid-cols-[1fr_auto] gap-2 sm:grid-cols-[180px_1fr_auto]">
                  <div className="col-span-2 sm:col-span-1">
                    <Select label={`Tipo del trabajo ${i + 1}`} value={j.type} onChange={(e) => setJobs(jobs.map((x, k) => (k === i ? { ...x, type: e.target.value as JobType } : x)))}>
                      {Object.entries(JOB_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                    </Select>
                  </div>
                  <Input label="Descripción" placeholder="Pastillas delanteras y purga" value={j.description} warning={personalDataWarning(j.description)} onChange={(e) => setJobs(jobs.map((x, k) => (k === i ? { ...x, description: e.target.value } : x)))} />
                  <button type="button" aria-label={`Quitar trabajo ${i + 1}`} onClick={() => setJobs(jobs.filter((_, k) => k !== i))} className="mt-7 flex size-12 items-center justify-center self-start rounded-md text-ink-muted hover:bg-surface-sunken lg:size-10">
                    <Trash2 aria-hidden className="size-[18px]" strokeWidth={1.75} />
                  </button>
                </div>
              ))}
              <Button variant="link" icon={Plus} className="self-start" onClick={() => setJobs([...jobs, { type: "OTHER", description: "" }])}>Agregar trabajo</Button>
            </fieldset>
            <div className="grid grid-cols-3 gap-3">
              <Input label="Mano de obra" inputMode="numeric" prefix="$" placeholder="60000" value={labor} onChange={(e) => setLabor(e.target.value.replace(/\D/g, "").slice(0, 8))} className="tabular" />
              <Input label="Garantía" optional inputMode="numeric" suffix="días" placeholder="30" value={wDays} onChange={(e) => setWDays(e.target.value.replace(/\D/g, "").slice(0, 3))} />
              <Input label="o hasta" optional inputMode="numeric" suffix="km" placeholder="1000" value={wKm} onChange={(e) => setWKm(e.target.value.replace(/\D/g, "").slice(0, 6))} />
            </div>
            {error != null && (
              <Banner tone="danger" role="alert" action={isCode(error, "VERSION_CONFLICT") ? <Button size="sm" onClick={async () => { await onSaved(); setEditing(false); }}>Ver cambios</Button> : undefined}>
                {messageFor(error)}
              </Banner>
            )}
            <div className="flex justify-end gap-2">
              <Button variant="ghost" icon={X} onClick={() => setEditing(false)}>Descartar cambios</Button>
              <Button variant="primary" icon={Save} loading={busy} loadingText="Guardando…" onClick={save}>Guardar cambios</Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Card>
  );
}

function ReasonDialog({ mode, busy, onClose, onConfirm }: { mode: "CANCELLED" | "REOPEN" | null; busy: boolean; onClose: () => void; onConfirm: (reason: string) => void }) {
  const [reason, setReason] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const cancel = mode === "CANCELLED";
  return (
    <Dialog open={!!mode} onClose={onClose} destructive={cancel} title={cancel ? "¿Cancelar esta orden?" : "¿Reabrir el trabajo?"}
      footer={<><Button variant="ghost" data-autofocus onClick={onClose}>Volver</Button>
        <Button variant={cancel ? "danger" : "primary"} icon={cancel ? CircleSlash : RotateCcw} loading={busy} loadingText={cancel ? "Cancelando…" : "Reabriendo…"}
          onClick={() => (reason.trim().length < 5 ? setErr("Escriba el motivo (mínimo 5 caracteres).") : onConfirm(reason.trim()))}>
          {cancel ? "Cancelar orden" : "Reabrir trabajo"}
        </Button></>}>
      <p className="mb-4">{cancel ? "La orden queda cancelada y no se puede retomar. Si ya tenía repuestos, el dueño recibe una alerta para revisarlo." : "La orden vuelve a En proceso. El motivo queda registrado."}</p>
      <Input label="Motivo" placeholder={cancel ? "El cliente no aprobó el presupuesto" : "Sigue sonando la cadena en la prueba de ruta"} value={reason} onChange={(e) => { setReason(e.target.value); setErr(null); }} error={err} />
    </Dialog>
  );
}

function DeliverDialog({ open, order, plate, onClose, onDone, onError }: {
  open: boolean; order: OrderView; plate: string | null; onClose: () => void; onDone: (r: DeliveryResult) => void; onError: (e: unknown) => void;
}) {
  const [busy, setBusy] = useState(false);
  return (
    <Dialog open={open} onClose={onClose} destructive title={`¿Entregar la moto ${plate ? plate.replace(/^(\w{3})/, "$1 ") : ""}?`}
      footer={<><Button variant="ghost" data-autofocus onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={PackageCheck} loading={busy} loadingText="Firmando…" onClick={async () => {
          setBusy(true);
          try {
            onDone(await api.post<DeliveryResult>(`/orders/${order.id}/delivery`, { version: order.version }));
          } catch (e) {
            onError(e instanceof ApiError ? e : e);
          } finally {
            setBusy(false);
          }
        }}>Entregar moto</Button></>}>
      Se firmará el registro en el historial y ya no podrá editarse. Las correcciones se harán como enmienda. Total cobrado: <span className="tabular font-medium text-ink">{cop(order.total)}</span>.
    </Dialog>
  );
}

/** Momento de entrega: el sello se estampa y aparece el código de reclamo, que sale una sola vez. */
function DeliveredDialog({ result, history, onClose }: { result: DeliveryResult | null; history?: HistoryView; onClose: () => void }) {
  return (
    <Dialog open={!!result} onClose={onClose} title="Moto entregada"
      footer={<><Button variant="ghost" icon={Printer} onClick={() => window.print()}>Imprimir recibo</Button><Button variant="primary" onClick={onClose}>Listo, ya lo anoté</Button></>}>
      {result && (
        <div className="flex flex-col items-center text-center text-ink">
          <svg viewBox="0 0 120 120" className="size-28" aria-hidden><SealFigure cx={60} cy={60} r={36} /></svg>
          <p className="mt-2 text-body-sm text-ink-muted">El servicio quedó firmado en el historial{history ? ` (registro #${history.entries.length})` : ""}.</p>
          <div className="mt-5 w-full rounded-lg border border-border bg-surface-sunken p-4">
            <p className="text-caption text-ink-muted">Código para que el propietario agregue la moto a su cuenta</p>
            <p className="mt-1 flex items-center justify-center gap-2 font-mono text-display font-semibold tracking-[0.2em]">
              {result.claimCode}<CopyButton value={result.claimCode} label="Copiar código de reclamo" />
            </p>
            <p className="mt-1 text-caption text-ink-muted">Vence el {date(result.claimCodeExpiresAt)}.</p>
          </div>
          <Banner tone="attention">Este código se muestra una sola vez. Imprímalo en el recibo o entrégueselo al cliente ahora.</Banner>
        </div>
      )}
    </Dialog>
  );
}

export function OrderSkeleton() {
  return (
    <div aria-busy="true">
      <span className="sr-only">Cargando la orden…</span>
      <Skeleton className="h-4 w-20" />
      <Skeleton className="mt-3 h-7 w-1/2" />
      <Skeleton className="mt-2 h-6 w-28" />
      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <div className="flex min-w-0 flex-col gap-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-[120px] rounded-lg!" />)}</div>
        <Skeleton className="h-[200px] rounded-lg!" />
      </div>
    </div>
  );
}
