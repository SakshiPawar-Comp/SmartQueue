import React, { useState } from "react";
import { useNavigate, useSearchParams, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { useAuth } from "../context/AuthContext";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Layers, User, Mail, Lock, Building2, ArrowRight, AlertCircle } from "lucide-react";

interface RegisterForm {
  fullName: string;
  email: string;
  password: string;
  role: "CUSTOMER" | "ORGANIZATION_ADMIN";
  organizationCode?: string;
}

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { register: apiRegister, isLoading } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);

  const redirectUrl = searchParams.get("redirect") || "/dashboard";

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
    watch,
    setValue,
  } = useForm<RegisterForm>({
    defaultValues: {
      role: "CUSTOMER",
      organizationCode: "org-597c359e",
    },
  });

  const selectedRole = watch("role");

  const onSubmit = async (data: RegisterForm) => {
    setServerError(null);
    try {
      const sanitizedCode = data.organizationCode?.trim() ? data.organizationCode.trim().toLowerCase() : "org-597c359e";
      await apiRegister(data.fullName, data.email, data.password, data.role, sanitizedCode);
      navigate(redirectUrl);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Registration failed. Please try again.";
      setServerError(message);
      reset({ password: "" });
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-slate-50 px-4 py-12">
      <div className="w-full max-w-sm">
        {/* Brand Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-teal-600 text-white shadow-card mb-4">
            <Layers className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">SmartQueue</h1>
          <p className="text-xs text-slate-500 mt-1">Create your digital queue account</p>
        </div>

        {/* Register Card */}
        <Card className="p-6 sm:p-8 shadow-elevated border-slate-200/80">
          {serverError && (
            <div
              className="mb-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-xs font-semibold text-rose-700 flex items-center gap-2"
              role="alert"
            >
              <AlertCircle className="w-4 h-4 text-rose-500 shrink-0" />
              <span>{serverError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label
                htmlFor="fullName"
                className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5"
              >
                <User className="w-3.5 h-3.5 text-slate-400" />
                <span>Full name</span>
              </label>
              <input
                id="fullName"
                type="text"
                autoComplete="name"
                placeholder="Aarav Patel"
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs ${errors.fullName ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
                  }`}
                {...register("fullName", {
                  required: "Full name is required",
                  minLength: {
                    value: 2,
                    message: "Name must be at least 2 characters",
                  },
                })}
                onChange={() => setServerError(null)}
              />
              {errors.fullName && (
                <p className="mt-1 text-xs text-rose-600 font-semibold">{errors.fullName.message}</p>
              )}
            </div>

            <div>
              <label
                htmlFor="email"
                className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5"
              >
                <Mail className="w-3.5 h-3.5 text-slate-400" />
                <span>Email address</span>
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs ${errors.email ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
                  }`}
                {...register("email", {
                  required: "Email is required",
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: "Enter a valid email address",
                  },
                })}
                onChange={() => setServerError(null)}
              />
              {errors.email && (
                <p className="mt-1 text-xs text-rose-600 font-semibold">{errors.email.message}</p>
              )}
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5"
              >
                <Lock className="w-3.5 h-3.5 text-slate-400" />
                <span>Password (min 8 characters)</span>
              </label>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                placeholder="At least 8 characters"
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs ${errors.password ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
                  }`}
                {...register("password", {
                  required: "Password is required",
                  minLength: {
                    value: 8,
                    message: "Password must be at least 8 characters",
                  },
                })}
                onChange={() => setServerError(null)}
              />
              {errors.password && (
                <p className="mt-1 text-xs text-rose-600 font-semibold">{errors.password.message}</p>
              )}
            </div>

            <div>
              <label
                htmlFor="role"
                className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5"
              >
                Account Role
              </label>
              <select
                id="role"
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 bg-white shadow-xs cursor-pointer ${errors.role ? "border-rose-300 bg-rose-50" : "border-slate-300"
                  }`}
                {...register("role", { required: "Please select a role" })}
                onChange={() => setServerError(null)}
              >
                <option value="CUSTOMER">Customer — Take &amp; monitor digital tokens</option>
                <option value="ORGANIZATION_ADMIN">Admin — Setup facility &amp; manage queues</option>
              </select>
              {errors.role && (
                <p className="mt-1 text-xs text-rose-600 font-semibold">{errors.role.message}</p>
              )}
            </div>

            {selectedRole === "CUSTOMER" && (
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label
                    htmlFor="organizationCode"
                    className="block text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5"
                  >
                    <Building2 className="w-3.5 h-3.5 text-slate-400" />
                    <span>Organization Code</span>
                  </label>
                  <span className="text-[11px] font-mono text-teal-700 font-semibold bg-teal-50 px-2 py-0.5 rounded-md border border-teal-200">
                    org-597c359e
                  </span>
                </div>
                <input
                  id="organizationCode"
                  type="text"
                  placeholder="e.g. org-597c359e"
                  className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs font-mono text-slate-800 ${errors.organizationCode ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
                    }`}
                  {...register("organizationCode")}
                  onChange={() => setServerError(null)}
                />
                <p className="mt-1 text-[11px] text-slate-400">
                  Pre-filled for Presentation Admin's Organization (<span className="text-teal-700 font-semibold">org-597c359e</span>).
                </p>
                {errors.organizationCode && (
                  <p className="mt-1 text-xs text-rose-600 font-semibold">
                    {errors.organizationCode.message}
                  </p>
                )}
              </div>
            )}

            <Button
              type="submit"
              loading={isLoading}
              variant="primary"
              size="md"
              fullWidth
              icon={<ArrowRight className="w-4 h-4" />}
              iconPosition="right"
              className="mt-2"
            >
              Create Account
            </Button>
          </form>
        </Card>

        <p className="text-center text-xs text-slate-500 mt-6">
          Already have an account?{" "}
          <Link
            to={searchParams.get("redirect") ? `/login?redirect=${encodeURIComponent(searchParams.get("redirect")!)}` : "/login"}
            className="text-teal-700 hover:text-teal-800 font-bold hover:underline"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};
