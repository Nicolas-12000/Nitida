// Backend simulado para el modo demostración. Responde con las mismas formas y códigos que el backend real
// (docs/api.md) y aplica sus reglas principales, para que el frontend sea el mismo en ambos modos.
import { ApiError } from "../errors";
import { date as fmtDate } from "../format";
import { personalDataIn } from "../public-text";
import { base32Encode, verifyTotp } from "../totp";
import { GENESIS, linkHash, randomToken, uuid } from "../chain";
import type { ApiResult } from "../api";
import type { OrderStatus, Role } from "../types";
import { CATALOG, POLICY_VERSION, bogotaDay, createSeed, type DB, type DOrder, type DMoto, type DUser } from "./seed";

const KEY = "nitida-demo-db";
const files = new Map<string, string>();
let db: DB | null = null;

function loadDb(): DB {
  if (db) return db;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as DB;
      if (parsed.v === 5) return (db = parsed);
    }
  } catch {
    /* sin almacenamiento: se arranca de cero */
  }
  return (db = createSeed());
}

function save() {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(db));
  } catch {
    /* la demo sigue en memoria */
  }
}

export function resetDemo() {
  db = createSeed();
  save();
}

const fail = (status: number, code: string, title = code, extra: Record<string, unknown> = {}): never => {
  throw new ApiError({ type: "about:blank", title, status, code, ...extra });
};
const notFound = () => fail(404, "NOT_FOUND", "No encontrado");

interface Ctx {
  db: DB;
  user: DUser | null;
  workshopId: string | null;
  query: URLSearchParams;
  headers: Headers;
}

function requireUser(c: Ctx): DUser {
  if (!c.user) fail(401, "UNAUTHENTICATED", "Inicie sesión");
  return c.user!;
}

/** T + roles: un taller ajeno responde 404, nunca 403. */
function requireWs(c: Ctx, ...roles: Role[]) {
  const user = requireUser(c);
  if (!c.workshopId) fail(400, "WORKSHOP_REQUIRED");
  const m = c.db.memberships.find((x) => x.userId === user.id && x.workshopId === c.workshopId && x.active);
  if (!m) notFound();
  if (roles.length && !roles.includes(m!.role)) fail(403, "FORBIDDEN", "Sin permiso");
  const ws = c.db.workshops.find((w) => w.id === c.workshopId)!;
  return { user, role: m!.role, ws };
}

function requireWritable(c: Ctx, ...roles: Role[]) {
  const r = requireWs(c, ...roles);
  if (r.ws.status === "SUSPENDED") fail(423, "WORKSHOP_SUSPENDED");
  return r;
}

const now = () => new Date().toISOString();
const str = (v: unknown) => (typeof v === "string" ? v.trim() : "");
const int = (v: unknown) => (typeof v === "number" && Number.isInteger(v) ? v : NaN);

function lastMileage(d: DB, motoId: string): number | null {
  const entries = d.entries.filter((e) => e.motorcycleId === motoId && typeof e.content.mileage === "number");
  return entries.length ? (entries[entries.length - 1].content.mileage as number) : null;
}

function orderTotal(o: DOrder) {
  return o.laborCost + o.parts.reduce((s, p) => s + p.quantity * p.unitPrice, 0);
}

function paidOf(d: DB, orderId: string) {
  return d.payments.filter((p) => p.orderId === orderId && !p.voided).reduce((s, p) => s + p.amount, 0);
}

function orderView(d: DB, o: DOrder, role: Role) {
  const money = role !== "MECHANIC";
  return {
    id: o.id, intakeId: o.intakeId, motorcycleId: o.motorcycleId, customerId: o.customerId, status: o.status, diagnosis: o.diagnosis,
    mileage: o.mileage, laborCost: o.laborCost, total: orderTotal(o), warrantyDays: o.warrantyDays, warrantyKm: o.warrantyKm,
    warranty: o.warrantyOf != null, mechanicId: o.mechanicId, deliveredAt: o.deliveredAt, version: o.version, jobs: o.jobs,
    parts: o.parts.map((p) => ({
      id: p.id, name: p.name, reference: p.reference, brand: p.brand, condition: p.condition, quantity: p.quantity, unitPrice: p.unitPrice,
      productCode: p.productCode, hasSerial: p.serial != null, evidenceLevel: p.evidenceLevel, authenticityMethod: p.authenticityMethod,
    })),
    payments: money
      ? d.payments.filter((p) => p.orderId === o.id).map(({ id, method, amount, recordedAt, voided }) => ({ id, method, amount, recordedAt, voided }))
      : [],
    paid: money ? paidOf(d, o.id) : null,
  };
}

function findOrder(c: Ctx, id: string) {
  const o = c.db.orders.find((x) => x.id === id && x.workshopId === c.workshopId);
  if (!o) notFound();
  return o!;
}

function checkVersion(o: { version: number }, v: unknown) {
  if (int(v) !== o.version) fail(409, "VERSION_CONFLICT", "La orden cambió");
}

function raiseAlert(d: DB, workshopId: string, type: DB["alerts"][number]["type"], referenceId: string | null, details: string) {
  d.alerts.push({ id: uuid(), workshopId, type, referenceId, details, createdAt: now(), resolvedAt: null, comment: null });
}

