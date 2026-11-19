"use client";
import { LockKeyhole, Save, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { WORKSHOP_STATUS_LABEL, cop, date } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Badge, Banner, Card, CardHeader, EmptyState, ErrorBanner, PageHeader, Skeleton } from "@/components/ui/primitives";
import type { WorkshopInfo } from "@/lib/types";

export default function AjustesPage() {
  const { workshop, can } = useSession();
  const info = useQuery<WorkshopInfo>(workshop ? `workshop:${workshop.id}` : null, () => api.get(`/workshops/${workshop!.id}`));
  if (!can("WORKSHOP_OWNER")) return <EmptyState icon={LockKeyhole} title="Solo el dueño cambia los ajustes" line="Los ajustes definen cuándo se generan las alertas del taller." />;
  return (
    <>
      <PageHeader title="Ajustes del taller" subtitle="Cuándo avisar y cuánta diferencia de caja se tolera. Valores por defecto razonables; cámbielos solo si lo necesita." />
      {info.error ? <ErrorBanner error={info.error} onRetry={info.refetch} /> : !info.data ? (
        <div aria-busy="true" className="grid grid-cols-1 gap-4 lg:grid-cols-3"><Skeleton className="h-72 rounded-lg! lg:col-span-2" /><Skeleton className="h-40 rounded-lg!" /></div>
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3 lg:gap-6">
          <SettingsForm key={info.data.version ?? 0} info={info.data} onSaved={() => { invalidate("workshop"); void info.refetch(); }} />
          <Card className="p-5">
            <p className="text-caption text-ink-muted">Estado en Nítida</p>
            <div className="mt-1 flex items-center gap-2">
              <Badge tone={info.data.status === "VERIFIED" ? "verified" : info.data.status === "SUSPENDED" ? "danger" : "attention"} icon={info.data.status === "VERIFIED" ? ShieldCheck : undefined}>
                {WORKSHOP_STATUS_LABEL[info.data.status]}
              </Badge>
            </div>
            {info.data.verifiedAt && <p className="mt-2 text-caption text-ink-muted">Verificado el {date(info.data.verifiedAt)}.</p>}
            <p className="mt-4 text-body-sm text-ink-muted">Un taller verificado firma sus registros como “taller verificado” en el historial público de cada moto.</p>
          </Card>
        </div>
      )}
    </>
  );
}

function SettingsForm({ info, onSaved }: { info: WorkshopInfo; onSaved: () => void }) {
  const [hours, setHours] = useState(String(info.intakeAlertHours));
  const [tolerance, setTolerance] = useState(String(info.cashTolerance));
  const [days, setDays] = useState(String(info.stalledDays));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const h = Number(hours), t = Number(tolerance), d = Number(days);
  const errs = {
    hours: !(h >= 1 && h <= 168) ? "Entre 1 y 168 horas." : null,
    tolerance: !(t >= 0) || tolerance === "" ? "Escriba un valor; 0 si no tolera diferencias." : null,
    days: !(d >= 1 && d <= 90) ? "Entre 1 y 90 días." : null,
  };
  return (
    <Card className="lg:col-span-2">
      <CardHeader title={info.name} subtitle={info.municipality} />
      <form className="flex flex-col gap-5 px-4 pb-5 lg:px-5" onSubmit={async (e) => {
        e.preventDefault();
        if (errs.hours || errs.tolerance || errs.days) return;
        setBusy(true);
        setError(null);
        try {
          await api.patch(`/workshops/${info.id}/settings`, { intakeAlertHours: h, cashTolerance: t, stalledDays: d, version: info.version ?? 0 });
          toast.success("Ajustes guardados");
          onSaved();
        } catch (err) {
          setError(err);
        } finally {
          setBusy(false);
        }
      }}>
        <Input label="Avisar si una moto lleva recibida sin orden más de" inputMode="numeric" suffix="horas" value={hours} onChange={(e) => setHours(e.target.value.replace(/\D/g, "").slice(0, 3))} error={errs.hours} hint="Genera la alerta “Moto recibida sin orden”." className="tabular" />
        <Input label="Diferencia de caja tolerada" inputMode="numeric" prefix="$" value={tolerance} onChange={(e) => setTolerance(e.target.value.replace(/\D/g, "").slice(0, 7))} error={errs.tolerance} hint={`Un cuadre con diferencia mayor a ${cop(t || 0)} genera la alerta “Descuadre en caja”.`} className="tabular" />
        <Input label="Avisar si una orden no se mueve en" inputMode="numeric" suffix="días" value={days} onChange={(e) => setDays(e.target.value.replace(/\D/g, "").slice(0, 2))} error={errs.days} hint="Genera la alerta “Orden quieta”." className="tabular" />
        {error != null && <Banner tone="danger" role="alert">{isCode(error, "VERSION_CONFLICT") ? "Alguien más cambió los ajustes mientras los editaba. Recargue y aplique los suyos de nuevo." : messageFor(error)}</Banner>}
        <Button type="submit" variant="primary" icon={Save} loading={busy} loadingText="Guardando…" className="self-start">Guardar ajustes</Button>
      </form>
    </Card>
  );
}
