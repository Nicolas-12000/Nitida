// TOTP (RFC 6238, SHA-1, 6 dígitos, 30 s) en el navegador. Solo lo usa el modo demostración: para verificar
// códigos en el backend simulado y para mostrar el código vigente mientras se prueba sin una app de autenticación.

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function base32Encode(bytes: Uint8Array): string {
  let bits = 0, value = 0, out = "";
  for (const b of bytes) {
    value = (value << 8) | b;
    bits += 8;
    while (bits >= 5) {
      out += ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

export function base32Decode(text: string): Uint8Array {
  const clean = text.replace(/[\s=]/g, "").toUpperCase();
  let bits = 0, value = 0;
  const out: number[] = [];
  for (const c of clean) {
    const i = ALPHABET.indexOf(c);
    if (i < 0) continue;
    value = (value << 5) | i;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(out);
}

export async function totpAt(secret: string, step: number): Promise<string> {
  const key = await crypto.subtle.importKey("raw", base32Decode(secret) as BufferSource, { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const counter = new ArrayBuffer(8);
  new DataView(counter).setBigUint64(0, BigInt(step));
  const mac = new Uint8Array(await crypto.subtle.sign("HMAC", key, counter));
  const offset = mac[mac.length - 1] & 15;
  const bin = ((mac[offset] & 127) << 24) | (mac[offset + 1] << 16) | (mac[offset + 2] << 8) | mac[offset + 3];
  return String(bin % 1_000_000).padStart(6, "0");
}

export const currentStep = () => Math.floor(Date.now() / 30_000);

/** Acepta el paso actual y uno de margen a cada lado, como hace el backend con los relojes desfasados. */
export async function verifyTotp(secret: string, code: string, lastStep: number | null): Promise<number | null> {
  if (!/^\d{6}$/.test(code)) return null;
  const now = currentStep();
  for (const s of [now - 1, now, now + 1]) {
    if (lastStep != null && s <= lastStep) continue;
    if ((await totpAt(secret, s)) === code) return s;
  }
  return null;
}
