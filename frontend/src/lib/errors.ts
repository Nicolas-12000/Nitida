// Traducción de `code` de Problem Details a textos fijos (DESIGN.MD §9.4). Nunca se muestra el detail ni el HTTP.
import { cop, km } from "./format";
import type { Problem } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.title);
    this.status = problem.status;
    this.code = problem.code;
    this.traceId = problem.traceId;
    this.problem = problem;
  }

  /** Primeros 8 caracteres del traceId, solo para 5xx. */
  get supportCode(): string | undefined {
    return this.status >= 500 && this.traceId ? this.traceId.replace(/-/g, "").slice(0, 8) : undefined;
  }
}

export const NETWORK_ERROR = "NETWORK_ERROR";

const MESSAGES: Record<string, string | ((p: Problem) => string)> = {
  VERSION_CONFLICT: "Alguien más cambió esta orden mientras la editaba. Recárguela y aplique sus cambios de nuevo.",
  INVALID_TRANSITION: "Esta orden ya no puede pasar a ese estado.",
  INCOMPLETE_PAYMENTS: (p) =>
    typeof p.pending === "number" ? `Falta registrar ${cop(p.pending)} para poder entregar.` : "Faltan pagos por registrar para poder entregar.",
  INVALID_WARRANTY: "Para entregar sin cobro hay que indicar la garantía que cubre el trabajo.",
  MILEAGE_REGRESSION: (p) =>
    typeof p.lastMileage === "number"
      ? `El kilometraje es menor que el último registrado (${km(p.lastMileage)}). Si cambiaron el tablero, regístrelo como cambio de odómetro.`
      : "El kilometraje es menor que el último registrado. Si cambiaron el tablero, regístrelo como cambio de odómetro.",
  INVALID_PRODUCT_CODE: "Este código no parece válido. Revise los números o escríbalo de nuevo.",
  SERIAL_ALREADY_ACTIVE: "Este serial ya está registrado en otra moto. Puede guardar el repuesto sin serial.",
  WORKSHOP_SUSPENDED: "Su taller está suspendido. Puede consultar la información, pero no hacer cambios.",
  RATE_LIMITED: "Demasiados intentos. Espere unos minutos e intente de nuevo.",
  INVALID_CREDENTIALS: "Correo, contraseña o código incorrectos.",
  UNAUTHENTICATED: "Su sesión terminó. Ingrese de nuevo para continuar.",
  FORBIDDEN: "Su rol no permite hacer esto.",
  NOT_FOUND: "No encontramos lo que buscaba.",
  EMAIL_TAKEN: "Ya existe una cuenta con ese correo. Ingrese o recupere su contraseña.",
  WEAK_PASSWORD: "La contraseña debe tener entre 12 y 128 caracteres y no ser de las más comunes.",
  INVALID_EMAIL: "Revise el correo: no parece válido.",
  INVALID_PLATE: "La placa no tiene un formato válido. Ejemplo: ABC 12D.",
  PLATE_IN_USE: "Esa placa ya pertenece a otra moto.",
  INVALID_VIN: "El VIN debe tener 17 caracteres, sin I, O ni Q.",
  PLATE_VIN_MISMATCH: "La placa y el VIN corresponden a motos distintas.",
  IDENTIFIER_REQUIRED: "Escriba la placa o el VIN de la moto.",
  INVALID_MODEL_YEAR: "Revise el año del modelo.",
  INVALID_DISPLACEMENT: "Revise el cilindraje.",
  INVALID_MILEAGE: "El kilometraje debe estar entre 0 y 999.999.",
  INTAKE_ALREADY_OPEN: "Esta moto ya tiene una recepción abierta.",
  INTAKE_NOT_OPEN: "Esta recepción ya no está abierta.",
  PHOTO_REQUIRED: "Faltan las fotos de la moto y del tablero.",
  INVALID_PHOTO: "Una de las fotos no es válida. Tómela de nuevo.",
  CONSENT_REQUIRED: "Sin la autorización de datos del cliente no se puede guardar.",
  STALE_POLICY: "La política de datos cambió. Muestre la versión actual al cliente.",
  INVALID_PHONE: "Revise el teléfono: debe ser un celular colombiano de 10 dígitos.",
  INVALID_NATIONAL_ID: "Revise el número de documento.",
  QUERY_TOO_SHORT: "Escriba al menos 2 caracteres para buscar.",
  ORDER_NOT_EDITABLE: "Esta orden ya no se puede editar.",
  JOBS_REQUIRED: "Agregue al menos un trabajo antes de completar la orden.",
  INVALID_JOB: "Revise los trabajos de la orden.",
  CUSTOMER_REQUIRED: "Asocie un cliente a la orden antes de continuar.",
  DETAILS_REQUIRED: "Escriba el diagnóstico (mínimo 10 caracteres).",
  INVALID_DIAGNOSIS: "El diagnóstico debe tener entre 10 y 2.000 caracteres.",
  PERSONAL_DATA: "Este texto queda público en el historial y no puede llevar correos, teléfonos ni cédulas. Quítelos: los datos del cliente van en la orden.",
  TOTP_REQUIRED: "Escriba el código de 6 dígitos de su aplicación de autenticación.",
  INVALID_TOTP: "Ese código no coincide. Revise que la hora del celular esté al día y escriba el código actual.",
  TOTP_ALREADY_ENABLED: "La verificación en dos pasos ya está activa en su cuenta.",
  TOTP_NOT_STARTED: "Primero genere el código QR y luego escriba el código de la aplicación.",
  INVALID_PART: "Revise los datos del repuesto.",
  INVALID_QUANTITY: "La cantidad debe estar entre 1 y 999.",
  INVALID_AMOUNT: "El valor debe estar entre $ 0 y $ 50.000.000.",
  INVALID_SERIAL: "El serial no tiene un formato válido.",
  SERIAL_NOT_ACTIVE: "Ese serial no está activo.",
  INVALID_METHOD: "Elija un medio de pago.",
  OVERPAYMENT: "El pago supera el saldo pendiente de la orden.",
  PAYMENT_ALREADY_VOIDED: "Este pago ya estaba anulado.",
  FILE_REQUIRED: "Adjunte la foto.",
  FILE_TOO_LARGE: "La foto pesa más de 8 MB. Tómela con menos resolución.",
  INVALID_IMAGE: "El archivo debe ser una foto JPEG, PNG o WebP.",
  REASON_REQUIRED: "Escriba el motivo.",
  INVALID_REASON: "El motivo es muy corto o muy largo.",
  COMMENT_REQUIRED: "Escriba un comentario de al menos 5 caracteres.",
  NOTHING_TO_AMEND: "Indique qué dato quiere corregir.",
  CANNOT_AMEND_AMENDMENT: "Una corrección no se puede corregir. Corrija el registro original.",
  INVALID_EXPIRATION: "La vigencia debe estar entre 1 y 365 días.",
  LINK_NOT_AVAILABLE: "Este enlace no está disponible. Puede haber vencido o haber sido revocado.",
  COUNTED_REQUIRED: "Escriba cuánto efectivo contó.",
  FUTURE_DAY: "No se puede cerrar un día que aún no termina.",
  INVALID_DAY: "Revise la fecha.",
  CASH_COUNT_CLOSED: "Este día ya está cerrado.",
  CASH_COUNT_NOT_CLOSED: "Este día aún no está cerrado.",
  ALERT_ALREADY_RESOLVED: "Esta alerta ya estaba resuelta.",
  ALREADY_OWNED: "Esta moto ya está en su cuenta.",
  SAME_OWNER: "Usted ya es el propietario de esta moto.",
  LAST_OWNER: "No puede retirar al único dueño del taller.",
  INVALID_ROLE: "Elija un rol.",
  INVALID_SETTING: "Revise los valores: alguno está fuera del rango permitido.",
  INVALID_DOCUMENT: "Revise el NIT o documento del taller.",
  INVALID_STATUS: "Ese estado no es válido.",
  INVALID_LIMIT: "No pudimos cargar la lista. Recargue la página.",
  INVALID_CURSOR: "No pudimos cargar más resultados. Recargue la lista.",
  [NETWORK_ERROR]: "No hay conexión con Nítida. Revise la señal e intente de nuevo.",
};

const GENERIC = "No pudimos completar la acción. Lo que escribió sigue aquí.";

export function messageFor(err: unknown): string {
  if (err instanceof ApiError) {
    const m = MESSAGES[err.code];
    if (typeof m === "function") return m(err.problem);
    if (m) return m;
    if (err.status === 404) return MESSAGES.NOT_FOUND as string;
    return GENERIC;
  }
  return GENERIC;
}

export function isCode(err: unknown, ...codes: string[]): boolean {
  return err instanceof ApiError && codes.includes(err.code);
}
