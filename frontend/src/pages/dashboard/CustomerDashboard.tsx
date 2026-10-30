import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import {
  getCustomerActiveTokens,
  getOrganizations,
  getBranchesByOrg,
  getServicesByBranch,
  getQueuesByBranch,
  generateToken,
  leaveToken,
  OrganizationDto,
  BranchDto,
  ServiceDto,
  QueueResponse,
  CustomerTokenDto,
  QueueStatus,
  TokenStatus,
} from "../../lib/apiClient";
import { useQueueWebSocket } from "../../lib/useQueueWebSocket";
import { useTranslation } from "../../i18n/LanguageContext";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card, CardContent } from "../../components/ui/Card";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import { LiveIndicator } from "../../components/ui/LiveIndicator";
import {
  Sparkles,
  Ticket,
  Clock,
  Users,
  Building2,
  MapPin,
  Stethoscope,
  ArrowRight,
  AlertCircle,
  CheckCircle2,
  XCircle,
  HelpCircle,
  ChevronRight,
  Compass,
  ShieldCheck,
  FileCheck,
  QrCode,
} from "lucide-react";
import { PreJoinDocumentModal } from "../../components/PreJoinDocumentModal";
import { QueueQRCodeModal } from "../../components/QueueQRCodeModal";

export const CustomerDashboard: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // Discovery State
  const [selectedOrgId, setSelectedOrgId] = useState<string>("");
  const [selectedBranchId, setSelectedBranchId] = useState<string>("");
  const [selectedServiceId, setSelectedServiceId] = useState<string>("");

  const [joiningQueueId, setJoiningQueueId] = useState<string | null>(null);
  const [leavingTokenId, setLeavingTokenId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmLeaveId, setConfirmLeaveId] = useState<string | null>(null);
  const [preJoinQueue, setPreJoinQueue] = useState<QueueResponse | null>(null);
  const [qrModalQueue, setQrModalQueue] = useState<any | null>(null);

  // Subscribe to real-time WebSocket events for automatic live updates
  useQueueWebSocket({});

  // 1. Fetch Customer Active Tokens
  const {
    data: activeTokens = [],
    isLoading: isTokensLoading,
    refetch: refetchTokens,
  } = useQuery<CustomerTokenDto[]>({
    queryKey: ["customerActiveTokens"],
    queryFn: async () => {
      const result = await getCustomerActiveTokens();
      if (!result.success || !result.data) {
        return [];
      }
      return result.data;
    },
    refetchInterval: 10000,
  });

  // 2. Fetch Organizations
  const { data: organizations = [] } = useQuery<OrganizationDto[]>({
    queryKey: ["organizations"],
    queryFn: async () => {
      const result = await getOrganizations();
      if (!result.success || !result.data) {
        return [];
      }
      return result.data;
    },
  });

  // 3. Fetch Branches for selected Organization
  const { data: branches = [], isLoading: isBranchesLoading } = useQuery<BranchDto[]>({
    queryKey: ["branches", selectedOrgId],
    queryFn: async () => {
      if (!selectedOrgId) return [];
      const result = await getBranchesByOrg(selectedOrgId);
      return result.success && result.data ? result.data : [];
    },
    enabled: !!selectedOrgId,
  });

  // 4. Fetch Services for selected Branch
  const { data: services = [], isLoading: isServicesLoading } = useQuery<ServiceDto[]>({
    queryKey: ["services", selectedBranchId],
    queryFn: async () => {
      if (!selectedBranchId) return [];
      const result = await getServicesByBranch(selectedBranchId);
      return result.success && result.data ? result.data : [];
    },
    enabled: !!selectedBranchId,
  });

  // 5. Fetch Queues for selected Branch
  const { data: branchQueues = [], isLoading: isQueuesLoading } = useQuery<QueueResponse[]>({
    queryKey: ["queuesByBranch", selectedBranchId],
    queryFn: async () => {
      if (!selectedBranchId) return [];
      const result = await getQueuesByBranch(selectedBranchId);
      return result.success && result.data ? result.data : [];
    },
    enabled: !!selectedBranchId,
    refetchInterval: 5000,
  });

  // Filter queues by selected service if specified
  const filteredQueues = selectedServiceId
    ? branchQueues.filter((q: any) => !q.serviceId || q.serviceId === selectedServiceId)
    : branchQueues;

  // Handle Join Queue
  async function handleJoinQueue(queueId: string) {
    setJoiningQueueId(queueId);
    setActionError(null);
    try {
      const result = await generateToken(queueId);
      if (result.success && result.data) {
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
        await queryClient.invalidateQueries({ queryKey: ["queuesByBranch"] });
        navigate(`/token/${result.data.id}`);
      } else {
        const msg = result.error?.message ?? "Failed to join queue";
        setActionError(msg);
        if (result.error?.code === "DUPLICATE_ACTIVE_TOKEN" || msg.toLowerCase().includes("active token")) {
          await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
        }
      }
    } catch {
      setActionError("Network error. Please try again.");
    } finally {
      setJoiningQueueId(null);
    }
  }

  // Handle Leave Queue
  async function handleLeaveQueue(tokenId: string) {
    setLeavingTokenId(tokenId);
    setActionError(null);
    try {
      const result = await leaveToken(tokenId);
      if (result.success) {
        setConfirmLeaveId(null);
        refetchTokens();
        await queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
      } else {
        setActionError(result.error?.message ?? "Failed to leave queue");
      }
    } catch {
      setActionError("Network error. Please try again.");
    } finally {
      setLeavingTokenId(null);
    }
  }

  return (
    <div className="space-y-8 pb-12">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden bg-gradient-to-r from-teal-700 via-teal-800 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-card">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/10 text-teal-200 text-xs font-semibold backdrop-blur-xs mb-3">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Digital Token &amp; Live Queue Portal</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            {t("welcome")}, {user?.fullName}!
          </h1>
          <p className="mt-2 text-teal-100/90 text-xs sm:text-sm leading-relaxed">
            Take a digital token from your phone, monitor your live position in real-time, and walk in only when it's your turn.
          </p>
        </div>

        {/* Decorative Background Accent */}
        <div className="absolute -right-8 -bottom-8 w-48 h-48 bg-teal-500/10 rounded-full blur-2xl pointer-events-none" />
      </div>

      {actionError && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-sm text-rose-700 rounded-2xl flex items-center justify-between shadow-xs">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{actionError}</span>
          </div>
          <button
            onClick={() => setActionError(null)}
            className="text-rose-500 hover:text-rose-700 font-bold p-1"
          >
            ✕
          </button>
        </div>
      )}

      {/* ------------------------------------------------------------- */}
      {/* SECTION 1: ACTIVE DIGITAL TOKENS HERO CARDS                   */}
      {/* ------------------------------------------------------------- */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2.5">
            <div className="w-2.5 h-2.5 rounded-full bg-teal-600 animate-pulse" />
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              {t("active_tokens")}
            </h2>
          </div>
          {activeTokens.length > 0 && <LiveIndicator label={`${activeTokens.length} ACTIVE`} />}
        </div>

        {isTokensLoading ? (
          <div className="grid gap-6 sm:grid-cols-1 md:grid-cols-2">
            <CardSkeleton />
          </div>
        ) : activeTokens.length === 0 ? (
          <Card className="p-8 text-center bg-white border-dashed border-slate-300">
            <div className="w-12 h-12 rounded-2xl bg-teal-50 text-teal-600 flex items-center justify-center mx-auto mb-3 border border-teal-100">
              <Ticket className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-bold text-slate-900 mb-1">{t("no_active_tokens")}</h3>
            <p className="text-xs text-slate-500 max-w-sm mx-auto mb-4">
              Select an organization, branch, and service from the discovery section below to take your live queue token.
            </p>
          </Card>
        ) : (
          <div className="grid gap-6 sm:grid-cols-1 md:grid-cols-2">
            {activeTokens.map((token) => {
              const isCalled = token.status === TokenStatus.CALLED;
              const isHold = token.status === TokenStatus.HOLD;

              return (
                <Card
                  key={token.id}
                  hover
                  className={`overflow-hidden transition-all ${
                    isCalled
                      ? "ring-2 ring-amber-400 border-amber-300 bg-gradient-to-br from-amber-50/70 to-white"
                      : "border-teal-200/80 bg-white"
                  }`}
                >
                  <CardContent className="p-6">
                    {/* Header Row: Queue / Org Info & Status Badge */}
                    <div className="flex items-start justify-between gap-3 mb-4">
                      <div>
                        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-0.5">
                          {token.organizationName || "SmartQueue"}
                        </span>
                        <h3 className="text-lg font-bold text-slate-900 leading-snug">
                          {token.queueName || "Service Queue"}
                        </h3>
                        {token.branchName && (
                          <div className="flex items-center gap-1 text-xs text-slate-500 mt-1">
                            <MapPin className="w-3.5 h-3.5 text-slate-400" />
                            <span>{token.branchName}</span>
                          </div>
                        )}
                      </div>
                      <Badge
                        variant={isCalled ? "warning" : isHold ? "warning" : "success"}
                        size="md"
                        dot
                        className="uppercase tracking-wider font-bold"
                      >
                        {token.status}
                      </Badge>
                    </div>

                    {/* Turn Alert Banner when CALLED */}
                    {isCalled && (
                      <div className="mb-4 p-3 bg-amber-500 text-white rounded-xl text-xs font-bold flex items-center gap-2 shadow-xs animate-pulse">
                        <AlertCircle className="w-4 h-4 shrink-0" />
                        <span>{t("your_turn_alert")}</span>
                      </div>
                    )}

                    {/* Prominent Token Display & Metrics Grid */}
                    <div className="grid grid-cols-2 gap-3 py-4 my-2 border-y border-slate-100 bg-slate-50/50 rounded-xl px-4">
                      <div>
                        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-1">
                          {t("current_token")}
                        </span>
                        <span className="text-3xl sm:text-4xl font-extrabold text-teal-700 font-mono tracking-tight">
                          {token.tokenNumber}
                        </span>
                      </div>
                      <div className="text-right flex flex-col justify-center">
                        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-0.5">
                          {t("position_in_queue")}
                        </span>
                        <span className="text-2xl font-extrabold text-slate-900">
                          #{token.position ?? 1}
                        </span>
                        <span className="text-[11px] text-slate-500">
                          {token.peopleAhead ?? 0} {t("people_ahead")}
                        </span>
                      </div>
                    </div>

                    {/* ETA Row */}
                    <div className="flex items-center justify-between text-xs py-2 px-1 text-slate-600 mb-4">
                      <span className="flex items-center gap-1.5 text-slate-500">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        <span>{t("estimated_wait")}:</span>
                      </span>
                      <span className="font-bold text-slate-900">
                        ~{token.estimatedWaitMinutes ?? 0} {t("minutes")}
                      </span>
                    </div>

                    {/* Action Palette */}
                    <div className="flex items-center gap-2.5 pt-2">
                      <Button
                        onClick={() => navigate(`/token/${token.id}`)}
                        variant="primary"
                        size="sm"
                        fullWidth
                        icon={<ChevronRight className="w-4 h-4" />}
                        iconPosition="right"
                      >
                        {t("view_token_details")}
                      </Button>

                      {confirmLeaveId === token.id ? (
                        <div className="flex items-center gap-1 shrink-0">
                          <Button
                            onClick={() => handleLeaveQueue(token.id)}
                            loading={leavingTokenId === token.id}
                            variant="danger"
                            size="sm"
                          >
                            {leavingTokenId === token.id ? t("leaving") : "Confirm"}
                          </Button>
                          <Button
                            onClick={() => setConfirmLeaveId(null)}
                            variant="ghost"
                            size="sm"
                          >
                            ✕
                          </Button>
                        </div>
                      ) : (
                        <Button
                          onClick={() => setConfirmLeaveId(token.id)}
                          variant="outline"
                          size="sm"
                          className="text-rose-600 hover:text-rose-700 hover:bg-rose-50 border-rose-200"
                        >
                          {t("leave_queue")}
                        </Button>
                      )}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        )}
      </div>

      {/* ------------------------------------------------------------- */}
      {/* SECTION 2: QUEUE DISCOVERY (ORG -> BRANCH -> SERVICE -> QUEUE)*/}
      {/* ------------------------------------------------------------- */}
      <Card className="p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-8 h-8 rounded-xl bg-teal-50 text-teal-600 flex items-center justify-center border border-teal-100">
            <Compass className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-900 tracking-tight">
              {t("discover_title")}
            </h2>
            <p className="text-xs text-slate-500">
              Select your organization, branch location, and desired service to join a queue.
            </p>
          </div>
        </div>

        {/* 3 Step Selectors */}
        <div className="grid gap-4 sm:grid-cols-3 mt-6">
          {/* Step 1: Organization */}
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2 flex items-center gap-1.5">
              <Building2 className="w-3.5 h-3.5 text-slate-400" />
              <span>{t("select_org")}</span>
            </label>
            <select
              value={selectedOrgId}
              onChange={(e) => {
                setSelectedOrgId(e.target.value);
                setSelectedBranchId("");
                setSelectedServiceId("");
              }}
              className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 outline-none shadow-xs cursor-pointer"
            >
              <option value="">{t("choose_org")}</option>
              {organizations.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </select>
          </div>

          {/* Step 2: Branch */}
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2 flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-slate-400" />
              <span>{t("select_branch")}</span>
            </label>
            <select
              value={selectedBranchId}
              onChange={(e) => {
                setSelectedBranchId(e.target.value);
                setSelectedServiceId("");
              }}
              disabled={!selectedOrgId || isBranchesLoading}
              className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 outline-none shadow-xs disabled:bg-slate-100 disabled:text-slate-400 cursor-pointer"
            >
              <option value="">{t("choose_branch")}</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name} {branch.address ? `— ${branch.address}` : ""}
                </option>
              ))}
            </select>
          </div>

          {/* Step 3: Service */}
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2 flex items-center gap-1.5">
              <Stethoscope className="w-3.5 h-3.5 text-slate-400" />
              <span>{t("select_service")}</span>
            </label>
            <select
              value={selectedServiceId}
              onChange={(e) => setSelectedServiceId(e.target.value)}
              disabled={!selectedBranchId || isServicesLoading}
              className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 outline-none shadow-xs disabled:bg-slate-100 disabled:text-slate-400 cursor-pointer"
            >
              <option value="">{t("choose_service")} (All Services)</option>
              {services.map((srv) => (
                <option key={srv.id} value={srv.id}>
                  {srv.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Available Queues List */}
        {selectedBranchId && (
          <div className="mt-8 pt-6 border-t border-slate-100">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
                {t("select_queue")}
              </h3>
              <span className="text-xs text-slate-400 font-medium">
                {filteredQueues.length} available
              </span>
            </div>

            {actionError && (
              <div className="mb-5 p-4 bg-rose-50 border border-rose-200 text-sm text-rose-700 rounded-2xl flex items-center justify-between shadow-xs">
                <div className="flex items-center gap-2.5">
                  <AlertCircle className="w-5 h-5 text-rose-500 shrink-0" />
                  <div>
                    <span className="font-bold">{actionError}</span>
                    {actionError.toLowerCase().includes("active token") && (
                      <p className="text-xs text-rose-600 mt-0.5">
                        You already have an active ticket in this queue. You can view it below or at the top of your dashboard.
                      </p>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => setActionError(null)}
                  className="text-rose-500 hover:text-rose-700 font-bold p-1 cursor-pointer"
                >
                  ✕
                </button>
              </div>
            )}

            {isQueuesLoading ? (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <CardSkeleton />
                <CardSkeleton />
              </div>
            ) : filteredQueues.length === 0 ? (
              <EmptyState
                title={t("no_queues_found")}
                description="No active queues are currently configured for this location or service."
              />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {filteredQueues.map((q: any) => {
                  const isOpen = q.status === QueueStatus.ACTIVE;
                  const isJoining = joiningQueueId === q.id;
                  const existingToken = activeTokens.find((t) => t.queueId === q.id);

                  return (
                    <Card
                      key={q.id}
                      hover={isOpen}
                      className={`p-5 flex flex-col justify-between transition-all ${
                        existingToken
                          ? "bg-teal-50/30 border-teal-300 ring-1 ring-teal-200"
                          : isOpen
                          ? "bg-white border-slate-200/80 hover:border-teal-500/50"
                          : "bg-slate-50/80 border-slate-200 opacity-60"
                      }`}
                    >
                      <div>
                        <div className="flex items-start justify-between gap-2 mb-2">
                          <div className="space-y-1">
                            <h4 className="text-base font-bold text-slate-900 leading-snug">
                              {q.name}
                            </h4>
                            {existingToken && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-100 text-teal-800 border border-teal-300">
                                <Ticket className="w-3.5 h-3.5 text-teal-700" />
                                Active Token: {existingToken.tokenNumber} (Pos #{existingToken.position ?? 1})
                              </span>
                            )}
                            {q.requiresDocumentVerification && (
                              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                                <ShieldCheck className="w-3 h-3 text-amber-600" />
                                AI Document Check Required
                              </span>
                            )}
                          </div>
                          <Badge
                            variant={isOpen ? "success" : "neutral"}
                            size="sm"
                            dot
                            className="uppercase font-bold shrink-0"
                          >
                            {isOpen ? t("queue_active") : t("queue_closed")}
                          </Badge>
                        </div>

                        {q.description && (
                          <p className="text-xs text-slate-500 mb-4 line-clamp-2 leading-relaxed">
                            {q.description}
                          </p>
                        )}

                        <div className="grid grid-cols-2 gap-2 text-xs bg-slate-50 p-3 rounded-xl mb-4 border border-slate-100">
                          <div>
                            <span className="text-slate-400 block text-[11px]">
                              {t("waiting_count")}
                            </span>
                            <span className="font-extrabold text-slate-900 text-sm">
                              {q.waitingCount ?? 0}
                            </span>
                          </div>
                          <div className="text-right">
                            <span className="text-slate-400 block text-[11px]">
                              {t("estimated_wait")}
                            </span>
                            <span className="font-extrabold text-teal-700 text-sm">
                              ~{q.estimatedWaitMinutes ?? (q.waitingCount ? q.waitingCount * 5 : 0)}{" "}
                              {t("minutes")}
                            </span>
                          </div>
                        </div>
                      </div>

                      {existingToken ? (
                        <div className="flex items-center gap-2 pt-2">
                          <Button
                            onClick={() => navigate(`/token/${existingToken.id}`)}
                            variant="primary"
                            size="sm"
                            className="flex-1 font-bold bg-teal-600 hover:bg-teal-700 shadow-sm"
                            icon={<ArrowRight className="w-4 h-4" />}
                            iconPosition="right"
                          >
                            View My Token ({existingToken.tokenNumber})
                          </Button>
                          <Button
                            onClick={() => setConfirmLeaveId(existingToken.id)}
                            variant="outline"
                            size="sm"
                            className="text-rose-600 border-rose-200 hover:bg-rose-50 font-semibold"
                            title="Leave this queue to take a new token"
                          >
                            Leave
                          </Button>
                          <button
                            type="button"
                            onClick={() => setQrModalQueue(q)}
                            className="p-2 text-slate-500 hover:text-teal-700 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-xl transition flex items-center justify-center shrink-0 cursor-pointer"
                            title="Scan QR Code to Join on Mobile"
                          >
                            <QrCode className="w-4 h-4" />
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-2">
                          <Button
                            onClick={() => {
                              if (q.requiresDocumentVerification) {
                                setPreJoinQueue(q);
                              } else {
                                handleJoinQueue(q.id);
                              }
                            }}
                            disabled={!isOpen || isJoining}
                            loading={isJoining}
                            variant="primary"
                            size="sm"
                            className="flex-1 font-bold"
                            icon={
                              q.requiresDocumentVerification ? (
                                <ShieldCheck className="w-4 h-4" />
                              ) : (
                                <ArrowRight className="w-4 h-4" />
                              )
                            }
                            iconPosition="right"
                          >
                            {isJoining
                              ? t("joining_queue")
                              : q.requiresDocumentVerification
                              ? "Verify Docs & Join"
                              : t("join_queue")}
                          </Button>

                          <button
                            type="button"
                            onClick={() => setQrModalQueue(q)}
                            className="p-2 text-slate-500 hover:text-teal-700 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-xl transition flex items-center justify-center shrink-0 cursor-pointer"
                            title="Scan QR Code to Join on Mobile"
                          >
                            <QrCode className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </Card>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </Card>

      {/* Pre-Join Document Analysis Modal */}
      {preJoinQueue && (
        <PreJoinDocumentModal
          isOpen={!!preJoinQueue}
          queue={preJoinQueue}
          onClose={() => setPreJoinQueue(null)}
          onSuccess={(tokenId) => {
            setPreJoinQueue(null);
            queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
            navigate(`/token/${tokenId}`);
          }}
        />
      )}

      {/* Queue QR Code Modal */}
      {qrModalQueue && (
        <QueueQRCodeModal
          isOpen={!!qrModalQueue}
          queueId={qrModalQueue.id}
          queueName={qrModalQueue.name}
          onClose={() => setQrModalQueue(null)}
        />
      )}
    </div>
  );
};
