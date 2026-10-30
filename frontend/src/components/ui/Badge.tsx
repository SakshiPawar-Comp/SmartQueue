import React from "react";

export type BadgeVariant =
  | "default"
  | "primary"
  | "success"
  | "warning"
  | "danger"
  | "neutral"
  | "info"
  | "purple"
  | "emerald";

export type BadgeSize = "sm" | "md" | "lg";

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  size?: BadgeSize;
  dot?: boolean;
  className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
  default: "bg-slate-100 text-slate-700 border-slate-200",
  primary: "bg-teal-50 text-teal-700 border-teal-200/80",
  success: "bg-emerald-50 text-emerald-700 border-emerald-200/80",
  emerald: "bg-emerald-50 text-emerald-700 border-emerald-200/80",
  warning: "bg-amber-50 text-amber-800 border-amber-200/80",
  danger: "bg-rose-50 text-rose-700 border-rose-200/80",
  neutral: "bg-slate-100 text-slate-600 border-slate-200",
  info: "bg-sky-50 text-sky-700 border-sky-200/80",
  purple: "bg-purple-50 text-purple-700 border-purple-200/80",
};

const dotStyles: Record<BadgeVariant, string> = {
  default: "bg-slate-500",
  primary: "bg-teal-600",
  success: "bg-emerald-500",
  emerald: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
  neutral: "bg-slate-400",
  info: "bg-sky-500",
  purple: "bg-purple-500",
};

const sizeStyles: Record<BadgeSize, string> = {
  sm: "px-2 py-0.5 text-[11px] font-medium leading-tight",
  md: "px-2.5 py-0.5 text-xs font-semibold leading-normal",
  lg: "px-3 py-1 text-sm font-semibold",
};

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = "default",
  size = "md",
  dot = false,
  className = "",
}) => {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border ${variantStyles[variant]} ${sizeStyles[size]} ${className}`}
    >
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dotStyles[variant]}`} />}
      <span>{children}</span>
    </span>
  );
};
