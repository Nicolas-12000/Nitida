"use client";
import { CameraOff, Keyboard, X } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";

interface Detector { detect: (src: CanvasImageSource) => Promise<{ rawValue: string }[]> }
declare global {
  interface Window { BarcodeDetector?: new (opts: { formats: string[] }) => Detector }
}

/**
 * Escáner de códigos de barras (DESIGN.MD §6.10) con la API BarcodeDetector del navegador.
 * Si no hay cámara, permiso o soporte, ofrece escribir el código.
 */
export function Scanner({ open, onClose, onRead }: { open: boolean; onClose: () => void; onRead: (code: string) => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [state, setState] = useState<"starting" | "scanning" | "denied" | "unsupported" | "read">("starting");

  useEffect(() => {
    if (!open) return;
    let stream: MediaStream | null = null;
    let stop = false;
    (async () => {
      if (!window.BarcodeDetector || !navigator.mediaDevices?.getUserMedia) {
        setState("unsupported");
        return;
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
      } catch {
        setState("denied");
        return;
      }
      if (stop || !video.current) return;
      video.current.srcObject = stream;
      await video.current.play().catch(() => {});
      setState("scanning");
      const detector = new window.BarcodeDetector({ formats: ["ean_13", "ean_8", "upc_a", "upc_e", "code_128", "qr_code"] });
      const tick = async () => {
        if (stop || !video.current) return;
        try {
          const found = await detector.detect(video.current);
          if (found[0]?.rawValue) {
            navigator.vibrate?.(30);
            setState("read");
            setTimeout(() => onRead(found[0].rawValue), 200);
            return;
          }
        } catch {
          /* cuadro no listo */
        }
        setTimeout(tick, 180);
      };
      void tick();
    })();
    return () => {
      stop = true;
      stream?.getTracks().forEach((t) => t.stop());
      setState("starting");
    };
  }, [open, onRead]);

  if (!open) return null;
  const blocked = state === "denied" || state === "unsupported";
  return (
    <div role="dialog" aria-modal="true" aria-label="Escanear código" className="fixed inset-0 z-50 flex flex-col bg-[oklch(0.12_0.02_268)] text-[oklch(0.97_0_0)]">
      {blocked ? (
        <div className="m-auto flex max-w-sm flex-col items-center px-6 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-surface-sunken text-ink-muted"><CameraOff aria-hidden className="size-6" strokeWidth={1.75} /></span>
          <h2 className="mt-4 text-heading font-medium">{state === "denied" ? "Nítida necesita la cámara para escanear" : "Este navegador no puede leer códigos"}</h2>
          <p className="mt-1 text-body-sm opacity-80">
            {state === "denied"
              ? "Dé permiso de cámara a este sitio desde el candado de la barra de direcciones y vuelva a intentarlo."
              : "Use Chrome en Android para escanear, o escriba el código que aparece debajo de las barras."}
          </p>
          <div className="mt-6 flex gap-2">
            <Button variant="secondary" icon={Keyboard} onClick={onClose}>Escribir código</Button>
          </div>
        </div>
      ) : (
        <>
          <video ref={video} playsInline muted className="absolute inset-0 size-full object-cover" />
          <div className="relative z-10 bg-[oklch(0_0_0/0.55)] px-4 py-4 text-center text-body">Apunte al código del repuesto</div>
          <div className="relative z-10 flex flex-1 items-center justify-center">
            <motion.div
              animate={{ borderColor: state === "read" ? "var(--verified)" : "oklch(1 0 0)" }}
              transition={{ duration: 0.2 }}
              className="relative h-[160px] w-[260px] rounded-md border-2 shadow-[0_0_0_9999px_oklch(0_0_0/0.55)]"
            >
              {state === "scanning" && (
                <motion.span
                  className="absolute inset-x-3 h-0.5 rounded-full bg-[oklch(0.8_0.1_168)]"
                  animate={{ top: ["12%", "88%", "12%"] }}
                  transition={{ duration: 1.6, repeat: Infinity, ease: "easeInOut" }}
                />
              )}
            </motion.div>
          </div>
        </>
      )}
      <div className="relative z-10 flex justify-center gap-2 bg-[oklch(0_0_0/0.55)] px-4 py-4 pb-safe">
        {!blocked && <Button variant="secondary" icon={Keyboard} onClick={onClose}>Escribir código</Button>}
        <button type="button" onClick={onClose} className="inline-flex h-12 items-center gap-2 rounded-md px-4 text-body-sm font-medium hover:bg-[oklch(1_0_0/0.1)]">
          <X aria-hidden className="size-[18px]" /> Cerrar
        </button>
      </div>
    </div>
  );
}

/** EAN-8/EAN-13/UPC-A: valida el dígito verificador en el cliente; el backend lo vuelve a validar. */
export function validProductCode(code: string): boolean {
  if (!/^(\d{8}|\d{12}|\d{13})$/.test(code)) return false;
  const digits = code.padStart(13, "0").split("").map(Number);
  const check = digits.pop()!;
  const sum = digits.reduce((s, d, i) => s + d * (i % 2 ? 3 : 1), 0);
  return (10 - (sum % 10)) % 10 === check;
}
