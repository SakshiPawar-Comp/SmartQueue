import React, { useState } from "react";
import { useParams, useNavigate, Navigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../context/AuthContext";
import { canManageQueues } from "../lib/roles";
import {
  getQueue,
  generateToken,
  getQueueTokens,
  callNext,
  holdToken,
  resumeToken,
  skipToken,
  completeToken,
  openQueue,
  closeQueue,
  QueueResponse,
  QueueTokenResponse,
} from "../lib/apiClient";
import { QueueStatus, TokenStatus } from "../types/queue";
import { Header } from "../components/Header";
import { useQueueWebSocket } from "../lib/useQueueWebSocket";
import { QueueQRCodeModal } from "../components/QueueQRCodeModal";
import { PreJoinDocumentModal } from "../components/PreJoinDocumentModal";
import { Badge, BadgeVariant } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent } from "../components/ui/Card";
import { LiveIndicator } from "../components/ui/LiveIndicator";
import {
  QrCode,
  ArrowLeft,
  Users,
  Clock,
  PhoneForwarded,
  PauseCircle,
  PlayCircle,
  CheckCircle,
  SkipForward,
  AlertTriangle,
  Ticket,
  ShieldCheck,
} from "lucide-react";

const tokenStatusConfig: Record<TokenStatus, { variant: BadgeVariant; label: string }> = {
  [TokenStatus.WAITING]: { variant: "info", label: "Waiting" },
  [TokenStatus.CALLED]: { variant: "warning", label: "Called" },
  [TokenStatus.SERVING]: { variant: "primary", label: "Serving" },
  [TokenStatus.HOLD]: { variant: "warning", label: "On Hold" },
  [TokenStatus.COMPLETED]: { variant: "success", label: "Completed" },
  [TokenStatus.SKIPPED]: { variant: "neutral", label: "Skipped" },
  [TokenStatus.CANCELLED]: { variant: "danger", label: "Cancelled" },
  [TokenStatus.EXPIRED]: { variant: "neutral", label: "Expired" },
};

