"use client";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { api, isDemo, setActiveWorkshop, setDemo } from "./api";
import { ApiError } from "./errors";
import { clearCache } from "./use-query";
import type { Me, MyWorkshop, Role } from "./types";

const WS_KEY = "nitida-workshop";

interface Session {
  me: Me;
  workshops: MyWorkshop[];
  workshop: MyWorkshop | null;
  role: Role | null;
  demo: boolean;
  selectWorkshop: (id: string) => void;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
  can: (...roles: Role[]) => boolean;
}

const Ctx = createContext<Session | null>(null);

export function useSession(): Session {
  const s = useContext(Ctx);
  if (!s) throw new Error("useSession fuera de SessionProvider");
  return s;
}

function readStored(): string | null {
  try {
    return window.localStorage.getItem(WS_KEY);
  } catch {
    return null;
  }
}

export function SessionProvider({ children, fallback }: { children: ReactNode; fallback: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [state, setState] = useState<{ me: Me; workshops: MyWorkshop[]; active: string | null } | null>(null);

  const load = useCallback(async () => {
    try {
      const me = await api.get<Me>("/auth/me");
      const workshops = me.memberships.length ? await api.get<MyWorkshop[]>("/workshops/mine") : [];
      const stored = readStored();
      const active = workshops.find((w) => w.id === stored)?.id ?? workshops[0]?.id ?? null;
      setActiveWorkshop(active);
      setState({ me, workshops, active });
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        router.replace(`/login?siguiente=${encodeURIComponent(pathname)}&motivo=sesion`);
      } else {
        router.replace("/login?motivo=conexion");
      }
    }
  }, [router, pathname]);

  useEffect(() => {
    // Carga inicial de la sesión: el estado llega por la respuesta asíncrona de /auth/me.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    // Solo al montar: navegar dentro de la app no vuelve a pedir la sesión.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const value = useMemo<Session | null>(() => {
    if (!state) return null;
    const workshop = state.workshops.find((w) => w.id === state.active) ?? null;
    const role = (state.me.memberships.find((m) => m.workshopId === state.active)?.role ?? null) as Role | null;
    return {
      me: state.me,
      workshops: state.workshops,
      workshop,
      role,
      demo: isDemo(),
      selectWorkshop: (id) => {
        try {
          window.localStorage.setItem(WS_KEY, id);
        } catch {
          /* preferencia no guardada */
        }
        setActiveWorkshop(id);
        clearCache();
        setState((s) => (s ? { ...s, active: id } : s));
      },
      logout: async () => {
        try {
          await api.post("/auth/logout");
        } catch {
          /* la sesión se descarta igual en el cliente */
        }
        clearCache();
        setActiveWorkshop(null);
        setDemo(false);
        router.replace("/login");
      },
      reload: load,
      can: (...roles) => role != null && roles.includes(role),
    };
  }, [state, router, load]);

  if (!value) return <>{fallback}</>;
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
