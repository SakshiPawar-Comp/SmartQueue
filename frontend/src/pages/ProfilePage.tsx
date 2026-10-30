import React, { useState, useEffect } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getHighestRole, getRoleLabel } from "../lib/roles";
import { Header } from "../components/Header";
import { updateCustomerProfile, changeCustomerPassword } from "../lib/apiClient";
import { useTranslation } from "../i18n/LanguageContext";
import { Language } from "../i18n/translations";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "../components/ui/Card";
import { User, Shield, CheckCircle2, AlertCircle, KeyRound, Globe, Phone, Mail } from "lucide-react";

export const ProfilePage: React.FC = () => {
  const { user, isLoading, refreshUser } = useAuth();
  const { language, setLanguage, t } = useTranslation();

  // Profile Form State
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [prefLang, setPrefLang] = useState<Language>("en");
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Password Form State
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setFullName(user.fullName || "");
      setPhone(user.phone || "");
      if (
        user.preferredLanguage === "hi" ||
        user.preferredLanguage === "mr" ||
        user.preferredLanguage === "en"
      ) {
        setPrefLang(user.preferredLanguage as Language);
      }
    }
  }, [user]);

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin h-8 w-8 border-4 border-teal-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const highestRole = getHighestRole(user);

  async function handleUpdateProfile(e: React.FormEvent) {
    e.preventDefault();
    setProfileSaving(true);
    setProfileSuccess(null);
    setProfileError(null);

    try {
      const result = await updateCustomerProfile({
        fullName,
        phone,
        preferredLanguage: prefLang,
      });

      if (result.success && result.data) {
        setLanguage(prefLang);
        setProfileSuccess(t("profile_updated_success"));
        if (refreshUser) {
          refreshUser();
        }
      } else {
        setProfileError(result.error?.message || "Failed to update profile");
      }
    } catch {
      setProfileError("Network error. Please try again.");
    } finally {
      setProfileSaving(false);
    }
  }

  async function handleChangePassword(e: React.FormEvent) {
    e.preventDefault();
    setPasswordSaving(true);
    setPasswordSuccess(null);
    setPasswordError(null);

    if (newPassword !== confirmPassword) {
      setPasswordError(t("password_mismatch"));
      setPasswordSaving(false);
      return;
    }

    try {
      const result = await changeCustomerPassword({
        currentPassword,
        newPassword,
      });

      if (result.success) {
        setPasswordSuccess(t("password_updated_success"));
        setCurrentPassword("");
        setNewPassword("");
        setConfirmPassword("");
      } else {
        setPasswordError(result.error?.message || "Failed to change password");
      }
    } catch {
      setPasswordError("Network error. Please try again.");
    } finally {
      setPasswordSaving(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Header />
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 pb-16">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="primary" size="sm" className="font-mono uppercase font-bold">
              SETTINGS &amp; IDENTITY
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("profile_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">{t("profile_subtitle")}</p>
        </div>

        <div className="grid gap-8 md:grid-cols-2">
          {/* PROFILE CARD */}
          <Card className="p-6 sm:p-8">
            <div className="flex items-center gap-4 mb-6">
              <div className="w-14 h-14 rounded-2xl bg-teal-600 flex items-center justify-center text-white text-xl font-bold shadow-xs">
                {user.fullName.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="font-extrabold text-slate-900 text-lg leading-tight">{user.fullName}</p>
                <p className="text-xs text-slate-400 mt-0.5">{user.email}</p>
                {highestRole && (
                  <Badge variant="primary" size="sm" className="mt-1 font-bold uppercase">
                    {getRoleLabel(highestRole)}
                  </Badge>
                )}
              </div>
            </div>

            {profileSuccess && (
              <div className="p-3 mb-4 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold rounded-xl flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                <span>{profileSuccess}</span>
              </div>
            )}
            {profileError && (
              <div className="p-3 mb-4 bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold rounded-xl flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-rose-500" />
                <span>{profileError}</span>
              </div>
            )}

            <form onSubmit={handleUpdateProfile} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-slate-400" />
                  <span>{t("full_name")}</span>
                </label>
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                  className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                  <Mail className="w-3.5 h-3.5 text-slate-400" />
                  <span>{t("email")}</span>
                </label>
                <input
                  type="email"
                  value={user.email}
                  disabled
                  className="w-full rounded-xl border border-slate-200 bg-slate-100 px-3.5 py-2.5 text-sm text-slate-500 cursor-not-allowed outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                  <Phone className="w-3.5 h-3.5 text-slate-400" />
                  <span>{t("phone")}</span>
                </label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+91 9876543210"
                  className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                  <Globe className="w-3.5 h-3.5 text-slate-400" />
                  <span>{t("preferred_language")}</span>
                </label>
                <select
                  value={prefLang}
                  onChange={(e) => setPrefLang(e.target.value as Language)}
                  className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs cursor-pointer"
                >
                  <option value="en">English (EN)</option>
                  <option value="hi">हिंदी (HI)</option>
                  <option value="mr">मराठी (MR)</option>
                </select>
              </div>

              <Button
                type="submit"
                loading={profileSaving}
                variant="primary"
                size="md"
                fullWidth
                className="mt-2"
              >
                {profileSaving ? t("updating") : t("save")}
              </Button>
            </form>
          </Card>

          {/* PASSWORD SECURITY CARD */}
          <Card className="p-6 sm:p-8 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <KeyRound className="w-4 h-4 text-slate-700" />
                <h2 className="text-base font-bold text-slate-900 tracking-tight">
                  {t("security_title")}
                </h2>
              </div>
              <p className="text-xs text-slate-500 mb-6">
                Ensure your account credentials remain secure with a strong password.
              </p>

              {passwordSuccess && (
                <div className="p-3 mb-4 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-semibold rounded-xl flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  <span>{passwordSuccess}</span>
                </div>
              )}
              {passwordError && (
                <div className="p-3 mb-4 bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold rounded-xl flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-rose-500" />
                  <span>{passwordError}</span>
                </div>
              )}

              <form onSubmit={handleChangePassword} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    {t("current_password")}
                  </label>
                  <input
                    type="password"
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    required
                    className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    {t("new_password")} (min 8 chars)
                  </label>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                    className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                    {t("confirm_new_password")}
                  </label>
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                    className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
                  />
                </div>

                <Button
                  type="submit"
                  loading={passwordSaving}
                  variant="secondary"
                  size="md"
                  fullWidth
                  className="mt-2"
                >
                  {passwordSaving ? t("updating") : t("password_change_btn")}
                </Button>
              </form>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};
