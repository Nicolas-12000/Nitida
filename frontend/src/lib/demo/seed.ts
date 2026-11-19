// Datos de demostración: un taller de Pasto con motos, órdenes, pagos y alertas plausibles (DESIGN.MD §11.2).
import { randomToken } from "../chain";
import { date as fmtDate } from "../format";
import type { AlertType, EntryType, JobType, OrderStatus, PartCondition, EvidenceLevel, PaymentMethod, Role, WorkshopStatus } from "../types";

export interface DUser {
  id: string; email: string; password: string; displayName: string; platformAdmin: boolean;
  totpEnabled?: boolean; totpSecret?: string; totpPending?: string; totpLastStep?: number; recoveryCodes?: string[];
}
export interface DMembership { userId: string; workshopId: string; role: Role; active: boolean }
export interface DWorkshop {
  id: string; name: string; documentNumber: string; address: string; municipality: string; phone: string;
  status: WorkshopStatus; verifiedAt: string | null; suspendedAt: string | null;
  intakeAlertHours: number; cashTolerance: number; stalledDays: number; version: number;
}
export interface DMoto {
  id: string; plate: string | null; vin: string | null; brand: string | null; line: string | null;
  modelYear: number | null; displacementCc: number | null; seenBy: string[];
}
export interface DIntake { id: string; workshopId: string; motorcycleId: string; mileage: number; status: "OPEN" | "CONVERTED" | "DISCARDED"; createdAt: string }
export interface DCustomer { id: string; workshopId: string; fullName: string; phone: string; nationalId: string | null; email: string | null }
export interface DPart {
  id: string; name: string; reference: string | null; brand: string; condition: PartCondition; quantity: number; unitPrice: number;
  productCode: string | null; serial: string | null; evidenceLevel: EvidenceLevel; authenticityMethod: string | null;
}
export interface DOrder {
  id: string; workshopId: string; intakeId: string; motorcycleId: string; customerId: string | null; status: OrderStatus;
  diagnosis: string; mileage: number; laborCost: number; warrantyDays: number | null; warrantyKm: number | null;
  warrantyOf: string | null; mechanicId: string | null; deliveredAt: string | null; version: number;
  jobs: { type: JobType; description: string }[]; parts: DPart[]; createdAt: string; updatedAt: string;
}
export interface DPayment { id: string; orderId: string; workshopId: string; method: PaymentMethod; amount: number; recordedAt: string; voided: boolean }
export interface DEntry { motorcycleId: string; seq: number; type: EntryType; recordedAt: string; workshopId: string | null; content: Record<string, unknown>; hash: string | null }
export interface DLink { id: string; token: string; motorcycleId: string; expiresAt: string; revoked: boolean }
export interface DCash { workshopId: string; day: string; expected: number; counted: number; status: "BALANCED" | "UNBALANCED"; closedAt: string; reopenReason: string | null }
export interface DAlert { id: string; workshopId: string; type: AlertType; referenceId: string | null; details: string; createdAt: string; resolvedAt: string | null; comment: string | null }
export interface DOwnership { motorcycleId: string; userId: string; since: string; until: string | null }
export interface DCode { code: string; motorcycleId: string; expiresAt: string; used: boolean; fromUserId?: string }

export interface DB {
  v: number;
  users: DUser[]; memberships: DMembership[]; workshops: DWorkshop[];
  invitations: { token: string; workshopId: string; email: string; role: Role; accepted: boolean; expiresAt?: string }[];
  resets: { token: string; userId: string; expiresAt: string; used: boolean }[];
  motos: DMoto[]; intakes: DIntake[]; customers: DCustomer[]; orders: DOrder[]; payments: DPayment[];
  entries: DEntry[]; links: DLink[]; cash: DCash[]; alerts: DAlert[]; ownership: DOwnership[];
  claimCodes: DCode[]; transfers: DCode[]; serials: { serial: string; motorcycleId: string; active: boolean }[];
  plateHistory: { motorcycleId: string; plate: string; until: string }[];
  sessionUserId: string | null;
  seq: number;
}

