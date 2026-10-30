import React, { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getHighestRole, getRoleLabel } from "../lib/roles";
import { useTranslation } from "../i18n/LanguageContext";
import { Language } from "../i18n/translations";
import { NotificationBell } from "./NotificationBell";
import { Badge } from "./ui/Badge";
import {
  LayoutDashboard,
  Building2,
  Stethoscope,
  Monitor,
  ListOrdered,
  Users,
  BarChart3,
  History,
  User,
  LogOut,
  Menu,
  X,
  Layers,
} from "lucide-react";

export const Header: React.FC = () => {
  const { user, logout } = useAuth();
  const location = useLocation();
  const { language, setLanguage, t } = useTranslation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const highestRole = getHighestRole(user);

  const isAdmin = highestRole === "ORGANIZATION_ADMIN" || highestRole === "SUPER_ADMIN";
  const isCustomer = highestRole === "CUSTOMER";

  const navLinks = user
    ? [
        { to: "/dashboard", label: t("nav_dashboard"), icon: LayoutDashboard },
        ...(isAdmin
          ? [
              { to: "/admin/branches", label: t("nav_branches"), icon: Building2 },
              { to: "/admin/services", label: t("nav_services"), icon: Stethoscope },
              { to: "/admin/counters", label: t("nav_counters"), icon: Monitor },
              { to: "/admin/queues", label: t("nav_queues"), icon: ListOrdered },
              { to: "/admin/staff", label: t("nav_staff"), icon: Users },
              { to: "/admin/analytics", label: t("nav_analytics"), icon: BarChart3 },
            ]
          : []),
        ...(isCustomer ? [{ to: "/history", label: t("nav_history"), icon: History }] : []),
        { to: "/profile", label: t("nav_profile"), icon: User },
      ]
    : [];

  return (
    <header className="bg-white/95 backdrop-blur-md border-b border-slate-200/80 sticky top-0 z-30 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand & Left Nav */}
          <div className="flex items-center gap-6 lg:gap-8">
            <Link to="/dashboard" className="flex items-center gap-2.5 group">
              <div className="w-9 h-9 rounded-xl bg-teal-600 flex items-center justify-center text-white shadow-xs group-hover:bg-teal-700 transition-colors">
                <Layers className="w-5 h-5" />
              </div>
              <div className="flex flex-col">
                <span className="text-base font-bold text-slate-900 tracking-tight leading-none group-hover:text-teal-700 transition-colors">
                  {t("app_title")}
                </span>
                <span className="text-[10px] text-slate-400 font-semibold tracking-wider uppercase mt-0.5">
                  Queue Engine
                </span>
              </div>
            </Link>

            {/* Desktop Navigation */}
            <nav className="hidden lg:flex items-center gap-1">
              {navLinks.map((link) => {
                const isActive = location.pathname === link.to;
                const Icon = link.icon;
                return (
                  <Link
                    key={link.to}
                    to={link.to}
                    className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold transition-all ${
                      isActive
                        ? "bg-teal-50 text-teal-800 border border-teal-200/60 shadow-xs"
                        : "text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent"
                    }`}
                  >
                    <Icon className={`w-4 h-4 ${isActive ? "text-teal-700" : "text-slate-400"}`} />
                    <span className="whitespace-nowrap">{link.label}</span>
                  </Link>
                );
              })}
            </nav>
          </div>

          {/* Right Area: Controls & User Profile */}
          <div className="flex items-center gap-2.5 sm:gap-3.5">
            {/* Language Selector */}
            <div className="relative">
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value as Language)}
                className="text-xs font-medium bg-slate-50 border border-slate-200 hover:border-slate-300 rounded-xl px-2.5 py-1.5 text-slate-700 focus:outline-none focus:ring-2 focus:ring-teal-500 cursor-pointer shadow-xs"
                aria-label="Select Language"
              >
                <option value="en">English (EN)</option>
                <option value="hi">हिंदी (HI)</option>
                <option value="mr">मराठी (MR)</option>
              </select>
            </div>

            {/* Notification Center */}
            {user && <NotificationBell />}

            {/* Role Badge */}
            {highestRole && (
              <Badge
                variant={
                  highestRole === "ORGANIZATION_ADMIN" || highestRole === "SUPER_ADMIN"
                    ? "purple"
                    : highestRole === "STAFF"
                    ? "info"
                    : "primary"
                }
                size="sm"
                className="hidden md:inline-flex uppercase font-bold tracking-wider"
              >
                {getRoleLabel(highestRole)}
              </Badge>
            )}

            {/* User Avatar & Info */}
            {user && (
              <div className="flex items-center gap-2 pl-2 border-l border-slate-200">
                <div className="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs font-bold shadow-xs">
                  {user.fullName?.charAt(0)?.toUpperCase() ?? "U"}
                </div>
                <div className="hidden xl:flex flex-col text-left">
                  <span className="text-xs font-bold text-slate-800 max-w-[130px] truncate leading-tight">
                    {user.fullName}
                  </span>
                  <span className="text-[10px] text-slate-400 max-w-[130px] truncate">
                    {user.email}
                  </span>
                </div>
              </div>
            )}

            {/* Sign Out Button if user is logged in, else Sign In button */}
            {user ? (
              <button
                onClick={() => logout()}
                title={t("nav_sign_out")}
                className="p-2 text-slate-400 hover:text-rose-600 rounded-xl hover:bg-rose-50 transition-colors"
                aria-label={t("nav_sign_out")}
              >
                <LogOut className="w-4 h-4" />
              </button>
            ) : (
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-teal-700 bg-teal-50 border border-teal-200/80 rounded-xl hover:bg-teal-100 transition-colors shadow-xs"
              >
                Sign In
              </Link>
            )}

            {/* Mobile Hamburger Menu Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="lg:hidden p-2 text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-xl"
              aria-label="Toggle Menu"
            >
              {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="lg:hidden py-3 px-2 border-t border-slate-100 bg-white space-y-1 animate-fade-in">
            {navLinks.map((link) => {
              const isActive = location.pathname === link.to;
              const Icon = link.icon;
              return (
                <Link
                  key={link.to}
                  to={link.to}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-semibold ${
                    isActive
                      ? "bg-teal-50 text-teal-800 border border-teal-200"
                      : "text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                  }`}
                >
                  <Icon className="w-4 h-4 text-slate-400" />
                  <span>{link.label}</span>
                </Link>
              );
            })}
          </div>
        )}
      </div>
    </header>
  );
};