async function addEntry(d: DB, m: DMoto, type: DB["entries"][number]["type"], workshopId: string | null, extra: Record<string, unknown>) {
  const seq = d.entries.filter((e) => e.motorcycleId === m.id).length + 1;
  const at = now();
  const ws = d.workshops.find((w) => w.id === workshopId);
  d.entries.push({
    motorcycleId: m.id, seq, type, recordedAt: at, workshopId, hash: null,
    content: {
      v: 1, motorcycleId: m.id, seq, type, workshopId, workshopVerified: ws?.status === "VERIFIED", occurredAt: at,
      privateHash: randomToken(22, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"), ...extra,
    },
  });
  await sealChains(d);
}

/** Calcula los hashes que falten (las semillas nacen sin hash). */
async function sealChains(d: DB) {
  const byMoto = new Map<string, DB["entries"]>();
  for (const e of d.entries) {
    const list = byMoto.get(e.motorcycleId) ?? [];
    list.push(e);
    byMoto.set(e.motorcycleId, list);
  }
  for (const list of byMoto.values()) {
    list.sort((a, b) => a.seq - b.seq);
    let prev = GENESIS;
    for (const e of list) {
      if (!e.hash) e.hash = await linkHash(prev, e.content);
      prev = e.hash;
    }
  }
}

async function verify(d: DB, motoId: string) {
  const list = d.entries.filter((e) => e.motorcycleId === motoId).sort((a, b) => a.seq - b.seq);
  let prev = GENESIS;
  for (const e of list) {
    const h = await linkHash(prev, e.content);
    if (h !== e.hash) return { status: "INVALID" as const, seq: e.seq, reason: "HASH_MISMATCH" };
    prev = h;
  }
  return { status: "VALID" as const, seq: null, reason: null };
}

function mask(plate: string | null) {
  if (!plate) return null;
  return plate.length === 6 ? `${plate.slice(0, 3)}**${plate[5]}` : `${plate.slice(0, 3)}**`;
}

async function historyView(d: DB, m: DMoto, until: string | null, masked: boolean) {
  const all = d.entries.filter((e) => e.motorcycleId === m.id).sort((a, b) => a.seq - b.seq);
  const shown = until ? all.filter((e) => e.recordedAt < until) : all;
  let partsTotal = 0, partsWithEvidence = 0, transfers = 0;
  const entries = shown.map((e) => {
    const ws = d.workshops.find((w) => w.id === e.workshopId);
    if (e.type === "OWNERSHIP_TRANSFER") transfers++;
    if (e.type === "SERVICE" && Array.isArray(e.content.parts)) {
      for (const p of e.content.parts as { evidenceLevel: string; hasPhotoEvidence: boolean }[]) {
        partsTotal++;
        if (p.evidenceLevel !== "MANUAL" || p.hasPhotoEvidence) partsWithEvidence++;
      }
    }
    return {
      seq: e.seq, type: e.type, recordedAt: e.recordedAt, workshopName: ws?.name ?? null, municipality: ws?.municipality ?? null,
      workshopVerified: e.content.workshopVerified === true, workshopSuspendedAt: ws?.suspendedAt ?? null, content: e.content,
    };
  });
  return {
    motorcycle: { plate: masked ? mask(m.plate) : m.plate, brand: m.brand, line: m.line, modelYear: m.modelYear, displacementCc: m.displacementCc },
    entries, verification: await verify(d, m.id), evidence: { partsTotal, partsWithEvidence }, transfers,
  };
}

function page<T extends { id: string }>(items: T[], c: Ctx): { data: T[]; next: string | null } {
  const limit = c.query.get("limit") ? Number(c.query.get("limit")) : 50;
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) fail(422, "INVALID_LIMIT");
  const cursor = c.query.get("cursor");
  let start = 0;
  if (cursor) {
    const idx = items.findIndex((i) => i.id === atob(cursor));
    if (idx < 0) fail(400, "INVALID_CURSOR");
    start = idx + 1;
  }
  const data = items.slice(start, start + limit);
  const next = start + limit < items.length ? btoa(data[data.length - 1].id) : null;
  return { data, next };
}

/** Texts.requirePublic: longitud y, como queda en el historial público, sin correos, teléfonos ni cédulas (RN-22). */
function publicText(v: string, min: number, max: number, code: string): string {
  if (v.length < min || v.length > max) fail(422, code);
  if (personalDataIn(v)) fail(422, "PERSONAL_DATA");
  return v;
}

function validPlate(p: string) {
  return /^[A-Z]{3}\d{2}[A-Z]?$/.test(p);
}

type Handler = (c: Ctx, params: string[], body: Record<string, unknown>) => unknown | Promise<unknown>;
const routes: [string, RegExp, Handler][] = [];
const on = (method: string, pattern: string, h: Handler) =>
  routes.push([method, new RegExp(`^${pattern.replace(/:[a-zA-Z]+/g, "([^/]+)")}$`), h]);

// ── Autenticación ──────────────────────────────────────────────
const me = (d: DB, u: DUser) => ({
  id: u.id, email: u.email, displayName: u.displayName, platformAdmin: u.platformAdmin,
  totpEnabled: !!u.totpEnabled,
  // Igual que el perfil de producción (nitida.admin.totp-required=true): el admin sin TOTP no tiene privilegios.
  totpSetupRequired: u.platformAdmin && !u.totpEnabled,
  memberships: d.memberships.filter((m) => m.userId === u.id && m.active).map((m) => ({ workshopId: m.workshopId, role: m.role })),
});

/** Solo se llega aquí con la contraseña correcta (o el atajo de la demostración), igual que en LoginUseCase. */
async function checkSecondFactor(u: DUser, code: unknown) {
  if (!u.totpEnabled || !u.totpSecret) return;
  const value = typeof code === "string" ? code.replace(/\s/g, "").toUpperCase() : "";
  if (!value) fail(401, "TOTP_REQUIRED", "Ingrese el código de su aplicación de autenticación");
  const step = await verifyTotp(u.totpSecret, value, u.totpLastStep ?? null);
  if (step != null) {
    u.totpLastStep = step;
    return;
  }
  const i = (u.recoveryCodes ?? []).indexOf(value);
  if (value.length >= 8 && i >= 0) {
    u.recoveryCodes!.splice(i, 1); // un código de recuperación sirve una sola vez
    return;
  }
  fail(401, "INVALID_CREDENTIALS");
}

const mailbox: { to: string; subject: string; code: string; at: string }[] = [];
function sendMail(to: string, subject: string, code: string) {
  mailbox.push({ to: to.toLowerCase(), subject, code, at: now() });
}
export function demoLastMail(to: string) {
  return [...mailbox].reverse().find((m) => m.to === to.trim().toLowerCase()) ?? null;
}

