import React, { useEffect, useState } from "react";
import { useSearchParams, useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "../i18n/LanguageContext";
import { getQueue, generateToken, getCustomerActiveTokens, QueueResponse, CustomerTokenDto } from "../lib/apiClient";
import { Header } from "../components/Header";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent } from "../components/ui/Card";
import { Users, Clock, ArrowRight, ShieldCheck, AlertCircle, Sparkles, MapPin, FileCheck } from "lucide-react";
import { PreJoinDocumentModal } from "../components/PreJoinDocumentModal";

export const DeepLinkJoinPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user, isLoading: authLoading } = useAuth();
  const { t } = useTranslation();

  const queueId = searchParams.get("queueId");

  const [queue, setQueue] = useState<QueueResponse | null>(null);
  const [existingToken, setExistingToken] = useState<CustomerTokenDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [joining, setJoining] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [joinError, setJoinError] = useState("");
  const [showPreJoinModal, setShowPreJoinModal] = useState(false);

  useEffect(() => {
    if (queueId) {
      loadQueue(queueId);
      if (user) {
        getCustomerActiveTokens().then((res) => {
          if (res.success && res.data) {
            const found = res.data.find((t) => t.queueId === queueId);
            if (found) setExistingToken(found);
          }
        });
      }
    } else {
      setLoading(false);
      setLoadError("No queue ID specified in link");
    }
  }, [queueId, user]);

  const loadQueue = async (id: string) => {
    setLoading(true);
    setLoadError("");
    const res = await getQueue(id);
    if (res.success && res.data) {
      setQueue(res.data);
    } else {
      setLoadError(res.error?.message || "Queue not found or unavailable");
    }
    setLoading(false);
  };

  const handleJoin = async () => {
    if (!queueId || !queue) return;
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(`/join?queueId=${queueId}`)}`);
      return;
    }

    if (queue.requiresDocumentVerification) {
      setShowPreJoinModal(true);
      return;
    }

    setJoining(true);
    setJoinError("");

    const res = await generateToken(queueId);
    if (res.success && res.data) {
      navigate(`/token/${res.data.id}`);
    } else {
      setJoinError(res.error?.message || "Failed to join queue");
      setJoining(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Header />

      <main className="flex-1 flex items-center justify-center p-4 sm:p-6">
        <div className="w-full max-w-md">
          <Card className="overflow-hidden shadow-elevated border-slate-200/80">
            {/* Card Header Banner */}
            <div className="bg-gradient-to-r from-teal-700 to-slate-900 px-6 py-8 text-white text-center">
              <div className="inline-flex p-3 bg-white/10 rounded-2xl mb-3 backdrop-blur-xs shadow-xs">
                <Sparkles className="w-7 h-7 text-teal-200" />
              </div>
              <h1 className="text-xl font-extrabold tracking-tight">{t("join_via_link")}</h1>
              <p className="text-teal-100/90 text-xs mt-1">{t("queue_overview")}</p>
            </div>

            {/* Card Body */}
            <CardContent className="p-6 sm:p-8 space-y-6">
              {loading || authLoading ? (
                <div className="py-12 text-center text-xs text-slate-400">{t("loading")}</div>
              ) : loadError ? (
                <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl text-center space-y-3">
                  <AlertCircle className="w-8 h-8 text-rose-500 mx-auto" />
                  <p className="text-xs font-bold text-rose-700">{loadError}</p>
                  <Link to="/dashboard">
                    <Button variant="outline" size="xs">
                      {t("nav_dashboard")}
                    </Button>
                  </Link>
                </div>
              ) : queue ? (
                <div className="space-y-6">
                  {/* Queue Meta */}
                  <div className="text-center space-y-1">
                    <h2 className="text-2xl font-black text-slate-900 tracking-tight leading-snug">
                      {queue.name}
                    </h2>
                    {queue.description && (
                      <p className="text-xs text-slate-500 leading-relaxed">{queue.description}</p>
                    )}
                    <div className="pt-2">
                      <Badge
                        variant={queue.status === "ACTIVE" ? "success" : "danger"}
                        size="md"
                        dot
                        className="uppercase font-bold"
                      >
                        {queue.status === "ACTIVE" ? t("queue_active") : t("queue_closed")}
                      </Badge>
                    </div>
                  </div>

                  {/* Queue Stats Cards */}
                  <div className="grid grid-cols-2 gap-3 p-4 bg-slate-50 rounded-2xl border border-slate-100">
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 bg-teal-50 text-teal-600 rounded-xl border border-teal-100">
                        <Users className="w-5 h-5" />
                      </div>
                      <div>
                        <span className="text-[11px] text-slate-400 block font-medium">
                          {t("waiting_count")}
                        </span>
                        <span className="text-lg font-black text-slate-900">
                          {queue.waitingCount ?? 0}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <div className="p-2.5 bg-teal-50 text-teal-600 rounded-xl border border-teal-100">
                        <Clock className="w-5 h-5" />
                      </div>
                      <div>
                        <span className="text-[11px] text-slate-400 block font-medium">
                          {t("estimated_wait")}
                        </span>
                        <span className="text-lg font-black text-teal-700">
                          ~{queue.estimatedWaitMinutes ?? (queue.waitingCount ?? 0) * 5} {t("minutes")}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Document Verification Notice if queue requires docs */}
                  {queue.requiresDocumentVerification && (
                    <div className="p-3.5 bg-amber-50/90 border border-amber-200 rounded-2xl flex items-start gap-2.5 text-left">
                      <ShieldCheck className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                      <div className="text-xs text-amber-900 space-y-0.5">
                        <p className="font-bold">AI Document Analysis Required</p>
                        <p className="text-amber-800 text-[11px] leading-relaxed">
                          This service requires your documents to be analyzed before you enter the waiting queue. You will be prompted to provide your document next.
                        </p>
                      </div>
                    </div>
                  )}

                  {/* Active Token Notice */}
                  {existingToken && (
                    <div className="p-4 bg-teal-50 border border-teal-200 rounded-2xl text-left space-y-2">
                      <div className="flex items-center gap-2 text-teal-800 font-bold text-sm">
                        <Sparkles className="w-4 h-4 text-teal-600 shrink-0" />
                        <span>You are already in this queue!</span>
                      </div>
                      <p className="text-xs text-teal-700">
                        Your active token is <span className="font-bold font-mono">{existingToken.tokenNumber}</span> (Position #{existingToken.position ?? 1}).
                      </p>
                    </div>
                  )}

                  {/* Inline Join Error */}
                  {joinError && (
                    <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-left space-y-2">
                      <div className="flex items-center gap-2 text-rose-700 font-bold text-xs">
                        <AlertCircle className="w-4 h-4 text-rose-500 shrink-0" />
                        <span>{joinError}</span>
                      </div>
                      {joinError.toLowerCase().includes("active token") && (
                        <div className="pt-1">
                          <Link to="/dashboard">
                            <Button variant="outline" size="xs" className="text-teal-700 border-teal-300">
                              View My Active Token
                            </Button>
                          </Link>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Guest Notice */}
                  {!user && (
                    <div className="p-3 bg-teal-50/70 border border-teal-100 rounded-xl text-xs text-teal-800 text-center">
                      <p className="font-medium">Sign in or create an account to issue and track your token live on your phone.</p>
                    </div>
                  )}

                  {/* Security Notice */}
                  <div className="flex items-center gap-1.5 text-[11px] text-slate-400 justify-center">
                    <ShieldCheck className="w-3.5 h-3.5 text-slate-400" />
                    <span>Real-time digital token generation</span>
                  </div>

                  {/* Join Action Button */}
                  {existingToken ? (
                    <Button
                      onClick={() => navigate(`/token/${existingToken.id}`)}
                      variant="primary"
                      size="lg"
                      fullWidth
                      icon={<ArrowRight className="w-4 h-4" />}
                      iconPosition="right"
                      className="font-bold py-3.5 shadow-md bg-teal-600 hover:bg-teal-700"
                    >
                      View My Ticket ({existingToken.tokenNumber})
                    </Button>
                  ) : (
                    <Button
                      onClick={handleJoin}
                      disabled={joining || queue.status !== "ACTIVE"}
                      loading={joining}
                      variant="primary"
                      size="lg"
                      fullWidth
                      icon={<ArrowRight className="w-4 h-4" />}
                      iconPosition="right"
                      className="font-bold py-3.5 shadow-md"
                    >
                      {!user
                        ? "Sign In & Join Queue"
                        : queue.requiresDocumentVerification
                          ? "Verify Document & Join"
                          : t("confirm_and_join")}
                    </Button>
                  )}

                  {!user && (
                    <p className="text-center text-xs text-slate-500 mt-2">
                      New to SmartQueue?{" "}
                      <Link
                        to={`/register?redirect=${encodeURIComponent(`/join?queueId=${queueId}`)}`}
                        className="text-teal-700 hover:text-teal-800 font-bold hover:underline"
                      >
                        Create an account
                      </Link>
                    </p>
                  )}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>
      </main>

      {/* Pre-Join Document Modal */}
      {showPreJoinModal && queue && (
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
export default DeepLinkJoinPage;
