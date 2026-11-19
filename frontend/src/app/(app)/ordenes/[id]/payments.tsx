"use client";
import { Ban, Banknote, CreditCard, Landmark, Plus, Wallet, type LucideIcon } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { METHOD_LABEL, cop, dateTime } from "@/lib/format";
import { useSession } from "@/lib/session";
import { Button } from "@/components/ui/button";
import { Choice, Input } from "@/components/ui/field";
import { Badge, Banner, Card, CardHeader, Dialog } from "@/components/ui/primitives";
import { AnimatedMoney } from "@/components/nitida/status";
import type { OrderView, Payment, PaymentMethod } from "@/lib/types";

const METHOD_ICON: Record<PaymentMethod, LucideIcon> = { CASH: Banknote, TRANSFER: Landmark, CARD: CreditCard, STORE_CREDIT: Wallet };

export function PaymentsSection({ order, onChange, payOpen, setPayOpen }: { order: OrderView; onChange: () => Promise<unknown>; payOpen: boolean; setPayOpen: (v: boolean) => void }) {
  const { can } = useSession();
  const [voidFor, setVoidFor] = useState<Payment | null>(null);
  if (!can("WORKSHOP_OWNER", "FRONT_DESK")) return null;
  const paid = order.paid ?? 0;
  const pending = Math.max(order.total - paid, 0);
  const closed = order.status === "DELIVERED" || order.status === "CANCELLED";
  const pct = order.total ? Math.min(paid / order.total, 1) : 0;

  return (
    <Card>
      <CardHeader title="Pagos" action={!closed && pending > 0 && <Button size="sm" icon={Plus} onClick={() => setPayOpen(true)}>Registrar pago</Button>} />
      <div className="px-4 pb-4 lg:px-5">
        <div className="grid grid-cols-3 gap-2">
          <div><p className="text-caption text-ink-muted">Total</p><p className="text-heading font-semibold tabular">{cop(order.total)}</p></div>
          <div><p className="text-caption text-ink-muted">Pagado</p><AnimatedMoney value={paid} className="text-heading font-semibold" /></div>
          <div><p className="text-caption text-ink-muted">Pendiente</p><AnimatedMoney value={pending} className={`text-heading font-semibold ${pending ? "text-attention-ink" : "text-verified-ink"}`} /></div>
        </div>
        <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-surface-sunken" role="progressbar" aria-valuenow={Math.round(pct * 100)} aria-valuemin={0} aria-valuemax={100} aria-label="Porcentaje pagado">
          <motion.div className={`h-full ${pct >= 1 ? "bg-verified" : "bg-primary"}`} initial={false} animate={{ width: `${pct * 100}%` }} transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }} />
        </div>
      </div>
      {order.payments.length > 0 && (
        <ul className="divide-y divide-border border-t border-border">
          <AnimatePresence initial={false}>
            {order.payments.map((p) => {
              const Icon = METHOD_ICON[p.method];
              return (
                <motion.li key={p.id} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="flex min-h-14 items-center gap-3 px-4 py-2 lg:px-5">
                  <Icon aria-hidden className="size-[18px] text-ink-muted" strokeWidth={1.75} />
                  <div className="min-w-0 flex-1">
                    <p className={`text-body-sm font-medium ${p.voided ? "text-ink-muted line-through" : "text-ink"}`}>{METHOD_LABEL[p.method]}</p>
                    <p className="text-caption text-ink-muted">{dateTime(p.recordedAt)}</p>
                  </div>
                  {p.voided && <Badge tone="neutral" icon={Ban}>Anulado</Badge>}
                  <span className={`text-body-sm tabular ${p.voided ? "text-ink-muted line-through" : "text-ink"}`}>{cop(p.amount)}</span>
                  {!p.voided && !closed && (
                    <Button variant="ghost" size="sm" onClick={() => setVoidFor(p)}>Anular</Button>
                  )}
                </motion.li>
              );
            })}
          </AnimatePresence>
        </ul>
      )}
      <PayDialog open={payOpen} order={order} pending={pending} onClose={() => setPayOpen(false)} onDone={onChange} />
      <VoidDialog payment={voidFor} onClose={() => setVoidFor(null)} onDone={onChange} />
    </Card>
  );
}

function PayDialog({ open, order, pending, onClose, onDone }: { open: boolean; order: OrderView; pending: number; onClose: () => void; onDone: () => Promise<unknown> }) {
  const [method, setMethod] = useState<PaymentMethod | null>("CASH");
  const [amount, setAmount] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const value = amount === "" ? pending : Number(amount);
  return (
    <Dialog open={open} onClose={onClose} title="Registrar pago"
      footer={<><Button variant="ghost" onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={Banknote} loading={busy} loadingText="Registrando…" onClick={async () => {
          if (!method) return setError("Elija el medio de pago.");
          if (!(value > 0)) return setError("Escriba un valor mayor que cero.");
          if (value > pending) return setError(`El pago supera el saldo pendiente (${cop(pending)}).`);
          setBusy(true);
          setError(null);
          try {
            await api.post(`/orders/${order.id}/payments`, { method, amount: value });
            await onDone();
            toast.success("Pago registrado");
            setAmount("");
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Registrar {cop(value)}</Button></>}>
      <div className="flex flex-col gap-4 text-ink">
        <Choice<PaymentMethod> label="Medio de pago" value={method} onChange={setMethod} columns={2}
          options={[
            { value: "CASH", label: "Efectivo", hint: "Entra al cuadre de caja" },
            { value: "TRANSFER", label: "Transferencia" },
            { value: "CARD", label: "Tarjeta" },
            { value: "STORE_CREDIT", label: "Fiado", hint: order.customerId ? "A nombre del cliente" : "Requiere cliente en la orden" },
          ]} />
        <Input label="Valor" inputMode="numeric" prefix="$" placeholder={String(pending)} value={amount}
          onChange={(e) => setAmount(e.target.value.replace(/\D/g, "").slice(0, 8))} hint={`Pendiente: ${cop(pending)}. Déjelo vacío para pagar el saldo completo.`} className="tabular" />
        {error && <Banner tone="danger" role="alert">{error}</Banner>}
      </div>
    </Dialog>
  );
}

function VoidDialog({ payment, onClose, onDone }: { payment: Payment | null; onClose: () => void; onDone: () => Promise<unknown> }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={!!payment} onClose={onClose} destructive title={`¿Anular el pago de ${cop(payment?.amount)}?`}
      footer={<><Button variant="ghost" data-autofocus onClick={onClose}>Volver</Button>
        <Button variant="danger" icon={Ban} loading={busy} loadingText="Anulando…" onClick={async () => {
          if (reason.trim().length < 5) return setError("Escriba el motivo (mínimo 5 caracteres).");
          setBusy(true);
          try {
            await api.post(`/payments/${payment!.id}/void`, { reason: reason.trim() });
            await onDone();
            toast.success("Pago anulado");
            setReason("");
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Anular pago</Button></>}>
      <p className="mb-4">El pago no se borra: queda tachado y fuera del cuadre de caja. La anulación queda auditada.</p>
      <Input label="Motivo" placeholder="Se registró dos veces" value={reason} onChange={(e) => setReason(e.target.value)} error={error} />
    </Dialog>
  );
}
