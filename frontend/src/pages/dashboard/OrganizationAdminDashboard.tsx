import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getQueues,
  openQueue,
  closeQueue,
  getQueueTokens,
  callNext,
  holdToken,
  resumeToken,
  skipToken,
  completeToken,
  getAdminOverview,
  QueueStatus,
  TokenStatus,
  QueueResponse,
  QueueTokenResponse,
  AdminOverviewDto,
} from "../../lib/apiClient";
import { useQueueWebSocket } from "../../lib/useQueueWebSocket";
import { Badge, BadgeVariant } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "../../components/ui/Card";
import { StatCard } from "../../components/ui/StatCard";
import { LiveIndicator } from "../../components/ui/LiveIndicator";
import {
  Building2,
  Stethoscope,
  Monitor,
  ListOrdered,
  Users,
  Activity,
  PhoneForwarded,
  PauseCircle,
  PlayCircle,
  CheckCircle,
  SkipForward,
  ChevronRight,
  AlertTriangle,
  BarChart3,
  QrCode,
  FileText,
  ShieldCheck,
  Clock,
} from "lucide-react";
import { QueueQRCodeModal } from "../../components/QueueQRCodeModal";
import { DocumentReviewModal } from "../../components/DocumentReviewModal";

const statusConfig: Record<TokenStatus, { variant: BadgeVariant; label: string }> = {
  [TokenStatus.WAITING]: { variant: "info", label: "Waiting" },
  [TokenStatus.CALLED]: { variant: "warning", label: "Called" },
  [TokenStatus.HOLD]: { variant: "warning", label: "On Hold" },
  [TokenStatus.COMPLETED]: { variant: "success", label: "Completed" },
  [TokenStatus.SKIPPED]: { variant: "neutral", label: "Skipped" },
  [TokenStatus.CANCELLED]: { variant: "danger", label: "Cancelled" },
};

