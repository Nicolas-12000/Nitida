"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, ChevronRight, CircleCheck, Inbox, LoaderCircle, Wrench } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api, request } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { cop, relative, shortId } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, setQueryData, useDelayedFlag, useQuery } from "@/lib/use-query";
import { Button, ButtonLink } from "@/components/ui/button";
import { Banner, Card, EmptyState, ErrorBanner, PageHeader, Skeleton, SkeletonRows } from "@/components/ui/primitives";
import { ORDER_STATUS, OrderStatusBadge } from "@/components/nitida/status";
import { EmptyOrdersFigure } from "@/components/illustrations";
import type { OrderStatus, OrderSummary, OrderView } from "@/lib/types";

const COLUMNS: { status: OrderStatus; hint: string }[] = [
  { status: "OPEN", hint: "Recibidas, sin empezar" },
  { status: "IN_PROGRESS", hint: "El mecánico está trabajando" },
  { status: "COMPLETED", hint: "Cobrar y entregar" },
];
const NEXT: Partial<Record<OrderStatus, { to: OrderStatus; label: string; icon: typeof Wrench }>> = {
  OPEN: { to: "IN_PROGRESS", label: "Empezar", icon: Wrench },
  IN_PROGRESS: { to: "COMPLETED", label: "Marcar lista", icon: CircleCheck },
};

