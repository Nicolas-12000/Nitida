"use client";
import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  // next-themes solo conoce el tema en el cliente; antes de montar se pinta un hueco del mismo tamaño.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setMounted(true), []);
  const dark = mounted && resolvedTheme === "dark";
  return (
    <button
      type="button"
      aria-label={dark ? "Usar modo claro" : "Usar modo oscuro"}
      title={dark ? "Usar modo claro" : "Usar modo oscuro"}
      onClick={() => setTheme(dark ? "light" : "dark")}
      className="flex size-12 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken hover:text-ink lg:size-10"
    >
      {mounted && (dark ? <Sun aria-hidden className="size-[18px]" strokeWidth={1.75} /> : <Moon aria-hidden className="size-[18px]" strokeWidth={1.75} />)}
    </button>
  );
}