on("POST", "/auth/login", async (c, _, b) => {
  const u = c.db.users.find((x) => x.email.toLowerCase() === str(b.email).toLowerCase());
  if (!u || u.password !== b.password) fail(401, "INVALID_CREDENTIALS");
  await checkSecondFactor(u!, b.totp);
  c.db.sessionUserId = u!.id;
  return me(c.db, u!);
});
on("POST", "/auth/demo-login", async (c, _, b) => {
  const u = (c.db.users.find((x) => x.id === b.userId) ?? notFound()) as DUser;
  await checkSecondFactor(u, b.totp);
  c.db.sessionUserId = u.id;
  return me(c.db, u);
});
on("POST", "/auth/totp/setup", (c) => {
  const u = requireUser(c);
  if (u.totpEnabled) fail(409, "TOTP_ALREADY_ENABLED");
  const secret = base32Encode(crypto.getRandomValues(new Uint8Array(20)));
  u.totpPending = secret;
  return { secret, otpauthUri: `otpauth://totp/Nitida:${encodeURIComponent(u.email)}?secret=${secret}&issuer=Nitida&algorithm=SHA1&digits=6&period=30` };
});
on("POST", "/auth/totp/confirm", async (c, _, b) => {
  const u = requireUser(c);
  if (u.totpEnabled) fail(409, "TOTP_ALREADY_ENABLED");
  if (!u.totpPending) fail(409, "TOTP_NOT_STARTED");
  const step = await verifyTotp(u.totpPending!, str(b.code), null);
  if (step == null) fail(422, "INVALID_TOTP");
  u.totpSecret = u.totpPending;
  u.totpPending = undefined;
  u.totpEnabled = true;
  u.totpLastStep = step ?? undefined;
  u.recoveryCodes = Array.from({ length: 8 }, () => randomToken(10));
  return { recoveryCodes: u.recoveryCodes };
});
on("POST", "/auth/password/forgot", (c, _, b) => {
  const email = str(b.email).toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail(422, "INVALID_EMAIL");
  const u = c.db.users.find((x) => x.email === email);
  if (!u) return; // misma respuesta haya o no cuenta
  const token = randomToken(32, "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789");
  c.db.resets.push({ token, userId: u.id, expiresAt: new Date(Date.now() + 3_600_000).toISOString(), used: false });
  sendMail(email, "Recuperar la contraseña de Nítida", token);
});
on("POST", "/auth/password/reset", (c, _, b) => {
  const r = c.db.resets.find((x) => x.token === str(b.token) && !x.used && x.expiresAt > now());
  if (!r) notFound();
  const pw = typeof b.password === "string" ? b.password : "";
  if (pw.length < 12 || pw.length > 128 || /^(123456|password|contraseña)/i.test(pw)) fail(422, "WEAK_PASSWORD");
  r!.used = true;
  c.db.users.find((x) => x.id === r!.userId)!.password = pw;
});
on("POST", "/auth/logout", (c) => {
  c.db.sessionUserId = null;
});
on("GET", "/auth/me", (c) => me(c.db, requireUser(c)));
on("POST", "/auth/register", (c, _, b) => {
  const email = str(b.email).toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fail(422, "INVALID_EMAIL");
  const pw = typeof b.password === "string" ? b.password : "";
  if (pw.length < 12 || pw.length > 128 || /^(123456|password|contraseña)/i.test(pw)) fail(422, "WEAK_PASSWORD");
  if (c.db.users.some((u) => u.email === email)) fail(409, "EMAIL_TAKEN");
  const u = { id: uuid(), email, password: pw, displayName: str(b.displayName) || email, platformAdmin: false };
  c.db.users.push(u);
  return { userId: u.id };
});

// ── Talleres ───────────────────────────────────────────────────
on("POST", "/workshops", (c, _, b) => {
  const u = requireUser(c);
  for (const k of ["name", "documentNumber", "address", "municipality", "phone"]) if (!str(b[k])) fail(422, k === "documentNumber" ? "INVALID_DOCUMENT" : "INVALID_SETTING");
  const w = {
    id: uuid(), name: str(b.name), documentNumber: str(b.documentNumber), address: str(b.address), municipality: str(b.municipality),
    phone: str(b.phone), status: "PENDING_VERIFICATION" as const, verifiedAt: null, suspendedAt: null, intakeAlertHours: 24,
    cashTolerance: 0, stalledDays: 7, version: 0,
  };
  c.db.workshops.push(w);
  c.db.memberships.push({ userId: u.id, workshopId: w.id, role: "WORKSHOP_OWNER", active: true });
  return { workshopId: w.id };
});
on("GET", "/workshops/mine", (c) => {
  const u = requireUser(c);
  return c.db.memberships
    .filter((m) => m.userId === u.id && m.active)
    .map((m) => {
      const w = c.db.workshops.find((x) => x.id === m.workshopId)!;
      return { id: w.id, name: w.name, municipality: w.municipality, status: w.status, roles: [m.role] };
    });
});
on("GET", "/workshops/:id", (c, [id]) => {
  if (id !== c.workshopId) notFound();
  const { ws } = requireWs(c);
  return {
    id: ws.id, name: ws.name, municipality: ws.municipality, status: ws.status, verifiedAt: ws.verifiedAt, suspendedAt: ws.suspendedAt,
    intakeAlertHours: ws.intakeAlertHours, cashTolerance: ws.cashTolerance, stalledDays: ws.stalledDays, version: ws.version,
  };
});
on("PATCH", "/workshops/:id/settings", (c, [id], b) => {
  if (id !== c.workshopId) notFound();
  const { ws } = requireWritable(c, "WORKSHOP_OWNER");
  if (b.version !== undefined && int(b.version) !== ws.version) fail(409, "VERSION_CONFLICT");
  const h = int(b.intakeAlertHours), t = int(b.cashTolerance), s = int(b.stalledDays);
  if (!(h >= 1 && h <= 168) || !(t >= 0) || !(s >= 1 && s <= 90)) fail(422, "INVALID_SETTING");
  Object.assign(ws, { intakeAlertHours: h, cashTolerance: t, stalledDays: s, version: ws.version + 1 });
  return { intakeAlertHours: h, cashTolerance: t, stalledDays: s, version: ws.version };
});
on("POST", "/workshops/:id/invitations", (c, [id], b) => {
  if (id !== c.workshopId) notFound();
  requireWritable(c, "WORKSHOP_OWNER");
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(str(b.email))) fail(422, "INVALID_EMAIL");
  if (b.role !== "MECHANIC" && b.role !== "FRONT_DESK") fail(422, "INVALID_ROLE");
  // El código sale por correo y no vuelve en la respuesta (decisión 0008).
  const token = randomToken(32, "abcdefghijkmnopqrstuvwxyz23456789");
  const email = str(b.email).toLowerCase();
  const expiresAt = new Date(Date.now() + 72 * 3_600_000).toISOString();
  c.db.invitations.push({ token, workshopId: id, email, role: b.role as Role, accepted: false, expiresAt });
  sendMail(email, "Invitación a un taller en Nítida", token);
  return { invitationId: uuid(), expiresAt };
});
on("GET", "/workshops/:id/members", (c, [id]) => {
  if (id !== c.workshopId) notFound();
  requireWs(c, "WORKSHOP_OWNER");
  return c.db.memberships
    .filter((m) => m.workshopId === id)
    .map((m) => {
      const u = c.db.users.find((x) => x.id === m.userId)!;
      return { userId: u.id, email: u.email, displayName: u.displayName, role: m.role, active: m.active };
    });
});
on("DELETE", "/workshops/:id/members/:userId", (c, [id, userId]) => {
  if (id !== c.workshopId) notFound();
  requireWritable(c, "WORKSHOP_OWNER");
  const m = c.db.memberships.find((x) => x.workshopId === id && x.userId === userId && x.active) ?? notFound();
  const owners = c.db.memberships.filter((x) => x.workshopId === id && x.role === "WORKSHOP_OWNER" && x.active);
  if ((m as DB["memberships"][number]).role === "WORKSHOP_OWNER" && owners.length === 1) fail(409, "LAST_OWNER");
  (m as DB["memberships"][number]).active = false;
});
on("POST", "/invitations/:token/accept", (c, [token]) => {
  const u = requireUser(c);
  const inv = c.db.invitations.find((i) => i.token === token.trim() && !i.accepted && i.email === u.email && (!i.expiresAt || i.expiresAt > now())) ?? notFound();
  const i = inv as DB["invitations"][number];
  i.accepted = true;
  c.db.memberships.push({ userId: u.id, workshopId: i.workshopId, role: i.role, active: true });
  return { workshopId: i.workshopId };
});
on("GET", "/admin/workshops", (c) => {
  const u = requireUser(c);
  // Sin TOTP el administrador no recibe privilegios en la sesión (/admin/** → 403).
  if (!u.platformAdmin || !u.totpEnabled) fail(403, "FORBIDDEN");
  const status = c.query.get("status") ?? "PENDING_VERIFICATION";
  return c.db.workshops
    .filter((w) => w.status === status)
    .map(({ id, name, municipality, documentNumber, phone, status }) => ({ id, name, municipality, documentNumber, phone, status }));
});
on("POST", "/admin/workshops/:id/status", (c, [id], b) => {
  const u = requireUser(c);
  // Sin TOTP el administrador no recibe privilegios en la sesión (/admin/** → 403).
  if (!u.platformAdmin || !u.totpEnabled) fail(403, "FORBIDDEN");
  const w = c.db.workshops.find((x) => x.id === id) ?? notFound();
  const ws = w as DB["workshops"][number];
  if (!["VERIFIED", "SUSPENDED", "PENDING_VERIFICATION"].includes(b.status as string)) fail(422, "INVALID_STATUS");
  if (str(b.evidence).length < 5) fail(422, "REASON_REQUIRED");
  ws.status = b.status as typeof ws.status;
  if (ws.status === "VERIFIED") ws.verifiedAt = now();
  ws.suspendedAt = ws.status === "SUSPENDED" ? now() : null;
  return { status: ws.status };
});