export default function OrdenesPage() {
  const router = useRouter();
  const { workshop, can } = useSession();
  const key = `orders:active:${workshop?.id ?? "none"}`;
  const active = useQuery<OrderSummary[]>(key, () => api.get("/orders?status=OPEN,IN_PROGRESS,COMPLETED&limit=100"));
  const { show } = useDelayedFlag(active.loading);
  const [filter, setFilter] = useState<OrderStatus>("IN_PROGRESS");
  const [dragging, setDragging] = useState<string | null>(null);
  const [over, setOver] = useState<OrderStatus | null>(null);
  const [flash, setFlash] = useState<string | null>(null);
  const [moveError, setMoveError] = useState<string | null>(null);
  const canMove = can("WORKSHOP_OWNER", "MECHANIC");

  /** Avanzar estado: optimista y reversible (DESIGN.MD §7.4). */
  const move = async (o: OrderSummary, to: OrderStatus) => {
    if (NEXT[o.status]?.to !== to) {
      if (o.status !== to) toast("Esa orden no puede pasar directo a ese estado. Ábrala para ver sus opciones.");
      return;
    }
    setMoveError(null);
    const prev = active.data;
    setQueryData<OrderSummary[]>(key, (old) => (old ?? []).map((x) => (x.id === o.id ? { ...x, status: to, updatedAt: new Date().toISOString() } : x)));
    setFlash(o.id);
    setTimeout(() => setFlash(null), 600);
    try {
      const detail = await api.get<OrderView>(`/orders/${o.id}`);
      await api.post(`/orders/${o.id}/transitions`, { status: to, version: detail.version });
      invalidate("orders", "order:");
    } catch (e) {
      setQueryData<OrderSummary[]>(key, () => prev ?? []);
      setMoveError(`Orden #${shortId(o.id)}: ${messageFor(e)}`);
    }
  };

  const byStatus = (s: OrderStatus) => (active.data ?? []).filter((o) => o.status === s);

  return (
    <>
      <PageHeader
        title="Órdenes"
        subtitle={canMove ? "Arrastre una tarjeta a la siguiente columna o use su botón para avanzarla." : "Estado de los trabajos del taller."}
        action={can("WORKSHOP_OWNER", "FRONT_DESK", "MECHANIC") && <ButtonLink href="/recepcion" variant="primary" icon={Inbox}>Registrar entrada</ButtonLink>}
      />
      {moveError && <div className="mb-4"><Banner tone="danger" role="alert" action={<Button variant="ghost" size="sm" onClick={() => setMoveError(null)}>Entendido</Button>}>{moveError}</Banner></div>}
      {active.error && <div className="mb-4"><ErrorBanner error={active.error} onRetry={active.refetch} /></div>}

      {/* Móvil: filtro por estado + lista */}
      <div className="lg:hidden">
        <div role="radiogroup" aria-label="Estado" className="mb-3 grid grid-cols-3 gap-1 rounded-md bg-surface-sunken p-1">
          {COLUMNS.map((c) => (
            <button key={c.status} role="radio" aria-checked={filter === c.status} onClick={() => setFilter(c.status)}
              className={`relative h-10 rounded-sm text-caption font-medium ${filter === c.status ? "text-ink" : "text-ink-muted"}`}>
              {filter === c.status && <motion.span layoutId="seg" className="absolute inset-0 rounded-sm bg-surface shadow-card" transition={{ duration: 0.18 }} />}
              <span className="relative">{ORDER_STATUS[c.status].label.replace(" para entregar", "s")} <span className="tabular">({byStatus(c.status).length})</span></span>
            </button>
          ))}
        </div>
        <Card>
          {active.loading ? (show ? <SkeletonRows rows={5} label="Cargando órdenes…" /> : <div className="h-72" />) : byStatus(filter).length === 0 ? (
            <EmptyState illustration={<EmptyOrdersFigure />} title="Nada en esta columna" line="Las órdenes se crean al registrar la entrada de una moto." />
          ) : (
            <ul className="divide-y divide-border">
              {byStatus(filter).map((o) => (
                <li key={o.id}>
                  <Link href={`/ordenes/${o.id}`} className="flex min-h-14 items-center gap-3 px-4 py-3">
                    <span className="min-w-0 flex-1">
                      <span className="line-clamp-2 text-body-sm font-medium text-ink">{o.diagnosis}</span>
                      <span className="block text-caption text-ink-muted">#{shortId(o.id)} · {relative(o.updatedAt)}</span>
                    </span>
                    <span className="text-body-sm tabular text-ink">{cop(o.total)}</span>
                    <ChevronRight aria-hidden className="size-4 text-ink-muted" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {/* Escritorio: kanban */}
      <div className="hidden gap-4 lg:grid lg:grid-cols-3">
        {COLUMNS.map((col) => {
          const items = byStatus(col.status);
          const S = ORDER_STATUS[col.status];
          return (
            <section
              key={col.status}
              aria-label={S.label}
              onDragOver={(e) => { if (canMove) { e.preventDefault(); setOver(col.status); } }}
              onDragLeave={() => setOver((o) => (o === col.status ? null : o))}
              onDrop={(e) => {
                e.preventDefault();
                setOver(null);
                const o = active.data?.find((x) => x.id === e.dataTransfer.getData("text/plain"));
                if (o) void move(o, col.status);
              }}
              className={`flex min-h-[420px] flex-col rounded-lg border p-2 transition-colors duration-[120ms] ${over === col.status ? "border-primary bg-primary-soft/50" : "border-border bg-surface-sunken"}`}
            >
              <header className="flex items-center justify-between px-2 pt-1 pb-3">
                <div>
                  <h2 className="flex items-center gap-2 text-body-sm font-semibold text-ink">
                    <S.icon aria-hidden className="size-4 text-ink-muted" strokeWidth={1.75} />{S.label}
                    <span className="rounded-xs bg-surface px-1.5 text-caption tabular text-ink-muted">{items.length}</span>
                  </h2>
                  <p className="text-caption text-ink-muted">{col.hint}</p>
                </div>
              </header>
              <ul className="flex flex-1 flex-col gap-2">
                {active.loading && show && [0, 1, 2].map((i) => <li key={i} className="card p-3"><Skeleton className="h-3.5" /><Skeleton className="mt-2 h-3 w-2/5" /></li>)}
                <AnimatePresence initial={false}>
                  {items.map((o) => (
                    <motion.li
                      key={o.id}
                      layout
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: dragging === o.id ? 0.5 : 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.18 }}
                    >
                      <div
                        draggable={canMove}
                        onDragStart={(e) => { e.dataTransfer.setData("text/plain", o.id); e.dataTransfer.effectAllowed = "move"; setDragging(o.id); }}
                        onDragEnd={() => setDragging(null)}
                        onClick={() => router.push(`/ordenes/${o.id}`)}
                        className={`card group cursor-pointer p-3 transition-[box-shadow,transform,background-color] duration-[600ms] hover:-translate-y-px hover:shadow-raised ${canMove ? "active:cursor-grabbing" : ""} ${flash === o.id ? "bg-primary-soft!" : ""}`}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <Link href={`/ordenes/${o.id}`} onClick={(e) => e.stopPropagation()} className="line-clamp-2 text-body-sm font-medium text-ink hover:underline">{o.diagnosis}</Link>
                        </div>
                        <div className="mt-2 flex items-center justify-between gap-2">
                          <span className="min-w-0 truncate font-mono text-caption text-ink-muted">#{shortId(o.id)} · {relative(o.updatedAt)}</span>
                          <span className="shrink-0 whitespace-nowrap text-body-sm font-medium tabular text-ink">{cop(o.total)}</span>
                        </div>
                        {canMove && NEXT[o.status] && (
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); void move(o, NEXT[o.status]!.to); }}
                            className="mt-2 inline-flex h-8 items-center gap-1 rounded-sm px-2 text-caption font-medium text-primary opacity-80 hover:bg-primary-soft hover:opacity-100 focus-visible:opacity-100"
                          >
                            {NEXT[o.status]!.label} <ArrowRight aria-hidden className="size-3.5" />
                          </button>
                        )}
                      </div>
                    </motion.li>
                  ))}
                </AnimatePresence>
                {!active.loading && items.length === 0 && (
                  <li className="flex flex-1 items-center justify-center rounded-md border border-dashed border-border-strong p-6 text-center text-caption text-ink-muted">
                    {col.status === "COMPLETED" ? "Cuando una orden esté lista aparecerá aquí para cobrar y entregar." : "Sin órdenes"}
                  </li>
                )}
              </ul>
            </section>
          );
        })}
      </div>

      <ClosedOrders />
    </>
  );
}

