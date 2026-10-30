import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { generateToken, uploadTokenDocument, leaveToken, QueueResponse } from "../lib/apiClient";
import { DocumentVerificationStatus } from "../types/queue";
import {
  X,
  ShieldCheck,
  FileText,
  UploadCloud,
  CheckCircle2,
  XCircle,
  RefreshCw,
  AlertTriangle,
  Loader2,
  Sparkles,
  ArrowRight,
} from "lucide-react";
import { Button } from "./ui/Button";

interface Props {
  isOpen: boolean;
  queue: QueueResponse | null;
  onClose: () => void;
  onSuccess?: (tokenId: string) => void;
}

const DOCUMENT_TYPES = [
  { value: "GOVERNMENT_ID", label: "National ID / Passport / Aadhaar" },
  { value: "DRIVERS_LICENSE", label: "Driver's License" },
  { value: "UTILITY_BILL", label: "Address Proof / Utility Bill" },
  { value: "STUDENT_ID", label: "Student / Academic ID Card" },
  { value: "GENERAL_DOCUMENT", label: "General Attestation Document" },
];

export const PreJoinDocumentModal: React.FC<Props> = ({
  isOpen,
  queue,
  onClose,
  onSuccess,
}) => {
  const navigate = useNavigate();

  const [documentType, setDocumentType] = useState("GOVERNMENT_ID");
  const [fileName, setFileName] = useState("");
  const [fileContentType, setFileContentType] = useState("application/pdf");
  const [fileData, setFileData] = useState("");

  const [analyzing, setAnalyzing] = useState(false);
  const [analysisStep, setAnalysisStep] = useState<"idle" | "token" | "ai" | "done">("idle");
  const [error, setError] = useState<string | null>(null);
  const [verdict, setVerdict] = useState<{
    status: DocumentVerificationStatus;
    confidence?: number;
    summary?: string;
  } | null>(null);

  if (!isOpen || !queue) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setError("File size exceeds 5MB limit.");
      return;
    }

    setError(null);
    setFileName(file.name);
    setFileContentType(file.type || "application/pdf");

    const reader = new FileReader();
    reader.onload = () => {
      const res = reader.result as string;
      const base64 = res.includes(",") ? res.split(",")[1] : res;
      setFileData(base64);
    };
    reader.onerror = () => {
      setError("Failed to read selected file.");
    };
    reader.readAsDataURL(file);
  };

  const handleAnalyzeAndJoin = async () => {
    if (!fileData) {
      setError("Please select a document file to proceed.");
      return;
    }

    setAnalyzing(true);
    setError(null);
    setAnalysisStep("token");

    try {
      // 1. Issue live digital token for the queue
      const tokenRes = await generateToken(queue.id);
      if (!tokenRes.success || !tokenRes.data) {
        throw new Error(tokenRes.error?.message || "Failed to generate queue token.");
      }
      const token = tokenRes.data;

      // 2. Upload and invoke AI Document Verification engine
      setAnalysisStep("ai");
      const uploadRes = await uploadTokenDocument(token.id, {
        documentType,
        fileName: fileName || "uploaded_id.pdf",
        fileContentType,
        fileData,
      });

      if (!uploadRes.success || !uploadRes.data) {
        throw new Error(uploadRes.error?.message || "Document analysis failed.");
      }

      const doc = uploadRes.data;
      const isApproved =
        doc.verificationStatus === DocumentVerificationStatus.VERIFIED ||
        doc.verificationStatus === DocumentVerificationStatus.MANUALLY_APPROVED;

      setVerdict({
        status: doc.verificationStatus,
        confidence: doc.aiConfidence,
        summary:
          doc.failureReason ||
          (isApproved
            ? "Automated AI checks passed. Document verified successfully."
            : "Document verification failed. Please provide an authentic, readable document."),
      });

      if (isApproved) {
        setAnalysisStep("done");
        // Briefly present success verdict and route to the active token page
        setTimeout(() => {
          if (onSuccess) {
            onSuccess(token.id);
          } else {
            navigate(`/token/${token.id}`);
          }
          onClose();
        }, 1600);
      } else {
        // Verification failed / rejected: cancel the unverified token so it doesn't take up queue space
        try {
          await leaveToken(token.id);
        } catch {
          // Ignore cancellation errors
        }
        setAnalyzing(false);
        setAnalysisStep("idle");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Document analysis failed.");
      setAnalyzing(false);
      setAnalysisStep("idle");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-3xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header Banner */}
        <div className="bg-gradient-to-r from-teal-800 via-teal-700 to-slate-900 px-6 py-6 text-white relative">
          <button
            onClick={onClose}
            disabled={analyzing}
            className="absolute top-4 right-4 p-2 text-teal-200 hover:text-white rounded-xl hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex items-center gap-3">
            <div className="p-3 bg-white/10 rounded-2xl backdrop-blur-xs border border-white/20">
              <ShieldCheck className="w-6 h-6 text-teal-300" />
            </div>
            <div>
              <h2 className="text-lg font-black tracking-tight text-white flex items-center gap-2">
                Document Analysis Required
                <span className="text-[10px] font-bold uppercase tracking-wider bg-teal-400/20 text-teal-200 px-2 py-0.5 rounded-full border border-teal-300/30">
                  AI Gatekeeper
                </span>
              </h2>
              <p className="text-xs text-teal-100/90 mt-0.5">
                Service Queue: <strong className="text-white">{queue.name}</strong>
              </p>
            </div>
          </div>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5">
          {/* Informational Alert */}
          <div className="p-3.5 bg-amber-50/80 border border-amber-200 rounded-2xl flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="text-xs text-amber-900 space-y-1">
              <p className="font-bold">Before joining this queue, customer documents must be analyzed.</p>
              <p className="text-amber-800 leading-relaxed">
                The advisory AI engine validates identity, document authenticity, and format validity so counters can serve you without delays.
              </p>
            </div>
          </div>

          {error && (
            <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-2xl flex items-center gap-2.5 text-xs font-bold text-rose-700">
              <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {verdict ? (
            verdict.status === DocumentVerificationStatus.VERIFIED ||
            verdict.status === DocumentVerificationStatus.MANUALLY_APPROVED ? (
              /* Success Card */
              <div className="p-6 bg-slate-50 border border-slate-200 rounded-2xl text-center space-y-3">
                <div className="w-12 h-12 mx-auto rounded-2xl bg-emerald-100 text-emerald-700 flex items-center justify-center shadow-xs">
                  <CheckCircle2 className="w-7 h-7 text-emerald-600" />
                </div>
                <h3 className="text-base font-extrabold text-slate-900">
                  AI Document Verification Complete!
                </h3>
                <p className="text-xs text-slate-600 max-w-sm mx-auto">
                  {verdict.summary}
                </p>
                {verdict.confidence != null && (
                  <div className="inline-flex items-center gap-1.5 px-3 py-1 bg-emerald-50 border border-emerald-200 rounded-full text-xs font-bold text-emerald-800">
                    <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
                    <span>AI Confidence: {Math.round(verdict.confidence * 100)}%</span>
                  </div>
                )}
                <div className="pt-2 text-[11px] text-slate-400 font-medium">
                  Verification verified. Redirecting to your live token...
                </div>
              </div>
            ) : (
              /* Rejection Card */
              <div className="p-6 bg-rose-50/70 border border-rose-200 rounded-2xl text-center space-y-3">
                <div className="w-12 h-12 mx-auto rounded-2xl bg-rose-100 text-rose-700 flex items-center justify-center shadow-xs">
                  <XCircle className="w-7 h-7 text-rose-600" />
                </div>
                <h3 className="text-base font-extrabold text-rose-900">
                  Document Verification Failed
                </h3>
                <div className="p-3 bg-white/90 border border-rose-200 rounded-xl text-left">
                  <p className="text-xs text-rose-800 font-medium leading-relaxed">
                    {verdict.summary}
                  </p>
                </div>
                <p className="text-[11px] text-rose-700/80">
                  You cannot enter this service queue without a genuine, valid document matching the selected category.
                </p>
              </div>
            )
          ) : (
            /* Upload & Configure Form */
            <div className="space-y-4">
              {/* Document Type Dropdown */}
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  1. Document Category
                </label>
                <select
                  value={documentType}
                  onChange={(e) => setDocumentType(e.target.value)}
                  disabled={analyzing}
                  className="w-full text-xs font-medium px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-teal-500/20 focus:border-teal-600 outline-hidden transition-all"
                >
                  {DOCUMENT_TYPES.map((dt) => (
                    <option key={dt.value} value={dt.value}>
                      {dt.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Document File Selector */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider">
                    2. Upload Document (PDF / Image)
                  </label>
                </div>

                <div className="relative border-2 border-dashed border-slate-300 hover:border-teal-500 rounded-2xl p-4 text-center bg-slate-50/60 transition-colors">
                  <input
                    type="file"
                    accept=".pdf,.png,.jpg,.jpeg,.webp"
                    onChange={handleFileChange}
                    disabled={analyzing}
                    className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                  />
                  <div className="flex flex-col items-center justify-center space-y-1.5 pointer-events-none">
                    <UploadCloud className="w-8 h-8 text-teal-600 mb-1" />
                    <p className="text-xs font-bold text-slate-800">
                      {fileName ? fileName : "Drag & drop file or browse"}
                    </p>
                    <p className="text-[11px] text-slate-400">
                      Supports PDF, PNG, JPG up to 5MB
                    </p>
                  </div>
                </div>

                {fileName && (
                  <div className="mt-2 flex items-center justify-between text-xs px-3 py-2 bg-teal-50/80 border border-teal-200/80 rounded-xl text-teal-900 font-medium">
                    <span className="flex items-center gap-2 truncate">
                      <FileText className="w-4 h-4 text-teal-600 shrink-0" />
                      <span className="truncate">{fileName}</span>
                    </span>
                    <span className="text-[10px] text-teal-700 font-bold uppercase bg-white px-2 py-0.5 rounded-md border border-teal-200">
                      Ready
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        {!verdict ? (
          <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-between gap-3">
            <Button
              onClick={onClose}
              disabled={analyzing}
              variant="outline"
              size="sm"
            >
              Cancel
            </Button>

            <Button
              onClick={handleAnalyzeAndJoin}
              disabled={analyzing || !fileData}
              loading={analyzing}
              variant="primary"
              size="sm"
              icon={
                analyzing ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <ArrowRight className="w-4 h-4" />
                )
              }
              iconPosition="right"
              className="bg-teal-700 hover:bg-teal-800 shadow-md font-bold px-5"
            >
              {analysisStep === "token"
                ? "Reserving Token..."
                : analysisStep === "ai"
                ? "Analyzing with AI..."
                : "Analyze Document & Join Queue"}
            </Button>
          </div>
        ) : verdict.status === DocumentVerificationStatus.REJECTED ? (
          <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-between gap-3">
            <Button onClick={onClose} variant="outline" size="sm">
              Cancel
            </Button>
            <Button
              onClick={() => {
                setVerdict(null);
                setFileData("");
                setFileName("");
                setError(null);
              }}
              variant="primary"
              size="sm"
              icon={<RefreshCw className="w-4 h-4" />}
              className="bg-rose-700 hover:bg-rose-800 font-bold px-4"
            >
              Try Another Document
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
};
