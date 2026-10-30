import React, { useState } from "react";
import { useNavigate, useSearchParams, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { useAuth } from "../context/AuthContext";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card, CardContent } from "../components/ui/Card";
import { Layers, Mail, Lock, ArrowRight, AlertCircle, ShieldCheck } from "lucide-react";

interface LoginForm {
  email: string;
  password: string;
}

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login, isLoading } = useAuth();
  const [serverError, setServerError] = useState<string | null>(null);

  const redirectUrl = searchParams.get("redirect") || "/dashboard";

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<LoginForm>();

  const onSubmit = async (data: LoginForm) => {
    setServerError(null);
    try {
      await login(data.email, data.password);
      navigate(redirectUrl);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Login failed. Please try again.";
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
          <p className="text-xs text-slate-500 mt-1">Sign in to your digital queue account</p>
        </div>

        {/* Login Card */}
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
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs ${
                  errors.email ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
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
                <span>Password</span>
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                placeholder="Enter your password"
                className={`w-full px-3.5 py-2.5 rounded-xl border text-sm transition-all focus:outline-none focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 shadow-xs ${
                  errors.password ? "border-rose-300 bg-rose-50" : "border-slate-300 bg-white"
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
              Sign In
            </Button>
          </form>
        </Card>

        <p className="text-center text-xs text-slate-500 mt-6">
          Don't have an account?{" "}
          <Link
            to={searchParams.get("redirect") ? `/register?redirect=${encodeURIComponent(searchParams.get("redirect")!)}` : "/register"}
            className="text-teal-700 hover:text-teal-800 font-bold hover:underline"
          >
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
};