/** Entregadas y canceladas, paginadas por cursor (X-Next-Cursor). */
function ClosedOrders() {
  const { workshop } = useSession();
  const [pages, setPages] = useState<OrderSummary[][]>([]);
  const [cursor, setCursor] = useState<string | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const first = useQuery<{ items: OrderSummary[]; next: string | null }>(`orders:closed:${workshop?.id}`, async () => {
    const r = await request<OrderSummary[]>("GET", "/orders?status=DELIVERED,CANCELLED&limit=8");
    return { items: r.data, next: r.headers.get("X-Next-Cursor") };
  });
  const next = cursor === undefined ? first.data?.next : cursor;
  const items = [...(first.data?.items ?? []), ...pages.flat()];

  const more = async () => {
    if (!next) return;
    setBusy(true);
    try {
      const r = await request<OrderSummary[]>("GET", `/orders?status=DELIVERED,CANCELLED&limit=8&cursor=${encodeURIComponent(next)}`);
      setPages((p) => [...p, r.data]);
      setCursor(r.headers.get("X-Next-Cursor"));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="mt-6">
      <div className="px-4 pt-4 pb-3 lg:px-5">
        <h2 className="text-heading font-semibold">Cerradas</h2>
        <p className="text-body-sm text-ink-muted">Entregadas y canceladas, las más recientes primero.</p>
      </div>
      {first.loading ? <SkeletonRows rows={3} label="Cargando órdenes cerradas…" /> : items.length === 0 ? (
        <p className="border-t border-border px-5 py-4 text-body-sm text-ink-muted">Aún no hay órdenes cerradas.</p>
      ) : (
        <ul className="divide-y divide-border border-t border-border">
          {items.map((o) => (
            <li key={o.id}>
              <Link href={`/ordenes/${o.id}`} className="flex min-h-14 items-center gap-3 px-4 py-3 hover:bg-surface-sunken lg:px-5">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-body-sm font-medium text-ink">{o.diagnosis}</span>
                  <span className="block text-caption text-ink-muted">
                    <span className={o.status === "CANCELLED" ? "line-through" : ""}>#{shortId(o.id)}</span> · {relative(o.updatedAt)}
                  </span>
                </span>
                <span className="hidden text-body-sm tabular text-ink sm:block">{cop(o.total)}</span>
                <OrderStatusBadge status={o.status} />
                <ChevronRight aria-hidden className="size-4 text-ink-muted" />
              </Link>
            </li>
          ))}
        </ul>
      )}
      {error != null && <div className="px-4 pb-4"><ErrorBanner error={error} onRetry={more} /></div>}
      {next && (
        <div className="border-t border-border p-3 text-center">
          <Button variant="ghost" onClick={more} disabled={busy}>
            {busy ? <LoaderCircle aria-hidden className="size-4 animate-spin" /> : null}
            {busy ? "Cargando…" : "Cargar más"}
          </Button>
        </div>
      )}
    </Card>
  );
}
