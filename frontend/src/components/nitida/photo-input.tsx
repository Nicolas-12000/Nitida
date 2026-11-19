"use client";
import { Camera, CircleCheck, RotateCw } from "lucide-react";
import { motion } from "motion/react";
import { useId, useRef, useState } from "react";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";

export interface UploadedPhoto { fileId: string; preview: string }

/** Foto con miniatura local inmediata y barra de progreso de 2 px (DESIGN.MD §7.1). */
export function PhotoInput({ label, hint, value, onChange, error }: {
  label: string; hint: string; value: UploadedPhoto | null; onChange: (p: UploadedPhoto | null) => void; error?: string | null;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const upload = async (file: File) => {
    const local = URL.createObjectURL(file);
    setPreview(local);
    setBusy(true);
    setErr(null);
    onChange(null);
    try {
      const { fileId } = await api.upload(file);
      onChange({ fileId, preview: local });
    } catch (e) {
      setErr(messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  const shown = value?.preview ?? preview;
  const message = err ?? error;
  return (
    <div className="flex flex-col gap-2">
      <span id={`${id}-label`} className="text-body-sm font-medium text-ink">{label}</span>
      <button
        type="button"
        aria-labelledby={`${id}-label`}
        aria-describedby={`${id}-hint`}
        onClick={() => input.current?.click()}
        className={`relative flex h-28 items-center justify-center overflow-hidden rounded-md border border-dashed bg-surface-sunken transition-colors hover:border-primary ${message ? "border-danger" : "border-border-input"}`}
      >
        {shown ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={shown} alt="" className="absolute inset-0 size-full object-cover" />
        ) : (
          <span className="flex flex-col items-center gap-1 text-caption text-ink-muted">
            <Camera aria-hidden className="size-6" strokeWidth={1.75} />
            Tomar foto
          </span>
        )}
        {value && (
          <span className="absolute top-2 right-2 inline-flex items-center gap-1 rounded-xs bg-verified-soft px-2 py-0.5 text-caption font-medium text-verified-ink">
            <CircleCheck aria-hidden className="size-3.5" strokeWidth={1.75} /> Guardada
          </span>
        )}
        {shown && !busy && (
          <span className="absolute bottom-2 left-2 inline-flex items-center gap-1 rounded-xs bg-surface px-2 py-0.5 text-caption text-ink">
            <RotateCw aria-hidden className="size-3.5" strokeWidth={1.75} /> Cambiar
          </span>
        )}
        {busy && (
          <span className="absolute inset-x-0 bottom-0 h-0.5 bg-primary-soft">
            <motion.span className="block h-full bg-primary" initial={{ width: "8%" }} animate={{ width: "92%" }} transition={{ duration: 1.2, ease: "easeOut" }} />
          </span>
        )}
      </button>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        capture="environment"
        className="sr-only"
        tabIndex={-1}
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) void upload(f);
          e.target.value = "";
        }}
      />
      {message ? <p className="text-caption text-danger-ink">{message}</p> : <p id={`${id}-hint`} className="text-caption text-ink-muted">{hint}</p>}
    </div>
  );
}

/** Solo en modo demostración: genera una foto de ejemplo para poder probar sin cámara. */
export async function sampleFile(kind: "moto" | "odometro"): Promise<File> {
  const c = document.createElement("canvas");
  c.width = 640;
  c.height = 400;
  const g = c.getContext("2d")!;
  g.fillStyle = "#E3DFD8";
  g.fillRect(0, 0, 640, 400);
  g.fillStyle = "#191F2E";
  if (kind === "odometro") {
    g.fillStyle = "#0F131E";
    g.fillRect(170, 140, 300, 120);
    g.fillStyle = "#78D3B1";
    g.font = "bold 64px monospace";
    g.fillText(String(Math.floor(10000 + Math.random() * 30000)).padStart(6, "0"), 200, 222);
  } else {
    g.lineWidth = 14;
    g.strokeStyle = "#3F5BB8";
    g.beginPath(); g.arc(190, 270, 70, 0, Math.PI * 2); g.stroke();
    g.beginPath(); g.arc(450, 270, 70, 0, Math.PI * 2); g.stroke();
    g.beginPath(); g.moveTo(190, 270); g.lineTo(300, 170); g.lineTo(420, 170); g.lineTo(450, 270); g.stroke();
  }
  const blob = await new Promise<Blob>((r) => c.toBlob((b) => r(b!), "image/jpeg", 0.8));
  return new File([blob], `${kind}.jpg`, { type: "image/jpeg" });
}

export async function sampleUpload(kind: "moto" | "odometro"): Promise<UploadedPhoto> {
  const f = await sampleFile(kind);
  const { fileId } = await api.upload(f);
  return { fileId, preview: URL.createObjectURL(f) };
}
