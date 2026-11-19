// Cadena de hashes como la del backend: cada registro se encadena con el anterior con SHA-256 sobre JSON canónico.
// La canonicalización aquí es una versión simplificada de RFC 8785 (claves ordenadas, sin espacios),
// suficiente para la demostración y para la explicación interactiva de la portada.

export function canonical(value: unknown): string {
  if (value === null || typeof value !== "object") return JSON.stringify(value ?? null);
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  const obj = value as Record<string, unknown>;
  return `{${Object.keys(obj)
    .filter((k) => obj[k] !== undefined)
    .sort()
    .map((k) => `${JSON.stringify(k)}:${canonical(obj[k])}`)
    .join(",")}}`;
}

export async function sha256Hex(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export const GENESIS = "0".repeat(64);

/** hash(n) = SHA-256(hash(n-1) + JCS(contenido n)). */
export async function linkHash(prevHash: string, content: unknown): Promise<string> {
  return sha256Hex(prevHash + canonical(content));
}

export interface ChainCheck {
  hashes: string[];
  /** Índice del primer registro cuyo hash guardado no coincide; -1 si la cadena está íntegra. */
  brokenAt: number;
}

export async function verifyChain(contents: unknown[], stored: string[]): Promise<ChainCheck> {
  const hashes: string[] = [];
  let prev = GENESIS;
  let brokenAt = -1;
  for (let i = 0; i < contents.length; i++) {
    const h = await linkHash(prev, contents[i]);
    hashes.push(h);
    if (brokenAt === -1 && h !== stored[i]) brokenAt = i;
    prev = h;
  }
  return { hashes, brokenAt };
}

export function randomToken(length: number, alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return [...bytes].map((b) => alphabet[b % alphabet.length]).join("");
}

export function uuid(): string {
  return crypto.randomUUID();
}
