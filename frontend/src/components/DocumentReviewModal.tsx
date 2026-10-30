import React, { useState, useEffect } from "react";
import { getTokenDocuments, reviewTokenDocument, getTokenDocumentStatus } from "../lib/apiClient";
import { TokenDocument, TokenDocumentStatus, DocumentVerificationStatus } from "../types/queue";
import {
  X,
  FileCheck2,
  FileX2,
  ShieldCheck,
  AlertTriangle,
  CheckCircle,
  FileText,
  Clock,
  Download,
  UserCheck,
} from "lucide-react";
import { Button } from "./ui/Button";

interface Props {
  isOpen: boolean;
  tokenId: string;
  tokenNumber: string;
  customerName?: string;
  onClose: () => void;
  onDocumentReviewed?: () => void;
  onForceCall?: () => void;
}

interface ParsedExtractedDetails {
  extracted?: {
    documentType?: string;
    documentNumber?: string;
    holderName?: string;
    issuer?: string;
    issueDate?: string;
    expiryDate?: string;
    isValidFormat?: boolean;
    readabilityScore?: number;
  } | null;
  checks: Array<{
    check: string;
    status: string; // PASSED, WARNING, FAILED
    details: string;
  }>;
  summary: string;
}

function parseExtractedData(raw?: string): ParsedExtractedDetails {
  if (!raw) return { extracted: null, checks: [], summary: "" };
  try {
    const data = JSON.parse(raw);
    return {
      extracted: data.extracted || null,
      checks: Array.isArray(data.checks) ? data.checks : [],
      summary: data.summary || "",
    };
  } catch {
    return { extracted: null, checks: [], summary: raw };
  }
}

