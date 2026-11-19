"use client";
import Link from "next/link";
import { LoaderCircle, type LucideIcon } from "lucide-react";
import type { ButtonHTMLAttributes, ReactNode } from "react";

export type Variant = "primary" | "secondary" | "ghost" | "danger" | "link";

const base =
  "relative inline-flex items-center justify-center gap-2 rounded-md text-body-sm font-medium select-none whitespace-nowrap " +
  "transition-[background-color,border-color,color,transform] duration-[120ms] ease-out active:scale-[0.98] disabled:active:scale-100 " +
  "disabled:cursor-not-allowed disabled:opacity-60";

const variants: Record<Variant, string> = {
  primary: "bg-primary text-on-primary hover:bg-primary-hover active:bg-primary-active",
  secondary: "bg-surface text-ink border border-border-strong hover:bg-surface-sunken",
  ghost: "bg-transparent text-ink hover:bg-surface-sunken",
  danger: "bg-danger text-on-danger hover:opacity-90",
  link: "bg-transparent text-primary hover:underline underline-offset-4 px-0! h-auto!",
};

const sizes = {
  md: "h-12 px-4 lg:h-10",
  lg: "h-[52px] px-5 w-full text-body",
  sm: "h-10 px-3 lg:h-8",
};

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: keyof typeof sizes;
  icon?: LucideIcon;
  loading?: boolean;
  loadingText?: string;
  children?: ReactNode;
}

export function Button({ variant = "secondary", size = "md", icon: Icon, loading, loadingText, children, className = "", disabled, ...rest }: Props) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {loading ? <LoaderCircle aria-hidden className="size-4 animate-spin" /> : Icon && <Icon aria-hidden className="size-[18px]" strokeWidth={1.75} />}
      {loading && loadingText ? loadingText : children}
    </button>
  );
}

export function ButtonLink({
  href, variant = "secondary", size = "md", icon: Icon, children, className = "",
}: { href: string; variant?: Variant; size?: keyof typeof sizes; icon?: LucideIcon; children: ReactNode; className?: string }) {
  return (
    <Link href={href} className={`${base} ${variants[variant]} ${sizes[size]} ${className}`}>
      {Icon && <Icon aria-hidden className="size-[18px]" strokeWidth={1.75} />}
      {children}
    </Link>
  );
}

export function IconButton({
  icon: Icon, label, className = "", ...rest
}: { icon: LucideIcon; label: string } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...rest}
      aria-label={label}
      title={label}
      className={`inline-flex size-12 items-center justify-center rounded-md text-ink-muted transition-colors duration-[120ms] hover:bg-surface-sunken hover:text-ink lg:size-10 ${className}`}
    >
      <Icon aria-hidden className="size-[18px]" strokeWidth={1.75} />
    </button>
  );
}