export const QueueDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { user, isLoading: authLoading } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showQrModal, setShowQrModal] = useState(false);
  const [showPreJoinModal, setShowPreJoinModal] = useState(false);

  // Live WebSocket updates
  useQueueWebSocket({
    queueId: id,
  });

  const isStaff = canManageQueues(user);

  const { isLoading, data: queue, error } = useQuery<QueueResponse>({
    queryKey: ["queue", id],
    queryFn: async () => {
      const result = await getQueue(id!);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load queue");
      }
      return result.data;
    },
    enabled: !!id && !authLoading,
  });

  const { data: tokens = [] } = useQuery<QueueTokenResponse[]>({
    queryKey: ["queueTokens", id],
    queryFn: async () => {
      const result = await getQueueTokens(id!);
      return result.success && result.data ? result.data : [];
    },
    enabled: !!id && isStaff,
  });

  const joinMutation = useMutation({
    mutationFn: async () => {
      const result = await generateToken(id!);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Unable to generate token");
      }
      return result.data;
    },
    onSuccess: (token) => {
      queryClient.invalidateQueries({ queryKey: ["queue", id] });
      navigate(`/token/${token.id}`);
    },
  });

  async function handleToggleStatus() {
    if (!queue) return;
    setActionLoading("status");
    setActionError(null);
    try {
      if (queue.status === QueueStatus.ACTIVE) {
        await closeQueue(queue.id);
      } else {
        await openQueue(queue.id);
      }
      await queryClient.invalidateQueries({ queryKey: ["queue", id] });
      await queryClient.invalidateQueries({ queryKey: ["queues"] });
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
      let res;
      switch (action) {
        case "call-next":
          res = await callNext(id!);
          break;
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
        setActionError(res.error?.message ?? "Operation failed");
      }
      await queryClient.invalidateQueries({ queryKey: ["queue", id] });
      await queryClient.invalidateQueries({ queryKey: ["queueTokens", id] });
    } catch {
      setActionError("Network error executing operation");
    } finally {
      setActionLoading(null);
    }
  }

  if (authLoading) return null;
  if (!user) return <Navigate to="/login" replace />;

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Header />
        <div className="flex items-center justify-center py-24">
          <div className="animate-spin h-8 w-8 border-4 border-teal-600 border-t-transparent rounded-full" />
        </div>
      </div>
    );
  }

  if (error || !queue) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Header />
        <div className="max-w-md mx-auto text-center py-16 px-4">
          <h2 className="text-lg font-bold text-slate-900 mb-2">Queue Not Found</h2>
          <p className="text-xs text-slate-500 mb-6">
            {error instanceof Error ? error.message : "Queue could not be loaded."}
          </p>
          <Button onClick={() => navigate("/dashboard")} variant="primary" size="sm">
            Back to Dashboard
          </Button>
        </div>
      </div>
    );
  }

  const isActive = queue.status === QueueStatus.ACTIVE;
  const activeTokens =
    tokens?.filter(
      (t) =>
        t.status !== TokenStatus.COMPLETED &&
        t.status !== TokenStatus.SKIPPED &&
        t.status !== TokenStatus.CANCELLED
    ) ?? [];
  const waitingCount = activeTokens.filter((t) => t.status === TokenStatus.WAITING).length;

  return (
    <div className="min-h-screen bg-slate-50 pb-16">
      <Header />
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        {/* Top bar */}
        <div className="flex items-center justify-between">
          <button
            onClick={() => navigate("/dashboard")}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back to Dashboard</span>
          </button>
          <LiveIndicator label="LIVE OPERATIONAL" />
        </div>

        {actionError && (
          <div className="p-4 bg-rose-50 border border-rose-200 text-xs text-rose-700 rounded-2xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{actionError}</span>
          </div>
        )}

        <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
          {/* Main content */}
          <div className="space-y-6">
            <Card className="p-6 sm:p-8">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div>
                  <h1 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                    {queue.name}
                  </h1>
                  <p className="text-xs text-slate-500 mt-1 flex flex-wrap items-center gap-1.5">
                    <span>
                      Token prefix:{" "}
                      <span className="font-mono font-bold text-slate-800">{queue.tokenPrefix}</span>
                    </span>
                    <span>&bull; Status:</span>
                    <span
                      className={`font-bold ${
                        isActive ? "text-emerald-700" : "text-slate-500"
                      }`}
                    >
                      {queue.status}
                    </span>
                    {queue.requiresDocumentVerification && (
                      <span className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                        <ShieldCheck className="w-3 h-3 text-amber-600" /> AI Document Verification Required
                      </span>
                    )}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {isStaff && (
                    <Button
                      onClick={() => setShowQrModal(true)}
                      variant="outline"
                      size="xs"
                      icon={<QrCode className="w-3.5 h-3.5 text-teal-600" />}
                    >
                      QR &amp; Share
                    </Button>
                  )}
                  {isStaff && (
                    <Button
                      onClick={handleToggleStatus}
                      loading={actionLoading === "status"}
                      variant="outline"
                      size="xs"
                      className={
                        isActive
                          ? "text-rose-600 border-rose-200 hover:bg-rose-50"
                          : "text-emerald-600 border-emerald-200 hover:bg-emerald-50"
                      }
                    >
                      {isActive ? "Close Queue" : "Open Queue"}
                    </Button>
                  )}
                </div>
              </div>

              {queue.description && (
                <p className="text-xs text-slate-600 mb-6 leading-relaxed">{queue.description}</p>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div className="bg-slate-50 rounded-2xl p-4 border border-slate-100 text-center">
                  <span className="text-[11px] text-slate-400 font-bold uppercase tracking-wider block mb-1">
                    Current Serving
                  </span>
                  <div className="text-2xl font-black font-mono text-slate-900">
                    {queue.currentTokenNumber ? `${queue.tokenPrefix}-${queue.currentTokenNumber}` : "-"}
                  </div>
                </div>
                <div className="bg-slate-50 rounded-2xl p-4 border border-slate-100 text-center">
                  <span className="text-[11px] text-slate-400 font-bold uppercase tracking-wider block mb-1">
                    Waiting in Line
                  </span>
                  <div className="text-2xl font-black text-teal-700">{waitingCount}</div>
                </div>
              </div>
            </Card>

            {/* Staff: Token management */}
            {isStaff && (
              <Card className="overflow-hidden">
                <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
                  <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                    Queue Tokens ({activeTokens.length})
                  </h3>
                  <Button
                    onClick={() => handleAction("call-next", "")}
                    disabled={actionLoading !== null || waitingCount === 0}
                    variant="primary"
                    size="xs"
                    icon={<PhoneForwarded className="w-3.5 h-3.5" />}
                  >
                    Call Next
                  </Button>
                </div>

                {activeTokens.length === 0 ? (
                  <div className="p-8 text-center text-xs text-slate-400">No active tokens.</div>
                ) : (
                  <div className="divide-y divide-slate-100 max-h-80 overflow-y-auto">
                    {activeTokens.map((token) => {
                      const sc =
                        tokenStatusConfig[token.status] || tokenStatusConfig[TokenStatus.WAITING];
                      const isLoading = actionLoading === token.id;
                      return (
                        <div
                          key={token.id}
                          className="px-6 py-3.5 flex items-center justify-between gap-3 text-xs hover:bg-slate-50/60 transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <span className="font-mono font-black text-slate-900 text-sm">
                              {queue.tokenPrefix}-{token.tokenNumber}
                            </span>
                            <Badge variant={sc.variant} size="sm">
                              {sc.label}
                            </Badge>
                          </div>
                          <div className="flex items-center gap-1.5">
                            {token.status === TokenStatus.CALLED && (
                              <>
                                <Button
                                  onClick={() => handleAction("hold", token.id)}
                                  disabled={isLoading}
                                  variant="outline"
                                  size="xs"
                                >
                                  Hold
                                </Button>
                                <Button
                                  onClick={() => handleAction("complete", token.id)}
                                  disabled={isLoading}
                                  variant="primary"
                                  size="xs"
                                  className="bg-emerald-600 hover:bg-emerald-700"
                                >
                                  Complete
                                </Button>
                                <Button
                                  onClick={() => handleAction("skip", token.id)}
                                  disabled={isLoading}
                                  variant="ghost"
                                  size="xs"
                                >
                                  Skip
                                </Button>
                              </>
                            )}
                            {token.status === TokenStatus.HOLD && (
                              <Button
                                onClick={() => handleAction("resume", token.id)}
                                disabled={isLoading}
                                variant="primary"
                                size="xs"
                              >
                                Resume
                              </Button>
                            )}
                            {token.status === TokenStatus.WAITING && (
                              <Button
                                onClick={() => handleAction("skip", token.id)}
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
              </Card>
            )}
          </div>

          {/* Sidebar */}
          <div className="space-y-4">
            {isActive && !isStaff && (
              <Card className="p-6">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-bold text-slate-900">Join this Queue</h3>
                  {queue.requiresDocumentVerification && (
                    <span className="text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200 px-2 py-0.5 rounded-full">
                      Docs Required
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-500 mb-4 leading-relaxed">
                  {queue.requiresDocumentVerification
                    ? "This service requires identity and document analysis before you enter the queue line."
                    : "Generate your instant digital token to receive real-time estimated wait times and turn notifications."}
                </p>
                <Button
                  onClick={() => {
                    if (queue.requiresDocumentVerification) {
                      setShowPreJoinModal(true);
                    } else {
                      joinMutation.mutate();
                    }
                  }}
                  loading={joinMutation.isPending}
                  variant="primary"
                  size="md"
                  fullWidth
                  icon={
                    queue.requiresDocumentVerification ? (
                      <ShieldCheck className="w-4 h-4" />
                    ) : (
                      <Ticket className="w-4 h-4" />
                    )
                  }
                >
                  {joinMutation.isPending
                    ? "Generating Token..."
                    : queue.requiresDocumentVerification
                    ? "Verify Document & Join"
                    : "Take Token"}
                </Button>
              </Card>
            )}

            <Card className="p-6 text-xs space-y-3">
              <h3 className="font-bold text-slate-900 uppercase tracking-wider text-[11px]">
                Queue Overview
              </h3>
              <div className="flex justify-between text-slate-600">
                <span className="text-slate-400">Status</span>
                <strong className={isActive ? "text-emerald-700" : "text-slate-500"}>
                  {queue.status}
                </strong>
              </div>
              <div className="flex justify-between text-slate-600">
                <span className="text-slate-400">Token Prefix</span>
                <span className="font-mono font-bold text-slate-900">{queue.tokenPrefix}</span>
              </div>
              <div className="flex justify-between text-slate-600">
                <span className="text-slate-400">Waiting Customers</span>
                <span className="font-bold text-slate-900">{waitingCount}</span>
              </div>
            </Card>
          </div>
        </div>
      </main>

      <QueueQRCodeModal
        isOpen={showQrModal}
        onClose={() => setShowQrModal(false)}
        queueId={queue.id}
        queueName={queue.name}
      />

      {/* Pre-Join Document Modal */}
      {showPreJoinModal && (
        <PreJoinDocumentModal
          isOpen={showPreJoinModal}
          queue={queue}
          onClose={() => setShowPreJoinModal(false)}
          onSuccess={(tokenId) => {
            setShowPreJoinModal(false);
            navigate(`/token/${tokenId}`);
          }}
        />
      )}
    </div>
  );
};
export default QueueDetailPage;
