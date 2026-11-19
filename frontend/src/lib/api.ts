// Cliente HTTP de Nítida. Mismo origen (/api/v1, Next reescribe al backend), cookie de sesión,
// CSRF en mutaciones y X-Workshop-Id en las rutas de taller (docs/api.md §Convenciones).
import { ApiError, NETWORK_ERROR } from "./errors";
import type { Problem } from "./types";

type Method = "GET" | "POST" | "PATCH" | "PUT" | "DELETE";

export interface ApiResult<T> {
  data: T;
  headers: Headers;
}

const MODE_KEY = "nitida-mode";
let workshopId: string | null = null;

export function setActiveWorkshop(id: string | null) {
  workshopId = id;
}

export function getActiveWorkshop() {
  return workshopId;
}

/** El modo demostración corre un backend simulado en el navegador. Se puede apagar con NEXT_PUBLIC_NITIDA_DEMO=off. */
export const demoAllowed = process.env.NEXT_PUBLIC_NITIDA_DEMO !== "off";

export function isDemo(): boolean {
  if (!demoAllowed || typeof window === "undefined") return false;
  try {
    return window.localStorage.getItem(MODE_KEY) === "demo";
  } catch {
    return false;
  }
}

export function setDemo(on: boolean) {
  try {
    if (on) window.localStorage.setItem(MODE_KEY, "demo");
    else window.localStorage.removeItem(MODE_KEY);
  } catch {
    /* sin almacenamiento: el modo no se recuerda */
  }
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const m = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return m ? decodeURIComponent(m[1]) : null;
}

export async function request<T>(method: Method, path: string, body?: unknown): Promise<ApiResult<T>> {
  if (isDemo()) {
    const { demoFetch } = await import("./demo/server");
    return demoFetch<T>(method, path, body, workshopId);
  }

  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  const isForm = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isForm) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const xsrf = readCookie("XSRF-TOKEN");
    if (xsrf) headers["X-XSRF-TOKEN"] = xsrf;
  }
  if (workshopId) headers["X-Workshop-Id"] = workshopId;

  let res: Response;
  try {
    res = await fetch(`/api/v1${path}`, {
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ title: "Sin conexión", status: 0, code: NETWORK_ERROR });
  }

  if (!res.ok) {
    let problem: Problem = { title: "Error", status: res.status, code: res.status >= 500 ? "INTERNAL_ERROR" : "UNKNOWN" };
    try {
      const json = await res.json();
      if (json && typeof json.code === "string") problem = { ...json, status: res.status };
    } catch {
      /* respuesta no JSON (proxy caído, HTML): se usa el genérico */
    }
    if (res.status === 401 && problem.code === "UNKNOWN") problem.code = "UNAUTHENTICATED";
    if (res.status === 404 && problem.code === "UNKNOWN") problem.code = "NOT_FOUND";
    throw new ApiError(problem);
  }

  const text = res.status === 204 ? "" : await res.text();
  return { data: (text ? JSON.parse(text) : undefined) as T, headers: res.headers };
}

export const api = {
  get: async <T>(path: string) => (await request<T>("GET", path)).data,
  post: async <T>(path: string, body?: unknown) => (await request<T>("POST", path, body ?? {})).data,
  patch: async <T>(path: string, body: unknown) => (await request<T>("PATCH", path, body)).data,
  del: async <T = void>(path: string) => (await request<T>("DELETE", path)).data,
  /** Sube una foto (JPEG/PNG/WebP, máx. 8 MB); el servidor la re-codifica y quita EXIF. */
  upload: async (file: File) => {
    if (isDemo()) {
      const { demoUpload } = await import("./demo/server");
      return demoUpload(file);
    }
    const form = new FormData();
    form.append("file", file);
    return (await request<{ fileId: string }>("POST", "/files", form)).data;
  },
};
