"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Mail, UserCheck } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { ROLE_LABEL } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Badge, Banner, Card, CardHeader, PageHeader } from "@/components/ui/primitives";
import { TotpSetupPanel } from "@/components/nitida/totp-setup";

export default function CuentaPage() {
  const { me, workshops } = useSession();
  return (
    <>
      <PageHeader title="Mi cuenta" subtitle="Sus datos de ingreso, la verificación en dos pasos y los talleres a los que pertenece." />
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
        <div className="flex min-w-0 flex-col gap-4">
          <Card>
            <div id="seguridad" className="scroll-mt-20" />
            <CardHeader title="Verificación en dos pasos" subtitle="Protege su cuenta aunque alguien conozca su contraseña." />
            <div className="px-4 pb-5 lg:px-5">
              {me.totpSetupRequired && (
                <div className="mb-4"><Banner tone="attention" title="Obligatoria para administrar la plataforma">Mientras no la active, puede ingresar pero no verificar ni suspender talleres.</Banner></div>
              )}
              <TotpSetupPanel />
            </div>
          </Card>
          <JoinWorkshop />
        </div>
        <aside className="flex flex-col gap-4">
          <Card className="p-5">
            <p className="text-caption text-ink-muted">Nombre</p>
            <p className="text-body font-medium text-ink">{me.displayName}</p>
            <p className="mt-3 text-caption text-ink-muted">Correo</p>
            <p className="text-body text-ink">{me.email}</p>
            {me.platformAdmin && <div className="mt-3"><Badge tone="primary">Administración de Nítida</Badge></div>}
          </Card>
          <Card className="p-5">
            <p className="text-body-sm font-semibold text-ink">Talleres</p>
            {workshops.length === 0 ? (
              <p className="mt-1 text-body-sm text-ink-muted">Aún no pertenece a ningún taller.</p>
            ) : (
              <ul className="mt-2 flex flex-col gap-2">
                {workshops.map((w) => (
                  <li key={w.id} className="flex items-center justify-between gap-2 text-body-sm">
                    <span className="text-ink">{w.name}</span>
                    <span className="text-caption text-ink-muted">{w.roles.map((r) => ROLE_LABEL[r]).join(", ")}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card className="p-5 text-body-sm text-ink-muted">
            <p className="font-semibold text-ink">¿Olvidó la contraseña?</p>
            <p className="mt-1">Cierre sesión y use “Recuperar contraseña” en la pantalla de ingreso: le enviaremos un código a su correo.</p>
            <Link href="/recuperar" className="mt-2 inline-flex font-medium text-primary hover:underline">Recuperar contraseña</Link>
          </Card>
        </aside>
      </div>
    </>
  );
}

/** El código de invitación llega por correo (ya no viene en la respuesta del dueño): se pega aquí. */
function JoinWorkshop() {
  const { me, reload, selectWorkshop } = useSession();
  const router = useRouter();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Card>
      <div id="unirme" className="scroll-mt-20" />
      <CardHeader title="Unirme a un taller" subtitle="Si el dueño de un taller lo invitó, le llegó un código por correo." />
      <form className="flex flex-col gap-3 px-4 pb-5 lg:px-5" onSubmit={async (e) => {
        e.preventDefault();
        if (code.trim().length < 8) return setError("Pegue el código completo que llegó al correo.");
        setBusy(true);
        setError(null);
        try {
          const r = await api.post<{ workshopId: string }>(`/invitations/${encodeURIComponent(code.trim())}/accept`);
          await reload();
          selectWorkshop(r.workshopId);
          toast.success("Ya es parte del taller");
          router.push("/inicio");
        } catch (err) {
          setError(isCode(err, "NOT_FOUND") ? "Ese código no está disponible para su cuenta: puede haber vencido (72 horas), ya se usó o es para otro correo." : messageFor(err));
        } finally {
          setBusy(false);
        }
      }}>
        <p className="flex items-center gap-2 text-body-sm text-ink-muted"><Mail aria-hidden className="size-4" strokeWidth={1.75} /> Debe haber llegado a <span className="font-medium text-ink">{me.email}</span>.</p>
        <Input label="Código de invitación" autoComplete="off" spellCheck={false} placeholder="Pegue aquí el código del correo" value={code} onChange={(e) => setCode(e.target.value)} className="font-mono" error={error} />
        <Button type="submit" variant="primary" icon={UserCheck} loading={busy} loadingText="Uniéndose…" className="self-start">Unirme al taller</Button>
      </form>
    </Card>
  );
}

