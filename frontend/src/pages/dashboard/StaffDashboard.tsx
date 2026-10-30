import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getStaffAssignments,
  getQueues,
  getQueueTokens,
  callNext,
  callNextByCounter,
  holdToken,
  resumeToken,
  skipToken,
  completeToken,
  TokenStatus,
  StaffUserDto,
  QueueResponse,
  QueueTokenResponse,
} from "../../lib/apiClient";
import { useQueueWebSocket } from "../../lib/useQueueWebSocket";
import { Badge, BadgeVariant } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card, CardContent } from "../../components/ui/Card";
import { EmptyState } from "../../components/ui/EmptyState";
import { LiveIndicator } from "../../components/ui/LiveIndicator";
import {
  Monitor,
  Building2,
  PhoneForwarded,
  PauseCircle,
  PlayCircle,
  CheckCircle,
  SkipForward,
  AlertTriangle,
  Users,
  Clock,
  ChevronRight,
  Sparkles,
  QrCode,
  ShieldCheck,
  FileText,
  AlertCircle,
} from "lucide-react";
import { QueueQRCodeModal } from "../../components/QueueQRCodeModal";
import { DocumentReviewModal } from "../../components/DocumentReviewModal";

const statusBadgeConfig: Record<TokenStatus, { variant: BadgeVariant; label: string }> = {
  [TokenStatus.WAITING]: { variant: "info", label: "Waiting" },
  [TokenStatus.CALLED]: { variant: "warning", label: "Called" },
  [TokenStatus.HOLD]: { variant: "warning", label: "On Hold" },
  [TokenStatus.COMPLETED]: { variant: "success", label: "Completed" },
  [TokenStatus.SKIPPED]: { variant: "neutral", label: "Skipped" },
  [TokenStatus.CANCELLED]: { variant: "danger", label: "Cancelled" },
};