export const DocumentReviewModal: React.FC<Props> = ({
  isOpen,
  tokenId,
  tokenNumber,
  customerName,
  onClose,
  onDocumentReviewed,
  onForceCall,
}) => {
  const [documents, setDocuments] = useState<TokenDocument[]>([]);
  const [statusSummary, setStatusSummary] = useState<TokenDocumentStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [reviewNotes, setReviewNotes] = useState<{ [docId: string]: string }>({});
  const [selectedDocPreview, setSelectedDocPreview] = useState<TokenDocument | null>(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  useEffect(() => {
    if (isOpen && tokenId) {
      loadDocuments();
    }
  }, [isOpen, tokenId]);

  const loadDocuments = async () => {
    setLoading(true);
    setErrorMsg("");
    try {
      const [docsRes, statusRes] = await Promise.all([
        getTokenDocuments(tokenId),
        getTokenDocumentStatus(tokenId),
      ]);

      if (docsRes.success && docsRes.data) {
        setDocuments(docsRes.data);
        if (docsRes.data.length > 0) {
          setSelectedDocPreview(docsRes.data[0]);
        }
      }
      if (statusRes.success && statusRes.data) {
        setStatusSummary(statusRes.data);
      }
    } catch (err: any) {
      setErrorMsg(err?.message || "Failed to load documents");
    } finally {
      setLoading(false);
    }
  };

  const handleReview = async (
    docId: string,
    action: "MANUALLY_APPROVED" | "REJECTED"
  ) => {
    setReviewingId(docId);
    setErrorMsg("");
    setSuccessMsg("");
    try {
      const notes = reviewNotes[docId] || "";
      const res = await reviewTokenDocument(docId, { status: action, notes });
      if (res.success && res.data) {
        setSuccessMsg(
          action === "MANUALLY_APPROVED"
            ? "Document approved successfully."
            : "Document marked as rejected."
        );
        await loadDocuments();
        if (onDocumentReviewed) {
          onDocumentReviewed();
        }
      } else {
        setErrorMsg(res.error?.message || "Failed to update review status");
      }
    } catch (err: any) {
      setErrorMsg(err?.message || "Review action failed");
    } finally {
      setReviewingId(null);
    }
  };

  if (!isOpen) return null;

  const renderStatusBadge = (status: DocumentVerificationStatus) => {
    switch (status) {
      case DocumentVerificationStatus.VERIFIED:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300">
            <CheckCircle className="w-3.5 h-3.5" /> AI Verified
          </span>
        );
      case DocumentVerificationStatus.MANUALLY_APPROVED:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300">
            <UserCheck className="w-3.5 h-3.5" /> Staff Approved
          </span>
        );
      case DocumentVerificationStatus.REJECTED:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-300">
            <FileX2 className="w-3.5 h-3.5" /> Rejected
          </span>
        );
      case DocumentVerificationStatus.PENDING:
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
            <Clock className="w-3.5 h-3.5" /> Pending Review
          </span>
        );
    }
  };

  const formatDocType = (type: string) => {
    switch (type) {
      case "GOVERNMENT_ID":
        return "Government ID / Passport / DL";
      case "MEDICAL_PRESCRIPTION":
        return "Medical Prescription";
      case "INCOME_PROOF":
        return "Income Proof / Pay Stub";
      default:
        return type.replace(/_/g, " ");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="relative w-full max-w-4xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-slate-50/50 dark:bg-slate-800/30">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-teal-100 dark:bg-teal-900/50 text-teal-700 dark:text-teal-400 flex items-center justify-center">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-slate-900 dark:white">
                  Document Verification Review
                </h3>
                <span className="px-2 py-0.5 text-xs font-bold rounded bg-teal-100 dark:bg-teal-950/80 text-teal-800 dark:text-teal-300">
                  {tokenNumber}
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {customerName ? `Customer: ${customerName}` : "Customer verification gatekeeper"}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Status bar */}
        {statusSummary && (
          <div className="px-6 py-2.5 bg-slate-100/70 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between text-xs gap-2">
            <div className="flex items-center gap-2">
              <span className="font-medium text-slate-600 dark:text-slate-300">
                Gatekeeper Status:
              </span>
              {statusSummary.isEligibleForService ? (
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400">
                  <CheckCircle className="w-3.5 h-3.5" /> Eligible for Service
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 font-semibold text-rose-600 dark:text-rose-400">
                  <AlertTriangle className="w-3.5 h-3.5" /> Documents Required / Unverified
                </span>
              )}
            </div>
            <div className="text-slate-500 dark:text-slate-400 font-medium">
              Status: <span className="font-bold text-slate-700 dark:text-slate-300">{statusSummary.status}</span> | Uploaded Documents: {statusSummary.documentCount}
            </div>
          </div>
        )}

        {/* Notifications */}
        {errorMsg && (
          <div className="mx-6 mt-4 p-3 rounded-lg bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900/50 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}
        {successMsg && (
          <div className="mx-6 mt-4 p-3 rounded-lg bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-900/50 text-emerald-700 dark:text-emerald-300 text-xs flex items-center gap-2">
            <CheckCircle className="w-4 h-4 shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading ? (
            <div className="py-16 text-center text-slate-400 dark:text-slate-500">
              <div className="w-8 h-8 border-2 border-teal-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
              <p className="text-sm">Loading uploaded customer documents...</p>
            </div>
          ) : documents.length === 0 ? (
            <div className="py-12 text-center rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-8">
              <FileX2 className="w-12 h-12 text-slate-400 mx-auto mb-3" />
              <h4 className="text-base font-semibold text-slate-700 dark:text-slate-300">
                No Documents Uploaded Yet
              </h4>
              <p className="text-sm text-slate-500 dark:text-slate-400 max-w-sm mx-auto mt-1">
                The customer has not submitted documents for this token. If they brought physical documents, you can manually override and force call them.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {documents.map((doc) => {
                const isSelected = selectedDocPreview?.id === doc.id;
                const isReviewing = reviewingId === doc.id;
                const parsed = parseExtractedData(doc.extractedData);
                const hasExtracted = !!parsed.extracted && Object.keys(parsed.extracted).length > 0;
                const hasChecks = parsed.checks.length > 0;

                const fileUrl = doc.fileData
                  ? doc.fileData.startsWith("data:")
                    ? doc.fileData
                    : `data:${doc.fileContentType || "application/octet-stream"};base64,${doc.fileData}`
                  : null;

                const isImage =
                  doc.fileContentType?.startsWith("image/") ||
                  doc.fileName.toLowerCase().match(/\.(png|jpg|jpeg|webp)$/);

                return (
                  <div
                    key={doc.id}
                    className={`rounded-xl border transition-all ${
                      isSelected
                        ? "border-teal-400 dark:border-teal-600 bg-teal-50/20 dark:bg-teal-950/20 shadow-sm"
                        : "border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-850"
                    } p-5 space-y-4`}
                  >
                    {/* Top Row: Type, Status & Confidence */}
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className="p-2.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                          <FileText className="w-5 h-5" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <h4 className="font-semibold text-slate-900 dark:text-white text-sm">
                              {formatDocType(doc.documentType)}
                            </h4>
                            {renderStatusBadge(doc.verificationStatus)}
                          </div>
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            {doc.fileName || "document"}
                            {doc.fileData && ` • ${(doc.fileData.length * 0.75 / 1024).toFixed(1)} KB`}
                          </p>
                        </div>
                      </div>

                      {doc.aiConfidence !== undefined && doc.aiConfidence !== null && (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-500 dark:text-slate-400">AI Confidence:</span>
                          <span className="text-xs font-bold text-slate-800 dark:text-slate-200">
                            {Math.round(doc.aiConfidence * 100)}%
                          </span>
                          <div className="w-16 h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                            <div
                              className={`h-full ${
                                doc.aiConfidence >= 0.8
                                  ? "bg-emerald-500"
                                  : doc.aiConfidence >= 0.6
                                  ? "bg-amber-500"
                                  : "bg-rose-500"
                              }`}
                              style={{ width: `${Math.round(doc.aiConfidence * 100)}%` }}
                            />
                          </div>
                        </div>
                      )}
                    </div>

                    {/* AI Extracted OCR Metadata */}
                    {hasExtracted && parsed.extracted && (
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-slate-100 dark:border-slate-800/80">
                        {parsed.extracted.documentNumber && (
                          <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/50">
                            <span className="text-[10px] text-slate-400 block font-medium">Doc Number</span>
                            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                              {parsed.extracted.documentNumber}
                            </span>
                          </div>
                        )}
                        {parsed.extracted.holderName && (
                          <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/50">
                            <span className="text-[10px] text-slate-400 block font-medium">Holder Name</span>
                            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                              {parsed.extracted.holderName}
                            </span>
                          </div>
                        )}
                        {parsed.extracted.expiryDate && (
                          <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/50">
                            <span className="text-[10px] text-slate-400 block font-medium">Expiry Date</span>
                            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                              {parsed.extracted.expiryDate}
                            </span>
                          </div>
                        )}
                        {parsed.extracted.issuer && (
                          <div className="p-2 rounded bg-slate-50 dark:bg-slate-800/50">
                            <span className="text-[10px] text-slate-400 block font-medium">Issuer / Authority</span>
                            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                              {parsed.extracted.issuer}
                            </span>
                          </div>
                        )}
                      </div>
                    )}

                    {/* AI Verification Checks */}
                    {hasChecks && (
                      <div className="pt-2">
                        <span className="text-xs font-medium text-slate-600 dark:text-slate-300 block mb-1.5">
                          Verification Validation Checks:
                        </span>
                        <div className="flex flex-wrap gap-1.5">
                          {parsed.checks.map((chk, idx) => (
                            <span
                              key={idx}
                              className={`text-[11px] px-2 py-0.5 rounded-md flex items-center gap-1 ${
                                chk.status === "PASSED"
                                  ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300"
                                  : chk.status === "WARNING"
                                  ? "bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300"
                                  : "bg-rose-50 text-rose-700 dark:bg-rose-950/40 dark:text-rose-300"
                              }`}
                            >
                              {chk.status === "PASSED" ? (
                                <CheckCircle className="w-3 h-3 text-emerald-600" />
                              ) : (
                                <AlertTriangle className="w-3 h-3 text-rose-600" />
                              )}
                              {chk.check}: {chk.details || chk.status}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Rejection / Failure Reason */}
                    {doc.failureReason && (
                      <div className="p-2.5 rounded-lg bg-rose-50/80 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-900/40 text-xs text-rose-700 dark:text-rose-300">
                        <span className="font-semibold block mb-0.5">Verification Notes:</span>
                        <p>{doc.failureReason}</p>
                      </div>
                    )}

                    {/* Document Preview / File Download */}
                    {fileUrl && (
                      <div className="pt-1">
                        {isImage ? (
                          <div className="rounded-lg overflow-hidden border border-slate-200 dark:border-slate-700 max-h-48 flex items-center justify-center bg-slate-100 dark:bg-slate-900">
                            <img
                              src={fileUrl}
                              alt={doc.fileName || "Document"}
                              className="object-contain max-h-48 w-full"
                            />
                          </div>
                        ) : (
                          <a
                            href={fileUrl}
                            download={doc.fileName || "uploaded_document"}
                            className="inline-flex items-center gap-1.5 text-xs text-teal-600 dark:text-teal-400 hover:underline font-semibold"
                          >
                            <Download className="w-3.5 h-3.5" /> Download / Inspect File ({doc.fileName})
                          </a>
                        )}
                      </div>
                    )}

                    {/* Staff Review Actions */}
                    <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex flex-wrap items-center justify-between gap-3">
                      <div className="flex-1 min-w-[200px]">
                        <input
                          type="text"
                          placeholder="Optional staff review notes..."
                          value={reviewNotes[doc.id] || ""}
                          onChange={(e) =>
                            setReviewNotes({ ...reviewNotes, [doc.id]: e.target.value })
                          }
                          className="w-full text-xs px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-800 dark:text-slate-200 focus:outline-none focus:ring-1 focus:ring-teal-500"
                        />
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={isReviewing}
                          onClick={() => handleReview(doc.id, "REJECTED")}
                          className="text-xs text-rose-600 border-rose-300 dark:border-rose-800 hover:bg-rose-50 dark:hover:bg-rose-950/30"
                        >
                          <FileX2 className="w-3.5 h-3.5 mr-1" />
                          Reject
                        </Button>
                        <Button
                          size="sm"
                          disabled={isReviewing}
                          onClick={() => handleReview(doc.id, "MANUALLY_APPROVED")}
                          className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
                        >
                          <FileCheck2 className="w-3.5 h-3.5 mr-1" />
                          Approve Document
                        </Button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-slate-50 dark:bg-slate-800/40 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
          <div className="text-xs text-slate-500 dark:text-slate-400">
            {onForceCall && (
              <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                <AlertTriangle className="w-3.5 h-3.5" /> Staff override bypasses document requirement
              </span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <Button variant="outline" size="sm" onClick={onClose}>
              Close
            </Button>
            {onForceCall && (
              <Button
                size="sm"
                variant="amber"
                onClick={() => {
                  onClose();
                  onForceCall();
                }}
                className="bg-amber-600 hover:bg-amber-700 text-white"
              >
                <ShieldCheck className="w-4 h-4 mr-1.5" />
                Force Call (Staff Override)
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