export const OrganizationAdminDashboard: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [selectedQueueId, setSelectedQueueId] = useState<string | null>(null);
  const [qrModalQueue, setQrModalQueue] = useState<{ id: string; name: string } | null>(null);
  const [reviewModalToken, setReviewModalToken] = useState<QueueTokenResponse | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Real-time WebSocket live updates
  useQueueWebSocket({
    queueId: selectedQueueId ?? undefined,
  });

  const { data: overview } = useQuery<AdminOverviewDto>({
    queryKey: ["adminOverview"],
    queryFn: async () => {
      const res = await getAdminOverview();
      return res.success && res.data
        ? res.data
        : {
            totalBranches: 0,
            totalServices: 0,
            totalCounters: 0,
            totalQueues: 0,
            totalActiveTokens: 0,
            totalStaff: 0,
          };
    },
  });

  const {
    data: queues = [],
    isLoading: loadingQueues,
    error: queuesError,
    refetch: refetchQueues,
  } = useQuery<QueueResponse[]>({
    queryKey: ["queues"],
    queryFn: async () => {
      const result = await getQueues();
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load queues");
      }
      return result.data;
    },
  });

  const currentSelectedQueueId = selectedQueueId || (queues.length > 0 ? queues[0].id : null);

  const { data: tokens = [], isLoading: loadingTokens } = useQuery<QueueTokenResponse[]>({
    queryKey: ["queueTokens", currentSelectedQueueId],
    queryFn: async () => {
      if (!currentSelectedQueueId) return [];
      const result = await getQueueTokens(currentSelectedQueueId);
      return result.success && result.data ? result.data : [];
    },
    enabled: !!currentSelectedQueueId,
  });

  async function handleToggleQueueStatus(queue: QueueResponse) {
    setActionLoading("queue-status");
    setActionError(null);
    try {
      if (queue.status === QueueStatus.ACTIVE) {
        await closeQueue(queue.id);
      } else {
        await openQueue(queue.id);
      }
      await queryClient.invalidateQueries({ queryKey: ["queues"] });
      await queryClient.invalidateQueries({ queryKey: ["adminOverview"] });
    } catch {
      setActionError("Failed to update queue status");
    } finally {
      setActionLoading(null);
    }
  }

  async function handleAction(action: string, tokenId: string) {
    setActionLoading(tokenId || action);
    setActionError(null);
    try {
      let result;
      switch (action) {
        case "call-next":
          if (currentSelectedQueueId) {
            result = await callNext(currentSelectedQueueId);
          }
          break;
        case "hold":
          result = await holdToken(tokenId);
          break;
        case "resume":
          result = await resumeToken(tokenId);
          break;
        case "skip":
          result = await skipToken(tokenId);
          break;
        case "complete":
          result = await completeToken(tokenId);
          break;
      }
      if (result && !result.success) {
        setActionError(result.error?.message ?? "Action failed");
      }
      if (currentSelectedQueueId) {
        await queryClient.invalidateQueries({ queryKey: ["queueTokens", currentSelectedQueueId] });
        await queryClient.invalidateQueries({ queryKey: ["queues"] });
        await queryClient.invalidateQueries({ queryKey: ["adminOverview"] });
      }
    } catch {
      setActionError("Network error executing operation");
    } finally {
      setActionLoading(null);
    }
  }

  const selectedQueue = queues.find((q) => q.id === currentSelectedQueueId);
  const waitingTokens = tokens.filter((t) => t.status === TokenStatus.WAITING);

  return (
    <div className="space-y-8 pb-12">
      {/* Overview Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 border-b border-slate-200">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="purple" size="sm" className="font-mono uppercase font-bold">
              ADMINISTRATIVE CENTER
            </Badge>
            <LiveIndicator label="MONITORING" />
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("nav_overview")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Facility infrastructure, counter allocation, and real-time operational monitor.
          </p>
        </div>
        <div className="mt-4 sm:mt-0">
          <Link to="/admin/analytics">
            <Button variant="outline" size="sm" icon={<BarChart3 className="w-4 h-4 text-teal-600" />}>
              Open Analytics
            </Button>
          </Link>
        </div>
      </div>

      {(queuesError || actionError) && (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-4 text-xs text-rose-700 flex justify-between items-center shadow-xs">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{actionError || (queuesError instanceof Error ? queuesError.message : "Error")}</span>
          </div>
          <button
            onClick={() => {
              setActionError(null);
              refetchQueues();
            }}
            className="underline font-bold text-rose-600"
          >
            Retry
          </button>
        </div>
      )}

      {/* 6 KPI Stat Cards Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3.5">
        <StatCard
          title={t("total_branches")}
          value={overview?.totalBranches ?? 0}
          icon={<Building2 className="w-5 h-5 text-teal-600" />}
          accentColor="teal"
        />
        <StatCard
          title={t("total_services")}
          value={overview?.totalServices ?? 0}
          icon={<Stethoscope className="w-5 h-5 text-sky-600" />}
          accentColor="blue"
        />
        <StatCard
          title={t("total_counters")}
          value={overview?.totalCounters ?? 0}
          icon={<Monitor className="w-5 h-5 text-emerald-600" />}
          accentColor="emerald"
        />
        <StatCard
          title={t("total_queues")}
          value={overview?.totalQueues ?? 0}
          icon={<ListOrdered className="w-5 h-5 text-amber-600" />}
          accentColor="amber"
        />
        <StatCard
          title={t("total_active_tokens")}
          value={overview?.totalActiveTokens ?? 0}
          icon={<Activity className="w-5 h-5 text-rose-600" />}
          accentColor="rose"
        />
        <StatCard
          title={t("total_staff")}
          value={overview?.totalStaff ?? 0}
          icon={<Users className="w-5 h-5 text-purple-600" />}
          accentColor="purple"
        />
      </div>

      {/* Domain Quick Action Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        {[
          {
            to: "/admin/branches",
            icon: Building2,
            title: t("nav_branches"),
            desc: "Physical locations & addresses",
            color: "text-teal-600 bg-teal-50",
          },
          {
            to: "/admin/services",
            icon: Stethoscope,
            title: t("nav_services"),
            desc: "Departments & service lines",
            color: "text-sky-600 bg-sky-50",
          },
          {
            to: "/admin/counters",
            icon: Monitor,
            title: t("nav_counters"),
            desc: "Desks, staff & queue routing",
            color: "text-emerald-600 bg-emerald-50",
          },
          {
            to: "/admin/queues",
            icon: ListOrdered,
            title: t("nav_queues"),
            desc: "Queue capacity & priority flags",
            color: "text-amber-600 bg-amber-50",
          },
          {
            to: "/admin/staff",
            icon: Users,
            title: t("nav_staff"),
            desc: "User accounts & desk assignment",
            color: "text-purple-600 bg-purple-50",
          },
        ].map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.to}
              to={item.to}
              className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-card hover:shadow-card-hover hover:border-teal-400/80 transition-all group"
            >
              <div
                className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold ${item.color} group-hover:scale-105 transition-transform`}
              >
                <Icon className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-slate-900 mt-3 text-sm flex items-center justify-between">
                <span>{item.title}</span>
                <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-teal-600 transition-colors" />
              </h3>
              <p className="text-[11px] text-slate-400 mt-0.5">{item.desc}</p>
            </Link>
          );
        })}
      </div>

      {/* Live Operational Monitor Board */}
      <Card className="p-6">
        <div className="flex items-center justify-between pb-4 border-b border-slate-100">
          <div>
            <h2 className="text-base font-bold text-slate-900 tracking-tight">
              Live Queue Operations Monitor
            </h2>
            <p className="text-xs text-slate-500">
              Select any queue to inspect real-time tokens and manage queue states.
            </p>
          </div>
          <Link
            to="/admin/queues"
            className="text-xs font-bold text-teal-700 hover:text-teal-800 flex items-center gap-1"
          >
            <span>Manage Queues</span>
            <ChevronRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {loadingQueues ? (
          <div className="py-12 text-center text-xs text-slate-400">{t("loading")}</div>
        ) : queues.length === 0 ? (
          <div className="py-12 text-center text-xs text-slate-400">No queues configured yet.</div>
        ) : (
          <div className="mt-6 grid gap-6 lg:grid-cols-[260px_1fr]">
            {/* Queue Selector List */}
            <div className="space-y-1.5 max-h-96 overflow-y-auto pr-1">
              {queues.map((q) => (
                <button
                  key={q.id}
                  onClick={() => setSelectedQueueId(q.id)}
                  className={`w-full text-left px-3.5 py-3 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                    currentSelectedQueueId === q.id
                      ? "bg-teal-50 text-teal-900 border border-teal-200/80 shadow-xs font-bold"
                      : "text-slate-700 hover:bg-slate-50 border border-transparent"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 truncate">
                      <span className="truncate">{q.name}</span>
                      {q.requiresDocumentVerification && (
                        <span className="text-[9px] font-bold bg-amber-100 text-amber-800 px-1.5 py-0.5 rounded border border-amber-200 shrink-0">
                          Docs Req
                        </span>
                      )}
                    </div>
                    <span
                      className={`w-2 h-2 rounded-full flex-shrink-0 ml-2 ${
                        q.status === QueueStatus.ACTIVE ? "bg-emerald-500" : "bg-slate-300"
                      }`}
                    />
                  </div>
                </button>
              ))}
            </div>

            {/* Selected Queue Monitor Panel */}
            {selectedQueue ? (
              <div className="space-y-4">
                <div className="bg-slate-50 rounded-2xl border border-slate-200 p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-extrabold text-slate-900 text-sm">{selectedQueue.name}</h3>
                      {selectedQueue.requiresDocumentVerification && (
                        <span className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                          <ShieldCheck className="w-3 h-3 text-amber-600" />
                          AI Doc Verification
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5">
                      Serving:{" "}
                      <strong className="font-mono text-slate-900 font-bold">
                        {selectedQueue.tokenPrefix}-{selectedQueue.currentTokenNumber || "None"}
                      </strong>{" "}
                      &bull; Status:{" "}
                      <span
                        className={
                          selectedQueue.status === QueueStatus.ACTIVE
                            ? "text-emerald-700 font-bold"
                            : "text-slate-500"
                        }
                      >
                        {selectedQueue.status}
                      </span>
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      onClick={() => setQrModalQueue({ id: selectedQueue.id, name: selectedQueue.name })}
                      variant="outline"
                      size="xs"
                      icon={<QrCode className="w-3.5 h-3.5 text-teal-600" />}
                    >
                      QR Code
                    </Button>
                    <Button
                      onClick={() => handleToggleQueueStatus(selectedQueue)}
                      variant="outline"
                      size="xs"
                      className={
                        selectedQueue.status === QueueStatus.ACTIVE
                          ? "text-rose-600 border-rose-200 hover:bg-rose-50"
                          : "text-emerald-600 border-emerald-200 hover:bg-emerald-50"
                      }
                    >
                      {selectedQueue.status === QueueStatus.ACTIVE
                        ? t("close_queue")
                        : t("open_queue")}
                    </Button>
                    <Button
                      onClick={() => handleAction("call-next", "")}
                      disabled={actionLoading !== null || waitingTokens.length === 0}
                      variant="primary"
                      size="xs"
                      icon={<PhoneForwarded className="w-3.5 h-3.5" />}
                    >
                      {t("call_next")}
                    </Button>
                  </div>
                </div>

                {/* Queue Active Tokens List */}
                <div className="border border-slate-200 rounded-2xl overflow-hidden shadow-xs">
                  <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-xs font-bold text-slate-700 flex items-center justify-between">
                    <span>
                      Active Tokens (
                      {
                        tokens.filter(
                          (t) =>
                            t.status !== TokenStatus.COMPLETED &&
                            t.status !== TokenStatus.SKIPPED &&
                            t.status !== TokenStatus.CANCELLED
                        ).length
                      }
                      )
                    </span>
                  </div>

                  {loadingTokens ? (
                    <div className="p-6 text-center text-xs text-slate-400">{t("loading")}</div>
                  ) : tokens.filter(
                      (t) =>
                        t.status !== TokenStatus.COMPLETED &&
                        t.status !== TokenStatus.SKIPPED &&
                        t.status !== TokenStatus.CANCELLED
                    ).length === 0 ? (
                    <div className="p-6 text-center text-xs text-slate-400">
                      No active tokens in this queue.
                    </div>
                  ) : (
                    <div className="divide-y divide-slate-100 max-h-60 overflow-y-auto">
                      {tokens
                        .filter(
                          (t) =>
                            t.status !== TokenStatus.COMPLETED &&
                            t.status !== TokenStatus.SKIPPED &&
                            t.status !== TokenStatus.CANCELLED
                        )
                        .map((tok) => {
                          const sc =
                            statusConfig[tok.status] || statusConfig[TokenStatus.WAITING];
                          const isLoading = actionLoading === tok.id;
                          return (
                            <div
                              key={tok.id}
                              className="px-4 py-3 flex items-center justify-between gap-3 text-xs"
                            >
                              <div className="flex items-center gap-2.5">
                                <span className="font-mono font-bold text-slate-900 text-sm">
                                  {selectedQueue.tokenPrefix}-{tok.tokenNumber}
                                </span>
                                <Badge variant={sc.variant} size="sm">
                                  {sc.label}
                                </Badge>
                                {selectedQueue.requiresDocumentVerification && (
                                  <span
                                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold flex items-center gap-1 border ${
                                      tok.documentVerificationStatus === "VERIFIED"
                                        ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                                        : tok.documentVerificationStatus === "PENDING"
                                        ? "bg-amber-50 text-amber-800 border-amber-200"
                                        : "bg-rose-50 text-rose-800 border-rose-200"
                                    }`}
                                  >
                                    {tok.documentVerificationStatus === "VERIFIED" ? (
                                      <>
                                        <CheckCircle className="w-3 h-3 text-emerald-600" /> Docs Verified
                                      </>
                                    ) : tok.documentVerificationStatus === "PENDING" ? (
                                      <>
                                        <Clock className="w-3 h-3 text-amber-600" /> Pending Review
                                      </>
                                    ) : (
                                      <>
                                        <AlertTriangle className="w-3 h-3 text-rose-600" /> Docs Missing
                                      </>
                                    )}
                                  </span>
                                )}
                              </div>
                              <div className="flex items-center gap-1.5">
                                {selectedQueue.requiresDocumentVerification && (
                                  <Button
                                    onClick={() => setReviewModalToken(tok)}
                                    variant="outline"
                                    size="xs"
                                    className="text-teal-700 border-teal-200 hover:bg-teal-50"
                                    icon={<FileText className="w-3 h-3" />}
                                  >
                                    Docs ({tok.documentCount ?? 0})
                                  </Button>
                                )}
                                {tok.status === TokenStatus.CALLED && (
                                  <>
                                    <Button
                                      onClick={() => handleAction("hold", tok.id)}
                                      disabled={isLoading}
                                      variant="outline"
                                      size="xs"
                                    >
                                      Hold
                                    </Button>
                                    <Button
                                      onClick={() => handleAction("complete", tok.id)}
                                      disabled={isLoading}
                                      variant="primary"
                                      size="xs"
                                      className="bg-emerald-600 hover:bg-emerald-700"
                                    >
                                      Complete
                                    </Button>
                                    <Button
                                      onClick={() => handleAction("skip", tok.id)}
                                      disabled={isLoading}
                                      variant="ghost"
                                      size="xs"
                                    >
                                      Skip
                                    </Button>
                                  </>
                                )}
                                {tok.status === TokenStatus.HOLD && (
                                  <Button
                                    onClick={() => handleAction("resume", tok.id)}
                                    disabled={isLoading}
                                    variant="primary"
                                    size="xs"
                                  >
                                    Resume
                                  </Button>
                                )}
                                {tok.status === TokenStatus.WAITING && (
                                  <Button
                                    onClick={() => handleAction("skip", tok.id)}
                                    disabled={isLoading}
                                    variant="ghost"
                                    size="xs"
                                  >
                                    Skip
                                  </Button>
                                )}
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="p-8 text-center text-xs text-slate-400">Select a queue to monitor.</div>
            )}
          </div>
        )}
      </Card>

      {/* Queue QR Code Modal */}
      {qrModalQueue && (
        <QueueQRCodeModal
          isOpen={!!qrModalQueue}
          onClose={() => setQrModalQueue(null)}
          queueId={qrModalQueue.id}
          queueName={qrModalQueue.name}
        />
      )}

      {/* Document Review Modal for Admin */}
      {reviewModalToken && (
        <DocumentReviewModal
          isOpen={!!reviewModalToken}
          tokenId={reviewModalToken.id}
          tokenNumber={`${selectedQueue?.tokenPrefix || ""}-${reviewModalToken.tokenNumber}`}
          onClose={() => setReviewModalToken(null)}
          onDocumentReviewed={() => {
            queryClient.invalidateQueries({ queryKey: ["tokens", selectedQueueId] });
            queryClient.invalidateQueries({ queryKey: ["queues"] });
          }}
        />
      )}
    </div>
  );
};
export default OrganizationAdminDashboard;
