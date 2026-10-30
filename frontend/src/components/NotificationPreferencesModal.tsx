import React, { useState, useEffect } from "react";
import { getNotificationPreferences, updateNotificationPreferences } from "../lib/apiClient";
import { UserNotificationPreference } from "../types/queue";
import { useLanguage } from "../i18n/LanguageContext";
import { X, Bell, Mail, MessageSquare, Smartphone, Sliders, Check } from "lucide-react";

interface Props {
  isOpen: boolean;
  onClose: () => void;
}

export const NotificationPreferencesModal: React.FC<Props> = ({ isOpen, onClose }) => {
  const { t } = useLanguage();
  const [pref, setPref] = useState<UserNotificationPreference | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  useEffect(() => {
    if (isOpen) {
      loadPreferences();
    }
  }, [isOpen]);

  const loadPreferences = async () => {
    setLoading(true);
    setErrorMsg("");
    setSuccessMsg("");
    const res = await getNotificationPreferences();
    if (res.success && res.data) {
      setPref(res.data);
    } else {
      setErrorMsg(res.error?.message || "Failed to load preferences");
    }
    setLoading(false);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pref) return;

    setSaving(true);
    setErrorMsg("");
    setSuccessMsg("");

    const res = await updateNotificationPreferences({
      inAppEnabled: pref.inAppEnabled,
      emailEnabled: pref.emailEnabled,
      smsEnabled: pref.smsEnabled,
      pushEnabled: pref.pushEnabled,
      positionNearThreshold: pref.positionNearThreshold,
    });

    if (res.success && res.data) {
      setPref(res.data);
      setSuccessMsg(t("preferences_saved") || "Preferences saved successfully!");
      setTimeout(() => {
        setSuccessMsg("");
        onClose();
      }, 1200);
    } else {
      setErrorMsg(res.error?.message || "Failed to save preferences");
    }
    setSaving(false);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md overflow-hidden animate-fadeIn">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50">
          <div className="flex items-center space-x-2">
            <Sliders className="w-5 h-5 text-indigo-600" />
            <h3 className="text-lg font-semibold text-gray-900">{t("notification_preferences")}</h3>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSave} className="p-6 space-y-5">
          {errorMsg && (
            <div className="p-3 text-sm text-red-700 bg-red-50 rounded-lg border border-red-200">
              {errorMsg}
            </div>
          )}
          {successMsg && (
            <div className="p-3 text-sm text-green-700 bg-green-50 rounded-lg border border-green-200 flex items-center space-x-2">
              <Check className="w-4 h-4" />
              <span>{successMsg}</span>
            </div>
          )}

          {loading ? (
            <div className="py-8 text-center text-gray-500">{t("loading")}</div>
          ) : pref ? (
            <>
              <div className="space-y-4">
                {/* In-App Toggle */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div className="flex items-center space-x-3">
                    <Bell className="w-5 h-5 text-indigo-600" />
                    <div>
                      <div className="text-sm font-medium text-gray-900">{t("in_app_notifications")}</div>
                      <div className="text-xs text-gray-500">Live alerts & bell stream</div>
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={pref.inAppEnabled}
                    onChange={(e) => setPref({ ...pref, inAppEnabled: e.target.checked })}
                    className="w-4 h-4 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                </div>

                {/* Email Toggle */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div className="flex items-center space-x-3">
                    <Mail className="w-5 h-5 text-blue-600" />
                    <div>
                      <div className="text-sm font-medium text-gray-900">{t("email_notifications")}</div>
                      <div className="text-xs text-gray-500">Token status & call emails</div>
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={pref.emailEnabled}
                    onChange={(e) => setPref({ ...pref, emailEnabled: e.target.checked })}
                    className="w-4 h-4 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                </div>

                {/* SMS Toggle */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div className="flex items-center space-x-3">
                    <MessageSquare className="w-5 h-5 text-emerald-600" />
                    <div>
                      <div className="text-sm font-medium text-gray-900">{t("sms_notifications")}</div>
                      <div className="text-xs text-gray-500">SMS alerts on registered phone</div>
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={pref.smsEnabled}
                    onChange={(e) => setPref({ ...pref, smsEnabled: e.target.checked })}
                    className="w-4 h-4 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                </div>

                {/* Push Toggle */}
                <div className="flex items-center justify-between p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <div className="flex items-center space-x-3">
                    <Smartphone className="w-5 h-5 text-purple-600" />
                    <div>
                      <div className="text-sm font-medium text-gray-900">{t("push_notifications")}</div>
                      <div className="text-xs text-gray-500">Mobile & browser push alerts</div>
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={pref.pushEnabled}
                    onChange={(e) => setPref({ ...pref, pushEnabled: e.target.checked })}
                    className="w-4 h-4 text-indigo-600 rounded focus:ring-indigo-500"
                  />
                </div>

                {/* Position Threshold */}
                <div className="p-3 rounded-lg bg-gray-50 border border-gray-100">
                  <label className="block text-sm font-medium text-gray-900 mb-1">
                    {t("position_near_threshold")}
                  </label>
                  <div className="flex items-center space-x-3">
                    <input
                      type="range"
                      min="1"
                      max="10"
                      value={pref.positionNearThreshold}
                      onChange={(e) =>
                        setPref({ ...pref, positionNearThreshold: parseInt(e.target.value, 10) })
                      }
                      className="w-full accent-indigo-600"
                    />
                    <span className="text-sm font-bold text-indigo-600 w-8 text-center">
                      {pref.positionNearThreshold}
                    </span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">
                    Alert when {pref.positionNearThreshold} or fewer people are ahead of you
                  </p>
                </div>
              </div>

              <div className="flex justify-end space-x-3 pt-3 border-t border-gray-100">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50"
                >
                  {t("cancel")}
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50"
                >
                  {saving ? t("loading") : t("save_preferences")}
                </button>
              </div>
            </>
          ) : null}
        </form>
      </div>
    </div>
  );
};
