"use client";
import { MotionConfig } from "motion/react";
import { ThemeProvider } from "next-themes";
import { Toaster } from "sonner";
import type { ReactNode } from "react";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem disableTransitionOnChange>
      <MotionConfig reducedMotion="user">
        {children}
        <Toaster
          position="bottom-right"
          visibleToasts={1}
          duration={4000}
          offset={{ bottom: 88, right: 16 }}
          mobileOffset={{ bottom: 88 }}
          toastOptions={{
            classNames: {
              toast: "!bg-surface !text-ink !border !border-border !rounded-md !shadow-raised !font-sans !text-body-sm",
              description: "!text-ink-muted",
              actionButton: "!bg-primary !text-on-primary !rounded-sm",
            },
          }}
        />
      </MotionConfig>
    </ThemeProvider>
  );
}
