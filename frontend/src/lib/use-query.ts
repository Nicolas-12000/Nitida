"use client";
// Caché mínima en el navegador: deduplica por clave, revalida al invalidar y avisa a todos los suscriptores.
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

interface Entry<T> {
  data?: T;
  error?: unknown;
  promise?: Promise<T>;
  at: number;
}

const cache = new Map<string, Entry<unknown>>();
const listeners = new Map<string, Set<() => void>>();

function notify(key: string) {
  listeners.get(key)?.forEach((l) => l());
}

function subscribe(key: string, cb: () => void) {
  let set = listeners.get(key);
  if (!set) listeners.set(key, (set = new Set()));
  set.add(cb);
  return () => set!.delete(cb);
}

async function load<T>(key: string, fn: () => Promise<T>): Promise<T> {
  const current = cache.get(key) as Entry<T> | undefined;
  if (current?.promise) return current.promise;
  const promise = fn();
  cache.set(key, { ...current, promise, at: current?.at ?? 0 });
  try {
    const data = await promise;
    cache.set(key, { data, at: Date.now() });
    return data;
  } catch (error) {
    cache.set(key, { ...(cache.get(key) as Entry<T>), error, promise: undefined, at: Date.now() });
    throw error;
  } finally {
    notify(key);
  }
}

/** Invalida todas las claves que empiezan por alguno de los prefijos y las vuelve a pedir si hay quien mire. */
export function invalidate(...prefixes: string[]) {
  for (const key of [...cache.keys()]) {
    if (prefixes.some((p) => key.startsWith(p))) {
      const e = cache.get(key)!;
      cache.set(key, { ...e, at: 0 });
      notify(key);
    }
  }
}

export function clearCache() {
  cache.clear();
  for (const key of listeners.keys()) notify(key);
}

export function setQueryData<T>(key: string, updater: (old: T | undefined) => T) {
  const e = cache.get(key) as Entry<T> | undefined;
  cache.set(key, { ...e, data: updater(e?.data), at: Date.now() });
  notify(key);
}

export function useQuery<T>(key: string | null, fn: () => Promise<T>) {
  const fnRef = useRef(fn);
  useEffect(() => {
    fnRef.current = fn;
  });
  const version = useSyncExternalStore(
    useCallback((cb) => (key ? subscribe(key, cb) : () => {}), [key]),
    () => (key ? cache.get(key) : undefined),
    () => undefined,
  );
  const entry = version as Entry<T> | undefined;

  useEffect(() => {
    if (!key) return;
    const e = cache.get(key);
    if (!e || (e.at === 0 && !e.promise)) load(key, () => fnRef.current()).catch(() => {});
  }, [key, entry?.at]);

  const refetch = useCallback(() => (key ? load(key, () => fnRef.current()) : Promise.resolve(undefined)), [key]);

  return {
    data: entry?.data,
    error: entry?.data === undefined ? entry?.error : undefined,
    loading: entry?.data === undefined && !entry?.error,
    refetch,
  };
}

/** El skeleton solo aparece si la carga pasa de 300 ms (DESIGN.MD §7.1). Pasados 8 s, avisa. */
export function useDelayedFlag(active: boolean) {
  const [show, setShow] = useState(false);
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    if (!active) return;
    const a = setTimeout(() => setShow(true), 300);
    const b = setTimeout(() => setSlow(true), 8000);
    return () => {
      clearTimeout(a);
      clearTimeout(b);
      setShow(false);
      setSlow(false);
    };
  }, [active]);
  return { show: active && show, slow: active && slow };
}

const MINUTE = 60_000;
const subscribeMinute = (cb: () => void) => {
  const t = setInterval(cb, MINUTE);
  return () => clearInterval(t);
};

/** Hora actual redondeada al minuto, estable entre renders (para comparar antigüedades sin romper la pureza). */
export function useNow(): number {
  return useSyncExternalStore(subscribeMinute, () => Math.floor(Date.now() / MINUTE) * MINUTE, () => 0);
}
