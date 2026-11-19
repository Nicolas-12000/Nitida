"use client";
import { AnimatePresence, motion } from "motion/react";
import { ArrowLeft, ArrowRight, ChevronDown, ClipboardList, History, ImagePlus, Inbox, Trash2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { ApiError, messageFor } from "@/lib/errors";
import { km, motoName, normalizePlate, relative } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useDelayedFlag, useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Choice, Input } from "@/components/ui/field";
import { Banner, Card, CardHeader, Dialog, EmptyState, ErrorBanner, PageHeader, SkeletonRows } from "@/components/ui/primitives";
import { PhotoInput, sampleUpload, type UploadedPhoto } from "@/components/nitida/photo-input";
import { OpenOrderDialog } from "@/components/nitida/open-order-dialog";
import { PlateTag } from "@/components/nitida/status";
import { EmptyOrdersFigure } from "@/components/illustrations";
import type { DiscardReason, HistoryView, Intake, MotorcycleInfo } from "@/lib/types";

const DRAFT = "nitida-recepcion-borrador";
const PLATE_RE = /^[A-Z]{3}\d{2}[A-Z]?$/;

interface Known { info: MotorcycleInfo; lastKm: number | null }

export default function RecepcionPage() {
  const { workshop, can, demo } = useSession();
  const wsKey = workshop?.id ?? "none";
  const intakes = useQuery<Intake[]>(`intakes:${wsKey}`, () => api.get("/intakes"));
  const { show } = useDelayedFlag(intakes.loading);

  const [step, setStep] = useState<1 | 2>(1);
  const [plate, setPlate] = useState("");
  const [plateError, setPlateError] = useState<string | null>(null);
  const [known, setKnown] = useState<Known | null | undefined>(undefined);
  const [details, setDetails] = useState({ brand: "", line: "", modelYear: "", displacementCc: "" });
  const [showDetails, setShowDetails] = useState(false);
  const [mileage, setMileage] = useState("");
  const [mileageError, setMileageError] = useState<string | null>(null);
  const [motoPhoto, setMotoPhoto] = useState<UploadedPhoto | null>(null);
  const [odoPhoto, setOdoPhoto] = useState<UploadedPhoto | null>(null);
  const [photoError, setPhotoError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [openFor, setOpenFor] = useState<Intake | null>(null);
  const [discardFor, setDiscardFor] = useState<Intake | null>(null);

  // Borrador local sin datos sensibles (placa y km), para no perder lo escrito (DESIGN.MD §9.3).
  useEffect(() => {
    try {
      const d = JSON.parse(window.sessionStorage.getItem(DRAFT) ?? "null");
      // eslint-disable-next-line react-hooks/set-state-in-effect
      if (d?.plate) setPlate(d.plate);
      if (d?.mileage) setMileage(d.mileage);
    } catch {
      /* sin borrador */
    }
  }, []);
  useEffect(() => {
    try {
      window.sessionStorage.setItem(DRAFT, JSON.stringify({ plate, mileage }));
    } catch {
      /* sin almacenamiento */
    }
  }, [plate, mileage]);

  // Enlace desde el tablero: /recepcion?abrir=<intakeId>
  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get("abrir");
    const it = id && intakes.data?.find((i) => i.id === id);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (it && can("WORKSHOP_OWNER", "MECHANIC")) setOpenFor(it);
  }, [intakes.data, can]);

  const checkPlate = async () => {
    const p = normalizePlate(plate);
    if (!PLATE_RE.test(p)) {
      setPlateError("La placa no tiene un formato válido. Ejemplo: ABC 12D (o ABC 12 si es antigua).");
      return false;
    }
    setPlateError(null);
    try {
      const info = await api.get<MotorcycleInfo>(`/motorcycles/lookup?plate=${p}`);
      let lastKm: number | null = null;
      try {
        const h = await api.get<HistoryView>(`/motorcycles/${info.id}/history`);
        const withKm = h.entries.filter((e) => typeof e.content.mileage === "number");
        lastKm = withKm.length ? (withKm[withKm.length - 1].content.mileage as number) : null;
      } catch {
        /* otra moto de la plataforma que este taller aún no recibió: no hay historial visible */
      }
      setKnown({ info, lastKm });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setKnown(null);
      else setError(e);
    }
    return true;
  };

  const next = async () => {
    if (await checkPlate()) setStep(2);
  };

  const submit = async () => {
    const m = Number(mileage.replace(/\D/g, ""));
    let bad = false;
    if (mileage === "" || !(m >= 0 && m <= 999_999)) {
      setMileageError("Escriba el kilometraje que marca el tablero (entre 0 y 999.999).");
      bad = true;
    } else if (known?.lastKm != null && m < known.lastKm) {
      setMileageError(`Es menor que el último registrado (${km(known.lastKm)}). Revíselo o registre un cambio de odómetro desde el historial.`);
      bad = true;
    } else setMileageError(null);
    if (!motoPhoto || !odoPhoto) {
      setPhotoError(true);
      bad = true;
    }
    if (bad) return;
    setBusy(true);
    setError(null);
    try {
      const body: Record<string, unknown> = { plate: normalizePlate(plate), mileage: m, motorcyclePhotoId: motoPhoto!.fileId, odometerPhotoId: odoPhoto!.fileId };
      if (!known) {
        if (details.brand) body.brand = details.brand;
        if (details.line) body.line = details.line;
        if (details.modelYear) body.modelYear = Number(details.modelYear);
        if (details.displacementCc) body.displacementCc = Number(details.displacementCc);
      }
      const r = await api.post<{ intakeId: string; newMotorcycle: boolean }>("/intakes", body);
      toast.success(r.newMotorcycle ? "Entrada registrada. Moto nueva en Nítida: su historial empieza hoy." : "Entrada registrada");
      try { window.sessionStorage.removeItem(DRAFT); } catch { /* nada */ }
      setPlate(""); setMileage(""); setMotoPhoto(null); setOdoPhoto(null); setKnown(undefined); setStep(1);
      setDetails({ brand: "", line: "", modelYear: "", displacementCc: "" });
      invalidate("intakes", "orders", "workshop");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title="Recepción" subtitle="Registre la entrada de la moto con su kilometraje y dos fotos. Luego el mecánico abre la orden." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-6">
        {can("WORKSHOP_OWNER", "MECHANIC", "FRONT_DESK") && (
          <Card>
            <CardHeader title="Registrar entrada" subtitle={`Paso ${step} de 2 · ${step === 1 ? "Identificar la moto" : "Kilometraje y fotos"}`} />
            <div className="mx-4 mb-4 h-1 overflow-hidden rounded-full bg-surface-sunken lg:mx-5">
              <motion.div className="h-full bg-primary" initial={false} animate={{ width: step === 1 ? "50%" : "100%" }} transition={{ duration: 0.26, ease: [0.2, 0.8, 0.2, 1] }} />
            </div>
            <form
              className="px-4 pb-5 lg:px-5"
              onSubmit={(e) => {
                e.preventDefault();
                if (step === 1) void next();
                else void submit();
              }}
            >
              <AnimatePresence mode="wait" initial={false}>
                {step === 1 ? (
                  <motion.div key="s1" initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -8 }} transition={{ duration: 0.18 }} className="flex flex-col gap-4">
                    <Input
                      label="Placa"
                      placeholder="ABC 12D"
                      autoCapitalize="characters"
                      autoComplete="off"
                      value={plate}
                      onChange={(e) => { setPlate(e.target.value.toUpperCase()); setKnown(undefined); }}
                      onBlur={() => plate && void checkPlate()}
                      error={plateError}
                      hint="Como aparece en la tarjeta de propiedad."
                      className="font-mono tracking-wider uppercase"
                    />
                    <AnimatePresence>
                      {known && (
                        <motion.div initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                          <Banner tone="primary" title={`Ya conocemos esta moto: ${motoName(known.info)}`}>
                            {known.lastKm != null ? <>Último kilometraje registrado: <span className="tabular font-medium">{km(known.lastKm)}</span>.</> : "Aún sin kilometraje en su historial."}{" "}
                            <Link href={`/motos/${known.info.id}`} className="font-medium underline underline-offset-2">Ver historial</Link>
                          </Banner>
                        </motion.div>
                      )}
                      {known === null && (
                        <motion.div initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="flex flex-col gap-3">
                          <Banner tone="neutral" title="Moto nueva en Nítida">Su historial empieza con esta entrada. Los datos de la moto son opcionales y se toman como declarados.</Banner>
                          <button type="button" onClick={() => setShowDetails((s) => !s)} aria-expanded={showDetails} className="inline-flex items-center gap-1 self-start text-body-sm font-medium text-primary">
                            Datos de la moto (opcional) <ChevronDown aria-hidden className={`size-4 transition-transform ${showDetails ? "rotate-180" : ""}`} />
                          </button>
                          {showDetails && (
                            <div className="grid grid-cols-2 gap-3">
                              <Input label="Marca" placeholder="Yamaha" value={details.brand} onChange={(e) => setDetails({ ...details, brand: e.target.value })} />
                              <Input label="Línea" placeholder="FZ 2.0 150" value={details.line} onChange={(e) => setDetails({ ...details, line: e.target.value })} />
                              <Input label="Año del modelo" inputMode="numeric" placeholder="2021" value={details.modelYear} onChange={(e) => setDetails({ ...details, modelYear: e.target.value.replace(/\D/g, "").slice(0, 4) })} />
                              <Input label="Cilindraje" inputMode="numeric" placeholder="149" suffix="cc" value={details.displacementCc} onChange={(e) => setDetails({ ...details, displacementCc: e.target.value.replace(/\D/g, "").slice(0, 4) })} />
                            </div>
                          )}
                        </motion.div>
                      )}
                    </AnimatePresence>
                    <Button type="submit" variant="primary" size="lg" icon={ArrowRight}>Seguir al kilometraje</Button>
                  </motion.div>
                ) : (
                  <motion.div key="s2" initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 8 }} transition={{ duration: 0.18 }} className="flex flex-col gap-4">
                    <div className="flex items-center gap-2">
                      <PlateTag plate={normalizePlate(plate)} />
                      <span className="text-body-sm text-ink-muted">{known ? motoName(known.info) : "Moto nueva"}</span>
                      <Button type="button" variant="link" icon={ArrowLeft} className="ml-auto" onClick={() => setStep(1)}>Cambiar</Button>
                    </div>
                    <Input
                      label="Kilometraje"
                      inputMode="numeric"
                      placeholder="18420"
                      suffix="km"
                      value={mileage}
                      onChange={(e) => setMileage(e.target.value.replace(/\D/g, "").slice(0, 6))}
                      onBlur={() => setMileageError(null)}
                      error={mileageError}
                      hint={known?.lastKm != null ? `Último registrado: ${km(known.lastKm)}` : "El que marca el tablero hoy."}
                      className="tabular"
                    />
                    <div className="grid grid-cols-2 gap-3">
                      <PhotoInput label="Foto de la moto" hint="De lado, con la placa visible." value={motoPhoto} onChange={setMotoPhoto} error={photoError && !motoPhoto ? "Falta esta foto." : null} />
                      <PhotoInput label="Foto del tablero" hint="Que se lea el kilometraje." value={odoPhoto} onChange={setOdoPhoto} error={photoError && !odoPhoto ? "Falta esta foto." : null} />
                    </div>
                    {demo && (!motoPhoto || !odoPhoto) && (
                      <Button type="button" variant="ghost" size="sm" icon={ImagePlus} className="self-start"
                        onClick={async () => { setMotoPhoto(await sampleUpload("moto")); setOdoPhoto(await sampleUpload("odometro")); }}>
                        Usar fotos de ejemplo (demostración)
                      </Button>
                    )}
                    {error != null && <ErrorBanner error={error} />}
                    <Button type="submit" variant="primary" size="lg" icon={Inbox} loading={busy} loadingText="Registrando…">Registrar entrada</Button>
                  </motion.div>
                )}
              </AnimatePresence>
            </form>
          </Card>
        )}

        <Card>
          <CardHeader title="Motos recibidas" subtitle="Recepciones abiertas que aún no tienen orden." />
          {intakes.error ? (
            <div className="px-4 pb-4"><ErrorBanner error={intakes.error} onRetry={intakes.refetch} /></div>
          ) : intakes.loading ? (
            show ? <SkeletonRows rows={3} label="Cargando recepciones…" /> : <div className="h-40" />
          ) : intakes.data!.length === 0 ? (
            <EmptyState illustration={<EmptyOrdersFigure />} title="Todas las motos tienen orden" line="Cuando registre una entrada aparecerá aquí hasta que el mecánico abra la orden." />
          ) : (
            <ul className="divide-y divide-border border-t border-border">
              <AnimatePresence initial={false}>
                {intakes.data!.map((it) => (
                  <motion.li key={it.id} layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, height: 0 }} transition={{ duration: 0.26 }} className="flex flex-wrap items-center gap-3 px-4 py-3 lg:px-5">
                    <PlateTag plate={it.plate} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-body-sm font-medium text-ink">{motoName(it)}</p>
                      <p className="text-caption text-ink-muted"><span className="tabular">{km(it.mileage)}</span> · recibida {relative(it.createdAt)}</p>
                    </div>
                    <div className="flex items-center gap-1">
                      <Link href={`/motos/${it.motorcycleId}`} aria-label="Ver historial" title="Ver historial" className="flex size-10 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken hover:text-ink">
                        <History aria-hidden className="size-[18px]" strokeWidth={1.75} />
                      </Link>
                      <button type="button" onClick={() => setDiscardFor(it)} aria-label="Descartar recepción" title="Descartar recepción" className="flex size-10 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken hover:text-ink">
                        <Trash2 aria-hidden className="size-[18px]" strokeWidth={1.75} />
                      </button>
                      {can("WORKSHOP_OWNER", "MECHANIC") ? (
                        <Button size="sm" icon={ClipboardList} onClick={() => setOpenFor(it)}>Abrir orden</Button>
                      ) : (
                        <span className="text-caption text-ink-muted">Esperando al mecánico</span>
                      )}
                    </div>
                  </motion.li>
                ))}
              </AnimatePresence>
            </ul>
          )}
        </Card>
      </div>
      <OpenOrderDialog intake={openFor} onClose={() => setOpenFor(null)} />
      <DiscardDialog intake={discardFor} onClose={() => setDiscardFor(null)} />
    </>
  );
}

