import React, { useState, useEffect } from "react";
import { getQueueQr, getLanIp } from "../lib/apiClient";
import { QrCodeInfo } from "../types/queue";
import { useLanguage } from "../i18n/LanguageContext";
import { X, QrCode, Download, Copy, Check, Smartphone, Globe, Info } from "lucide-react";

interface Props {
  isOpen: boolean;
  queueId: string;
  queueName: string;
  onClose: () => void;
}

export const QueueQRCodeModal: React.FC<Props> = ({ isOpen, queueId, queueName, onClose }) => {
  const { t } = useLanguage();
  const [qrInfo, setQrInfo] = useState<QrCodeInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  const defaultOrigin = typeof window !== "undefined" ? window.location.origin : "http://localhost:5173";
  const isLocalhost = typeof window !== "undefined" && (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1");

  // Dynamic base URL for mobile network testing (e.g., local WiFi IP)
  const [customBaseUrl, setCustomBaseUrl] = useState<string>(() => {
    if (isLocalhost) {
      return `${window.location.protocol}//10.30.7.37:${window.location.port || "5173"}`;
    }
    return defaultOrigin;
  });
  const [useNetworkIp, setUseNetworkIp] = useState(isLocalhost);

  // Fetch actual LAN IP when opening modal on localhost
  useEffect(() => {
    if (isOpen && isLocalhost) {
      getLanIp().then((ip) => {
        if (ip) {
          const lanUrl = `${window.location.protocol}//${ip}:${window.location.port || "5173"}`;
          setCustomBaseUrl(lanUrl);
        }
      });
    }
  }, [isOpen, isLocalhost]);

  const activeBaseUrl = useNetworkIp ? customBaseUrl : defaultOrigin;

  useEffect(() => {
    if (isOpen && queueId) {
      loadQrInfo(activeBaseUrl);
    }
  }, [isOpen, queueId, activeBaseUrl]);

  const loadQrInfo = async (baseUrl: string) => {
    setLoading(true);
    setErrorMsg("");
    const res = await getQueueQr(queueId, 320, baseUrl);
    if (res.success && res.data) {
      setQrInfo(res.data);
    } else {
      setErrorMsg(res.error?.message || "Failed to load QR code");
    }
    setLoading(false);
  };

  const fullUrl = qrInfo
    ? qrInfo.deepLinkUrl.startsWith("http")
      ? qrInfo.deepLinkUrl
      : `${activeBaseUrl}${qrInfo.deepLinkUrl}`
    : "";

  const handleCopy = async () => {
    if (!fullUrl) return;
    try {
      await navigator.clipboard.writeText(fullUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Failed to copy link", err);
    }
  };

  const handleDownload = () => {
    if (!qrInfo?.qrDataUri) return;
    const a = document.createElement("a");
    a.href = qrInfo.qrDataUri;
    a.download = `queue-qr-${queueName.toLowerCase().replace(/\s+/g, "-")}.png`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm overflow-hidden animate-fadeIn text-center border border-slate-200">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50">
          <div className="flex items-center space-x-2">
            <QrCode className="w-5 h-5 text-teal-600" />
            <h3 className="text-base font-bold text-slate-900">{t("qr_code")}</h3>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* QR Content */}
        <div className="p-6 space-y-4">
          <div>
            <h4 className="text-lg font-extrabold text-slate-900">{queueName}</h4>
            <p className="text-xs text-slate-500 mt-0.5">{t("scan_qr_to_join")}</p>
          </div>

          {/* Network IP Switch for Mobile Testing */}
          {isLocalhost && (
            <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-2.5 text-left text-xs space-y-2">
              <div className="flex items-center justify-between font-semibold text-slate-700">
                <span className="flex items-center gap-1.5">
                  <Smartphone className="w-3.5 h-3.5 text-teal-600" />
                  Mobile Phone Scanning
                </span>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={useNetworkIp}
                    onChange={(e) => setUseNetworkIp(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-8 h-4 bg-slate-300 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:bg-teal-600"></div>
                </label>
              </div>

              {useNetworkIp ? (
                <div>
                  <label className="text-[10px] text-slate-400 uppercase font-bold block mb-1">
                    Your Computer's Wi-Fi IP / Host
                  </label>
                  <div className="flex items-center gap-1">
                    <input
                      type="text"
                      value={customBaseUrl}
                      onChange={(e) => setCustomBaseUrl(e.target.value)}
                      className="flex-1 px-2 py-1 text-xs font-mono bg-white border border-slate-200 rounded-lg outline-none focus:border-teal-500"
                    />
                    <button
                      type="button"
                      onClick={() => loadQrInfo(customBaseUrl)}
                      className="px-2 py-1 text-[11px] font-bold bg-teal-50 text-teal-700 rounded-lg border border-teal-200 hover:bg-teal-100"
                    >
                      Update
                    </button>
                  </div>
                  <p className="text-[10px] text-amber-800 bg-amber-50 rounded-md p-1.5 mt-1.5 border border-amber-200 leading-tight">
                    Notice: Phone & PC must be connected to the <strong>same Wi-Fi network</strong> (turn off mobile 5G data on phone). Or paste a tunnel URL (e.g. ngrok) above.
                  </p>
                </div>
              ) : (
                <p className="text-[11px] text-slate-500 flex items-center gap-1">
                  <Globe className="w-3 h-3 text-slate-400" />
                  Encoding: <code className="font-mono text-[10px] bg-slate-200 px-1 rounded">{defaultOrigin}</code>
                </p>
              )}
            </div>
          )}

          {loading ? (
            <div className="py-12 text-center text-sm text-slate-400 font-medium">{t("loading")}</div>
          ) : qrInfo ? (
            <div className="flex flex-col items-center">
              <div className="p-3 bg-white border-2 border-teal-100 rounded-2xl shadow-inner">
                <img
                  src={qrInfo.qrDataUri}
                  alt={`QR for ${queueName}`}
                  className="w-52 h-52 object-contain"
                />
              </div>

              {/* Action Buttons */}
              <div className="grid grid-cols-2 gap-3 w-full mt-4">
                <button
                  type="button"
                  onClick={handleDownload}
                  className="flex items-center justify-center space-x-1.5 px-3 py-2 text-xs font-bold text-teal-700 bg-teal-50 hover:bg-teal-100 border border-teal-200 rounded-xl transition"
                >
                  <Download className="w-4 h-4" />
                  <span>{t("download_qr")}</span>
                </button>

                <button
                  type="button"
                  onClick={handleCopy}
                  className="flex items-center justify-center space-x-1.5 px-3 py-2 text-xs font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 border border-slate-200 rounded-xl transition"
                >
                  {copied ? (
                    <>
                      <Check className="w-4 h-4 text-emerald-600" />
                      <span className="text-emerald-700">Copied!</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-4 h-4" />
                      <span>{t("copy_link")}</span>
                    </>
                  )}
                </button>
              </div>

              {fullUrl && (
                <p className="text-[10px] text-slate-400 mt-2 break-all max-w-full font-mono">
                  {fullUrl}
                </p>
              )}
            </div>
          ) : (
            <div className="py-8 text-sm text-rose-500">{errorMsg || "Error generating QR"}</div>
          )}
        </div>
      </div>
    </div>
  );
};
