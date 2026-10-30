import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Header } from "../../components/Header";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getBranchesByOrg,
  getServicesByBranch,
  getQueues,
  getAnalyticsOverview,
  getQueuesAnalytics,
  getServicesAnalytics,
  getCountersAnalytics,
  getStaffAnalytics,
  getPeakHoursAnalytics,
  getWaitTimeDistribution,
  downloadReportCsv,
  getDemandForecast,
  getAnomalies,
  getRecommendations,
} from "../../lib/apiClient";
import type {
  BranchDto,
  ServiceDto,
  QueueResponse,
} from "../../lib/apiClient";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Legend,
  Line,
  ComposedChart,
} from "recharts";
import {
  TrendingUp,
  Download,
  AlertTriangle,
  Lightbulb,
  Clock,
  Users,
  Activity,
  CheckCircle2,
  XCircle,
  SkipForward,
  Cpu,
  Layers,
  LayoutGrid,
} from "lucide-react";

export const AnalyticsDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();

  const [activeTab, setActiveTab] = useState<"overview" | "queues" | "counters" | "reports" | "ai">("overview");

  // Filter States
  const [branchId, setBranchId] = useState<string>("");
  const [serviceId, setServiceId] = useState<string>("");
  const [queueId, setQueueId] = useState<string>("");
  const [datePreset, setDatePreset] = useState<"today" | "7days" | "30days" | "custom">("7days");
  const [fromDate, setFromDate] = useState<string>(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().split("T")[0] + "T00:00:00";
  });
  const [toDate, setToDate] = useState<string>(() => {
    return new Date().toISOString().split("T")[0] + "T23:59:59";
  });

  const [exportingReport, setExportingReport] = useState<string | null>(null);

  const orgId = user?.organizationId ?? "";

  // Preset Date Helper
  const handleDatePresetChange = (preset: "today" | "7days" | "30days" | "custom") => {
    setDatePreset(preset);
    const now = new Date();
    const todayStr = now.toISOString().split("T")[0];
    if (preset === "today") {
      setFromDate(`${todayStr}T00:00:00`);
      setToDate(`${todayStr}T23:59:59`);
    } else if (preset === "7days") {
      const past = new Date();
      past.setDate(past.getDate() - 7);
      setFromDate(`${past.toISOString().split("T")[0]}T00:00:00`);
      setToDate(`${todayStr}T23:59:59`);
    } else if (preset === "30days") {
      const past = new Date();
      past.setDate(past.getDate() - 30);
      setFromDate(`${past.toISOString().split("T")[0]}T00:00:00`);
      setToDate(`${todayStr}T23:59:59`);
    }
  };

  // Branch & Service Lookups
  const { data: branches = [] } = useQuery<BranchDto[]>({
    queryKey: ["adminBranches", orgId],
    queryFn: async () => {
      if (!orgId) return [];
      const res = await getBranchesByOrg(orgId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!orgId,
  });

  const { data: services = [] } = useQuery<ServiceDto[]>({
    queryKey: ["adminServices", branchId],
    queryFn: async () => {
      if (!branchId) return [];
      const res = await getServicesByBranch(branchId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!branchId,
  });

  const { data: queues = [] } = useQuery<QueueResponse[]>({
    queryKey: ["adminQueues", branchId, serviceId],
    queryFn: async () => {
      const res = await getQueues({
        branchId: branchId || undefined,
        serviceId: serviceId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
  });

  // Query Params Payload
  const queryFilter = {
    from: fromDate ? new Date(fromDate).toISOString() : undefined,
    to: toDate ? new Date(toDate).toISOString() : undefined,
    branchId: branchId || undefined,
    serviceId: serviceId || undefined,
    queueId: queueId || undefined,
  };

  // 1. Overview Analytics
  const { data: overview, isLoading: overviewLoading } = useQuery({
    queryKey: ["analyticsOverview", queryFilter],
    queryFn: async () => {
      const res = await getAnalyticsOverview(queryFilter);
      return res.success ? res.data : null;
    },
  });

  // 2. Peak Hours & Traffic
  const { data: peakHours = [] } = useQuery({
    queryKey: ["analyticsPeakHours", queryFilter],
    queryFn: async () => {
      const res = await getPeakHoursAnalytics({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
        queueId: queueId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
  });

  // 3. Wait Time Percentiles
  const { data: waitDistribution } = useQuery({
    queryKey: ["analyticsWaitDist", queryFilter],
    queryFn: async () => {
      const res = await getWaitTimeDistribution({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
        queueId: queueId || undefined,
      });
      return res.success ? res.data : null;
    },
  });

  // 4. Queues & Services Table Analytics
  const { data: queuesAnalytics = [], isLoading: queuesLoading } = useQuery({
    queryKey: ["analyticsQueues", queryFilter],
    queryFn: async () => {
      const res = await getQueuesAnalytics({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
        serviceId: serviceId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
    enabled: activeTab === "queues",
  });

  const { data: servicesAnalytics = [], isLoading: servicesLoading } = useQuery({
    queryKey: ["analyticsServices", queryFilter],
    queryFn: async () => {
      const res = await getServicesAnalytics({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
    enabled: activeTab === "queues",
  });

  // 5. Counters & Staff Analytics
  const { data: countersAnalytics = [], isLoading: countersLoading } = useQuery({
    queryKey: ["analyticsCounters", queryFilter],
    queryFn: async () => {
      const res = await getCountersAnalytics({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
    enabled: activeTab === "counters",
  });

  const { data: staffAnalytics = [], isLoading: staffLoading } = useQuery({
    queryKey: ["analyticsStaff", queryFilter],
    queryFn: async () => {
      const res = await getStaffAnalytics({
        from: queryFilter.from,
        to: queryFilter.to,
        branchId: branchId || undefined,
      });
      return res.success && res.data ? res.data : [];
    },
    enabled: activeTab === "counters",
  });

  // 6. AI Operational Intelligence Queries
  const { data: aiForecast, isLoading: forecastLoading } = useQuery({
    queryKey: ["aiForecast", queueId, serviceId],
    queryFn: async () => {
      const res = await getDemandForecast({
        queueId: queueId || undefined,
        serviceId: serviceId || undefined,
        horizonHours: 6,
      });
      return res.success ? res.data : null;
    },
    enabled: activeTab === "ai",
  });

  const { data: aiAnomalies, isLoading: anomaliesLoading } = useQuery({
    queryKey: ["aiAnomalies", branchId, queueId],
    queryFn: async () => {
      const res = await getAnomalies({
        branchId: branchId || undefined,
        queueId: queueId || undefined,
      });
      return res.success ? res.data : null;
    },
    enabled: activeTab === "ai",
  });

  const { data: aiRecommendations, isLoading: recsLoading } = useQuery({
    queryKey: ["aiRecommendations", branchId, queueId],
    queryFn: async () => {
      const res = await getRecommendations({
        branchId: branchId || undefined,
        queueId: queueId || undefined,
      });
      return res.success ? res.data : null;
    },
    enabled: activeTab === "ai",
  });

  // Export CSV Handler
  const handleExportCsv = async (type: "daily" | "queues" | "services" | "summary") => {
    try {
      setExportingReport(type);
      const params: Record<string, string> = {};
      if (fromDate) params.from = new Date(fromDate).toISOString();
      if (toDate) params.to = new Date(toDate).toISOString();
      if (branchId) params.branchId = branchId;
      if (serviceId) params.serviceId = serviceId;
      if (queueId) params.queueId = queueId;

      const blob = await downloadReportCsv(type, params);
      if (blob) {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `smartqueue_${type}_report_${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
      }
    } catch (err) {
      console.error("Export CSV failed:", err);
    } finally {
      setExportingReport(null);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <Header />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Page Header */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between pb-6 border-b border-slate-200 gap-4">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight flex items-center gap-2">
              <TrendingUp className="w-7 h-7 text-teal-600" />
              {t("analytics_title")}
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Authoritative operational reporting, multi-counter throughput, and explainable AI insights.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => handleExportCsv("summary")}
              disabled={!!exportingReport}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-teal-600 hover:bg-teal-700 text-white text-xs font-semibold rounded-lg shadow-sm transition-colors disabled:opacity-50"
            >
              <Download className="w-4 h-4" />
              {exportingReport === "summary" ? "Exporting..." : t("summary_report")} (CSV)
            </button>
          </div>
        </div>

        {/* Global Filter Bar */}
        <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm my-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end">
          {/* Preset Buttons */}
          <div>
            <label className="block text-xs font-bold text-slate-500 mb-1">Time Range</label>
            <div className="grid grid-cols-3 gap-1">
              <button
                type="button"
                onClick={() => handleDatePresetChange("today")}
                className={`px-2 py-1.5 text-xs rounded-lg font-medium border ${
                  datePreset === "today"
                    ? "bg-teal-600 text-white border-teal-600"
                    : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"
                }`}
              >
                Today
              </button>
              <button
                type="button"
                onClick={() => handleDatePresetChange("7days")}
                className={`px-2 py-1.5 text-xs rounded-lg font-medium border ${
                  datePreset === "7days"
                    ? "bg-teal-600 text-white border-teal-600"
                    : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"
                }`}
              >
                7 Days
              </button>
              <button
                type="button"
                onClick={() => handleDatePresetChange("30days")}
                className={`px-2 py-1.5 text-xs rounded-lg font-medium border ${
                  datePreset === "30days"
                    ? "bg-teal-600 text-white border-teal-600"
                    : "bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100"
                }`}
              >
                30 Days
              </button>
            </div>
          </div>

          {/* Branch Filter */}
          <div>
            <label className="block text-xs font-bold text-slate-500 mb-1">{t("branch")}</label>
            <select
              value={branchId}
              onChange={(e) => {
                setBranchId(e.target.value);
                setServiceId("");
                setQueueId("");
              }}
              className="w-full text-xs bg-slate-50 border border-slate-300 rounded-lg px-2.5 py-1.5 focus:ring-teal-500 focus:border-teal-500"
            >
              <option value="">All Branches</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.code})
                </option>
              ))}
            </select>
          </div>

          {/* Service Filter */}
          <div>
            <label className="block text-xs font-bold text-slate-500 mb-1">{t("service")}</label>
            <select
              value={serviceId}
              onChange={(e) => {
                setServiceId(e.target.value);
                setQueueId("");
              }}
              disabled={!branchId}
              className="w-full text-xs bg-slate-50 border border-slate-300 rounded-lg px-2.5 py-1.5 focus:ring-teal-500 focus:border-teal-500 disabled:opacity-50"
            >
              <option value="">All Services</option>
              {services.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.code})
                </option>
              ))}
            </select>
          </div>

          {/* Queue Filter */}
          <div>
            <label className="block text-xs font-bold text-slate-500 mb-1">{t("queue_name")}</label>
            <select
              value={queueId}
              onChange={(e) => setQueueId(e.target.value)}
              className="w-full text-xs bg-slate-50 border border-slate-300 rounded-lg px-2.5 py-1.5 focus:ring-teal-500 focus:border-teal-500"
            >
              <option value="">All Queues</option>
              {queues.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.name} ({q.tokenPrefix})
                </option>
              ))}
            </select>
          </div>

          {/* Date range inputs */}
          <div className="flex items-center gap-1.5 sm:col-span-2 lg:col-span-2">
            <div className="flex-1">
              <label className="block text-xs font-bold text-slate-500 mb-1">From</label>
              <input
                type="datetime-local"
                value={fromDate.slice(0, 16)}
                onChange={(e) => {
                  setDatePreset("custom");
                  setFromDate(e.target.value ? `${e.target.value}:00` : "");
                }}
                className="w-full text-xs bg-slate-50 border border-slate-300 rounded-lg px-2 py-1.5"
              />
            </div>
            <div className="flex-1">
              <label className="block text-xs font-bold text-slate-500 mb-1">To</label>
              <input
                type="datetime-local"
                value={toDate.slice(0, 16)}
                onChange={(e) => {
                  setDatePreset("custom");
                  setToDate(e.target.value ? `${e.target.value}:59` : "");
                }}
                className="w-full text-xs bg-slate-50 border border-slate-300 rounded-lg px-2 py-1.5"
              />
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-200 mb-6 space-x-1 sm:space-x-4 overflow-x-auto">
          <button
            onClick={() => setActiveTab("overview")}
            className={`flex items-center gap-2 py-3 px-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "overview"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Activity className="w-4 h-4" />
            {t("analytics_tab_overview")}
          </button>
          <button
            onClick={() => setActiveTab("queues")}
            className={`flex items-center gap-2 py-3 px-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "queues"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Layers className="w-4 h-4" />
            {t("analytics_tab_queues")}
          </button>
          <button
            onClick={() => setActiveTab("counters")}
            className={`flex items-center gap-2 py-3 px-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "counters"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <LayoutGrid className="w-4 h-4" />
            {t("analytics_tab_counters")}
          </button>
          <button
            onClick={() => setActiveTab("reports")}
            className={`flex items-center gap-2 py-3 px-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "reports"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Download className="w-4 h-4" />
            {t("analytics_tab_reports")}
          </button>
          <button
            onClick={() => setActiveTab("ai")}
            className={`flex items-center gap-2 py-3 px-3 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "ai"
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Cpu className="w-4 h-4" />
            {t("analytics_tab_ai")}
          </button>
        </div>

        {/* ------------------------------------------------------------- */}
        {/* TAB 1: OVERVIEW & KPIS */}
        {/* ------------------------------------------------------------- */}
        {activeTab === "overview" && (
          <div className="space-y-6">
            {overviewLoading ? (
              <div className="p-12 text-center text-slate-400">Loading operational overview...</div>
            ) : overview ? (
              <>
                {/* Metric Summary Cards Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-slate-400 block">{t("total_tokens")}</span>
                    <span className="text-2xl font-extrabold text-slate-900 mt-1 block">
                      {overview.totalTokens}
                    </span>
                  </div>
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-emerald-600 block">{t("completed_tokens")}</span>
                    <span className="text-2xl font-extrabold text-emerald-700 mt-1 block">
                      {overview.completedTokens}
                    </span>
                  </div>
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-teal-600 block">{t("waiting_tokens")}</span>
                    <span className="text-2xl font-extrabold text-teal-700 mt-1 block">
                      {overview.waitingTokens}
                    </span>
                  </div>
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-amber-600 block">{t("serving_tokens")}</span>
                    <span className="text-2xl font-extrabold text-amber-700 mt-1 block">
                      {overview.servingTokens}
                    </span>
                  </div>
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-rose-500 block">{t("cancelled_tokens")}</span>
                    <span className="text-2xl font-extrabold text-rose-600 mt-1 block">
                      {overview.cancelledTokens}
                    </span>
                  </div>
                  <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
                    <span className="text-xs font-bold text-slate-500 block">{t("skipped_tokens")}</span>
                    <span className="text-2xl font-extrabold text-slate-700 mt-1 block">
                      {overview.skippedTokens}
                    </span>
                  </div>
                </div>

                {/* Performance Rate & Speed KPI Row */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-500 uppercase">{t("avg_wait_time")}</span>
                      <Clock className="w-4 h-4 text-teal-600" />
                    </div>
                    <div className="mt-2 flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-teal-900">
                        {overview.avgWaitMinutes}
                      </span>
                      <span className="text-xs font-semibold text-slate-500">mins (median: {overview.medianWaitMinutes}m)</span>
                    </div>
                  </div>

                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-500 uppercase">{t("avg_service_time")}</span>
                      <Activity className="w-4 h-4 text-emerald-600" />
                    </div>
                    <div className="mt-2 flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-emerald-900">
                        {overview.avgServiceMinutes}
                      </span>
                      <span className="text-xs font-semibold text-slate-500">mins (median: {overview.medianServiceMinutes}m)</span>
                    </div>
                  </div>

                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-500 uppercase">{t("throughput_per_hr")}</span>
                      <TrendingUp className="w-4 h-4 text-indigo-600" />
                    </div>
                    <div className="mt-2 flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-indigo-900">
                        {overview.throughputPerHour}
                      </span>
                      <span className="text-xs font-semibold text-slate-500">tokens / hour</span>
                    </div>
                  </div>

                  <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-slate-500 uppercase">{t("completion_rate")}</span>
                      <CheckCircle2 className="w-4 h-4 text-teal-600" />
                    </div>
                    <div className="mt-2 flex items-baseline gap-2">
                      <span className="text-3xl font-extrabold text-teal-900">
                        {overview.completionRate}%
                      </span>
                      <span className="text-xs font-semibold text-slate-500">
                        (cancel: {overview.cancellationRate}%, skip: {overview.skipRate}%)
                      </span>
                    </div>
                  </div>
                </div>

                {/* Hourly Traffic & Peak Hours Chart */}
                <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                  <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-4 flex items-center gap-2">
                    <Clock className="w-4 h-4 text-teal-600" />
                    {t("peak_hours_chart")}
                  </h3>
                  {peakHours.length > 0 ? (
                    <div className="h-72 w-full">
                      <ResponsiveContainer width="100%" height="100%">
                        <ComposedChart data={peakHours} margin={{ top: 10, right: 20, bottom: 20, left: 0 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                          <XAxis dataKey="hourLabel" stroke="#94a3b8" fontSize={11} />
                          <YAxis yAxisId="left" stroke="#94a3b8" fontSize={11} label={{ value: "Tokens", angle: -90, position: "insideLeft", fontSize: 10, fill: "#94a3b8" }} />
                          <YAxis yAxisId="right" orientation="right" stroke="#0d9488" fontSize={11} label={{ value: "Avg Wait (m)", angle: 90, position: "insideRight", fontSize: 10, fill: "#0d9488" }} />
                          <Tooltip
                            contentStyle={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", fontSize: "12px" }}
                          />
                          <Legend wrapperStyle={{ fontSize: "12px" }} />
                          <Bar yAxisId="left" dataKey="tokenCount" name="Total Issued" fill="#0d9488" radius={[4, 4, 0, 0]} />
                          <Bar yAxisId="left" dataKey="completedCount" name="Completed" fill="#10b981" radius={[4, 4, 0, 0]} />
                          <Line yAxisId="right" type="monotone" dataKey="avgWaitMinutes" name="Avg Wait (min)" stroke="#f59e0b" strokeWidth={2} dot={{ r: 3 }} />
                        </ComposedChart>
                      </ResponsiveContainer>
                    </div>
                  ) : (
                    <div className="h-40 flex items-center justify-center text-slate-400 text-xs">
                      No traffic data available for the selected interval.
                    </div>
                  )}
                </div>

                {/* Wait Time Distribution Percentiles */}
                {waitDistribution && (
                  <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                    <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-4 flex items-center gap-2">
                      <TrendingUp className="w-4 h-4 text-teal-600" />
                      Wait Time Percentiles & Priority Breakdown
                    </h3>
                    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3 text-center">
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <span className="text-xs text-slate-400 font-bold block">P50 (Median)</span>
                        <span className="text-lg font-extrabold text-teal-800">{waitDistribution.p50Minutes}m</span>
                      </div>
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <span className="text-xs text-slate-400 font-bold block">P75</span>
                        <span className="text-lg font-extrabold text-teal-800">{waitDistribution.p75Minutes}m</span>
                      </div>
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <span className="text-xs text-slate-400 font-bold block">P90</span>
                        <span className="text-lg font-extrabold text-amber-700">{waitDistribution.p90Minutes}m</span>
                      </div>
                      <div className="p-3 bg-slate-50 rounded-xl">
                        <span className="text-xs text-slate-400 font-bold block">P95</span>
                        <span className="text-lg font-extrabold text-rose-700">{waitDistribution.p95Minutes}m</span>
                      </div>
                      <div className="p-3 bg-teal-50/50 rounded-xl">
                        <span className="text-xs text-teal-700 font-bold block">Normal Tier</span>
                        <span className="text-lg font-extrabold text-teal-900">{waitDistribution.normalPriorityAvgMinutes}m</span>
                      </div>
                      <div className="p-3 bg-indigo-50/50 rounded-xl">
                        <span className="text-xs text-indigo-700 font-bold block">Priority Tier</span>
                        <span className="text-lg font-extrabold text-indigo-900">{waitDistribution.priorityAvgMinutes}m</span>
                      </div>
                      <div className="p-3 bg-amber-50/50 rounded-xl">
                        <span className="text-xs text-amber-700 font-bold block">VIP Tier</span>
                        <span className="text-lg font-extrabold text-amber-900">{waitDistribution.vipPriorityAvgMinutes}m</span>
                      </div>
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="p-12 text-center text-slate-400">No operational data available for this range.</div>
            )}
          </div>
        )}

        {/* ------------------------------------------------------------- */}
        {/* TAB 2: QUEUES & SERVICES */}
        {/* ------------------------------------------------------------- */}
        {activeTab === "queues" && (
          <div className="space-y-6">
            {/* Queues Analytics Table */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Layers className="w-4 h-4 text-teal-600" />
                  Queue Operational Performance
                </h3>
                <button
                  onClick={() => handleExportCsv("queues")}
                  disabled={!!exportingReport}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-teal-600 hover:text-teal-700 border border-teal-200 px-3 py-1.5 rounded-lg hover:bg-teal-50"
                >
                  <Download className="w-3.5 h-3.5" />
                  Export Queues CSV
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 text-left">Queue</th>
                      <th className="px-4 py-3 text-left">Branch / Service</th>
                      <th className="px-4 py-3 text-right">Total</th>
                      <th className="px-4 py-3 text-right">Completed</th>
                      <th className="px-4 py-3 text-right">Waiting</th>
                      <th className="px-4 py-3 text-right">Avg Wait</th>
                      <th className="px-4 py-3 text-right">Avg Service</th>
                      <th className="px-4 py-3 text-center">Peak Hour</th>
                      <th className="px-4 py-3 text-right">Throughput/hr</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {queuesLoading ? (
                      <tr>
                        <td colSpan={9} className="px-4 py-8 text-center text-slate-400">Loading queue analytics...</td>
                      </tr>
                    ) : queuesAnalytics.length === 0 ? (
                      <tr>
                        <td colSpan={9} className="px-4 py-8 text-center text-slate-400">No queues found for selected criteria.</td>
                      </tr>
                    ) : (
                      queuesAnalytics.map((q) => (
                        <tr key={q.queueId} className="hover:bg-slate-50 transition-colors">
                          <td className="px-4 py-3 font-semibold text-slate-900">
                            {q.queueName} <span className="text-slate-400 font-mono">({q.tokenPrefix})</span>
                          </td>
                          <td className="px-4 py-3 text-slate-500">
                            {q.branchName || "-"} / {q.serviceName || "-"}
                          </td>
                          <td className="px-4 py-3 text-right font-bold text-slate-800">{q.totalTokens}</td>
                          <td className="px-4 py-3 text-right font-semibold text-emerald-600">{q.completedTokens}</td>
                          <td className="px-4 py-3 text-right font-semibold text-teal-600">{q.currentWaiting}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{q.avgWaitMinutes}m</td>
                          <td className="px-4 py-3 text-right text-slate-700">{q.avgServiceMinutes}m</td>
                          <td className="px-4 py-3 text-center font-mono text-slate-600">{q.peakHour || "-"}</td>
                          <td className="px-4 py-3 text-right font-bold text-indigo-600">{q.throughputPerHour}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Services Analytics Table */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Activity className="w-4 h-4 text-emerald-600" />
                  Service Operational Performance
                </h3>
                <button
                  onClick={() => handleExportCsv("services")}
                  disabled={!!exportingReport}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-teal-600 hover:text-teal-700 border border-teal-200 px-3 py-1.5 rounded-lg hover:bg-teal-50"
                >
                  <Download className="w-3.5 h-3.5" />
                  Export Services CSV
                </button>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 text-left">Service</th>
                      <th className="px-4 py-3 text-left">Branch</th>
                      <th className="px-4 py-3 text-right">Total Tokens</th>
                      <th className="px-4 py-3 text-right">Completed</th>
                      <th className="px-4 py-3 text-right">Cancelled</th>
                      <th className="px-4 py-3 text-right">Avg Wait</th>
                      <th className="px-4 py-3 text-right">Avg Service</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {servicesLoading ? (
                      <tr>
                        <td colSpan={7} className="px-4 py-8 text-center text-slate-400">Loading service analytics...</td>
                      </tr>
                    ) : servicesAnalytics.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="px-4 py-8 text-center text-slate-400">No services found for selected criteria.</td>
                      </tr>
                    ) : (
                      servicesAnalytics.map((s) => (
                        <tr key={s.serviceId} className="hover:bg-slate-50 transition-colors">
                          <td className="px-4 py-3 font-semibold text-slate-900">{s.serviceName}</td>
                          <td className="px-4 py-3 text-slate-500">{s.branchName || "-"}</td>
                          <td className="px-4 py-3 text-right font-bold text-slate-800">{s.totalTokens}</td>
                          <td className="px-4 py-3 text-right font-semibold text-emerald-600">{s.completedTokens}</td>
                          <td className="px-4 py-3 text-right text-rose-500">{s.cancelledTokens}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{s.avgWaitMinutes}m</td>
                          <td className="px-4 py-3 text-right text-slate-700">{s.avgServiceMinutes}m</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------------- */}
        {/* TAB 3: COUNTERS & STAFF */}
        {/* ------------------------------------------------------------- */}
        {activeTab === "counters" && (
          <div className="space-y-6">
            {/* Counters Table */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-200">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <LayoutGrid className="w-4 h-4 text-teal-600" />
                  Counter Utilization & Throughput
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 text-left">Counter</th>
                      <th className="px-4 py-3 text-left">Branch</th>
                      <th className="px-4 py-3 text-right">Tokens Served</th>
                      <th className="px-4 py-3 text-right">Avg Service</th>
                      <th className="px-4 py-3 text-right">Utilization %</th>
                      <th className="px-4 py-3 text-right">Active Hours</th>
                      <th className="px-4 py-3 text-right">Idle Hours</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {countersLoading ? (
                      <tr>
                        <td colSpan={7} className="px-4 py-8 text-center text-slate-400">Loading counter analytics...</td>
                      </tr>
                    ) : countersAnalytics.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="px-4 py-8 text-center text-slate-400">No counter records found.</td>
                      </tr>
                    ) : (
                      countersAnalytics.map((c) => (
                        <tr key={c.counterId} className="hover:bg-slate-50 transition-colors">
                          <td className="px-4 py-3 font-semibold text-slate-900">{c.counterName}</td>
                          <td className="px-4 py-3 text-slate-500">{c.branchName || "-"}</td>
                          <td className="px-4 py-3 text-right font-bold text-teal-700">{c.tokensServed}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{c.avgServiceMinutes}m</td>
                          <td className="px-4 py-3 text-right">
                            <span className={`inline-flex px-2 py-0.5 rounded-full font-bold text-xs ${
                              c.utilizationPercentage > 75
                                ? "bg-emerald-100 text-emerald-800"
                                : c.utilizationPercentage > 40
                                ? "bg-teal-100 text-teal-800"
                                : "bg-slate-100 text-slate-600"
                            }`}>
                              {c.utilizationPercentage}%
                            </span>
                          </td>
                          <td className="px-4 py-3 text-right text-slate-600">{c.activeHours}h</td>
                          <td className="px-4 py-3 text-right text-slate-600">{c.idleHours}h</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Staff Table */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-200">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Users className="w-4 h-4 text-teal-600" />
                  Staff Member Operational Performance
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 text-left">Staff Name</th>
                      <th className="px-4 py-3 text-left">Email</th>
                      <th className="px-4 py-3 text-right">Tokens Served</th>
                      <th className="px-4 py-3 text-right">Avg Service Time</th>
                      <th className="px-4 py-3 text-right">Total Serving Minutes</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {staffLoading ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-slate-400">Loading staff analytics...</td>
                      </tr>
                    ) : staffAnalytics.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-slate-400">No staff performance records found.</td>
                      </tr>
                    ) : (
                      staffAnalytics.map((st) => (
                        <tr key={st.staffUserId} className="hover:bg-slate-50 transition-colors">
                          <td className="px-4 py-3 font-semibold text-slate-900">{st.staffName}</td>
                          <td className="px-4 py-3 text-slate-500">{st.email}</td>
                          <td className="px-4 py-3 text-right font-bold text-emerald-700">{st.tokensServed}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{st.avgServiceMinutes}m</td>
                          <td className="px-4 py-3 text-right text-slate-600 font-mono">{st.totalServingMinutes} min</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ------------------------------------------------------------- */}
        {/* TAB 4: REPORTS & CSV EXPORT */}
        {/* ------------------------------------------------------------- */}
        {activeTab === "reports" && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {/* Daily Report */}
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
                <div>
                  <div className="w-10 h-10 rounded-xl bg-teal-50 text-teal-600 flex items-center justify-center mb-3">
                    <Clock className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-slate-900 text-sm mb-1">{t("daily_report")}</h4>
                  <p className="text-xs text-slate-500 mb-4">
                    Day-by-day aggregated token volumes, wait times, and completion rates.
                  </p>
                </div>
                <button
                  onClick={() => handleExportCsv("daily")}
                  disabled={!!exportingReport}
                  className="w-full inline-flex items-center justify-center gap-2 py-2 px-3 bg-teal-600 hover:bg-teal-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors disabled:opacity-50"
                >
                  <Download className="w-4 h-4" />
                  {exportingReport === "daily" ? "Downloading..." : "Download Daily CSV"}
                </button>
              </div>

              {/* Queue Report */}
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
                <div>
                  <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center mb-3">
                    <Layers className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-slate-900 text-sm mb-1">{t("queue_report")}</h4>
                  <p className="text-xs text-slate-500 mb-4">
                    Queue-level throughput, active waiting, peak hours, and service duration.
                  </p>
                </div>
                <button
                  onClick={() => handleExportCsv("queues")}
                  disabled={!!exportingReport}
                  className="w-full inline-flex items-center justify-center gap-2 py-2 px-3 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors disabled:opacity-50"
                >
                  <Download className="w-4 h-4" />
                  {exportingReport === "queues" ? "Downloading..." : "Download Queues CSV"}
                </button>
              </div>

              {/* Service Report */}
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
                <div>
                  <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3">
                    <Activity className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-slate-900 text-sm mb-1">{t("service_report")}</h4>
                  <p className="text-xs text-slate-500 mb-4">
                    Cross-branch service performance, turnaround times, and cancellation metrics.
                  </p>
                </div>
                <button
                  onClick={() => handleExportCsv("services")}
                  disabled={!!exportingReport}
                  className="w-full inline-flex items-center justify-center gap-2 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors disabled:opacity-50"
                >
                  <Download className="w-4 h-4" />
                  {exportingReport === "services" ? "Downloading..." : "Download Services CSV"}
                </button>
              </div>

              {/* Executive Summary */}
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
                <div>
                  <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center mb-3">
                    <TrendingUp className="w-5 h-5" />
                  </div>
                  <h4 className="font-bold text-slate-900 text-sm mb-1">{t("summary_report")}</h4>
                  <p className="text-xs text-slate-500 mb-4">
                    High-level organization operational KPI metrics for executives and managers.
                  </p>
                </div>
                <button
                  onClick={() => handleExportCsv("summary")}
                  disabled={!!exportingReport}
                  className="w-full inline-flex items-center justify-center gap-2 py-2 px-3 bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors disabled:opacity-50"
                >
                  <Download className="w-4 h-4" />
                  {exportingReport === "summary" ? "Downloading..." : "Download Summary CSV"}
                </button>
              </div>
            </div>

            {/* Quick Preview Card */}
            {overview && (
              <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-3">
                  Report Scope Summary Preview
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                  <div>
                    <span className="text-slate-400 block">Date Filter:</span>
                    <span className="font-semibold text-slate-800">{fromDate.slice(0, 10)} to {toDate.slice(0, 10)}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">Scope:</span>
                    <span className="font-semibold text-slate-800">{branchId ? "Specific Branch" : "All Organization Branches"}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">Total Processed:</span>
                    <span className="font-semibold text-slate-800">{overview.totalTokens} tokens</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">Avg Wait:</span>
                    <span className="font-semibold text-slate-800">{overview.avgWaitMinutes} mins</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ------------------------------------------------------------- */}
        {/* TAB 5: AI OPERATIONAL INTELLIGENCE */}
        {/* ------------------------------------------------------------- */}
        {activeTab === "ai" && (
          <div className="space-y-6">
            {/* Demand Forecast Card */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                    <Cpu className="w-4 h-4 text-teal-600" />
                    {t("ai_demand_forecast")}
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {aiForecast?.message || "Predicting expected customer volume based on historical Poisson moving average."}
                  </p>
                </div>
                {aiForecast && (
                  <span className="text-xs px-2.5 py-1 rounded-full font-mono bg-teal-50 text-teal-800 border border-teal-200">
                    Model: {aiForecast.modelVersion} ({aiForecast.status})
                  </span>
                )}
              </div>

              {forecastLoading ? (
                <div className="h-48 flex items-center justify-center text-slate-400 text-xs">Calculating forecast...</div>
              ) : aiForecast && aiForecast.forecast && aiForecast.forecast.length > 0 ? (
                <div className="h-64 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={aiForecast.forecast} margin={{ top: 10, right: 20, bottom: 20, left: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                      <XAxis dataKey="hourOffset" stroke="#94a3b8" fontSize={11} tickFormatter={(val) => `+${val}h`} />
                      <YAxis stroke="#94a3b8" fontSize={11} label={{ value: "Predicted Tokens", angle: -90, position: "insideLeft", fontSize: 10, fill: "#94a3b8" }} />
                      <Tooltip
                        formatter={(val: any, name: any) => [val, name === "predictedCount" ? "Expected Volume" : name]}
                        labelFormatter={(label) => `Forecast: +${label} Hours`}
                        contentStyle={{ backgroundColor: "#ffffff", borderRadius: "8px", border: "1px solid #e2e8f0", fontSize: "12px" }}
                      />
                      <Bar dataKey="predictedCount" name="Expected Volume" fill="#0d9488" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="p-6 bg-slate-50 rounded-xl text-center text-slate-500 text-xs">
                  {aiForecast?.message || "Insufficient historical data to generate forecast for this queue/service."}
                </div>
              )}
            </div>

            {/* Operational Anomalies Card */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-amber-500" />
                  {t("ai_anomalies")}
                </h3>
                {aiAnomalies && (
                  <span className="text-xs px-2.5 py-1 rounded-full font-mono bg-slate-100 text-slate-700">
                    Model: {aiAnomalies.modelVersion}
                  </span>
                )}
              </div>

              {anomaliesLoading ? (
                <div className="p-6 text-center text-slate-400 text-xs">Detecting anomalies...</div>
              ) : !aiAnomalies || !aiAnomalies.anomalies || aiAnomalies.anomalies.length === 0 ? (
                <div className="p-6 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>{t("no_anomalies_detected")}</span>
                </div>
              ) : (
                <div className="space-y-3">
                  {aiAnomalies.anomalies.map((anom, idx) => (
                    <div
                      key={idx}
                      className={`p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                        anom.severity === "CRITICAL"
                          ? "bg-rose-50 border-rose-200 text-rose-900"
                          : anom.severity === "HIGH"
                          ? "bg-amber-50 border-amber-200 text-amber-900"
                          : "bg-slate-50 border-slate-200 text-slate-900"
                      }`}
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
                              anom.severity === "CRITICAL"
                                ? "bg-rose-600 text-white"
                                : anom.severity === "HIGH"
                                ? "bg-amber-500 text-white"
                                : "bg-slate-600 text-white"
                            }`}
                          >
                            {anom.severity}
                          </span>
                          <span className="font-bold text-xs">{anom.metricName}</span>
                          {anom.queueName && (
                            <span className="text-xs text-slate-500 font-normal">({anom.queueName})</span>
                          )}
                        </div>
                        <p className="text-xs">{anom.message}</p>
                      </div>
                      <div className="text-right sm:shrink-0 text-xs">
                        <div className="font-mono font-bold">
                          Observed: {anom.observedValue} (Base: {anom.baselineMean})
                        </div>
                        <div className="text-[11px] text-slate-500">
                          Dev Factor: {anom.deviationFactor.toFixed(2)}x
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Operational Recommendations Card */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <Lightbulb className="w-4 h-4 text-amber-500" />
                  {t("ai_recommendations")}
                </h3>
                {aiRecommendations && (
                  <span className="text-xs px-2.5 py-1 rounded-full font-mono bg-slate-100 text-slate-700">
                    Model: {aiRecommendations.modelVersion}
                  </span>
                )}
              </div>

              {recsLoading ? (
                <div className="p-6 text-center text-slate-400 text-xs">Generating recommendations...</div>
              ) : !aiRecommendations || !aiRecommendations.recommendations || aiRecommendations.recommendations.length === 0 ? (
                <div className="p-6 bg-slate-50 rounded-xl text-center text-slate-500 text-xs">
                  {t("no_recommendations_available")}
                </div>
              ) : (
                <div className="space-y-3">
                  {aiRecommendations.recommendations.map((rec) => (
                    <div
                      key={rec.id}
                      className="p-4 rounded-xl border border-slate-200 bg-slate-50/50 hover:bg-slate-50 transition-colors"
                    >
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
                              rec.priority === "HIGH" || rec.priority === "CRITICAL"
                                ? "bg-amber-500 text-white"
                                : "bg-teal-600 text-white"
                            }`}
                          >
                            {rec.priority}
                          </span>
                          <span className="font-bold text-xs text-slate-900">{rec.title}</span>
                        </div>
                        <span className="text-[10px] uppercase font-mono text-slate-400">{rec.category}</span>
                      </div>
                      <p className="text-xs text-slate-700 mt-1">{rec.description}</p>
                      <div className="mt-2 pt-2 border-t border-slate-200 text-[11px] text-teal-800 bg-teal-50/50 px-2.5 py-1.5 rounded-lg">
                        <strong>Evidence:</strong> {rec.evidence}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
