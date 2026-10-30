import React, { useState } from "react";
import { useParams, useNavigate, Navigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../context/AuthContext";
import {
  getTokenById,
  getQueue,
  getTokenEstimate,
  leaveToken,
  holdToken,
  resumeToken,
  QueueTokenResponse,
  QueueResponse,
  QueueEstimate,
  updateTravelTime,
  predictEta,
  uploadTokenDocument,
  getTokenDocuments,
  getTokenDocumentStatus,
} from "../lib/apiClient";
import type { AiEtaPrediction, TokenDocument, TokenDocumentStatus } from "../types/queue";
import { QueueStatus, TokenStatus, DocumentVerificationStatus } from "../types/queue";
import { Header } from "../components/Header";
import { useQueueWebSocket } from "../lib/useQueueWebSocket";
import { useTranslation } from "../i18n/LanguageContext";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent } from "../components/ui/Card";
import { LiveIndicator } from "../components/ui/LiveIndicator";
import { QueueQRCodeModal } from "../components/QueueQRCodeModal";
import {
  Ticket,
  Clock,
  Users,
  Building2,
  MapPin,
  ArrowLeft,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  QrCode,
  Sparkles,
  Info,
  ShieldCheck,
  ChevronRight,
  HelpCircle,
  Upload,
  FileCheck2,
  FileX2,
  FileText,
  CheckCircle,
} from "lucide-react";

