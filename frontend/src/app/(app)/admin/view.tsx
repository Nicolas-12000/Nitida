"use client";
import { Building2, LockKeyhole, ShieldCheck, ShieldOff, Undo2 } from "lucide-react";
import { motion } from "motion/react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { WORKSHOP_STATUS_LABEL } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { Button, ButtonLink } from "@/components/ui/button";
import { Textarea } from "@/components/ui/field";
import { Card, Dialog, EmptyState, ErrorBanner, PageHeader, SkeletonRows } from "@/components/ui/primitives";
import type { AdminWorkshop, WorkshopStatus } from "@/lib/types";

const TABS: WorkshopStatus[] = ["PENDING_VERIFICATION", "VERIFIED", "SUSPENDED"];

export default function AdminPage() {
  const { me } = useSession();
  const [status, setStatus] = useState<WorkshopStatus>("PENDING_VERIFICATION");
  const list = useQuery<AdminWorkshop[]>(me.platformAdmin && !me.totpSetupRequired ? `admin:${status}` : null, () => api.get(`/admin/workshops?status=${status}`));
  const [action, setAction] = useState<{ w: AdminWorkshop; to: WorkshopStatus } | null>(null);
  if (!me.platformAdmin) return <EmptyState icon={LockKeyhole} title="Solo administración de Nítida" line="Esta pantalla verifica y suspende talleres de la plataforma." />;
  if (me.totpSetupRequired)
    return (
      <EmptyState icon={ShieldCheck} title="Primero active la verificación en dos pasos" line="Administrar la plataforma exige TOTP. Es un paso de un minuto con una aplicación de autenticación en su celular."
        action={<ButtonLink href="/cuenta#seguridad" variant="primary" icon={ShieldCheck}>Activarla ahora</ButtonLink>} />
    );

  return (
    <>
      <PageHeader title="Talleres de la plataforma" subtitle="Verifique la documentación de cada taller. Un taller verificado firma como tal en los historiales." />
      <div role="radiogroup" aria-label="Estado" className="mb-4 inline-grid grid-cols-3 gap-1 rounded-md bg-surface-sunken p-1">
        {TABS.map((s) => (
          <button key={s} role="radio" aria-checked={status === s} onClick={() => setStatus(s)} className={`relative h-10 rounded-sm px-3 text-caption font-medium ${status === s ? "text-ink" : "text-ink-muted"}`}>
            {status === s && <motion.span layoutId="admin-seg" className="absolute inset-0 rounded-sm bg-surface shadow-card" transition={{ duration: 0.18 }} />}
            <span className="relative">{WORKSHOP_STATUS_LABEL[s].replace(" de verificación", "s").replace("Verificado", "Verificados").replace("Suspendido", "Suspendidos")}</span>
          </button>
        ))}
      </div>
      <Card>
        {list.error ? <div className="p-4"><ErrorBanner error={list.error} onRetry={list.refetch} /></div> : list.loading ? <SkeletonRows rows={3} label="Cargando talleres…" /> : list.data!.length === 0 ? (
          <EmptyState icon={Building2} title="Nada por revisar aquí" line="Cuando un taller se registre aparecerá en Pendientes." />
        ) : (
          <ul className="divide-y divide-border">
            {list.data!.map((w) => (
              <li key={w.id} className="flex flex-wrap items-center gap-3 px-4 py-3 lg:px-5">
                <div className="min-w-0 flex-1">
                  <p className="text-body-sm font-medium text-ink">{w.name}</p>
                  <p className="text-caption text-ink-muted">{w.municipality} · NIT/CC <span className="font-mono">{w.documentNumber}</span> · <span className="font-mono">{w.phone}</span></p>
                </div>
                <div className="flex gap-2">
                  {w.status !== "VERIFIED" && <Button size="sm" icon={ShieldCheck} onClick={() => setAction({ w, to: "VERIFIED" })}>Verificar</Button>}
                  {w.status !== "SUSPENDED" && <Button size="sm" variant="ghost" icon={ShieldOff} onClick={() => setAction({ w, to: "SUSPENDED" })}>Suspender</Button>}
                  {w.status === "SUSPENDED" && <Button size="sm" variant="ghost" icon={Undo2} onClick={() => setAction({ w, to: "PENDING_VERIFICATION" })}>Volver a pendiente</Button>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <StatusDialog action={action} onClose={() => setAction(null)} />
    </>
  );
}

function StatusDialog({ action, onClose }: { action: { w: AdminWorkshop; to: WorkshopStatus } | null; onClose: () => void }) {
  const [evidence, setEvidence] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const suspend = action?.to === "SUSPENDED";
  const verb = action?.to === "VERIFIED" ? "Verificar" : suspend ? "Suspender" : "Volver a pendiente";
  return (
    <Dialog open={!!action} onClose={onClose} destructive={suspend} title={`¿${verb} ${action?.w.name}?`}
      footer={<><Button variant="ghost" data-autofocus onClick={onClose}>Volver</Button>
        <Button variant={suspend ? "danger" : "primary"} loading={busy} loadingText="Guardando…" onClick={async () => {
          if (evidence.trim().length < 5) return setError("Escriba la evidencia o el motivo.");
          setBusy(true);
          try {
            await api.post(`/admin/workshops/${action!.w.id}/status`, { status: action!.to, evidence: evidence.trim() });
            invalidate("admin:");
            toast.success(action!.to === "VERIFIED" ? "Taller verificado" : suspend ? "Taller suspendido" : "Taller en pendiente");
            setEvidence("");
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>{verb}</Button></>}>
      <p className="mb-4">{suspend ? "El taller podrá consultar su información, pero no crear registros nuevos." : "El cambio queda auditado con la evidencia que escriba."}</p>
      <Textarea label={suspend ? "Motivo" : "Evidencia revisada"} placeholder={suspend ? "Documentación falsa reportada por la Cámara de Comercio" : "RUT y Cámara de Comercio vigentes, fachada verificada por videollamada"} value={evidence} onChange={(e) => { setEvidence(e.target.value); setError(null); }} error={error} />
    </Dialog>
  );
}
