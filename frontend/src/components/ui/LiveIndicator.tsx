import React from "react";

interface LiveIndicatorProps {
  label?: string;
  className?: string;
}

export const LiveIndicator: React.FC<LiveIndicatorProps> = ({
  label = "LIVE",
  className = "",
}) => {
  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold bg-teal-50 text-teal-800 border border-teal-200/80 tracking-wide uppercase ${className}`}
    >
      <span className="relative flex h-2 w-2">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-teal-400 opacity-75"></span>
        <span className="relative inline-flex rounded-full h-2 w-2 bg-teal-600"></span>
      </span>
      <span>{label}</span>
    </span>
  );
};