// ── Recepción, clientes y motos ────────────────────────────────
on("POST", "/intakes", async (c, _, b) => {
  const { ws } = requireWritable(c);
  const plate = str(b.plate).replace(/[\s-]/g, "").toUpperCase();
  const vin = str(b.vin).toUpperCase();
  if (!plate && !vin) fail(422, "IDENTIFIER_REQUIRED");
  if (plate && !validPlate(plate)) fail(422, "INVALID_PLATE");
  if (vin && !/^[A-HJ-NPR-Z0-9]{17}$/.test(vin)) fail(422, "INVALID_VIN");
  const mileage = int(b.mileage);
  if (!(mileage >= 0 && mileage <= 999_999)) fail(422, "INVALID_MILEAGE");
  if (!b.odometerPhotoId || !b.motorcyclePhotoId) fail(422, "PHOTO_REQUIRED");
  let moto = c.db.motos.find((m) => (plate && m.plate === plate) || (vin && m.vin === vin));
  const isNew = !moto;
  if (moto) {
    const last = lastMileage(c.db, moto.id);
    if (last != null && mileage < last) fail(422, "MILEAGE_REGRESSION", "Kilometraje menor", { lastMileage: last });
    if (c.db.intakes.some((i) => i.motorcycleId === moto!.id && i.status === "OPEN" && i.workshopId === ws.id)) fail(409, "INTAKE_ALREADY_OPEN");
    if (!moto.seenBy.includes(ws.id)) moto.seenBy.push(ws.id);
  } else {
    moto = {
      id: uuid(), plate: plate || null, vin: vin || null, brand: str(b.brand) || null, line: str(b.line) || null,
      modelYear: Number.isInteger(b.modelYear) ? (b.modelYear as number) : null,
      displacementCc: Number.isInteger(b.displacementCc) ? (b.displacementCc as number) : null, seenBy: [ws.id],
    };
    c.db.motos.push(moto);
    await addEntry(c.db, moto, "INITIAL_REGISTRATION", ws.id, { mileage, mileageDeclared: true });
  }
  const intake = { id: uuid(), workshopId: ws.id, motorcycleId: moto.id, mileage, status: "OPEN" as const, createdAt: now() };
  c.db.intakes.push(intake);
  return { intakeId: intake.id, motorcycleId: moto.id, newMotorcycle: isNew };
});
on("GET", "/intakes", (c) => {
  const { ws } = requireWs(c);
  return c.db.intakes
    .filter((i) => i.workshopId === ws.id && i.status === "OPEN")
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map((i) => {
      const m = c.db.motos.find((x) => x.id === i.motorcycleId)!;
      return { id: i.id, motorcycleId: m.id, plate: m.plate, brand: m.brand, line: m.line, mileage: i.mileage, createdAt: i.createdAt };
    });
});
on("POST", "/intakes/:id/discard", (c, [id], b) => {
  const { ws } = requireWritable(c);
  const i = c.db.intakes.find((x) => x.id === id && x.workshopId === ws.id) ?? notFound();
  const intake = i as DB["intakes"][number];
  if (intake.status !== "OPEN") fail(409, "INTAKE_NOT_OPEN");
  if (!["CONSULTATION_ONLY", "CUSTOMER_WITHDREW", "ENTRY_ERROR"].includes(b.reason as string)) fail(422, "REASON_REQUIRED");
  intake.status = "DISCARDED";
});
on("POST", "/customers", (c, _, b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  if (b.consentAccepted !== true) fail(422, "CONSENT_REQUIRED");
  if (b.policyVersion !== POLICY_VERSION) fail(422, "STALE_POLICY");
  const phone = str(b.phone).replace(/\D/g, "");
  if (!/^3\d{9}$/.test(phone)) fail(422, "INVALID_PHONE");
  if (str(b.fullName).length < 3) fail(422, "DETAILS_REQUIRED");
  const cu = { id: uuid(), workshopId: ws.id, fullName: str(b.fullName), phone, nationalId: str(b.nationalId) || null, email: str(b.email) || null };
  c.db.customers.push(cu);
  return { customerId: cu.id };
});
on("GET", "/customers", (c) => {
  const { ws } = requireWs(c);
  const q = (c.query.get("q") ?? "").trim().toLowerCase();
  if (q.length < 2) fail(422, "QUERY_TOO_SHORT");
  return c.db.customers
    .filter((x) => x.workshopId === ws.id && (x.fullName.toLowerCase().includes(q) || x.phone.includes(q) || (x.nationalId ?? "").includes(q)))
    .slice(0, 20)
    .map(({ id, fullName, phone, nationalId, email }) => ({ id, fullName, phone, nationalId, email }));
});
on("GET", "/motorcycles/lookup", (c) => {
  requireWs(c);
  const plate = (c.query.get("plate") ?? "").replace(/[\s-]/g, "").toUpperCase();
  const vin = (c.query.get("vin") ?? "").toUpperCase();
  const m = c.db.motos.find((x) => (plate && x.plate === plate) || (vin && x.vin === vin)) ?? notFound();
  const moto = m as DMoto;
  return {
    id: moto.id, vin: moto.vin, plate: moto.plate, maskedPlate: mask(moto.plate), legacyPlateFormat: (moto.plate ?? "").length === 5,
    brand: moto.brand, line: moto.line, modelYear: moto.modelYear, displacementCc: moto.displacementCc,
  };
});
on("PATCH", "/motorcycles/:id/plate", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  const m = c.db.motos.find((x) => x.id === id && x.seenBy.includes(ws.id)) ?? notFound();
  const moto = m as DMoto;
  const plate = str(b.plate).replace(/[\s-]/g, "").toUpperCase();
  if (!validPlate(plate)) fail(422, "INVALID_PLATE");
  if (c.db.motos.some((x) => x.plate === plate && x.id !== id)) fail(409, "PLATE_IN_USE");
  if (moto.plate) c.db.plateHistory.push({ motorcycleId: id, plate: moto.plate, until: now() });
  moto.plate = plate;
});