export const TokenPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const { user, isLoading: authLoading } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [leaving, setLeaving] = useState(false);
  const [holding, setHolding] = useState(false);
  const [resuming, setResuming] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  const [showQrModal, setShowQrModal] = useState(false);
  
  const [travelTime, setTravelTime] = useState<string>("");
  const [updatingTravelTime, setUpdatingTravelTime] = useState(false);

  // Document verification upload state
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState("GOVERNMENT_ID");
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);

  const tokenQuery = useQuery<QueueTokenResponse>({
    queryKey: ["token", id],
    queryFn: async () => {
      const result = await getTokenById(id!);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load token");
      }
      return result.data;
    },
    enabled: !!id && !authLoading,
  });

  const documentsQuery = useQuery<TokenDocument[]>({
    queryKey: ["tokenDocuments", id],
    queryFn: async () => {
      if (!id) return [];
      const res = await getTokenDocuments(id);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!id && !authLoading,
  });

  const documentStatusQuery = useQuery<TokenDocumentStatus | null>({
    queryKey: ["tokenDocumentStatus", id],
    queryFn: async () => {
      if (!id) return null;
      const res = await getTokenDocumentStatus(id);
      return res.success && res.data ? res.data : null;
    },
    enabled: !!id && !authLoading,
  });

  const token = tokenQuery.data;

  React.useEffect(() => {
    if (token?.expectedTravelTimeMinutes !== undefined) {
      setTravelTime(token.expectedTravelTimeMinutes.toString());
    }
  }, [token?.expectedTravelTimeMinutes]);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !id) return;

    if (file.size > 5 * 1024 * 1024) {
      setUploadError("File size exceeds 5MB limit.");
      return;
    }

    setUploadingDoc(true);
    setUploadError(null);
    setUploadSuccess(null);

    const reader = new FileReader();
    reader.onload = async () => {
      try {
        const base64Data = reader.result as string;
        const res = await uploadTokenDocument(id, {
          documentType: selectedDocType,
          fileName: file.name,
          fileContentType: file.type || "application/octet-stream",
          fileData: base64Data,
        });

        if (res.success && res.data) {
          const doc = res.data;
          setUploadSuccess(
            `"${file.name}" uploaded! AI Verdict: ${doc.verificationStatus} (${Math.round((doc.aiConfidence ?? 0.9) * 100)}% confidence)`
          );
          await queryClient.invalidateQueries({ queryKey: ["tokenDocuments", id] });
          await queryClient.invalidateQueries({ queryKey: ["tokenDocumentStatus", id] });
          await queryClient.invalidateQueries({ queryKey: ["token", id] });
        } else {
          setUploadError(res.error?.message || "Failed to upload document");
        }
      } catch (err: any) {
        setUploadError(err?.message || "Error processing upload");
      } finally {
        setUploadingDoc(false);
      }
    };
    reader.onerror = () => {
      setUploadError("Could not read local file.");
      setUploadingDoc(false);
    };
    reader.readAsDataURL(file);
  };

  // Real-time live updates via STOMP WebSocket
  useQueueWebSocket({
    queueId: token?.queueId,
  });

  const queueQuery = useQuery<QueueResponse>({
    queryKey: ["queue", token?.queueId],
    queryFn: async () => {
      const result = await getQueue(token!.queueId);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load queue");
      }
      return result.data;
    },
    enabled: !!token?.queueId,
  });

  const estimateQuery = useQuery<QueueEstimate>({
    queryKey: ["estimate", token?.queueId, id],
    queryFn: async () => {
      const result = await getTokenEstimate(token!.queueId, id!);
      if (!result.success || !result.data) {
        throw new Error(result.error?.message || "Failed to load estimate");
      }
      return result.data;
    },
    enabled: !!token?.queueId && !!id,
    refetchInterval: 10000,
  });

  const aiEtaQuery = useQuery<AiEtaPrediction | null>({
    queryKey: ["aiEta", id],
    queryFn: async () => {
      const result = await predictEta(id!);
      return result.success && result.data ? result.data : null;
    },
    enabled: !!id && token?.status === TokenStatus.WAITING,
    refetchInterval: 15000,
  });

  async function handleLeaveQueue() {
    if (!id) return;
    if (!window.confirm(t("leave_queue_confirm"))) {
      return;
    }
    setLeaving(true);
    setLeaveError(null);
    try {
      const result = await leaveToken(id);
      if (result.success) {
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
        navigate("/dashboard");
      } else {
        setLeaveError(result.error?.message ?? "Failed to leave queue");
      }
    } catch {
      setLeaveError("Network error. Please try again.");
    } finally {
      setLeaving(false);
    }
  }

  async function handleHoldQueue() {
    if (!id) return;
    if (!window.confirm(t("hold_queue_confirm"))) {
      return;
    }
    setHolding(true);
    setLeaveError(null);
    try {
      const result = await holdToken(id);
      if (result.success) {
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
      } else {
        setLeaveError(result.error?.message ?? t("hold_failed"));
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
      }
    } catch {
      setLeaveError("Unable to update your queue right now. Please try again.");
    } finally {
      setHolding(false);
    }
  }

  async function handleResumeQueue() {
    if (!id) return;
    if (!window.confirm(t("resume_queue_confirm"))) {
      return;
    }
    setResuming(true);
    setLeaveError(null);
    try {
      const result = await resumeToken(id);
      if (result.success) {
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
        await queryClient.invalidateQueries({ queryKey: ["estimate", token?.queueId, id] });
      } else {
        setLeaveError(result.error?.message ?? t("resume_failed"));
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
      }
    } catch {
      setLeaveError("Unable to update your queue right now. Please try again.");
    } finally {
      setResuming(false);
    }
  }

  async function handleUpdateTravelTime() {
    if (!id || !travelTime) return;
    const minutes = parseInt(travelTime, 10);
    if (isNaN(minutes) || minutes < 0) return;
    
    setUpdatingTravelTime(true);
    try {
      const result = await updateTravelTime(id, minutes);
      if (result.success) {
        await queryClient.invalidateQueries({ queryKey: ["token", id] });
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
      }
    } finally {
      setUpdatingTravelTime(false);
    }
  }

  if (authLoading) {
    return null;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!id) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Header />
        <div className="max-w-md mx-auto text-center py-16 px-4">
          <h2 className="text-lg font-bold text-slate-900 mb-2">{t("error")}</h2>
          <p className="text-xs text-slate-500 mb-4">No token ID provided.</p>
          <Button onClick={() => navigate("/dashboard")} variant="primary" size="sm">
            {t("back")}
          </Button>
        </div>
      </div>
    );
  }

  if (tokenQuery.isLoading) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Header />
        <div className="flex items-center justify-center py-24">
          <div className="animate-spin h-8 w-8 border-4 border-teal-600 border-t-transparent rounded-full" />
        </div>
      </div>
    );
  }

  if (tokenQuery.error || !token) {
    return (
      <div className="min-h-screen bg-slate-50">
        <Header />
        <div className="max-w-md mx-auto text-center py-16 px-4">
          <div className="w-12 h-12 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center mx-auto mb-3">
            <XCircle className="w-6 h-6" />
          </div>
          <h2 className="text-lg font-bold text-slate-900 mb-2">{t("error")}</h2>
          <p className="text-xs text-slate-500 mb-6">
            {tokenQuery.error instanceof Error ? tokenQuery.error.message : "Token could not be loaded."}
          </p>
          <Button onClick={() => navigate("/dashboard")} variant="primary" size="sm">
            {t("back")}
          </Button>
        </div>
      </div>
    );
  }

  const queue = queueQuery.data;
  const estimate = estimateQuery.data;
  const isQueueClosed = queue && queue.status !== QueueStatus.ACTIVE;
  const canLeave = token.status === TokenStatus.WAITING || token.status === TokenStatus.HOLD;

  const isWaiting = token.status === TokenStatus.WAITING;
  const isHeld = token.status === TokenStatus.HOLD;
  const isCalled = token.status === TokenStatus.CALLED;
  const isCompleted = token.status === TokenStatus.COMPLETED;
  const isCancelled = token.status === TokenStatus.CANCELLED;
  const isSkipped = token.status === TokenStatus.SKIPPED;

  // Timeline Step Status
  const getStepState = (stepIndex: number) => {
    // 0: Joined, 1: Waiting, 2: Called/Serving, 3: Completed
    if (isCompleted) return "completed";
    if (isCancelled || isSkipped) return stepIndex === 0 ? "completed" : "inactive";
    if (isCalled) return stepIndex <= 2 ? "active" : "upcoming";
    if (isWaiting || isHeld) return stepIndex === 0 ? "completed" : stepIndex === 1 ? "active" : "upcoming";
    return "upcoming";
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <Header />
      <main className="max-w-xl mx-auto px-4 sm:px-6 py-8">
        {/* Top Action Bar */}
        <div className="mb-6 flex items-center justify-between">
          <button
            onClick={() => navigate("/dashboard")}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>{t("back")}</span>
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowQrModal(true)}
              className="p-1.5 rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-100 transition-colors"
              title="QR Share"
            >
              <QrCode className="w-4 h-4" />
            </button>
            <LiveIndicator label={t("live_badge")} />
          </div>
        </div>

        {leaveError && (
          <div className="mb-4 p-4 bg-rose-50 border border-rose-200 text-xs text-rose-700 rounded-2xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{leaveError}</span>
          </div>
        )}

        {isQueueClosed && (
          <div className="mb-6 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-xs text-amber-800 font-semibold flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0" />
            <span>{t("queue_closed")}</span>
          </div>
        )}

        {/* Digital Boarding Pass Hero Ticket Card */}
        <Card
          className={`overflow-hidden transition-all shadow-elevated border-2 ${
            isCalled
              ? "border-amber-400 ring-4 ring-amber-400/20 bg-gradient-to-b from-amber-50/50 via-white to-white"
              : isCompleted
              ? "border-emerald-300 bg-gradient-to-b from-emerald-50/40 via-white to-white"
              : "border-teal-200/80 bg-white"
          }`}
        >
          {/* Card Top Brand Strip */}
          <div className="bg-slate-900 text-white px-6 py-4 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Ticket className="w-4 h-4 text-teal-400" />
              <span className="text-xs font-bold uppercase tracking-wider text-slate-200">
                Digital Queue Token
              </span>
            </div>
            <Badge
              variant={
                isCalled
                  ? "warning"
                  : isCompleted
                  ? "success"
                  : isCancelled
                  ? "danger"
                  : isSkipped
                  ? "neutral"
                  : "primary"
              }
              size="sm"
              dot
              className="uppercase font-bold tracking-wider"
            >
              {t(`status_${token.status}`)}
            </Badge>
          </div>

          <CardContent className="p-6 sm:p-8 text-center space-y-6">
            {/* Queue & Branch Header */}
            <div>
              <h1 className="text-xl font-extrabold text-slate-900 tracking-tight">
                {queue?.name || "Service Queue"}
              </h1>
              {queue?.description && (
                <p className="text-xs text-slate-500 mt-1 flex items-center justify-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-slate-400" />
                  <span>{queue.description}</span>
                </p>
              )}
            </div>

            {/* Giant Token Display */}
            <div className="py-4">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-widest block mb-1">
                {t("current_token")}
              </span>
              <div className="text-6xl sm:text-7xl font-black text-teal-800 font-mono tracking-tight select-all">
                {token.tokenNumber}
              </div>
            </div>

            {/* Status Notifications */}
            {isCalled && (
              <div className="p-4 bg-amber-500 text-white rounded-2xl text-xs font-bold shadow-card animate-bounce">
                🚨 {t("your_turn_alert")}
              </div>
            )}

            {isCompleted && (
              <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl text-xs font-semibold flex items-center justify-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span>{t("token_completed_msg")}</span>
              </div>
            )}

            {isCancelled && (
              <div className="p-4 bg-rose-50 border border-rose-200 text-rose-700 rounded-2xl text-xs font-semibold flex items-center justify-center gap-2">
                <XCircle className="w-4 h-4 text-rose-600" />
                <span>{t("token_cancelled_msg")}</span>
              </div>
            )}

            {isHeld && (
              <div className="p-4 bg-slate-100 border border-slate-200 text-slate-800 rounded-2xl text-xs font-semibold flex flex-col items-center justify-center gap-2">
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 text-slate-500" />
                  <span>{t("queue_on_hold")}</span>
                </div>
                <span className="text-slate-500 text-[10px] font-normal">{t("queue_on_hold_warning")}</span>
              </div>
            )}

            {/* Live Visual Progression Timeline */}
            <div className="py-3 px-2 border-y border-slate-100">
              <div className="grid grid-cols-4 gap-1 text-center">
                {[
                  { label: "Joined", state: getStepState(0) },
                  { label: "Waiting", state: getStepState(1) },
                  { label: "Called", state: getStepState(2) },
                  { label: "Done", state: getStepState(3) },
                ].map((step, idx) => (
                  <div key={idx} className="flex flex-col items-center">
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-all mb-1.5 ${
                        step.state === "completed"
                          ? "bg-emerald-600 text-white shadow-xs"
                          : step.state === "active"
                          ? "bg-teal-600 text-white ring-4 ring-teal-100 shadow-xs"
                          : "bg-slate-100 text-slate-400"
                      }`}
                    >
                      {step.state === "completed" ? "✓" : idx + 1}
                    </div>
                    <span
                      className={`text-[10px] uppercase font-bold tracking-wider ${
                        step.state === "active"
                          ? "text-teal-700 font-extrabold"
                          : step.state === "completed"
                          ? "text-emerald-700"
                          : "text-slate-400"
                      }`}
                    >
                      {step.label}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Live Metrics: Position in Queue & Estimated Wait */}
            {isWaiting && (
              <div className="space-y-4 pt-2">
                <div className="grid grid-cols-2 gap-3">
                  <div className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 text-center">
                    <div className="text-3xl font-black text-slate-900">
                      #{estimate?.position ?? 1}
                    </div>
                    <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mt-1">
                      {t("position_in_queue")}
                    </div>
                  </div>
                  <div className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 text-center">
                    <div className="text-3xl font-black text-teal-700">
                      ~{aiEtaQuery.data?.estimatedWaitMinutes ?? estimate?.estimatedWaitMinutes ?? 0}m
                    </div>
                    <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider mt-1">
                      {t("estimated_wait")}
                    </div>
                  </div>
                </div>

                {/* Explainable AI Forecast Breakdown */}
                {aiEtaQuery.data && (
                  <div className="p-4 bg-teal-50/60 border border-teal-100/80 rounded-2xl text-left">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-teal-900">
                        <Sparkles className="w-3.5 h-3.5 text-teal-600" />
                        <span>
                          {aiEtaQuery.data.predictionType === "STATISTICAL_AI"
                            ? t("ai_predicted_badge")
                            : t("deterministic_badge")}
                        </span>
                      </div>
                      <span className="text-[10px] font-mono text-teal-700 bg-teal-100/60 px-2 py-0.5 rounded-md font-semibold">
                        v{aiEtaQuery.data.modelVersion}
                      </span>
                    </div>

                    {aiEtaQuery.data.factors && Object.keys(aiEtaQuery.data.factors).length > 0 && (
                      <div className="grid grid-cols-2 gap-2 text-[11px] text-slate-600 pt-1">
                        {aiEtaQuery.data.factors.tokensAhead !== undefined && (
                          <div className="bg-white/80 p-2 rounded-xl border border-teal-100">
                            <span className="text-slate-400 block text-[10px]">Tokens Ahead</span>
                            <strong className="text-slate-900 font-bold">{aiEtaQuery.data.factors.tokensAhead}</strong>
                          </div>
                        )}
                        {aiEtaQuery.data.factors.activeCounters !== undefined && (
                          <div className="bg-white/80 p-2 rounded-xl border border-teal-100">
                            <span className="text-slate-400 block text-[10px]">Active Counters</span>
                            <strong className="text-slate-900 font-bold">{aiEtaQuery.data.factors.activeCounters}</strong>
                          </div>
                        )}
                        {aiEtaQuery.data.factors.avgHistoricalMinutes !== undefined && (
                          <div className="bg-white/80 p-2 rounded-xl border border-teal-100">
                            <span className="text-slate-400 block text-[10px]">Service Speed</span>
                            <strong className="text-slate-900 font-bold">{aiEtaQuery.data.factors.avgHistoricalMinutes} min/token</strong>
                          </div>
                        )}
                        {aiEtaQuery.data.factors.hourOfDay !== undefined && (
                          <div className="bg-white/80 p-2 rounded-xl border border-teal-100">
                            <span className="text-slate-400 block text-[10px]">Time Slot</span>
                            <strong className="text-slate-900 font-bold">{aiEtaQuery.data.factors.hourOfDay}:00</strong>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Travel Time Input & Recommended Departure */}
                <div className="p-4 bg-white border border-slate-200 rounded-2xl text-left shadow-sm">
                  <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
                    <MapPin className="w-4 h-4 text-slate-400" />
                    Travel to Branch
                  </h3>
                  
                  <div className="flex gap-2 mb-3">
                    <div className="flex-1 relative">
                      <input
                        type="number"
                        min="0"
                        placeholder="Minutes"
                        value={travelTime}
                        onChange={(e) => setTravelTime(e.target.value)}
                        className="w-full pl-3 pr-10 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 transition-all"
                      />
                      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 font-medium">min</span>
                    </div>
                    <Button 
                      onClick={handleUpdateTravelTime} 
                      loading={updatingTravelTime}
                      disabled={!travelTime || travelTime === token?.expectedTravelTimeMinutes?.toString()}
                      variant="primary" 
                      size="sm"
                      className="px-4 shrink-0"
                    >
                      Save
                    </Button>
                  </div>
                  
                  {token?.recommendedDepartureTime && (
                    <div className="p-3 bg-blue-50/50 border border-blue-100 rounded-xl">
                      <div className="flex items-start gap-2">
                        <Clock className="w-4 h-4 text-blue-500 shrink-0 mt-0.5" />
                        <div>
                          <div className="text-[11px] font-bold text-blue-800 uppercase tracking-wider mb-0.5">
                            Recommended Departure
                          </div>
                          <div className="text-sm font-semibold text-slate-900">
                            {new Date(token.recommendedDepartureTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </div>
                          <div className="text-xs text-slate-500 mt-1 leading-snug">
                            Includes a 5-minute buffer before your expected turn at {token.expectedTurnTime && new Date(token.expectedTurnTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}.
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>

              </div>
            )}

            {/* AI Document Verification Facility Card */}
            {(token.requiresDocumentVerification || queue?.requiresDocumentVerification || (documentsQuery.data && documentsQuery.data.length > 0)) && (
              <div className="p-5 bg-white border border-slate-200 rounded-2xl text-left shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center">
                      <ShieldCheck className="w-4 h-4" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">
                        Document Verification
                      </h3>
                      <p className="text-[11px] text-slate-500">
                        {documentStatusQuery.data?.requiresVerification
                          ? "Required before you can be called for service"
                          : "Optional customer documentation"}
                      </p>
                    </div>
                  </div>

                  {documentStatusQuery.data && (
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-bold flex items-center gap-1 ${
                        documentStatusQuery.data.isEligibleForService
                          ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                          : "bg-amber-50 text-amber-700 border border-amber-200"
                      }`}
                    >
                      {documentStatusQuery.data.isEligibleForService ? (
                        <>
                          <CheckCircle className="w-3.5 h-3.5 text-emerald-600" /> Verified
                        </>
                      ) : (
                        <>
                          <AlertTriangle className="w-3.5 h-3.5 text-amber-600" /> Pending Upload
                        </>
                      )}
                    </span>
                  )}
                </div>

                {/* Upload Feedback */}
                {uploadError && (
                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 flex items-center gap-2">
                    <XCircle className="w-4 h-4 shrink-0 text-rose-600" />
                    <span>{uploadError}</span>
                  </div>
                )}
                {uploadSuccess && (
                  <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 flex items-center gap-2">
                    <CheckCircle className="w-4 h-4 shrink-0 text-emerald-600" />
                    <span>{uploadSuccess}</span>
                  </div>
                )}

                {/* Document Type Selector & Upload Button */}
                {isWaiting && (
                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-3">
                    <div className="flex flex-col sm:flex-row gap-2">
                      <div className="flex-1">
                        <label className="block text-[11px] font-bold text-slate-600 uppercase tracking-wider mb-1">
                          Document Type
                        </label>
                        <select
                          value={selectedDocType}
                          onChange={(e) => setSelectedDocType(e.target.value)}
                          className="w-full text-xs font-semibold px-3 py-2 bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500"
                        >
                          <option value="GOVERNMENT_ID">National ID / Passport / Driver's License</option>
                          <option value="MEDICAL_PRESCRIPTION">Medical Prescription / Doctor's Slip</option>
                          <option value="INCOME_PROOF">Income Proof / Bank Statement</option>
                          <option value="GENERAL_DOCUMENT">General Verification Document</option>
                        </select>
                      </div>

                      <div className="sm:self-end">
                        <label
                          className={`inline-flex items-center justify-center gap-2 px-4 py-2 text-xs font-bold rounded-xl text-white transition-all cursor-pointer shadow-xs ${
                            uploadingDoc
                              ? "bg-slate-400 cursor-not-allowed"
                              : "bg-teal-600 hover:bg-teal-700 active:bg-teal-800"
                          }`}
                        >
                          <Upload className="w-4 h-4" />
                          <span>{uploadingDoc ? "AI Verifying..." : "Upload File"}</span>
                          <input
                            type="file"
                            accept=".pdf,image/png,image/jpeg,image/jpg,image/webp"
                            disabled={uploadingDoc}
                            onChange={handleFileUpload}
                            className="hidden"
                          />
                        </label>
                      </div>
                    </div>
                    <p className="text-[10px] text-slate-400">
                      Supports PDF, PNG, JPG, JPEG, WEBP (Max 5MB). Instant verification by SmartQueue AI.
                    </p>
                  </div>
                )}

                {/* Uploaded Documents List */}
                {documentsQuery.data && documentsQuery.data.length > 0 && (
                  <div className="space-y-2 pt-1">
                    <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                      Uploaded Documents ({documentsQuery.data.length})
                    </span>
                    <div className="divide-y divide-slate-100 border border-slate-100 rounded-xl overflow-hidden bg-slate-50/50">
                      {documentsQuery.data.map((doc) => (
                        <div
                          key={doc.id}
                          className="p-3 flex items-center justify-between text-xs hover:bg-slate-50 transition-colors"
                        >
                          <div className="flex items-center gap-2.5">
                            <div className="p-1.5 rounded-lg bg-white border border-slate-200 text-slate-500">
                              <FileText className="w-4 h-4" />
                            </div>
                            <div>
                              <div className="font-semibold text-slate-800">{doc.fileName}</div>
                              <span className="text-[10px] text-slate-400">
                                {doc.documentType.replace(/_/g, " ")} • {new Date(doc.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </span>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            {doc.verificationStatus === DocumentVerificationStatus.VERIFIED ? (
                              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-700 bg-emerald-100/70 px-2 py-0.5 rounded-md">
                                <CheckCircle className="w-3 h-3" /> Verified ({Math.round((doc.aiConfidence ?? 0.95) * 100)}%)
                              </span>
                            ) : doc.verificationStatus === DocumentVerificationStatus.MANUALLY_APPROVED ? (
                              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-blue-700 bg-blue-100/70 px-2 py-0.5 rounded-md">
                                <CheckCircle className="w-3 h-3" /> Approved by Staff
                              </span>
                            ) : doc.verificationStatus === DocumentVerificationStatus.REJECTED ? (
                              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-rose-700 bg-rose-100/70 px-2 py-0.5 rounded-md">
                                <FileX2 className="w-3 h-3" /> Rejected
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-amber-700 bg-amber-100/70 px-2 py-0.5 rounded-md">
                                <Clock className="w-3 h-3" /> Pending Review
                              </span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Queue Counter Live Status */}
            {queue && (
              <div className="p-3 bg-slate-50 rounded-xl border border-slate-100 text-xs text-slate-600 flex items-center justify-between">
                <span>{t("now_serving")}:</span>
                <span className="font-mono font-bold text-slate-900">
                  {queue.currentTokenNumber ? `${queue.tokenPrefix}-${queue.currentTokenNumber}` : "None"}
                </span>
              </div>
            )}

            {/* Action Buttons */}
            {canLeave && (
              <div className="pt-2 flex flex-col sm:flex-row gap-2 justify-center">
                {isWaiting && (
                  <Button
                    onClick={handleHoldQueue}
                    loading={holding}
                    disabled={leaving || resuming}
                    variant="outline"
                    size="sm"
                    className="flex-1"
                  >
                    {holding ? t("holding") : t("hold_queue")}
                  </Button>
                )}
                {isHeld && (
                  <Button
                    onClick={handleResumeQueue}
                    loading={resuming}
                    disabled={leaving || holding}
                    variant="primary"
                    size="sm"
                    className="flex-1"
                  >
                    {resuming ? t("resuming") : t("resume_queue")}
                  </Button>
                )}
                <Button
                  onClick={handleLeaveQueue}
                  loading={leaving}
                  disabled={holding || resuming}
                  variant="outline"
                  size="sm"
                  className="flex-1 text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                >
                  {leaving ? t("leaving") : t("leave_queue")}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </main>

      {queue && (
        <QueueQRCodeModal
          isOpen={showQrModal}
          onClose={() => setShowQrModal(false)}
          queueId={queue.id}
          queueName={queue.name}
        />
      )}
    </div>
  );
};