export const DEMO_PASSWORD = "demo-nitida-2026";
export { POLICY_VERSION } from "../constants";

export { DEMO_ACCOUNTS } from "./accounts";
import { DEMO_ACCOUNTS } from "./accounts";

const DAY = 86_400_000;
const HOUR = 3_600_000;
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();

export function bogotaDay(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(iso));
}

/** Dígito verificador EAN-13. */
export function eanCheck(d12: string): string {
  const sum = [...d12].reduce((s, c, i) => s + Number(c) * (i % 2 ? 3 : 1), 0);
  return d12 + ((10 - (sum % 10)) % 10);
}

export const CATALOG: Record<string, { name: string; brand: string; reference: string | null }> = {
  [eanCheck("770200501018")]: { name: "Aceite 20W-50 4T 1 L", brand: "Motul", reference: "5100-4T" },
  [eanCheck("770412300456")]: { name: "Kit de arrastre 428H 43T/14T", brand: "Riffel", reference: "KA-428-FZ" },
  [eanCheck("890123400078")]: { name: "Pastillas de freno delanteras", brand: "Vesrah", reference: "FD-410" },
  [eanCheck("770998800031")]: { name: "Bujía CR8E", brand: "NGK", reference: "CR8E" },
  [eanCheck("490011200345")]: { name: "Filtro de aceite", brand: "Yamaha", reference: "5YP-E3440-00" },
};

export const W1 = "0f6f3a2e-8f6a-4c1e-9d1b-1a2b3c4d5e01";
const W2 = "0f6f3a2e-8f6a-4c1e-9d1b-1a2b3c4d5e02";
const W3 = "0f6f3a2e-8f6a-4c1e-9d1b-1a2b3c4d5e03";

let n = 0;
const id = (p: string) => `${p}-${(++n).toString(16).padStart(4, "0")}-4e2a-9c3b-${Math.random().toString(16).slice(2, 14).padEnd(12, "0")}`;

