// Formatos es-CO / America/Bogota (DESIGN.MD §3).
const TZ = "America/Bogota";

const money = new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
const num = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 0 });
const dateFmt = new Intl.DateTimeFormat("es-CO", { day: "numeric", month: "short", year: "numeric", timeZone: TZ });
const dateTimeFmt = new Intl.DateTimeFormat("es-CO", {
  day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit", timeZone: TZ,
});
const rtf = new Intl.RelativeTimeFormat("es-CO", { numeric: "auto" });

const clean = (s: string) => s.replace(/\./g, "").replace(/ /g, " ");

/** `$ 125.000`. Un valor ausente se muestra como "—", nunca NaN. */
export function cop(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return money.format(value).replace(/ /g, " ");
}

export function km(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return `${num.format(value)} km`;
}

export function number(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "—";
  return num.format(value);
}

/** `12 mar 2026` (DESIGN.MD §3): se arma por partes para no depender del "de" que agrega es-CO. */
function dayMonthYear(d: Date): string {
  const p = Object.fromEntries(dateFmt.formatToParts(d).map((x) => [x.type, x.value]));
  return clean(`${p.day} ${p.month} ${p.year}`);
}

export function date(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T12:00:00-05:00` : iso);
  return Number.isNaN(d.getTime()) ? "—" : dayMonthYear(d);
}

/** `12 mar 2026, 3:40 p. m.` */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  const p = Object.fromEntries(dateTimeFmt.formatToParts(d).map((x) => [x.type, x.value]));
  return `${dayMonthYear(d)}, ${p.hour}:${p.minute} ${(p.dayPeriod ?? "").replace(/ /g, " ")}`.trim();
}

/** Relativa solo hasta 7 días; después, fecha completa (DESIGN.MD §10). */
export function relative(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  const diff = d.getTime() - Date.now();
  const abs = Math.abs(diff);
  const min = 60_000, hour = 60 * min, day = 24 * hour;
  if (abs > 7 * day) return date(iso);
  if (abs < min) return "hace un momento";
  if (abs < hour) return rtf.format(Math.round(diff / min), "minute");
  if (abs < day) return rtf.format(Math.round(diff / hour), "hour");
  return rtf.format(Math.round(diff / day), "day");
}

/** `ABC12D` → `ABC 12D`. La API las guarda sin espacio. */
export function plate(p: string | null | undefined): string {
  if (!p) return "Sin placa";
  const v = p.replace(/[\s-]/g, "").toUpperCase();
  return v.length > 3 ? `${v.slice(0, 3)} ${v.slice(3)}` : v;
}

export function normalizePlate(p: string): string {
  return p.replace(/[\s-]/g, "").toUpperCase();
}

/** `a91f…c3d2`. Nunca el hash completo en la vista normal. */
export function shortHash(h: string | null | undefined): string {
  if (!h) return "—";
  return h.length <= 10 ? h : `${h.slice(0, 4)}…${h.slice(-4)}`;
}

export function shortId(id: string): string {
  return id.replace(/-/g, "").slice(-4).toUpperCase();
}

/** Día de caja de hoy en Bogotá, `AAAA-MM-DD`. */
export function bogotaToday(offsetDays = 0): string {
  const d = new Date(Date.now() + offsetDays * 86_400_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(d);
}

export function motoName(m: { brand?: string | null; line?: string | null } | null | undefined): string {
  const s = [m?.brand, m?.line].filter(Boolean).join(" ");
  return s || "Moto sin datos de marca";
}

export const ROLE_LABEL: Record<string, string> = {
  WORKSHOP_OWNER: "Dueño del taller",
  MECHANIC: "Mecánico",
  FRONT_DESK: "Recepción",
};

export const JOB_LABEL: Record<string, string> = {
  OIL_CHANGE: "Cambio de aceite",
  DRIVE_CHAIN_KIT: "Kit de arrastre",
  BRAKES: "Frenos",
  SUSPENSION: "Suspensión",
  ELECTRICAL: "Sistema eléctrico",
  ENGINE: "Motor",
  TIRES: "Llantas",
  TUNE_UP: "Sincronización",
  OTHER: "Otro trabajo",
};

export const METHOD_LABEL: Record<string, string> = {
  CASH: "Efectivo",
  TRANSFER: "Transferencia",
  CARD: "Tarjeta",
  STORE_CREDIT: "Saldo a favor",
};

export const CONDITION_LABEL: Record<string, string> = {
  OEM: "Original (OEM)",
  AFTERMARKET: "Alternativo",
  USED: "Usado",
};

export const ENTRY_LABEL: Record<string, string> = {
  INITIAL_REGISTRATION: "Registro inicial",
  SERVICE: "Servicio",
  AMENDMENT: "Corrección",
  ODOMETER_CHANGE: "Cambio de odómetro",
  OWNERSHIP_TRANSFER: "Cambio de propietario",
};

export const WORKSHOP_STATUS_LABEL: Record<string, string> = {
  PENDING_VERIFICATION: "Pendiente de verificación",
  VERIFIED: "Verificado",
  SUSPENDED: "Suspendido",
};
