// Mismo criterio que Texts.requirePublic del backend (RN-22): los textos que quedan en el historial público,
// firmado e inmutable, no pueden llevar correos, teléfonos ni cédulas. api.md pide avisarlo mientras se escribe.
const EMAIL = /[\w.+-]+@[\w-]+\.[\w.-]+/;
/** 8 o más dígitos, cada uno seguido opcionalmente de un espacio, punto o guion: 310 123 4567, 1.085.123.456. */
const LONG_NUMBER = /(?:\d[\s.-]?){8,}/;

export function personalDataIn(text: string): "email" | "number" | null {
  if (EMAIL.test(text)) return "email";
  if (LONG_NUMBER.test(text)) return "number";
  return null;
}

export function personalDataWarning(text: string): string | null {
  const kind = personalDataIn(text);
  if (!kind) return null;
  return kind === "email"
    ? "Parece un correo. Este texto queda público en el historial: quítelo."
    : "Parece un teléfono o una cédula. Este texto queda público en el historial: quítelo (los datos del cliente van en la orden).";
}
