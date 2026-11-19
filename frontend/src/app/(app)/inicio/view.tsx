"use client";
import Link from "next/link";
import { ChevronRight, CircleCheck, ClipboardList, Inbox, TriangleAlert, Wallet, Wrench, PackageCheck } from "lucide-react";
import { motion } from "motion/react";
import { api } from "@/lib/api";
import { bogotaToday, cop, date, motoName, relative, shortId } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useDelayedFlag, useNow, useQuery } from "@/lib/use-query";
import { Card, CardHeader, ErrorBanner, PageHeader, Skeleton, SkeletonRows, Stat, EmptyState } from "@/components/ui/primitives";
import { ButtonLink } from "@/components/ui/button";
import { ALERT_COPY, OrderStatusBadge, PlateTag } from "@/components/nitida/status";
import type { CashCount, Intake, LeakAlert, OrderSummary, WorkshopInfo } from "@/lib/types";

function greeting() {
  const h = Number(new Intl.DateTimeFormat("es-CO", { hour: "numeric", hour12: false, timeZone: "America/Bogota" }).format(new Date()));
  return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
}

export default function InicioPage() {
  const { me, workshop, role } = useSession();
  const owner = role === "WORKSHOP_OWNER";
  const wsKey = workshop?.id ?? "none";
  const orders = useQuery<OrderSummary[]>(`orders:active:${wsKey}`, () => api.get("/orders?status=OPEN,IN_PROGRESS,COMPLETED&limit=100"));
  const intakes = useQuery<Intake[]>(`intakes:${wsKey}`, () => api.get("/intakes"));
  const settings = useQuery<WorkshopInfo>(workshop ? `workshop:${wsKey}` : null, () => api.get(`/workshops/${workshop!.id}`));
  const alerts = useQuery<LeakAlert[]>(owner ? `alerts:PENDING:${wsKey}` : null, () => api.get("/cashdesk/alerts?status=PENDING&limit=5"));
  const cash = useQuery<CashCount>(owner ? `cash:${wsKey}:${bogotaToday()}` : null, () => api.get(`/cashdesk/counts/${bogotaToday()}`));

  const now = useNow();
  const loading = orders.loading || intakes.loading;
  const { show } = useDelayedFlag(loading);
  const list = orders.data ?? [];
  const ready = list.filter((o) => o.status === "COMPLETED");
  const working = list.filter((o) => o.status === "IN_PROGRESS" || o.status === "OPEN");
  const intakeHours = settings.data?.intakeAlertHours ?? 24;
  const lateIntakes = (intakes.data ?? []).filter((i) => now - new Date(i.createdAt).getTime() > intakeHours * 3_600_000);

  if (!workshop) {
    return (
      <EmptyState icon={ClipboardList} title="Aún no pertenece a un taller" line="Si el dueño de un taller lo invitó, le llegó un código por correo: péguelo en Mi cuenta. Para ver el historial de su moto, vaya a Mis motos." action={<ButtonLink href="/cuenta#unirme" variant="primary">Tengo un código de invitación</ButtonLink>} />
    );
  }

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${me.displayName.split(" ")[0]}`}
        subtitle={`${workshop.name} · ${date(new Date().toISOString())}`}
        action={role !== "MECHANIC" ? <ButtonLink href="/recepcion" variant="primary" icon={Inbox}>Registrar entrada</ButtonLink> : undefined}
      />

      {(orders.error || intakes.error) && <div className="mb-6"><ErrorBanner error={orders.error ?? intakes.error} onRetry={() => { orders.refetch(); intakes.refetch(); }} /></div>}

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4" aria-busy={loading}>
        {loading ? (
          show ? Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="rounded-lg bg-surface-sunken p-4 lg:p-5"><Skeleton className="h-3 w-24" /><Skeleton className="mt-3 h-7 w-12" /></div>
          )) : <div className="col-span-2 h-[104px] lg:col-span-4" />
        ) : (
          <>
            <Stat label="Motos esperando orden" value={intakes.data?.length ?? 0} icon={Inbox} tone={lateIntakes.length ? "attention" : "neutral"}
              hint={lateIntakes.length ? `${lateIntakes.length} con más de ${intakeHours} h` : "Al día"} />
            <Stat label="En taller" value={working.length} icon={Wrench} tone="primary" hint={`${list.filter((o) => o.status === "OPEN").length} sin empezar`} />
            <Stat label="Listas para entregar" value={ready.length} icon={CircleCheck} tone={ready.length ? "verified" : "neutral"} hint={ready.length ? cop(ready.reduce((s, o) => s + o.total, 0)) + " por cobrar" : "Ninguna pendiente"} />
            {owner ? (
              <Stat label="Alertas pendientes" value={alerts.data?.length ?? "—"} icon={TriangleAlert} tone={alerts.data?.length ? "attention" : "neutral"} hint={alerts.data?.length ? "Revíselas en Caja" : "Todo en orden"} />
            ) : (
              <Stat label="Actualizadas hoy" value={list.filter((o) => o.updatedAt.slice(0, 10) === new Date().toISOString().slice(0, 10)).length} icon={PackageCheck} />
            )}
          </>
        )}
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="Qué sigue" subtitle="Lo más urgente primero: entregas, motos sin orden y trabajos en curso." action={<ButtonLink href="/ordenes" variant="ghost" size="sm">Ver tablero</ButtonLink>} />
          {loading ? (
            show ? <SkeletonRows rows={4} label="Cargando pendientes…" /> : <div className="h-56" />
          ) : ready.length + (intakes.data?.length ?? 0) + working.length === 0 ? (
            <EmptyState icon={ClipboardList} title="Aún no hay órdenes" line="Las órdenes se crean al registrar la entrada de una moto." action={<ButtonLink href="/recepcion" variant="secondary" icon={Inbox}>Registrar entrada</ButtonLink>} />
          ) : (
            <ul className="divide-y divide-border border-t border-border">
              {ready.map((o, i) => <OrderRow key={o.id} order={o} i={i} lead="Cobrar y entregar" />)}
              {(intakes.data ?? []).map((it, i) => (
                <motion.li key={it.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: (ready.length + i) * 0.04, duration: 0.26 }}>
                  <Link href={`/recepcion?abrir=${it.id}`} className="flex min-h-14 items-center gap-3 px-4 py-3 hover:bg-surface-sunken lg:px-5">
                    <PlateTag plate={it.plate} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-body-sm font-medium text-ink">{motoName(it)} · abrir orden</span>
                      <span className="block text-caption text-ink-muted">Recibida {relative(it.createdAt)}</span>
                    </span>
                    {lateIntakes.includes(it) ? <span className="text-caption font-medium text-attention-ink">Esperando</span> : <span className="text-caption text-ink-muted">Nueva</span>}
                    <ChevronRight aria-hidden className="size-4 text-ink-muted" />
                  </Link>
                </motion.li>
              ))}
              {working.slice(0, 6).map((o, i) => <OrderRow key={o.id} order={o} i={ready.length + (intakes.data?.length ?? 0) + i} />)}
            </ul>
          )}
        </Card>

        <div className="flex flex-col gap-4">
          {owner && (
            <Card>
              <CardHeader title="Caja de hoy" action={<ButtonLink href="/caja" variant="ghost" size="sm" icon={Wallet}>Abrir</ButtonLink>} />
              <div className="px-4 pb-4 lg:px-5">
                {cash.data ? (
                  <>
                    <p className="text-caption text-ink-muted">Efectivo esperado</p>
                    <p className="text-display font-semibold tabular">{cop(cash.data.expected)}</p>
                    <p className="mt-1 text-caption text-ink-muted">{cash.data.status === "OPEN" ? "Sin cerrar. Cuente el efectivo al final del día." : "Día cerrado."}</p>
                  </>
                ) : (
                  <><Skeleton className="h-3 w-24" /><Skeleton className="mt-2 h-8 w-32" /></>
                )}
              </div>
            </Card>
          )}
          {owner && (
            <Card>
              <CardHeader title="Alertas" action={<ButtonLink href="/caja#alertas" variant="ghost" size="sm">Ver todas</ButtonLink>} />
              {alerts.data && alerts.data.length === 0 ? (
                <p className="px-4 pb-4 text-body-sm text-ink-muted lg:px-5">Todo en orden. No hay movimientos raros en caja ni en órdenes.</p>
              ) : (
                <ul className="divide-y divide-border border-t border-border">
                  {(alerts.data ?? []).slice(0, 3).map((a) => {
                    const c = ALERT_COPY[a.type];
                    return (
                      <li key={a.id} className="flex gap-3 px-4 py-3 lg:px-5">
                        <c.icon aria-hidden className={`mt-0.5 size-4 shrink-0 ${c.tone === "danger" ? "text-danger" : "text-attention-ink"}`} strokeWidth={1.75} />
                        <div className="min-w-0">
                          <p className="text-body-sm font-medium text-ink">{c.title}</p>
                          <p className="text-caption text-ink-muted">{a.details}</p>
                        </div>
                      </li>
                    );
                  })}
                  {!alerts.data && <SkeletonRows rows={2} label="Cargando alertas…" />}
                </ul>
              )}
            </Card>
          )}
          {!owner && (
            <Card>
              <CardHeader title="Su rol" />
              <p className="px-4 pb-4 text-body-sm text-ink-muted lg:px-5">
                {role === "MECHANIC"
                  ? "Usted abre órdenes, registra trabajos y repuestos y marca la orden como lista. Los pagos y la caja los maneja recepción."
                  : "Usted registra la entrada de las motos, cobra y entrega. Al entregar se firma el servicio en el historial de la moto."}
              </p>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function OrderRow({ order: o, i, lead }: { order: OrderSummary; i: number; lead?: string }) {
  return (
    <motion.li initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04, duration: 0.26 }}>
      <Link href={`/ordenes/${o.id}`} className="flex min-h-14 items-center gap-3 px-4 py-3 hover:bg-surface-sunken lg:px-5">
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body-sm font-medium text-ink">{lead ? `${lead} · ` : ""}{o.diagnosis}</span>
          <span className="block text-caption text-ink-muted">Orden #{shortId(o.id)} · {relative(o.updatedAt)}{o.total ? ` · ${cop(o.total)}` : ""}</span>
        </span>
        <OrderStatusBadge status={o.status} />
        <ChevronRight aria-hidden className="size-4 text-ink-muted" />
      </Link>
    </motion.li>
  );
}
