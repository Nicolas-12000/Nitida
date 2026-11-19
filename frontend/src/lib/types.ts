// Formas de las respuestas del backend (docs/api.md y los records de co.nitida.*.web / application).

export type Role = "WORKSHOP_OWNER" | "MECHANIC" | "FRONT_DESK";
export type WorkshopStatus = "PENDING_VERIFICATION" | "VERIFIED" | "SUSPENDED";
export type OrderStatus = "OPEN" | "IN_PROGRESS" | "COMPLETED" | "DELIVERED" | "CANCELLED";
export type EvidenceLevel = "MANUAL" | "SCANNED" | "UNIQUE_SERIAL" | "AUTHENTICITY_VERIFIED";
export type PartCondition = "OEM" | "AFTERMARKET" | "USED";
export type PaymentMethod = "CASH" | "TRANSFER" | "CARD" | "STORE_CREDIT";
export type JobType =
  | "OIL_CHANGE" | "DRIVE_CHAIN_KIT" | "BRAKES" | "SUSPENSION" | "ELECTRICAL" | "ENGINE" | "TIRES" | "TUNE_UP" | "OTHER";
export type VerificationStatus = "VALID" | "INVALID" | "UNVERIFIABLE";
export type EntryType = "INITIAL_REGISTRATION" | "SERVICE" | "AMENDMENT" | "ODOMETER_CHANGE" | "OWNERSHIP_TRANSFER";
export type AlertType =
  | "F1_INTAKE_WITHOUT_ORDER" | "F2_CANCELLED_WITH_PARTS" | "F3_CASH_MISMATCH"
  | "F4_ZERO_TOTAL_WITHOUT_WARRANTY" | "F5_STALLED_ORDER" | "F6_DUPLICATE_SERIAL";
export type CashCountStatus = "OPEN" | "BALANCED" | "UNBALANCED";
export type DiscardReason = "CONSULTATION_ONLY" | "CUSTOMER_WITHDREW" | "ENTRY_ERROR";

export interface Me {
  id: string;
  email: string;
  displayName: string;
  platformAdmin: boolean;
  totpEnabled: boolean;
  /** Administrador sin TOTP: inicia sesión, pero sin privilegios de administrador hasta activarlo. */
  totpSetupRequired: boolean;
  memberships: { workshopId: string; role: Role }[];
}

export interface MyWorkshop {
  id: string;
  name: string;
  municipality: string;
  status: WorkshopStatus;
  roles: Role[];
}

export interface WorkshopInfo {
  id: string;
  name: string;
  municipality: string;
  status: WorkshopStatus;
  verifiedAt: string | null;
  suspendedAt: string | null;
  intakeAlertHours: number;
  cashTolerance: number;
  stalledDays: number;
  /** api.md lo promete; el record WorkshopInfo del backend aún no lo expone. */
  version?: number;
}

export interface AdminWorkshop {
  id: string;
  name: string;
  municipality: string;
  documentNumber: string;
  phone: string;
  status: WorkshopStatus;
}

export interface Member {
  userId: string;
  email: string;
  displayName: string;
  role: Role;
  active: boolean;
}

export interface Intake {
  id: string;
  motorcycleId: string;
  plate: string | null;
  brand: string | null;
  line: string | null;
  mileage: number;
  createdAt: string;
}

export interface Customer {
  id: string;
  fullName: string;
  phone: string;
  nationalId: string | null;
  email: string | null;
}

export interface MotorcycleInfo {
  id: string;
  vin: string | null;
  plate: string | null;
  maskedPlate: string | null;
  legacyPlateFormat: boolean;
  brand: string | null;
  line: string | null;
  modelYear: number | null;
  displacementCc: number | null;
}

export interface OrderSummary {
  id: string;
  motorcycleId: string;
  status: OrderStatus;
  diagnosis: string;
  total: number;
  updatedAt: string;
}

export interface Job { type: JobType; description: string }

export interface Part {
  id: string;
  name: string;
  reference: string | null;
  brand: string;
  condition: PartCondition;
  quantity: number;
  unitPrice: number;
  productCode: string | null;
  hasSerial: boolean;
  evidenceLevel: EvidenceLevel;
  authenticityMethod: string | null;
}

export interface Payment {
  id: string;
  method: PaymentMethod;
  amount: number;
  recordedAt: string;
  voided: boolean;
}

export interface OrderView {
  id: string;
  intakeId: string;
  motorcycleId: string;
  customerId: string | null;
  status: OrderStatus;
  diagnosis: string;
  mileage: number;
  laborCost: number;
  total: number;
  warrantyDays: number | null;
  warrantyKm: number | null;
  warranty: boolean;
  mechanicId: string | null;
  deliveredAt: string | null;
  version: number;
  jobs: Job[];
  parts: Part[];
  /** Vacío para mecánicos: el dinero es de caja (spec §3.1). */
  payments: Payment[];
  paid: number | null;
}

export interface DeliveryResult {
  orderId: string;
  total: number;
  claimCode: string;
  claimCodeExpiresAt: string;
}

export interface SerialResult {
  accepted: boolean;
  evidenceLevel: EvidenceLevel;
  message: string;
}

export interface Verification {
  status: VerificationStatus;
  seq: number | null;
  reason: string | null;
}

export interface HistoryEntry {
  seq: number;
  type: EntryType;
  recordedAt: string;
  workshopName: string | null;
  municipality: string | null;
  workshopVerified: boolean;
  workshopSuspendedAt: string | null;
  content: Record<string, unknown>;
}

export interface HistoryView {
  motorcycle: {
    plate: string | null;
    brand: string | null;
    line: string | null;
    modelYear: number | null;
    displacementCc: number | null;
  };
  entries: HistoryEntry[];
  verification: Verification;
  evidence: { partsTotal: number; partsWithEvidence: number };
  transfers: number;
}

export interface PublicLink {
  linkId: string;
  token: string;
  expiresAt: string;
}

export interface CashCount {
  day: string;
  expected: number;
  counted: number | null;
  difference: number | null;
  status: CashCountStatus;
  closedAt: string | null;
  reopenReason: string | null;
}

export interface LeakAlert {
  id: string;
  type: AlertType;
  referenceId: string | null;
  details: string | null;
  createdAt: string;
  resolvedAt: string | null;
  comment: string | null;
}

export interface OwnedMotorcycle {
  motorcycleId: string;
  plate: string | null;
  brand: string | null;
  line: string | null;
  modelYear: number | null;
  active: boolean;
  since: string;
  until: string | null;
}

export interface Problem {
  type?: string;
  title: string;
  status: number;
  code: string;
  traceId?: string;
  [k: string]: unknown;
}

export interface TotpSetup { secret: string; otpauthUri: string }
export interface InvitationSent { invitationId: string; expiresAt: string }
