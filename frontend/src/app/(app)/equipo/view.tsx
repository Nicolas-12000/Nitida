"use client";
import { LockKeyhole, Send, UserMinus, UserPlus, Users } from "lucide-react";
import { motion } from "motion/react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { ROLE_LABEL, dateTime } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Choice, Input } from "@/components/ui/field";
import { Badge, Banner, Card, Dialog, EmptyState, ErrorBanner, PageHeader, SkeletonRows } from "@/components/ui/primitives";
import type { InvitationSent, Member } from "@/lib/types";
import { DemoMail } from "@/components/nitida/demo-mail";

const CAN: Record<string, string[]> = {
  WORKSHOP_OWNER: ["Todo lo del taller", "Caja, alertas y ajustes", "Invitar y retirar personas"],
  FRONT_DESK: ["Registrar entradas y clientes", "Cobrar, anular pagos y entregar", "Compartir historiales"],
  MECHANIC: ["Abrir y trabajar órdenes", "Repuestos, seriales y evidencia", "Corregir el historial"],
};

export default function EquipoPage() {
  const { workshop, can, me } = useSession();
  const members = useQuery<Member[]>(can("WORKSHOP_OWNER") ? `members:${workshop?.id}` : null, () => api.get(`/workshops/${workshop!.id}/members`));
  const [invite, setInvite] = useState(false);
  const [removeFor, setRemoveFor] = useState<Member | null>(null);

  if (!can("WORKSHOP_OWNER")) return <EmptyState icon={LockKeyhole} title="Solo el dueño administra el equipo" line="Si necesita que inviten a alguien, pídaselo al dueño del taller." />;
  const active = (members.data ?? []).filter((m) => m.active);

  return (
    <>
      <PageHeader title="Equipo" subtitle="Cada persona entra con su propio usuario. Lo que puede hacer depende de su rol." action={<Button variant="primary" icon={UserPlus} onClick={() => setInvite(true)}>Invitar persona</Button>} />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3 lg:gap-6">
        <Card className="lg:col-span-2">
          {members.error ? <div className="p-4"><ErrorBanner error={members.error} onRetry={members.refetch} /></div> : members.loading ? <SkeletonRows rows={3} label="Cargando equipo…" /> : active.length <= 1 ? (
            <EmptyState icon={Users} title="Invite a su equipo" line="Cada persona entra con su propio usuario y permisos." action={<Button icon={UserPlus} onClick={() => setInvite(true)}>Invitar persona</Button>} />
          ) : (
            <ul className="divide-y divide-border">
              {active.map((m, i) => (
                <motion.li key={m.userId} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.05 }} className="flex min-h-16 items-center gap-3 px-4 py-3 lg:px-5">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-sunken text-caption font-semibold text-ink-muted" aria-hidden>
                    {m.displayName.split(" ").map((w) => w[0]).slice(0, 2).join("")}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-body-sm font-medium text-ink">{m.displayName}{m.userId === me.id && <span className="text-ink-muted"> (usted)</span>}</p>
                    <p className="truncate text-caption text-ink-muted">{m.email}</p>
                  </div>
                  <Badge tone={m.role === "WORKSHOP_OWNER" ? "primary" : "neutral"}>{ROLE_LABEL[m.role]}</Badge>
                  {m.userId !== me.id && (
                    <button type="button" aria-label={`Retirar a ${m.displayName}`} title="Retirar del taller" onClick={() => setRemoveFor(m)} className="flex size-10 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken hover:text-ink">
                      <UserMinus aria-hidden className="size-[18px]" strokeWidth={1.75} />
                    </button>
                  )}
                </motion.li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="p-5">
          <h2 className="text-body-sm font-semibold text-ink">Qué puede hacer cada rol</h2>
          <dl className="mt-3 flex flex-col gap-4">
            {Object.entries(CAN).map(([role, items]) => (
              <div key={role}>
                <dt className="text-body-sm font-medium text-ink">{ROLE_LABEL[role]}</dt>
                <dd><ul className="mt-1 list-disc pl-5 text-caption text-ink-muted">{items.map((t) => <li key={t}>{t}</li>)}</ul></dd>
              </div>
            ))}
          </dl>
          <p className="mt-4 text-caption text-ink-muted">Los mecánicos no ven pagos ni la caja.</p>
        </Card>
      </div>
      <InviteDialog open={invite} onClose={() => setInvite(false)} />
      <Dialog open={!!removeFor} onClose={() => setRemoveFor(null)} destructive title={`¿Retirar a ${removeFor?.displayName}?`}
        footer={<><Button variant="ghost" data-autofocus onClick={() => setRemoveFor(null)}>Volver</Button>
          <Button variant="danger" icon={UserMinus} onClick={async () => {
            try {
              await api.del(`/workshops/${workshop!.id}/members/${removeFor!.userId}`);
              invalidate("members");
              toast.success("Persona retirada del taller");
            } catch (e) {
              toast.error(messageFor(e));
            }
            setRemoveFor(null);
          }}>Retirar del taller</Button></>}>
        Pierde el acceso a este taller de inmediato. Lo que registró sigue en el historial con su firma.
      </Dialog>
    </>
  );
}

function InviteDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { workshop } = useSession();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<"MECHANIC" | "FRONT_DESK" | null>(null);
  const [sent, setSent] = useState<InvitationSent | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const close = () => { setEmail(""); setRole(null); setSent(null); setError(null); onClose(); };
  return (
    <Dialog open={open} onClose={close} title={sent ? "Invitación enviada" : "Invitar persona"}
      footer={sent ? <><span /><Button variant="primary" onClick={close}>Listo</Button></> : <><Button variant="ghost" onClick={close}>Volver</Button>
        <Button variant="primary" icon={Send} loading={busy} loadingText="Enviando…" onClick={async () => {
          if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return setError("Revise el correo: no parece válido.");
          if (!role) return setError("Elija el rol.");
          setBusy(true);
          setError(null);
          try {
            setSent(await api.post<InvitationSent>(`/workshops/${workshop!.id}/invitations`, { email: email.trim(), role }));
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Enviar invitación</Button></>}>
      {!sent ? (
        <div className="flex flex-col gap-4 text-ink">
          <Input label="Correo" type="email" inputMode="email" autoComplete="off" placeholder="mecanico@correo.co" value={email} onChange={(e) => setEmail(e.target.value)}
            hint="Le llegará un código por correo. Lo pega en Mi cuenta después de ingresar con ese mismo correo." />
          <Choice label="Rol" value={role} onChange={setRole} options={[{ value: "MECHANIC", label: "Mecánico", hint: "Sin acceso a pagos" }, { value: "FRONT_DESK", label: "Recepción", hint: "Recibe, cobra y entrega" }]} />
          {error && <Banner tone="danger">{error}</Banner>}
        </div>
      ) : (
        <div className="flex flex-col gap-3 text-ink">
          <Banner tone="verified" title={`Le enviamos el código a ${email}`}>
            Vence el {dateTime(sent.expiresAt)}. Si no tiene cuenta, que la cree con ese correo y luego pegue el código en Mi cuenta, en “Unirme a un taller”.
          </Banner>
          <p className="text-caption text-ink-muted">Por seguridad, usted no ve el código: solo lo recibe la persona invitada.</p>
          <DemoMail to={email} />
        </div>
      )}
    </Dialog>
  );
}