export const StaffDashboard: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [selectedQueueId, setSelectedQueueId] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [qrModalQueue, setQrModalQueue] = useState<{ id: string; name: string } | null>(null);
  const [reviewModalToken, setReviewModalToken] = useState<{ id: string; tokenNumber: string; customerName?: string } | null>(null);
  const [docBlockedNotice, setDocBlockedNotice] = useState<string | null>(null);

  // Fetch staff assignment (branch, counter, assigned queues)
  const { data: staffData } = useQuery<StaffUserDto>({
    queryKey: ["staffAssignments"],
    queryFn: async () => {
      const res = await getStaffAssignments();
      if (!res.success || !res.data) throw new Error("Failed to load staff assignments");
      return res.data;
    },
  });

  // Fetch organization queues
  const { data: allQueues = [] } = useQuery<QueueResponse[]>({
    queryKey: ["queues"],
    queryFn: async () => {
      const res = await getQueues();
      return res.success && res.data ? res.data : [];
    },
  });

  // Filter queues assigned to this staff/counter, or show all if unconstrained
  const relevantQueues =
    staffData?.assignedQueueIds && staffData.assignedQueueIds.length > 0
      ? allQueues.filter((q) => staffData.assignedQueueIds?.includes(q.id))
      : allQueues;

  const currentSelectedQueueId =
    selectedQueueId || (relevantQueues.length > 0 ? relevantQueues[0].id : null);

  // Real-time WebSocket connection
  useQueueWebSocket({
    queueId: currentSelectedQueueId ?? undefined,
  });

  // Fetch tokens for currently selected queue
  const { data: tokens = [], isLoading: loadingTokens } = useQuery<QueueTokenResponse[]>({
    queryKey: ["queueTokens", currentSelectedQueueId],
    queryFn: async () => {
      if (!currentSelectedQueueId) return [];
      const res = await getQueueTokens(currentSelectedQueueId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!currentSelectedQueueId,
  });

  const selectedQueue = allQueues.find((q) => q.id === currentSelectedQueueId);

  // Active serving token (called or hold)
  const servingToken = tokens.find(
    (t) => t.status === TokenStatus.CALLED || t.status === TokenStatus.HOLD
  );
  const waitingTokens = tokens.filter((t) => t.status === TokenStatus.WAITING);

  const handleCallNext = async (forceCall = false) => {
    setActionLoading("call-next");
    setActionError(null);
    setDocBlockedNotice(null);
    try {
      let res;
      if (staffData?.activeCounterId) {
        res = await callNextByCounter(staffData.activeCounterId, forceCall);
      } else if (currentSelectedQueueId) {
        res = await callNext(currentSelectedQueueId, undefined, forceCall);
      }

      if (res && !res.success) {
        const errorMsg = res.error?.message || "Failed to call next token";
        if (errorMsg.includes("AI Document Verification is required") || errorMsg.includes("Document Verification")) {
          setDocBlockedNotice(errorMsg);
        } else {
          setActionError(errorMsg);
        }
      }

      if (currentSelectedQueueId) {
        await queryClient.invalidateQueries({
          queryKey: ["queueTokens", currentSelectedQueueId],
        });
      }
      await queryClient.invalidateQueries({ queryKey: ["queues"] });
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Error calling next token");
    } finally {
      setActionLoading(null);
    }
  };

  const handleTokenAction = async (
    action: "hold" | "resume" | "skip" | "complete",
    tokenId: string
  ) => {
    setActionLoading(tokenId);
    setActionError(null);
    try {
      let res;
      switch (action) {
        case "hold":
          res = await holdToken(tokenId);
          break;
        case "resume":
          res = await resumeToken(tokenId);
          break;
        case "skip":
          res = await skipToken(tokenId);
          break;
        case "complete":
          res = await completeToken(tokenId);
          break;
      }

      if (res && !res.success) {
        setActionError(res.error?.message || "Action failed");
      }

      if (currentSelectedQueueId) {
        await queryClient.invalidateQueries({
          queryKey: ["queueTokens", currentSelectedQueueId],
        });
      }
      await queryClient.invalidateQueries({ queryKey: ["queues"] });
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="space-y-6 pb-12">
      {/* Staff Operator Desk Banner */}
      <div className="bg-slate-900 rounded-3xl p-6 text-white shadow-card flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="primary" size="sm" className="font-mono">
              OPERATOR CONSOLE
            </Badge>
            <LiveIndicator label="ONLINE" />
          </div>
          <h1 className="text-xl sm:text-2xl font-black mt-2 tracking-tight">
            {user?.fullName}
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">{user?.email}</p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="bg-slate-800/80 border border-slate-700/80 rounded-2xl px-4 py-2.5 text-xs shadow-xs">
            <span className="text-slate-400 block text-[11px] font-semibold uppercase tracking-wider">
              {t("my_branch")}
            </span>
            <span className="font-bold text-slate-100 text-sm flex items-center gap-1.5 mt-0.5">
              <Building2 className="w-3.5 h-3.5 text-teal-400" />
              <span>{staffData?.branchName || "Central Branch"}</span>
            </span>
          </div>

          <div className="bg-slate-800/80 border border-slate-700/80 rounded-2xl px-4 py-2.5 text-xs shadow-xs">
            <span className="text-slate-400 block text-[11px] font-semibold uppercase tracking-wider">
              {t("my_counter")}
            </span>
            <span className="font-bold text-teal-400 text-sm flex items-center gap-1.5 mt-0.5">
              <Monitor className="w-3.5 h-3.5 text-teal-400" />
              <span>{staffData?.activeCounterName || "Counter 1"}</span>
            </span>
          </div>
        </div>
      </div>

      {actionError && (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-4 text-xs text-rose-700 flex items-center justify-between shadow-xs">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{actionError}</span>
          </div>
          <button onClick={() => setActionError(null)} className="font-bold p-1 text-rose-500">
            ✕
          </button>
        </div>
      )}

      {/* Main Operational Two-Column Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column (5 Cols): Active Serving Desk */}
        <div className="lg:col-span-5 space-y-6">
          <Card className="overflow-hidden border-2 border-teal-200/80 shadow-elevated">
            <div className="bg-teal-50/60 px-6 py-4 border-b border-teal-100 flex items-center justify-between">
              <span className="text-xs font-bold text-teal-950 uppercase tracking-wider">
                {t("serving_now")}
              </span>
              {servingToken && (
                <Badge
                  variant={statusBadgeConfig[servingToken.status].variant}
                  size="sm"
                  dot
                  className="uppercase font-bold"
                >
                  {statusBadgeConfig[servingToken.status].label}
                </Badge>
              )}
            </div>

            <CardContent className="p-6 text-center space-y-6">
              {servingToken ? (
                <div>
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest block mb-1">
                    Current Active Token
                  </span>
                  <div className="text-6xl font-black font-mono text-slate-900 tracking-tight py-2 select-all">
                    {servingToken.tokenNumber}
                  </div>
                  <p className="text-xs text-slate-500 font-medium mt-1">
                    Queue: <strong className="text-slate-800">{selectedQueue?.name}</strong>
                  </p>

                  {/* Operational Action Buttons */}
                  <div className="grid grid-cols-2 gap-2.5 mt-6">
                    {servingToken.status === TokenStatus.CALLED ? (
                      <>
                        <Button
                          onClick={() => handleTokenAction("hold", servingToken.id)}
                          loading={actionLoading === servingToken.id}
                          variant="outline"
                          size="md"
                          icon={<PauseCircle className="w-4 h-4 text-amber-600" />}
                          className="border-amber-200 text-amber-800 hover:bg-amber-50"
                        >
                          {t("hold_token")}
                        </Button>
                        <Button
                          onClick={() => handleTokenAction("complete", servingToken.id)}
                          loading={actionLoading === servingToken.id}
                          variant="primary"
                          size="md"
                          icon={<CheckCircle className="w-4 h-4" />}
                          className="bg-emerald-600 hover:bg-emerald-700 border-emerald-600"
                        >
                          {t("complete_token")}
                        </Button>
                        <Button
                          onClick={() => handleTokenAction("skip", servingToken.id)}
                          loading={actionLoading === servingToken.id}
                          variant="ghost"
                          size="sm"
                          icon={<SkipForward className="w-4 h-4" />}
                          className="col-span-2 text-slate-500 hover:text-slate-700"
                        >
                          {t("skip_token")}
                        </Button>
                      </>
                    ) : (
                      <Button
                        onClick={() => handleTokenAction("resume", servingToken.id)}
                        loading={actionLoading === servingToken.id}
                        variant="primary"
                        size="md"
                        fullWidth
                        icon={<PlayCircle className="w-4 h-4" />}
                        className="col-span-2"
                      >
                        {t("resume_token")}
                      </Button>
                    )}
                  </div>

                  {/* Document Review for Active Serving Token */}
                  <div className="pt-3 border-t border-slate-100 flex justify-center">
                    <Button
                      onClick={() => setReviewModalToken({ id: servingToken.id, tokenNumber: servingToken.tokenNumber })}
                      variant="outline"
                      size="xs"
                      icon={<ShieldCheck className="w-3.5 h-3.5 text-teal-600" />}
                      className="border-slate-200 text-slate-700 hover:bg-slate-50 font-medium"
                    >
                      Inspect Documents &amp; AI Verdict
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="py-12">
                  <div className="w-14 h-14 rounded-2xl bg-slate-50 border border-slate-200 text-slate-400 flex items-center justify-center mx-auto mb-3">
                    <Users className="w-6 h-6" />
                  </div>
                  <h3 className="text-sm font-bold text-slate-800 mb-1">
                    {t("no_token_serving")}
                  </h3>
                  <p className="text-xs text-slate-400 max-w-xs mx-auto">
                    Click "Call Next Customer" below to serve the next waiting token.
                  </p>
                </div>
              )}

              {/* Document Gatekeeping Warning Notice */}
              {docBlockedNotice && (
                <div className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 text-left space-y-2.5">
                  <div className="flex items-start gap-2">
                    <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                    <div>
                      <h4 className="text-xs font-bold text-amber-900">
                        Document Verification Required
                      </h4>
                      <p className="text-xs text-amber-800 mt-0.5 leading-snug">
                        {docBlockedNotice}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 pt-1">
                    {waitingTokens.length > 0 && (
                      <Button
                        size="xs"
                        variant="outline"
                        onClick={() =>
                          setReviewModalToken({
                            id: waitingTokens[0].id,
                            tokenNumber: waitingTokens[0].tokenNumber,
                          })
                        }
                        className="text-xs border-amber-300 text-amber-900 hover:bg-amber-100/60"
                      >
                        Review Documents
                      </Button>
                    )}
                    <Button
                      size="xs"
                      variant="amber"
                      onClick={() => handleCallNext(true)}
                      className="text-xs bg-amber-600 hover:bg-amber-700 text-white"
                    >
                      Force Call (Staff Override)
                    </Button>
                  </div>
                </div>
              )}

              {/* Big Primary Call Next Button */}
              <div className="pt-4 border-t border-slate-100">
                <Button
                  onClick={() => handleCallNext(false)}
                  loading={actionLoading === "call-next"}
                  variant="primary"
                  size="lg"
                  fullWidth
                  icon={<PhoneForwarded className="w-5 h-5" />}
                  className="bg-teal-600 hover:bg-teal-700 shadow-md font-bold text-base py-3.5"
                >
                  {t("call_next")}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right Column (7 Cols): Queue Selector & Waiting List */}
        <div className="lg:col-span-7 space-y-6">
          <Card className="p-6">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wider">
                Assigned Queues
              </h2>
              {selectedQueue && (
                <Button
                  onClick={() =>
                    setQrModalQueue({
                      id: selectedQueue.id,
                      name: selectedQueue.name,
                    })
                  }
                  variant="outline"
                  size="xs"
                  icon={<QrCode className="w-3.5 h-3.5 text-teal-600" />}
                >
                  Queue QR Code
                </Button>
              )}
            </div>

            {/* Assigned Queues Segmented Tabs */}
            <div className="flex flex-wrap gap-2 pb-4 border-b border-slate-100">
              {relevantQueues.map((q) => {
                const isSelected = q.id === currentSelectedQueueId;
                return (
                  <button
                    key={q.id}
                    onClick={() => setSelectedQueueId(q.id)}
                    className={`px-4 py-2.5 rounded-xl text-xs font-bold transition-all flex items-center gap-2 cursor-pointer ${
                      isSelected
                        ? "bg-teal-600 text-white shadow-xs"
                        : "bg-slate-50 hover:bg-slate-100 text-slate-700 border border-slate-200"
                    }`}
                  >
                    <span>{q.name}</span>
                    <span
                      className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                        isSelected ? "bg-teal-700 text-teal-100" : "bg-slate-200 text-slate-600"
                      }`}
                    >
                      {q.tokenPrefix}
                    </span>
                  </button>
                );
              })}
            </div>

            {/* Waiting Tokens List */}
            <div className="mt-6">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                    {t("waiting_queue")} ({waitingTokens.length})
                  </h3>
                </div>
                <span className="text-[11px] text-slate-400 font-medium">
                  Ordered by Priority &amp; Arrival
                </span>
              </div>

              {loadingTokens ? (
                <div className="py-8 text-center text-xs text-slate-400">Loading queue...</div>
              ) : waitingTokens.length === 0 ? (
                <div className="py-12 text-center text-xs text-slate-400 bg-slate-50 rounded-2xl border border-dashed border-slate-200">
                  No customers currently waiting in this queue.
                </div>
              ) : (
                <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto pr-1">
                  {waitingTokens.map((tok, idx) => (
                    <div
                      key={tok.id}
                      className="py-3 px-3 flex items-center justify-between hover:bg-slate-50/80 rounded-xl transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <span className="text-xs font-mono font-bold text-slate-400 w-6">
                          #{idx + 1}
                        </span>
                        <span className="font-mono text-base font-extrabold text-slate-900">
                          {tok.tokenNumber}
                        </span>
                        {tok.priority && tok.priority !== "NORMAL" && (
                          <Badge
                            variant={tok.priority === "VIP" ? "purple" : "warning"}
                            size="sm"
                            className="font-bold"
                          >
                            {tok.priority}
                          </Badge>
                        )}
                      </div>

                      <div className="flex items-center gap-2">
                        <Button
                          onClick={() =>
                            setReviewModalToken({
                              id: tok.id,
                              tokenNumber: tok.tokenNumber,
                            })
                          }
                          variant="outline"
                          size="xs"
                          icon={<FileText className="w-3 h-3 text-slate-500" />}
                          className="border-slate-200 text-slate-600 hover:bg-slate-100"
                        >
                          Docs
                        </Button>
                        <Button
                          onClick={() => handleTokenAction("skip", tok.id)}
                          loading={actionLoading === tok.id}
                          variant="ghost"
                          size="xs"
                          className="text-slate-500 hover:text-slate-700"
                        >
                          {t("skip_token")}
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>

      {/* QR Code Modal */}
      {qrModalQueue && (
        <QueueQRCodeModal
          isOpen={!!qrModalQueue}
          onClose={() => setQrModalQueue(null)}
          queueId={qrModalQueue.id}
          queueName={qrModalQueue.name}
        />
      )}

      {/* Document Review Modal */}
      {reviewModalToken && (
        <DocumentReviewModal
          isOpen={!!reviewModalToken}
          tokenId={reviewModalToken.id}
          tokenNumber={reviewModalToken.tokenNumber}
          customerName={reviewModalToken.customerName}
          onClose={() => setReviewModalToken(null)}
          onDocumentReviewed={() => {
            if (currentSelectedQueueId) {
              queryClient.invalidateQueries({
                queryKey: ["queueTokens", currentSelectedQueueId],
              });
            }
            queryClient.invalidateQueries({ queryKey: ["queues"] });
          }}
          onForceCall={() => {
            setReviewModalToken(null);
            handleCallNext(true);
          }}
        />
      )}
    </div>
  );
};
export default StaffDashboard;