// ── Órdenes ────────────────────────────────────────────────────
on("POST", "/orders", (c, _, b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const intake = c.db.intakes.find((i) => i.id === b.intakeId && i.workshopId === ws.id) ?? notFound();
  const i = intake as DB["intakes"][number];
  if (i.status !== "OPEN") fail(409, "INTAKE_NOT_OPEN");
  const diagnosis = str(b.diagnosis);
  publicText(diagnosis, 10, 2000, "INVALID_DIAGNOSIS");
  i.status = "CONVERTED";
  const o: DOrder = {
    id: uuid(), workshopId: ws.id, intakeId: i.id, motorcycleId: i.motorcycleId, customerId: (b.customerId as string) || null, status: "OPEN",
    diagnosis, mileage: i.mileage, laborCost: 0, warrantyDays: null, warrantyKm: null, warrantyOf: (b.warrantyOfOrderId as string) || null,
    mechanicId: (b.mechanicId as string) || null, deliveredAt: null, version: 1, jobs: [], parts: [], createdAt: now(), updatedAt: now(),
  };
  c.db.orders.push(o);
  return { orderId: o.id, motorcycleId: o.motorcycleId, mileage: o.mileage };
});
on("GET", "/orders", (c) => {
  const { ws } = requireWs(c);
  const statuses = (c.query.get("status") ?? "").split(",").filter(Boolean);
  const all = c.db.orders
    .filter((o) => o.workshopId === ws.id && (!statuses.length || statuses.includes(o.status)))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .map((o) => ({ id: o.id, motorcycleId: o.motorcycleId, status: o.status, diagnosis: o.diagnosis, total: orderTotal(o), updatedAt: o.updatedAt }));
  const p = page(all, c);
  if (p.next) c.headers.set("X-Next-Cursor", p.next);
  return p.data;
});
on("GET", "/orders/:id", (c, [id]) => {
  const { role } = requireWs(c);
  return orderView(c.db, findOrder(c, id), role);
});
on("PATCH", "/orders/:id", (c, [id], b) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  checkVersion(o, b.version);
  if (o.status === "DELIVERED" || o.status === "CANCELLED") fail(409, "ORDER_NOT_EDITABLE");
  if (b.diagnosis !== undefined) {
    const d = str(b.diagnosis);
    publicText(d, 10, 2000, "INVALID_DIAGNOSIS");
    o.diagnosis = d;
  }
  if (b.laborCost !== undefined) {
    const l = int(b.laborCost);
    if (!(l >= 0 && l <= 50_000_000)) fail(422, "INVALID_AMOUNT");
    o.laborCost = l;
  }
  if (b.warrantyDays !== undefined) o.warrantyDays = (b.warrantyDays as number) ?? null;
  if (b.warrantyKm !== undefined) o.warrantyKm = (b.warrantyKm as number) ?? null;
  if (Array.isArray(b.jobs)) {
    for (const j of b.jobs as { type: string; description: string }[]) {
      if (!j.type) fail(422, "INVALID_JOB");
      publicText(str(j.description), 3, 500, "INVALID_JOB");
    }
    o.jobs = (b.jobs as DOrder["jobs"]).map((j) => ({ type: j.type, description: j.description.trim() }));
  }
  o.version++;
  o.updatedAt = now();
});
on("POST", "/orders/:id/transitions", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  checkVersion(o, b.version);
  const next: Record<OrderStatus, OrderStatus[]> = {
    OPEN: ["IN_PROGRESS", "CANCELLED"], IN_PROGRESS: ["COMPLETED", "CANCELLED"], COMPLETED: ["IN_PROGRESS"], DELIVERED: [], CANCELLED: [],
  };
  const target = b.status as OrderStatus;
  if (!next[o.status].includes(target)) fail(409, "INVALID_TRANSITION");
  const reason = str(b.reason);
  if (target === "COMPLETED" && !o.jobs.length) fail(422, "JOBS_REQUIRED");
  if ((target === "CANCELLED" || (target === "IN_PROGRESS" && o.status === "COMPLETED")) && (reason.length < 5 || reason.length > 500)) fail(422, "REASON_REQUIRED");
  if (target === "CANCELLED" && o.parts.length) raiseAlert(c.db, ws.id, "F2_CANCELLED_WITH_PARTS", o.id, "La orden se canceló después de registrar repuestos.");
  o.status = target;
  o.version++;
  o.updatedAt = now();
  return { status: o.status };
});
on("POST", "/orders/:id/parts", (c, [id], b) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  if (o.status === "DELIVERED" || o.status === "CANCELLED") fail(409, "ORDER_NOT_EDITABLE");
  const q = int(b.quantity), price = int(b.unitPrice);
  publicText(str(b.name), 2, 120, "INVALID_PART");
  if (!str(b.brand) || !["OEM", "AFTERMARKET", "USED"].includes(b.condition as string)) fail(422, "INVALID_PART");
  if (!(q >= 1 && q <= 999)) fail(422, "INVALID_QUANTITY");
  if (!(price >= 0 && price <= 50_000_000)) fail(422, "INVALID_AMOUNT");
  const code = str(b.productCode) || null;
  if (code && !CATALOG[code] && !/^\d{8,14}$/.test(code)) fail(422, "INVALID_PRODUCT_CODE");
  const p = {
    id: uuid(), name: str(b.name), reference: str(b.reference) || null, brand: str(b.brand), condition: b.condition as DOrder["parts"][number]["condition"],
    quantity: q, unitPrice: price, productCode: code, serial: null, evidenceLevel: (code ? "SCANNED" : "MANUAL") as DOrder["parts"][number]["evidenceLevel"],
    authenticityMethod: null,
  };
  o.parts.push(p);
  o.version++;
  o.updatedAt = now();
  return { partId: p.id };
});
on("DELETE", "/orders/:id/parts/:partId", (c, [id, partId]) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  if (o.status === "DELIVERED" || o.status === "CANCELLED") fail(409, "ORDER_NOT_EDITABLE");
  const before = o.parts.length;
  o.parts = o.parts.filter((p) => p.id !== partId);
  if (o.parts.length === before) notFound();
  o.version++;
  o.updatedAt = now();
});
on("POST", "/orders/:id/parts/:partId/serial", (c, [id, partId], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  const p = o.parts.find((x) => x.id === partId) ?? notFound();
  const part = p as DOrder["parts"][number];
  const serial = str(b.serial).toUpperCase();
  if (!/^[A-Z0-9-]{4,40}$/.test(serial)) fail(422, "INVALID_SERIAL");
  const active = c.db.serials.find((s) => s.serial === serial && s.active);
  if (active && active.motorcycleId !== o.motorcycleId) {
    raiseAlert(c.db, ws.id, "F6_DUPLICATE_SERIAL", o.id, "Se intentó usar un serial que ya está activo en otra moto.");
    return { accepted: false, evidenceLevel: part.evidenceLevel, message: "Este serial no se puede registrar. Puede guardar el repuesto sin serial." };
  }
  c.db.serials.push({ serial, motorcycleId: o.motorcycleId, active: true });
  part.serial = serial;
  if (part.evidenceLevel !== "AUTHENTICITY_VERIFIED") part.evidenceLevel = "UNIQUE_SERIAL";
  o.version++;
  return { accepted: true, evidenceLevel: part.evidenceLevel, message: "Serial registrado." };
});
on("POST", "/orders/:id/parts/:partId/authenticity", (c, [id, partId], b) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const o = findOrder(c, id);
  const p = (o.parts.find((x) => x.id === partId) ?? notFound()) as DOrder["parts"][number];
  if (!b.photoId) fail(422, "PHOTO_REQUIRED");
  p.evidenceLevel = "AUTHENTICITY_VERIFIED";
  p.authenticityMethod = str(b.method) || "Holograma y empaque revisados";
  o.version++;
  return { evidenceLevel: p.evidenceLevel };
});
on("POST", "/orders/:id/photos", (c, [id], b) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  findOrder(c, id);
  if (!b.fileId) fail(422, "FILE_REQUIRED");
  return {};
});
on("POST", "/orders/:id/payments", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  const o = findOrder(c, id);
  if (o.status === "DELIVERED" || o.status === "CANCELLED") fail(409, "ORDER_NOT_EDITABLE");
  if (!["CASH", "TRANSFER", "CARD", "STORE_CREDIT"].includes(b.method as string)) fail(422, "INVALID_METHOD");
  const amount = int(b.amount);
  if (!(amount > 0 && amount <= 50_000_000)) fail(422, "INVALID_AMOUNT");
  if (b.method === "STORE_CREDIT" && !o.customerId) fail(422, "CUSTOMER_REQUIRED");
  if (paidOf(c.db, o.id) + amount > orderTotal(o)) fail(422, "OVERPAYMENT");
  const p = { id: uuid(), orderId: o.id, workshopId: ws.id, method: b.method as DB["payments"][number]["method"], amount, recordedAt: now(), voided: false };
  c.db.payments.push(p);
  return { paymentId: p.id };
});
on("POST", "/payments/:id/void", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  const p = (c.db.payments.find((x) => x.id === id && x.workshopId === ws.id) ?? notFound()) as DB["payments"][number];
  if (p.voided) fail(409, "PAYMENT_ALREADY_VOIDED");
  const r = str(b.reason);
  if (r.length < 5) fail(422, "REASON_REQUIRED");
  p.voided = true;
});
on("POST", "/orders/:id/delivery", async (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  const o = findOrder(c, id);
  checkVersion(o, b.version);
  if (o.status !== "COMPLETED") fail(409, "INVALID_TRANSITION");
  const total = orderTotal(o);
  const paid = paidOf(c.db, o.id);
  if (total === 0 && !o.warrantyOf) fail(422, "INVALID_WARRANTY");
  if (paid !== total) fail(422, "INCOMPLETE_PAYMENTS", "Pagos incompletos", { pending: total - paid });
  o.status = "DELIVERED";
  o.deliveredAt = now();
  o.version++;
  o.updatedAt = now();
  const m = c.db.motos.find((x) => x.id === o.motorcycleId)!;
  await addEntry(c.db, m, "SERVICE", ws.id, {
    mileage: o.mileage, diagnosis: o.diagnosis, jobs: o.jobs,
    parts: o.parts.map((p) => ({
      name: p.name, brand: p.brand, condition: p.condition, quantity: p.quantity, productCode: p.productCode, evidenceLevel: p.evidenceLevel,
      serialHash: p.serial ? randomToken(64, "0123456789abcdef") : null, hasPhotoEvidence: p.evidenceLevel === "AUTHENTICITY_VERIFIED",
    })),
    warranty: o.warrantyDays == null && o.warrantyKm == null ? null : { days: o.warrantyDays, km: o.warrantyKm }, amends: null,
  });
  const claimCode = randomToken(8);
  const expiresAt = new Date(Date.now() + 30 * 86_400_000).toISOString();
  c.db.claimCodes.push({ code: claimCode, motorcycleId: m.id, expiresAt, used: false });
  return { orderId: o.id, total, claimCode, claimCodeExpiresAt: expiresAt };
});