function DiscardDialog({ intake, onClose }: { intake: Intake | null; onClose: () => void }) {
  const [reason, setReason] = useState<DiscardReason | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog
      open={!!intake}
      onClose={onClose}
      title="¿Descartar esta recepción?"
      destructive
      footer={
        <>
          <Button variant="ghost" data-autofocus onClick={onClose}>Volver</Button>
          <Button
            variant="danger"
            icon={Trash2}
            loading={busy}
            loadingText="Descartando…"
            onClick={async () => {
              if (!reason) return setError("Elija el motivo.");
              setBusy(true);
              try {
                await api.post(`/intakes/${intake!.id}/discard`, { reason });
                invalidate("intakes");
                toast.success("Recepción descartada");
                onClose();
              } catch (e) {
                setError(messageFor(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            Descartar recepción
          </Button>
        </>
      }
    >
      <p className="mb-4">La moto sale de la lista de recibidas. El registro inicial de su historial no se borra.</p>
      <Choice<DiscardReason>
        label="Motivo"
        value={reason}
        onChange={(v) => { setReason(v); setError(null); }}
        columns={2}
        options={[
          { value: "CONSULTATION_ONLY", label: "Solo consulta" },
          { value: "CUSTOMER_WITHDREW", label: "El cliente desistió" },
          { value: "ENTRY_ERROR", label: "Error al registrar" },
        ]}
      />
      {error && <p className="mt-2 text-caption text-danger-ink">{error}</p>}
    </Dialog>
  );
}