function salt() {
  return randomToken(22, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");
}

export function createSeed(): DB {
  n = 0;
  const db: DB = {
    v: 5, users: [], memberships: [], workshops: [], invitations: [], resets: [], motos: [], intakes: [], customers: [], orders: [],
    payments: [], entries: [], links: [], cash: [], alerts: [], ownership: [], claimCodes: [], transfers: [], serials: [],
    plateHistory: [], sessionUserId: null, seq: 1040,
  };

  for (const a of DEMO_ACCOUNTS) {
    db.users.push({ id: a.id, email: a.email, password: DEMO_PASSWORD, displayName: a.name, platformAdmin: a.id === "u-admin" });
  }
  db.users.push({ id: "u-w2", email: "hernan.guerrero@avenida.test", password: DEMO_PASSWORD, displayName: "Hernán Guerrero", platformAdmin: false });
  db.users.push({ id: "u-w3", email: "lucia.cortes@tumaco.test", password: DEMO_PASSWORD, displayName: "Lucía Cortés", platformAdmin: false });

  db.workshops.push(
    { id: W1, name: "Motos El Ejido", documentNumber: "900.481.227-3", address: "Cra. 27 #18-42, barrio El Ejido", municipality: "Pasto", phone: "3168402215",
      status: "VERIFIED", verifiedAt: ago(120 * DAY), suspendedAt: null, intakeAlertHours: 24, cashTolerance: 2000, stalledDays: 5, version: 3 },
    { id: W2, name: "Taller La Avenida", documentNumber: "1.085.297.334", address: "Av. Panamericana #4-15", municipality: "Ipiales", phone: "3007721904",
      status: "PENDING_VERIFICATION", verifiedAt: null, suspendedAt: null, intakeAlertHours: 24, cashTolerance: 0, stalledDays: 7, version: 0 },
    { id: W3, name: "Moto Servicio Tumaco", documentNumber: "901.233.118-0", address: "Calle del Comercio #9-61", municipality: "Tumaco", phone: "3124409876",
      status: "PENDING_VERIFICATION", verifiedAt: null, suspendedAt: null, intakeAlertHours: 24, cashTolerance: 0, stalledDays: 7, version: 0 },
  );
  db.memberships.push(
    { userId: "u-owner", workshopId: W1, role: "WORKSHOP_OWNER", active: true },
    { userId: "u-desk", workshopId: W1, role: "FRONT_DESK", active: true },
    { userId: "u-mech", workshopId: W1, role: "MECHANIC", active: true },
    { userId: "u-w2", workshopId: W2, role: "WORKSHOP_OWNER", active: true },
    { userId: "u-w3", workshopId: W3, role: "WORKSHOP_OWNER", active: true },
  );

  const customers = [
    ["Luis Fernando Getial", "3104567821", "1085264731"],
    ["María Camila Rosero", "3157788123", "1004587211"],
    ["Óscar Mauricio Bastidas", "3012345678", "98765432"],
    ["Yolanda Chamorro", "3209988776", "27089123"],
    ["Diego Armando Ortega", "3176655443", "1085301122"],
    ["Diana Muñoz", "3145566778", "1085999001"],
  ].map(([fullName, phone, nationalId]) => {
    const c: DCustomer = { id: id("c0c0"), workshopId: W1, fullName, phone, nationalId, email: null };
    db.customers.push(c);
    return c;
  });

  const moto = (plate: string, brand: string, line: string, modelYear: number, cc: number): DMoto => {
    const m: DMoto = { id: id("a1b2"), plate, vin: null, brand, line, modelYear, displacementCc: cc, seenBy: [W1] };
    db.motos.push(m);
    return m;
  };
  const M1 = moto("KZT41F", "Yamaha", "FZ 2.0 150", 2021, 149);
  const M2 = moto("HQN27E", "Honda", "CB 160F", 2023, 162);
  const M3 = moto("BVR88D", "Suzuki", "Gixxer 150", 2020, 155);
  const M4 = moto("PLM53C", "Bajaj", "Pulsar NS 200", 2019, 199);
  const M5 = moto("TKD19G", "AKT", "NKD 125", 2022, 124);
  const M6 = moto("RXA62B", "TVS", "Apache RTR 160", 2021, 159);
  const M7 = moto("JCE45F", "Yamaha", "XTZ 150", 2024, 149);
  const M8 = moto("MNO31A", "Honda", "XR 150L", 2022, 149);
  const M9 = moto("SDF74H", "Suzuki", "DR 150", 2023, 149);

  const entry = (m: DMoto, type: EntryType, at: string, extra: Record<string, unknown>) => {
    const seq = db.entries.filter((e) => e.motorcycleId === m.id).length + 1;
    db.entries.push({
      motorcycleId: m.id, seq, type, recordedAt: at, workshopId: W1, hash: null,
      content: { v: 1, motorcycleId: m.id, seq, type, workshopId: W1, workshopVerified: true, occurredAt: at, privateHash: salt(), ...extra },
    });
  };
  const initial = (m: DMoto, at: string, mileage: number) => entry(m, "INITIAL_REGISTRATION", at, { mileage, mileageDeclared: true });

  // Historial largo de M1 (la moto de Diana) y de M7.
  initial(M1, ago(240 * DAY), 12_300);
  entry(M1, "SERVICE", ago(180 * DAY), {
    mileage: 15_120, diagnosis: "Cambio de aceite y kit de arrastre por desgaste; cadena estirada.",
    jobs: [{ type: "OIL_CHANGE", description: "Aceite 20W-50 y filtro" }, { type: "DRIVE_CHAIN_KIT", description: "Kit 428H 43T/14T" }],
    parts: [
      { name: "Aceite 20W-50 4T 1 L", brand: "Motul", condition: "OEM", quantity: 1, productCode: eanCheck("770200501018"), evidenceLevel: "SCANNED", serialHash: null, hasPhotoEvidence: false },
      { name: "Kit de arrastre 428H", brand: "Riffel", condition: "AFTERMARKET", quantity: 1, productCode: eanCheck("770412300456"), evidenceLevel: "SCANNED", serialHash: null, hasPhotoEvidence: true },
    ],
    warranty: { days: 30, km: 1000 }, amends: null,
  });
  entry(M1, "SERVICE", ago(62 * DAY), {
    mileage: 18_420, diagnosis: "Frenos delanteros cristalizados; se cambian pastillas y se purga el líquido.",
    jobs: [{ type: "BRAKES", description: "Pastillas delanteras y purga" }],
    parts: [{ name: "Pastillas de freno delanteras", brand: "Vesrah", condition: "AFTERMARKET", quantity: 1, productCode: eanCheck("890123400078"), evidenceLevel: "AUTHENTICITY_VERIFIED", serialHash: null, hasPhotoEvidence: true }],
    warranty: { days: 15, km: null }, amends: null,
  });
  entry(M1, "AMENDMENT", ago(61 * DAY), { amends: { seq: 3 }, reason: "El kilometraje se digitó con un cero de más en la orden.", corrected: { mileage: 18_420 } });
  db.ownership.push({ motorcycleId: M1.id, userId: "u-rider", since: ago(180 * DAY), until: null });

  initial(M7, ago(9 * DAY), 2_150);
  for (const m of [M2, M3, M5, M6, M8]) initial(m, ago(12 * DAY + Math.random() * 6 * DAY), 0);
  // Kilometrajes iniciales coherentes con las recepciones.
  const firstKm: Record<string, number> = { [M2.id]: 8_640, [M3.id]: 31_210, [M5.id]: 4_980, [M6.id]: 22_475, [M8.id]: 15_030 };
  for (const e of db.entries) if (firstKm[e.motorcycleId] != null && e.seq === 1) e.content.mileage = firstKm[e.motorcycleId];

  const intake = (m: DMoto, mileage: number, at: string, status: DIntake["status"]) => {
    const i: DIntake = { id: id("1a7e"), workshopId: W1, motorcycleId: m.id, mileage, status, createdAt: at };
    db.intakes.push(i);
    return i;
  };
  const part = (name: string, brand: string, condition: PartCondition, quantity: number, unitPrice: number, evidenceLevel: EvidenceLevel = "MANUAL", productCode: string | null = null): DPart => ({
    id: id("9a7c"), name, reference: null, brand, condition, quantity, unitPrice, productCode, serial: null, evidenceLevel, authenticityMethod: null,
  });
  const order = (o: Partial<DOrder> & Pick<DOrder, "motorcycleId" | "intakeId" | "status" | "diagnosis" | "mileage">): DOrder => {
    const full: DOrder = {
      id: id("0dd0"), workshopId: W1, customerId: null, laborCost: 0, warrantyDays: null, warrantyKm: null, warrantyOf: null,
      mechanicId: "u-mech", deliveredAt: null, version: 1, jobs: [], parts: [], createdAt: ago(3 * DAY), updatedAt: ago(2 * HOUR), ...o,
    };
    db.orders.push(full);
    return full;
  };

  // M2: en proceso
  const i2 = intake(M2, 9_870, ago(26 * HOUR), "CONVERTED");
  order({ motorcycleId: M2.id, intakeId: i2.id, customerId: customers[1].id, status: "IN_PROGRESS", mileage: 9_870, laborCost: 60_000,
    diagnosis: "Ruido en la transmisión al acelerar; revisar kit de arrastre y tensión de cadena.",
    jobs: [{ type: "DRIVE_CHAIN_KIT", description: "Cambio de kit de arrastre" }],
    parts: [part("Kit de arrastre 428H 43T/14T", "Riffel", "AFTERMARKET", 1, 138_000, "SCANNED", eanCheck("770412300456"))],
    createdAt: ago(25 * HOUR), updatedAt: ago(3 * HOUR) });

  // M3: lista para entregar, con pago parcial
  const i3 = intake(M3, 33_480, ago(2 * DAY), "CONVERTED");
  const o3 = order({ motorcycleId: M3.id, intakeId: i3.id, customerId: customers[2].id, status: "COMPLETED", mileage: 33_480, laborCost: 85_000,
    warrantyDays: 30, warrantyKm: 1_500,
    diagnosis: "Mantenimiento general de 33.000 km: aceite, bujía, filtro y ajuste de frenos.",
    jobs: [{ type: "OIL_CHANGE", description: "Aceite 20W-50 y filtro" }, { type: "TUNE_UP", description: "Bujía y sincronización" }],
    parts: [
      part("Aceite 20W-50 4T 1 L", "Motul", "OEM", 1, 42_000, "SCANNED", eanCheck("770200501018")),
      part("Bujía CR8E", "NGK", "OEM", 1, 18_000, "SCANNED", eanCheck("770998800031")),
      part("Filtro de aceite", "Suzuki", "OEM", 1, 25_000),
    ],
    createdAt: ago(2 * DAY), updatedAt: ago(40 * 60_000) });
  db.payments.push({ id: id("4a11"), orderId: o3.id, workshopId: W1, method: "CASH", amount: 100_000, recordedAt: ago(30 * 60_000), voided: false });

  // M4: recepción sin orden hace 2 días (F1)
  const i4 = intake(M4, 41_200, ago(50 * HOUR), "OPEN");
  initial(M4, ago(50 * HOUR), 41_200);

  // M5: abierta
  const i5 = intake(M5, 5_310, ago(5 * HOUR), "CONVERTED");
  order({ motorcycleId: M5.id, intakeId: i5.id, customerId: customers[3].id, status: "OPEN", mileage: 5_310,
    diagnosis: "Luz de freno trasera no enciende; revisar interruptor y bombillo.", createdAt: ago(4 * HOUR), updatedAt: ago(4 * HOUR) });

  // M6: en proceso pero quieta 6 días (F5)
  const i6 = intake(M6, 24_010, ago(8 * DAY), "CONVERTED");
  const o6 = order({ motorcycleId: M6.id, intakeId: i6.id, customerId: customers[4].id, status: "IN_PROGRESS", mileage: 24_010, laborCost: 180_000,
    diagnosis: "Pérdida de compresión; se desarma culata para revisar válvulas y empaques.",
    jobs: [{ type: "ENGINE", description: "Reparación de culata" }],
    parts: [part("Juego de empaques de culata", "Vesrah", "AFTERMARKET", 1, 64_000)],
    createdAt: ago(8 * DAY), updatedAt: ago(6 * DAY) });

  // M7: entregada la semana pasada
  const i7 = intake(M7, 3_020, ago(8 * DAY), "CONVERTED");
  const o7 = order({ motorcycleId: M7.id, intakeId: i7.id, customerId: customers[0].id, status: "DELIVERED", mileage: 3_020, laborCost: 35_000,
    warrantyDays: 30, warrantyKm: 1_000, deliveredAt: ago(7 * DAY),
    diagnosis: "Primer mantenimiento de garantía de fábrica: aceite y ajuste general.",
    jobs: [{ type: "OIL_CHANGE", description: "Primer cambio de aceite" }],
    parts: [part("Aceite 20W-50 4T 1 L", "Motul", "OEM", 1, 42_000, "SCANNED", eanCheck("770200501018"))],
    createdAt: ago(8 * DAY), updatedAt: ago(7 * DAY) });
  db.payments.push({ id: id("4a11"), orderId: o7.id, workshopId: W1, method: "TRANSFER", amount: 77_000, recordedAt: ago(7 * DAY), voided: false });
  entry(M7, "SERVICE", ago(7 * DAY), {
    mileage: 3_020, diagnosis: o7.diagnosis, jobs: o7.jobs,
    parts: [{ name: "Aceite 20W-50 4T 1 L", brand: "Motul", condition: "OEM", quantity: 1, productCode: eanCheck("770200501018"), evidenceLevel: "SCANNED", serialHash: null, hasPhotoEvidence: false }],
    warranty: { days: 30, km: 1000 }, amends: null,
  });

  // M8: cancelada con repuestos (F2, resuelta)
  const i8 = intake(M8, 15_800, ago(5 * DAY), "CONVERTED");
  const o8 = order({ motorcycleId: M8.id, intakeId: i8.id, customerId: null, status: "CANCELLED", mileage: 15_800,
    diagnosis: "Cambio de amortiguador trasero; el cliente decidió no hacerlo por precio.",
    jobs: [{ type: "SUSPENSION", description: "Amortiguador trasero" }],
    parts: [part("Amortiguador trasero", "YSS", "AFTERMARKET", 1, 210_000)], createdAt: ago(5 * DAY), updatedAt: ago(4 * DAY) });

  // M9: recepción de hoy, aún sin orden
  intake(M9, 11_640, ago(40 * 60_000), "OPEN");
  initial(M9, ago(40 * 60_000), 11_640);

  // Otros pagos en efectivo de hoy y de días anteriores para la caja.
  const extra = order({ motorcycleId: M5.id, intakeId: i5.id, status: "DELIVERED", mileage: 4_980, laborCost: 25_000, diagnosis: "Ajuste de clutch y engrase de guayas.",
    jobs: [{ type: "OTHER", description: "Ajuste de clutch" }], deliveredAt: ago(11 * DAY), createdAt: ago(11 * DAY), updatedAt: ago(11 * DAY) });
  db.payments.push(
    { id: id("4a11"), orderId: extra.id, workshopId: W1, method: "CASH", amount: 25_000, recordedAt: ago(26 * HOUR), voided: false },
    { id: id("4a11"), orderId: extra.id, workshopId: W1, method: "CASH", amount: 48_000, recordedAt: ago(50 * HOUR), voided: false },
  );

  const today = bogotaDay(new Date().toISOString());
  void today;
  db.cash.push(
    { workshopId: W1, day: bogotaDay(ago(DAY)), expected: 25_000, counted: 25_000, status: "BALANCED", closedAt: ago(DAY - 3 * HOUR), reopenReason: null },
    { workshopId: W1, day: bogotaDay(ago(2 * DAY)), expected: 48_000, counted: 36_000, status: "UNBALANCED", closedAt: ago(2 * DAY - 3 * HOUR), reopenReason: null },
  );

  const alert = (type: AlertType, referenceId: string | null, details: string, createdAt: string, resolved?: { at: string; comment: string }) =>
    db.alerts.push({ id: id("a1e7"), workshopId: W1, type, referenceId, details, createdAt, resolvedAt: resolved?.at ?? null, comment: resolved?.comment ?? null });
  alert("F1_INTAKE_WITHOUT_ORDER", i4.id, `La moto ${M4.plate} entró hace 2 días y no tiene orden de trabajo.`, ago(26 * HOUR));
  alert("F5_STALLED_ORDER", o6.id, "La orden lleva 6 días sin movimiento.", ago(HOUR));
  alert("F3_CASH_MISMATCH", null, `El cuadre del ${fmtDate(bogotaDay(ago(2 * DAY)))} tiene una diferencia de $ 12.000.`, ago(2 * DAY - 3 * HOUR));
  alert("F2_CANCELLED_WITH_PARTS", o8.id, "La orden se canceló después de registrar repuestos.", ago(4 * DAY),
    { at: ago(3 * DAY), comment: "El amortiguador volvió a la bodega; el proveedor aceptó la devolución." });

  return db;
}
