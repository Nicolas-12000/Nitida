"use client";
import { BadgeCheck, Fingerprint, ImagePlus, Plus, ScanBarcode, Trash2, Undo2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError, messageFor } from "@/lib/errors";
import { CONDITION_LABEL, cop } from "@/lib/format";
import { useSession } from "@/lib/session";
import { personalDataWarning } from "@/lib/public-text";
import { Button } from "@/components/ui/button";
import { Choice, Input } from "@/components/ui/field";
import { Banner, Card, CardHeader, Dialog } from "@/components/ui/primitives";
import { EVIDENCE, EvidenceBadge } from "@/components/nitida/status";
import { PhotoInput, sampleUpload, type UploadedPhoto } from "@/components/nitida/photo-input";
import { Scanner, validProductCode } from "@/components/nitida/scanner";
import type { OrderView, Part, PartCondition, SerialResult } from "@/lib/types";

export function PartsSection({ order, editable, onChange }: { order: OrderView; editable: boolean; onChange: () => Promise<unknown> }) {
  const { can } = useSession();
  const [adding, setAdding] = useState(false);
  const [serialFor, setSerialFor] = useState<Part | null>(null);
  const [sealFor, setSealFor] = useState<Part | null>(null);
  const money = can("WORKSHOP_OWNER", "FRONT_DESK", "MECHANIC");
  const canEdit = editable && can("WORKSHOP_OWNER", "MECHANIC");

  const remove = async (p: Part) => {
    try {
      await api.del(`/orders/${order.id}/parts/${p.id}`);
      await onChange();
      toast.success(`${p.name} quitado`, {
        duration: 6000,
        action: {
          label: "Deshacer",
          onClick: async () => {
            await api.post(`/orders/${order.id}/parts`, {
              name: p.name, reference: p.reference, brand: p.brand, condition: p.condition, quantity: p.quantity, unitPrice: p.unitPrice, productCode: p.productCode ?? undefined,
            });
            await onChange();
          },
        },
      });
    } catch (e) {
      toast.error(messageFor(e));
    }
  };

  return (
    <Card>
      <CardHeader
        title="Repuestos"
        subtitle="La evidencia de cada repuesto queda en el historial público. Escanear es evidencia, no prueba de originalidad."
        action={canEdit && <Button size="sm" icon={Plus} onClick={() => setAdding(true)}>Agregar</Button>}
      />
      {order.parts.length === 0 ? (
        <p className="border-t border-border px-4 py-4 text-body-sm text-ink-muted lg:px-5">Sin repuestos. Si el trabajo solo es mano de obra, no hace falta agregar ninguno.</p>
      ) : (
        <ul className="divide-y divide-border border-t border-border">
          <AnimatePresence initial={false}>
            {order.parts.map((p) => (
              <motion.li key={p.id} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.26 }} className="px-4 py-3 lg:px-5">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-body-sm font-medium text-ink">
                      {p.quantity > 1 && <span className="tabular">{p.quantity} × </span>}{p.name}
                    </p>
                    <p className="text-caption text-ink-muted">
                      {p.brand} · {CONDITION_LABEL[p.condition]}{p.productCode && <> · <span className="font-mono">{p.productCode}</span></>}
                      {p.hasSerial && " · con serial"}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <EvidenceBadge level={p.evidenceLevel} />
                    {money && <span className="text-body-sm tabular text-ink">{cop(p.quantity * p.unitPrice)}</span>}
                  </div>
                </div>
                {canEdit && (
                  <div className="mt-1 -ml-2 flex flex-wrap gap-1">
                    {!p.hasSerial && <Button variant="ghost" size="sm" icon={Fingerprint} onClick={() => setSerialFor(p)}>Registrar serial</Button>}
                    {p.evidenceLevel !== "AUTHENTICITY_VERIFIED" && <Button variant="ghost" size="sm" icon={BadgeCheck} onClick={() => setSealFor(p)}>Revisar sello</Button>}
                    <Button variant="ghost" size="sm" icon={Trash2} onClick={() => remove(p)}>Quitar</Button>
                  </div>
                )}
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      )}
      <AddPartDialog open={adding} order={order} onClose={() => setAdding(false)} onDone={onChange} />
      <SerialDialog part={serialFor} order={order} onClose={() => setSerialFor(null)} onDone={onChange} />
      <SealDialog part={sealFor} order={order} onClose={() => setSealFor(null)} onDone={onChange} />
    </Card>
  );
}

function AddPartDialog({ open, order, onClose, onDone }: { open: boolean; order: OrderView; onClose: () => void; onDone: () => Promise<unknown> }) {
  const empty = { code: "", name: "", brand: "", reference: "", quantity: "1", unitPrice: "", condition: null as PartCondition | null };
  const [f, setF] = useState(empty);
  const [scan, setScan] = useState(false);
  const [codeMsg, setCodeMsg] = useState<{ tone: "primary" | "danger" | "neutral"; text: string } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const lookup = async (code: string) => {
    const c = code.replace(/\D/g, "");
    if (!c) return setCodeMsg(null);
    if (!validProductCode(c)) return setCodeMsg({ tone: "danger", text: "Este código no parece válido. Revise los números o escríbalo de nuevo." });
    try {
      const p = await api.get<{ name: string; brand: string; reference: string | null }>(`/catalog/products/${c}`);
      setF((x) => ({ ...x, code: c, name: p.name, brand: p.brand, reference: p.reference ?? "" }));
      setCodeMsg({ tone: "primary", text: `Encontrado en el catálogo: ${p.name}.` });
    } catch (e) {
      setCodeMsg(e instanceof ApiError && e.status === 404
        ? { tone: "neutral", text: "Código válido, pero no está en el catálogo. Escriba nombre y marca; quedará como escaneado." }
        : { tone: "danger", text: messageFor(e) });
    }
  };

  const submit = async () => {
    const e: Record<string, string> = {};
    if (!f.name.trim()) e.name = "Escriba el nombre del repuesto.";
    if (!f.brand.trim()) e.brand = "Escriba la marca.";
    if (!f.condition) e.condition = "Elija el estado del repuesto.";
    const q = Number(f.quantity);
    if (!(q >= 1 && q <= 999)) e.quantity = "Entre 1 y 999.";
    if (f.unitPrice === "") e.unitPrice = "Escriba el precio unitario.";
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    setError(null);
    try {
      await api.post(`/orders/${order.id}/parts`, {
        name: f.name.trim(), brand: f.brand.trim(), reference: f.reference.trim() || undefined, condition: f.condition, quantity: q,
        unitPrice: Number(f.unitPrice), productCode: f.code || undefined,
      });
      await onDone();
      toast.success("Repuesto agregado");
      setF(empty);
      setCodeMsg(null);
      onClose();
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Dialog open={open && !scan} onClose={onClose} title="Agregar repuesto"
        footer={<><Button variant="ghost" onClick={onClose}>Volver</Button><Button variant="primary" icon={Plus} loading={busy} loadingText="Agregando…" onClick={submit}>Agregar repuesto</Button></>}>
        <div className="flex flex-col gap-4 text-ink">
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <Input label="Código de barras" optional inputMode="numeric" placeholder="7702005010185" value={f.code} className="font-mono"
                onChange={(e) => setF({ ...f, code: e.target.value.replace(/\D/g, "").slice(0, 14) })} onBlur={() => lookup(f.code)} />
            </div>
            <Button icon={ScanBarcode} onClick={() => setScan(true)}>Escanear</Button>
          </div>
          {codeMsg && <Banner tone={codeMsg.tone}>{codeMsg.text}</Banner>}
          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2"><Input label="Nombre" placeholder="Pastillas de freno delanteras" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={errors.name} warning={personalDataWarning(f.name)} /></div>
            <Input label="Marca" placeholder="Vesrah" value={f.brand} onChange={(e) => setF({ ...f, brand: e.target.value })} error={errors.brand} />
            <Input label="Referencia" optional placeholder="FD-410" value={f.reference} onChange={(e) => setF({ ...f, reference: e.target.value })} />
            <Input label="Cantidad" inputMode="numeric" value={f.quantity} onChange={(e) => setF({ ...f, quantity: e.target.value.replace(/\D/g, "").slice(0, 3) })} error={errors.quantity} className="tabular" />
            <Input label="Precio unitario" inputMode="numeric" prefix="$" placeholder="45000" value={f.unitPrice} onChange={(e) => setF({ ...f, unitPrice: e.target.value.replace(/\D/g, "").slice(0, 8) })} error={errors.unitPrice} className="tabular" />
          </div>
          <Choice<PartCondition> label="Estado" value={f.condition} onChange={(v) => setF({ ...f, condition: v })} columns={3}
            options={[{ value: "OEM", label: "Original" }, { value: "AFTERMARKET", label: "Alternativo" }, { value: "USED", label: "Usado" }]} />
          {errors.condition && <p className="-mt-2 text-caption text-danger-ink">{errors.condition}</p>}
          {error && <Banner tone="danger" role="alert">{error}</Banner>}
        </div>
      </Dialog>
      <Scanner open={scan} onClose={() => setScan(false)} onRead={(code) => { setScan(false); setF((x) => ({ ...x, code })); void lookup(code); }} />
    </>
  );
}

function SerialDialog({ part, order, onClose, onDone }: { part: Part | null; order: OrderView; onClose: () => void; onDone: () => Promise<unknown> }) {
  const [serial, setSerial] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ tone: "verified" | "danger"; text: string } | null>(null);
  const close = () => { setSerial(""); setResult(null); onClose(); };
  return (
    <Dialog open={!!part} onClose={close} title="Registrar serial"
      footer={<><Button variant="ghost" onClick={close}>{result?.tone === "danger" ? "Guardar sin serial" : "Volver"}</Button>
        <Button variant="primary" icon={Fingerprint} loading={busy} loadingText="Registrando…" onClick={async () => {
          if (serial.trim().length < 4) return setResult({ tone: "danger", text: "Escriba el serial completo como aparece en la pieza." });
          setBusy(true);
          try {
            const r = await api.post<SerialResult>(`/orders/${order.id}/parts/${part!.id}/serial`, { serial: serial.trim() });
            if (r.accepted) {
              await onDone();
              toast.success("Serial registrado");
              close();
            } else setResult({ tone: "danger", text: "Este serial ya está registrado en otra moto. Puede guardar el repuesto sin serial." });
          } catch (e) {
            setResult({ tone: "danger", text: messageFor(e) });
          } finally {
            setBusy(false);
          }
        }}>Registrar serial</Button></>}>
      <div className="flex flex-col gap-4 text-ink">
        <p className="text-body-sm text-ink-muted">{part?.name}. {EVIDENCE.UNIQUE_SERIAL.help} En el historial público solo se guarda su huella, nunca el serial.</p>
        <Input label="Serial" placeholder="NGK-CR8E-0042117" value={serial} onChange={(e) => setSerial(e.target.value.toUpperCase())} className="font-mono" autoCapitalize="characters" />
        {result && <Banner tone={result.tone}>{result.text}</Banner>}
      </div>
    </Dialog>
  );
}

function SealDialog({ part, order, onClose, onDone }: { part: Part | null; order: OrderView; onClose: () => void; onDone: () => Promise<unknown> }) {
  const { demo } = useSession();
  const [photo, setPhoto] = useState<UploadedPhoto | null>(null);
  const [method, setMethod] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const close = () => { setPhoto(null); setMethod(""); setError(null); onClose(); };
  return (
    <Dialog open={!!part} onClose={close} title="Revisar sello de autenticidad"
      footer={<><Button variant="ghost" icon={Undo2} onClick={close}>Volver</Button>
        <Button variant="primary" icon={BadgeCheck} loading={busy} loadingText="Guardando…" onClick={async () => {
          if (!photo) return setError("Tome la foto del sello o empaque.");
          if (method.trim().length < 3) return setError("Diga qué revisó, por ejemplo: holograma y empaque sellado.");
          setBusy(true);
          try {
            await api.post(`/orders/${order.id}/parts/${part!.id}/authenticity`, { photoId: photo.fileId, method: method.trim() });
            await onDone();
            toast.success("Sello revisado");
            close();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Guardar revisión</Button></>}>
      <div className="flex flex-col gap-4 text-ink">
        <p className="text-body-sm text-ink-muted">{part?.name}. {EVIDENCE.AUTHENTICITY_VERIFIED.help}</p>
        <PhotoInput label="Foto del sello o empaque" hint="Que se vea el holograma o el sello de fábrica." value={photo} onChange={setPhoto} />
        {demo && !photo && <Button variant="ghost" size="sm" icon={ImagePlus} className="self-start" onClick={async () => setPhoto(await sampleUpload("moto"))}>Usar foto de ejemplo (demostración)</Button>}
        <Input label="Qué revisó" placeholder="Holograma del fabricante y empaque sellado" value={method} onChange={(e) => setMethod(e.target.value)} />
        {error && <Banner tone="danger">{error}</Banner>}
      </div>
    </Dialog>
  );
}