// ── Catálogo y seriales ────────────────────────────────────────
on("GET", "/catalog/products/:code", (c, [code]) => {
  requireWs(c, "WORKSHOP_OWNER", "MECHANIC");
  if (!/^\d{8,14}$/.test(code)) fail(422, "INVALID_PRODUCT_CODE");
  const p = CATALOG[code] ?? notFound();
  return { code, ...(p as object) };
});
on("POST", "/catalog/products", (c, _, b) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const code = str(b.code);
  if (!/^\d{8,14}$/.test(code)) fail(422, "INVALID_PRODUCT_CODE");
  CATALOG[code] = { name: str(b.name), brand: str(b.brand), reference: str(b.reference) || null };
  return { code, ...CATALOG[code] };
});
on("POST", "/serials/:id/retire", (c) => {
  requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
});

// ── Historial y links ──────────────────────────────────────────
function wsMoto(c: Ctx, id: string, wsId: string) {
  const m = c.db.motos.find((x) => x.id === id && x.seenBy.includes(wsId));
  if (!m) notFound();
  return m!;
}
on("GET", "/motorcycles/:id/history", async (c, [id]) => {
  const { ws } = requireWs(c);
  return historyView(c.db, wsMoto(c, id, ws.id), null, false);
});
on("POST", "/motorcycles/:id/history/amendments", async (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const m = wsMoto(c, id, ws.id);
  const target = c.db.entries.find((e) => e.motorcycleId === id && e.seq === int(b.amendsSeq)) ?? notFound();
  const t = target as DB["entries"][number];
  if (t.workshopId !== ws.id) notFound();
  if (t.type === "AMENDMENT") fail(422, "CANNOT_AMEND_AMENDMENT");
  const reason = str(b.reason);
  publicText(reason, 10, 500, "INVALID_REASON");
  const corrected: Record<string, unknown> = {};
  if (str(b.diagnosis)) corrected.diagnosis = publicText(str(b.diagnosis), 10, 2000, "INVALID_DIAGNOSIS");
  if (Number.isInteger(b.mileage)) corrected.mileage = b.mileage;
  if (!Object.keys(corrected).length) fail(422, "NOTHING_TO_AMEND");
  await addEntry(c.db, m, "AMENDMENT", ws.id, { amends: { seq: t.seq }, reason, corrected });
  return {};
});
on("POST", "/motorcycles/:id/history/odometer-changes", async (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "MECHANIC");
  const m = wsMoto(c, id, ws.id);
  const mileage = int(b.mileage);
  if (!(mileage >= 0 && mileage <= 999_999)) fail(422, "INVALID_MILEAGE");
  publicText(str(b.reason), 10, 500, "INVALID_REASON");
  if (!b.photoId) fail(422, "PHOTO_REQUIRED");
  await addEntry(c.db, m, "ODOMETER_CHANGE", ws.id, { mileage, reason: str(b.reason), hasPhotoEvidence: true });
  return {};
});
function createLink(d: DB, motoId: string, days: unknown) {
  const n = days === undefined ? 30 : int(days);
  if (!(n >= 1 && n <= 365)) fail(422, "INVALID_EXPIRATION");
  const link = { id: uuid(), token: randomToken(32, "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"), motorcycleId: motoId,
    expiresAt: new Date(Date.now() + n * 86_400_000).toISOString(), revoked: false };
  d.links.push(link);
  return { linkId: link.id, token: link.token, expiresAt: link.expiresAt };
}
on("POST", "/motorcycles/:id/links", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  wsMoto(c, id, ws.id);
  return createLink(c.db, id, b.days);
});
on("DELETE", "/links/:id", (c, [id]) => {
  requireWritable(c, "WORKSHOP_OWNER", "FRONT_DESK");
  const l = (c.db.links.find((x) => x.id === id) ?? notFound()) as DB["links"][number];
  l.revoked = true;
});
on("GET", "/public/v/:token", async (c, [token]) => {
  const l = c.db.links.find((x) => x.token === token && !x.revoked && x.expiresAt > now());
  if (!l) fail(404, "LINK_NOT_AVAILABLE");
  const m = c.db.motos.find((x) => x.id === l!.motorcycleId)!;
  return historyView(c.db, m, null, true);
});

