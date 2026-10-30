import React from "react";
import { Card } from "./Card";

interface StatCardProps {
  title: string;
  value: string | number;
  icon?: React.ReactNode;
  subtitle?: string;
  trend?: {
    value: string;
    isPositive?: boolean;
  };
  accentColor?: "teal" | "blue" | "emerald" | "amber" | "purple" | "rose" | "slate";
  className?: string;
}

const accentStyles = {
  teal: "bg-teal-50 text-teal-700 border-teal-100",
  blue: "bg-sky-50 text-sky-700 border-sky-100",
  emerald: "bg-emerald-50 text-emerald-700 border-emerald-100",
  amber: "bg-amber-50 text-amber-700 border-amber-100",
  purple: "bg-purple-50 text-purple-700 border-purple-100",
  rose: "bg-rose-50 text-rose-700 border-rose-100",
  slate: "bg-slate-100 text-slate-700 border-slate-200",
};

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  icon,
  subtitle,
  trend,
  accentColor = "teal",
  className = "",
}) => {
  return (
    <Card className={`p-5 relative overflow-hidden ${className}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block">
            {title}
          </span>
          <div className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
            {value}
          </div>
        </div>

        {icon && (
          <div
            className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 border ${accentStyles[accentColor]}`}
          >
            {icon}
          </div>
        )}
      </div>

      {(subtitle || trend) && (
        <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
          {subtitle && <span className="text-slate-500">{subtitle}</span>}
          {trend && (
            <span
              className={`font-semibold flex items-center gap-1 ${
                trend.isPositive ? "text-emerald-600" : "text-slate-600"
              }`}
            >
              {trend.value}
            </span>
          )}
        </div>
      )}
    </Card>
  );
};
