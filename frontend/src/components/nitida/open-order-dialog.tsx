"use client";
import { useRouter } from "next/navigation";
import { ClipboardList, Search, UserPlus, Check } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { motoName } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate } from "@/lib/use-query";
import { personalDataWarning } from "@/lib/public-text";
import { POLICY_VERSION } from "@/lib/constants";
import { Button } from "@/components/ui/button";
import { Banner, Dialog } from "@/components/ui/primitives";
import { Input, Textarea } from "@/components/ui/field";
import { PlateTag } from "./status";
import type { Customer, Intake } from "@/lib/types";

/** Abrir la orden de trabajo de una moto recibida: cliente (opcional) y diagnóstico. */
export function OpenOrderDialog({ intake, onClose }: { intake: Intake | null; onClose: () => void }) {
  const router = useRouter();
  const { can } = useSession();
  const [diagnosis, setDiagnosis] = useState("");
  const [touched, setTouched] = useState(false);
  const [q, setQ] = useState("");
  const [results, setResults] = useState<Customer[] | null>(null);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [creating, setCreating] = useState(false);
  const [newC, setNewC] = useState({ fullName: "", phone: "", consent: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const diagError = touched && diagnosis.trim().length < 10 ? "Describa el problema con al menos 10 caracteres." : null;

  const search = async () => {
    setError(null);
    try {
      setResults(await api.get<Customer[]>(`/customers?q=${encodeURIComponent(q.trim())}`));
    } catch (e) {
      setError(messageFor(e));
    }
  };

  const submit = async () => {
    setTouched(true);
    if (diagnosis.trim().length < 10 || !intake || personalDataWarning(diagnosis)) return;
    setBusy(true);
    setError(null);
    try {
      let customerId = customer?.id;
      if (creating && newC.fullName.trim()) {
        const r = await api.post<{ customerId: string }>("/customers", {
          fullName: newC.fullName, phone: newC.phone, consentAccepted: newC.consent, policyVersion: POLICY_VERSION, consentChannel: "IN_PERSON",
        });
        customerId = r.customerId;
      }
      const r = await api.post<{ orderId: string }>("/orders", { intakeId: intake.id, diagnosis: diagnosis.trim(), customerId });
      invalidate("intakes", "orders");
      toast.success("Orden abierta");
      onClose();
      router.push(`/ordenes/${r.orderId}`);
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={!!intake}
      onClose={onClose}
      title="Abrir orden de trabajo"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Volver</Button>
          <Button variant="primary" icon={ClipboardList} loading={busy} loadingText="Abriendo…" onClick={submit}>Abrir orden</Button>
        </>
      }
    >
      {intake && (
        <div className="flex flex-col gap-4 text-ink">
          <div className="flex items-center gap-3 rounded-md bg-surface-sunken p-3">
            <PlateTag plate={intake.plate} />
            <span className="text-body-sm">{motoName(intake)}</span>
          </div>
          <Textarea
            label="Diagnóstico"
            placeholder="Ruido en la transmisión al acelerar; revisar kit de arrastre."
            value={diagnosis}
            onChange={(e) => setDiagnosis(e.target.value)}
            onBlur={() => setTouched(true)}
            error={diagError}
            warning={personalDataWarning(diagnosis)}
            hint="Lo que dice el cliente y lo que usted observa. Queda en el historial al entregar."
          />
          {!customer && !creating && (
            <div className="flex flex-col gap-2">
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <Input label="Cliente" optional placeholder="Nombre, celular o documento" value={q} onChange={(e) => setQ(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && (e.preventDefault(), search())} />
                </div>
                <Button icon={Search} onClick={search} aria-label="Buscar cliente">Buscar</Button>
              </div>
              {results && (
                <ul className="max-h-40 overflow-y-auto rounded-md border border-border">
                  {results.length === 0 && <li className="px-3 py-2 text-body-sm text-ink-muted">No encontramos “{q}”.</li>}
                  {results.map((c) => (
                    <li key={c.id}>
                      <button type="button" onClick={() => setCustomer(c)} className="flex h-12 w-full items-center justify-between px-3 text-left text-body-sm hover:bg-surface-sunken">
                        <span>{c.fullName}</span>
                        <span className="font-mono text-caption text-ink-muted">{c.phone.replace(/(\d{3})(\d{3})(\d{4})/, "$1 $2 $3")}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {can("WORKSHOP_OWNER", "FRONT_DESK") && (
                <Button variant="link" icon={UserPlus} onClick={() => setCreating(true)} className="self-start">Registrar cliente nuevo</Button>
              )}
            </div>
          )}
          {customer && (
            <div className="flex items-center justify-between rounded-md border border-border px-3 py-2">
              <span className="flex items-center gap-2 text-body-sm"><Check aria-hidden className="size-4 text-verified" />{customer.fullName}</span>
              <Button variant="link" onClick={() => setCustomer(null)}>Cambiar</Button>
            </div>
          )}
          {creating && (
            <div className="flex flex-col gap-3 rounded-md border border-border p-3">
              <Input label="Nombre completo" autoComplete="name" value={newC.fullName} onChange={(e) => setNewC({ ...newC, fullName: e.target.value })} placeholder="María Camila Rosero" />
              <Input label="Celular" inputMode="numeric" autoComplete="tel" value={newC.phone} onChange={(e) => setNewC({ ...newC, phone: e.target.value })} placeholder="3001234567" />
              <label className="flex items-start gap-3 text-body-sm text-ink">
                <input type="checkbox" checked={newC.consent} onChange={(e) => setNewC({ ...newC, consent: e.target.checked })} className="mt-0.5 size-5 accent-[var(--primary)]" />
                <span>El cliente autorizó el tratamiento de sus datos (Ley 1581, política {POLICY_VERSION}).</span>
              </label>
              <Button variant="link" onClick={() => setCreating(false)} className="self-start">Buscar uno existente</Button>
            </div>
          )}
          {error && <Banner tone="danger" role="alert">{error}</Banner>}
        </div>
      )}
    </Dialog>
  );
}