// ── Caja y alertas ─────────────────────────────────────────────
function expectedCash(d: DB, wsId: string, day: string) {
  return d.payments.filter((p) => p.workshopId === wsId && p.method === "CASH" && !p.voided && bogotaDay(p.recordedAt) === day).reduce((s, p) => s + p.amount, 0);
}
function checkDay(day: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) fail(400, "INVALID_DAY");
}
on("GET", "/cashdesk/counts/:day", (c, [day]) => {
  const { ws } = requireWs(c, "WORKSHOP_OWNER");
  checkDay(day);
  const closed = c.db.cash.find((x) => x.workshopId === ws.id && x.day === day);
  if (closed) {
    return { day, expected: closed.expected, counted: closed.counted, difference: closed.counted - closed.expected, status: closed.status,
      closedAt: closed.closedAt, reopenReason: closed.reopenReason };
  }
  const reopened = c.db.alerts.find((a) => a.type === "F3_CASH_MISMATCH" && a.referenceId === `reopen:${day}`);
  return { day, expected: expectedCash(c.db, ws.id, day), counted: null, difference: null, status: "OPEN", closedAt: null, reopenReason: reopened?.comment ?? null };
});
on("POST", "/cashdesk/counts/:day/close", (c, [day], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER");
  checkDay(day);
  if (day > bogotaDay(now())) fail(422, "FUTURE_DAY");
  if (c.db.cash.some((x) => x.workshopId === ws.id && x.day === day)) fail(409, "CASH_COUNT_CLOSED");
  const counted = int(b.counted);
  if (!(counted >= 0)) fail(422, "COUNTED_REQUIRED");
  const expected = expectedCash(c.db, ws.id, day);
  const diff = counted - expected;
  const status = Math.abs(diff) <= ws.cashTolerance ? "BALANCED" : "UNBALANCED";
  const rec = { workshopId: ws.id, day, expected, counted, status: status as "BALANCED" | "UNBALANCED", closedAt: now(), reopenReason: null };
  c.db.cash.push(rec);
  if (status === "UNBALANCED") {
    raiseAlert(c.db, ws.id, "F3_CASH_MISMATCH", null, `El cuadre del ${fmtDate(day)} tiene una diferencia de $ ${Math.abs(diff).toLocaleString("es-CO")}.`);
  }
  return { day, expected, counted, difference: diff, status, closedAt: rec.closedAt, reopenReason: null };
});
on("POST", "/cashdesk/counts/:day/reopen", (c, [day], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER");
  checkDay(day);
  const reason = str(b.reason);
  if (reason.length < 10 || reason.length > 500) fail(422, "INVALID_REASON");
  const idx = c.db.cash.findIndex((x) => x.workshopId === ws.id && x.day === day);
  if (idx < 0) fail(409, "CASH_COUNT_NOT_CLOSED");
  c.db.cash.splice(idx, 1);
  return { day, expected: expectedCash(c.db, ws.id, day), counted: null, difference: null, status: "OPEN", closedAt: null, reopenReason: reason };
});
on("GET", "/cashdesk/alerts", (c) => {
  const { ws } = requireWs(c, "WORKSHOP_OWNER");
  const status = c.query.get("status");
  const all = c.db.alerts
    .filter((a) => a.workshopId === ws.id && (!status || (status === "PENDING" ? !a.resolvedAt : !!a.resolvedAt)))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(({ id, type, referenceId, details, createdAt, resolvedAt, comment }) => ({ id, type, referenceId, details, createdAt, resolvedAt, comment }));
  const p = page(all, c);
  if (p.next) c.headers.set("X-Next-Cursor", p.next);
  return p.data;
});
on("POST", "/cashdesk/alerts/:id/resolve", (c, [id], b) => {
  const { ws } = requireWritable(c, "WORKSHOP_OWNER");
  const a = (c.db.alerts.find((x) => x.id === id && x.workshopId === ws.id) ?? notFound()) as DB["alerts"][number];
  if (a.resolvedAt) fail(409, "ALERT_ALREADY_RESOLVED");
  const comment = str(b.comment);
  if (comment.length < 5 || comment.length > 500) fail(422, "COMMENT_REQUIRED");
  a.resolvedAt = now();
  a.comment = comment;
});

