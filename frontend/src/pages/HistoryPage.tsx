import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getCustomerHistory, CustomerHistoryResponseDto } from "../lib/apiClient";
import { useTranslation } from "../i18n/LanguageContext";
import { Badge, BadgeVariant } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { TableSkeleton } from "../components/ui/LoadingSkeleton";
import { EmptyState } from "../components/ui/EmptyState";
import { History, Calendar, Clock, MapPin, ChevronLeft, ChevronRight, AlertCircle } from "lucide-react";

const filterOptions = [
  { key: "ALL", labelKey: "filter_all" },
  { key: "COMPLETED", labelKey: "filter_completed" },
  { key: "CANCELLED", labelKey: "filter_cancelled" },
  { key: "SKIPPED", labelKey: "filter_skipped" },
  { key: "WAITING", labelKey: "filter_waiting" },
  { key: "CALLED", labelKey: "filter_called" },
  { key: "HOLD", labelKey: "filter_on_hold" },
];

export const HistoryPage: React.FC = () => {
  const { t } = useTranslation();
  const [selectedStatus, setSelectedStatus] = useState<string>("ALL");
  const [page, setPage] = useState<number>(0);
  const pageSize = 10;

  const { data, isLoading, error, refetch } = useQuery<CustomerHistoryResponseDto>({
    queryKey: ["customerHistory", selectedStatus, page],
    queryFn: async () => {
      const result = await getCustomerHistory(selectedStatus, page, pageSize);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load history");
      }
      return result.data;
    },
  });

  const getStatusBadgeVariant = (status: string): BadgeVariant => {
    switch (status) {
      case "COMPLETED":
        return "success";
      case "CALLED":
        return "warning";
      case "HOLD":
        return "warning";
      case "CANCELLED":
        return "danger";
      case "SKIPPED":
        return "neutral";
      default:
        return "primary";
    }
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "-";
    try {
      const date = new Date(dateStr);
      return date.toLocaleString(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6 pb-12">
      {/* Header */}
      <div className="pb-6 border-b border-slate-200">
        <div className="flex items-center gap-2">
          <Badge variant="primary" size="sm" className="font-mono uppercase font-bold">
            AUDIT &amp; VISITS
          </Badge>
        </div>
        <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
          {t("history_title")}
        </h1>
        <p className="text-xs text-slate-500 mt-0.5">{t("history_subtitle")}</p>
      </div>

      {/* Status Filter Tabs */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-2 border-b border-slate-100">
        {filterOptions.map((opt) => {
          const isActive = selectedStatus === opt.key;
          return (
            <button
              key={opt.key}
              onClick={() => {
                setSelectedStatus(opt.key);
                setPage(0);
              }}
              className={`px-3.5 py-2 rounded-xl text-xs font-bold whitespace-nowrap transition-all cursor-pointer ${
                isActive
                  ? "bg-teal-600 text-white shadow-xs"
                  : "bg-white text-slate-600 hover:bg-slate-50 border border-slate-200"
              }`}
            >
              {t(opt.labelKey)}
            </button>
          );
        })}
      </div>

      {/* Main Content Area */}
      {isLoading ? (
        <TableSkeleton rows={5} />
      ) : error ? (
        <div className="p-8 text-center bg-rose-50 border border-rose-200 rounded-2xl">
          <p className="text-xs text-rose-700 font-bold mb-3">
            {error instanceof Error ? error.message : "Error loading history"}
          </p>
          <Button onClick={() => refetch()} variant="primary" size="xs">
            {t("back")}
          </Button>
        </div>
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          icon={<History className="w-7 h-7" />}
          title={t("no_history")}
          description="Your completed and historical queue visits will appear here once served."
        />
      ) : (
        <Card className="overflow-hidden">
          {/* Desktop Table View */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-700">
              <thead className="bg-slate-50/80 text-[11px] uppercase font-bold text-slate-400 border-b border-slate-200 tracking-wider">
                <tr>
                  <th className="px-6 py-4">{t("token_number")}</th>
                  <th className="px-6 py-4">{t("queue_name")}</th>
                  <th className="px-6 py-4">{t("status")}</th>
                  <th className="px-6 py-4">{t("created_at")}</th>
                  <th className="px-6 py-4">{t("completed_at")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.items.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-6 py-4 font-mono font-black text-teal-800 text-base">
                      {item.tokenNumber}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-bold text-slate-900">{item.queueName || "Queue"}</div>
                      <div className="text-xs text-slate-400 flex items-center gap-1 mt-0.5">
                        <MapPin className="w-3 h-3 text-slate-300" />
                        <span>
                          {item.branchName ? `${item.branchName} • ` : ""}
                          {item.organizationName || "SmartQueue"}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <Badge
                        variant={getStatusBadgeVariant(item.status)}
                        size="sm"
                        dot
                        className="uppercase font-bold"
                      >
                        {t(`status_${item.status}`)}
                      </Badge>
                    </td>
                    <td className="px-6 py-4 text-xs text-slate-500 font-medium">
                      {formatDate(item.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-xs text-slate-500 font-medium">
                      {formatDate(item.completedAt || item.cancelledAt || item.skippedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View */}
          <div className="md:hidden divide-y divide-slate-100">
            {data.items.map((item) => (
              <div key={item.id} className="p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-mono font-black text-teal-800 text-lg">
                    {item.tokenNumber}
                  </span>
                  <Badge
                    variant={getStatusBadgeVariant(item.status)}
                    size="sm"
                    dot
                    className="uppercase font-bold"
                  >
                    {t(`status_${item.status}`)}
                  </Badge>
                </div>
                <div>
                  <h4 className="font-bold text-slate-900 text-sm">{item.queueName || "Queue"}</h4>
                  <p className="text-xs text-slate-400">
                    {item.branchName ? `${item.branchName} • ` : ""}
                    {item.organizationName || "SmartQueue"}
                  </p>
                </div>
                <div className="pt-2 flex items-center justify-between text-[11px] text-slate-400 border-t border-slate-50">
                  <span>Created: {formatDate(item.createdAt)}</span>
                  <span>Closed: {formatDate(item.completedAt || item.cancelledAt || item.skippedAt)}</span>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination Footer */}
          <div className="p-4 bg-slate-50/70 border-t border-slate-100 flex items-center justify-between">
            <div className="text-xs text-slate-500 font-medium">
              {t("page_info", { page: data.page + 1, totalPages: Math.max(1, data.totalPages) })} (
              {data.totalElements} total records)
            </div>
            <div className="flex items-center gap-2">
              <Button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={data.page === 0}
                variant="outline"
                size="xs"
                icon={<ChevronLeft className="w-3.5 h-3.5" />}
              >
                {t("page_prev")}
              </Button>
              <Button
                onClick={() => setPage((p) => p + 1)}
                disabled={data.page + 1 >= data.totalPages}
                variant="outline"
                size="xs"
                icon={<ChevronRight className="w-3.5 h-3.5" />}
                iconPosition="right"
              >
                {t("page_next")}
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
};