// ── Portal del propietario ─────────────────────────────────────
function activeOwnership(d: DB, motoId: string, userId: string) {
  return d.ownership.find((o) => o.motorcycleId === motoId && o.userId === userId && !o.until);
}
on("POST", "/owner/claims", (c, _, b) => {
  const u = requireUser(c);
  const code = str(b.code).toUpperCase();
  const cc = c.db.claimCodes.find((x) => x.code === code && !x.used && x.expiresAt > now());
  if (!cc) notFound();
  if (activeOwnership(c.db, cc!.motorcycleId, u.id)) fail(409, "ALREADY_OWNED");
  for (const o of c.db.ownership) if (o.motorcycleId === cc!.motorcycleId && !o.until) o.until = now();
  cc!.used = true;
  c.db.ownership.push({ motorcycleId: cc!.motorcycleId, userId: u.id, since: now(), until: null });
  return { motorcycleId: cc!.motorcycleId };
});
on("GET", "/owner/motorcycles", (c) => {
  const u = requireUser(c);
  return c.db.ownership
    .filter((o) => o.userId === u.id)
    .map((o) => {
      const m = c.db.motos.find((x) => x.id === o.motorcycleId)!;
      return { motorcycleId: m.id, plate: m.plate, brand: m.brand, line: m.line, modelYear: m.modelYear, active: !o.until, since: o.since, until: o.until };
    });
});
on("GET", "/owner/motorcycles/:id/history", async (c, [id]) => {
  const u = requireUser(c);
  const o = c.db.ownership.filter((x) => x.motorcycleId === id && x.userId === u.id).sort((a, b) => b.since.localeCompare(a.since))[0];
  if (!o) notFound();
  const m = c.db.motos.find((x) => x.id === id)!;
  return historyView(c.db, m, o.until, false);
});
on("POST", "/owner/motorcycles/:id/links", (c, [id], b) => {
  const u = requireUser(c);
  if (!activeOwnership(c.db, id, u.id)) notFound();
  return createLink(c.db, id, b.days);
});
on("POST", "/owner/motorcycles/:id/transfers", (c, [id]) => {
  const u = requireUser(c);
  if (!activeOwnership(c.db, id, u.id)) notFound();
  const code = randomToken(10);
  const expiresAt = new Date(Date.now() + 7 * 86_400_000).toISOString();
  c.db.transfers.push({ code, motorcycleId: id, expiresAt, used: false, fromUserId: u.id });
  return { code, expiresAt };
});
on("POST", "/owner/transfers/:code/accept", async (c, [code]) => {
  const u = requireUser(c);
  const t = c.db.transfers.find((x) => x.code === code.toUpperCase() && !x.used && x.expiresAt > now());
  if (!t) notFound();
  if (t!.fromUserId === u.id) fail(422, "SAME_OWNER");
  t!.used = true;
  for (const o of c.db.ownership) if (o.motorcycleId === t!.motorcycleId && !o.until) o.until = now();
  c.db.ownership.push({ motorcycleId: t!.motorcycleId, userId: u.id, since: now(), until: null });
  const m = c.db.motos.find((x) => x.id === t!.motorcycleId)!;
  await addEntry(c.db, m, "OWNERSHIP_TRANSFER", null, { transferredAt: now() });
  return { motorcycleId: m.id };
});
on("GET", "/public/keys", () => ({ keys: [] }));

// ── Despacho ───────────────────────────────────────────────────
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function demoFetch<T>(method: string, fullPath: string, body: unknown, workshopId: string | null): Promise<ApiResult<T>> {
  await sleep(160 + Math.random() * 260);
  const d = loadDb();
  await sealChains(d);
  const [path, qs] = fullPath.split("?");
  const headers = new Headers();
  const ctx: Ctx = {
    db: d, user: d.users.find((u) => u.id === d.sessionUserId) ?? null, workshopId, query: new URLSearchParams(qs ?? ""), headers,
  };
  for (const [m, re, h] of routes) {
    if (m !== method) continue;
    const match = path.match(re);
    if (!match) continue;
    const data = await h(ctx, match.slice(1).map(decodeURIComponent), (body ?? {}) as Record<string, unknown>);
    if (method !== "GET") save();
    return { data: data as T, headers };
  }
  return notFound();
}

export async function demoUpload(file: File): Promise<{ fileId: string }> {
  await sleep(300 + Math.random() * 300);
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) fail(422, "INVALID_IMAGE");
  if (file.size > 8 * 1024 * 1024) fail(422, "FILE_TOO_LARGE");
  const fileId = uuid();
  files.set(fileId, URL.createObjectURL(file));
  return { fileId };
}
