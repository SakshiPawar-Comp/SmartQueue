import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getBranchesByOrg,
  getStaffList,
  createStaff,
  updateStaff,
  deactivateStaff,
  BranchDto,
  StaffUserDto,
} from "../../lib/apiClient";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import { Users, Plus, Edit2, UserX, AlertTriangle, Building2, Monitor, Mail, Phone } from "lucide-react";

export const StaffManagementPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [selectedBranchId, setSelectedBranchId] = useState<string>("");
  const [showModal, setShowModal] = useState(false);
  const [editingStaff, setEditingStaff] = useState<StaffUserDto | null>(null);

  // Form states
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [branchId, setBranchId] = useState("");
  const [preferredLanguage, setPreferredLanguage] = useState("en");

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const orgId = user?.organizationId ?? "";

  const { data: branches = [] } = useQuery<BranchDto[]>({
    queryKey: ["adminBranches", orgId],
    queryFn: async () => {
      if (!orgId) return [];
      const res = await getBranchesByOrg(orgId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!orgId,
  });

  const { data: staffList = [], isLoading, error } = useQuery<StaffUserDto[]>({
    queryKey: ["adminStaffList", selectedBranchId],
    queryFn: async () => {
      const res = await getStaffList(selectedBranchId || undefined);
      if (!res.success || !res.data) throw new Error(res.error?.message || "Failed to load staff");
      return res.data;
    },
  });

  const handleOpenCreate = () => {
    setEditingStaff(null);
    setFullName("");
    setEmail("");
    setPhone("");
    setPassword("");
    setBranchId(branches.length > 0 ? branches[0].id : "");
    setPreferredLanguage("en");
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (staff: StaffUserDto) => {
    setEditingStaff(staff);
    setFullName(staff.fullName);
    setEmail(staff.email);
    setPhone(staff.phone || "");
    setPassword("");
    setBranchId(staff.branchId || "");
    setPreferredLanguage(staff.preferredLanguage || "en");
    setFormError(null);
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim() || !email.trim()) {
      setFormError("Name and Email are required");
      return;
    }
    setSubmitting(true);
    setFormError(null);

    try {
      if (editingStaff) {
        const res = await updateStaff(editingStaff.id, {
          fullName: fullName.trim(),
          phone: phone.trim() || undefined,
          preferredLanguage,
          branchId: branchId || undefined,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to update staff member");
      } else {
        if (!password.trim() || password.length < 8) {
          setFormError("Password must be at least 8 characters");
          setSubmitting(false);
          return;
        }
        const res = await createStaff({
          fullName: fullName.trim(),
          email: email.trim(),
          password: password.trim(),
          phone: phone.trim() || undefined,
          branchId: branchId || undefined,
          preferredLanguage,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to create staff member");
      }

      await queryClient.invalidateQueries({ queryKey: ["adminStaffList"] });
      setShowModal(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeactivate = async (staffId: string) => {
    if (!window.confirm("Are you sure you want to deactivate this staff account?")) return;
    try {
      const res = await deactivateStaff(staffId);
      if (!res.success) throw new Error(res.error?.message || "Failed to deactivate");
      await queryClient.invalidateQueries({ queryKey: ["adminStaffList"] });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Action failed");
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 border-b border-slate-200 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="purple" size="sm" className="font-mono uppercase font-bold">
              USER DIRECTORY
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("staff_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Manage counter desk operators, credentials, and facility branch assignments.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {branches.length > 1 && (
            <select
              value={selectedBranchId}
              onChange={(e) => setSelectedBranchId(e.target.value)}
              className="px-3 py-2 border border-slate-300 rounded-xl text-xs font-semibold bg-white text-slate-800 shadow-xs outline-none focus:ring-2 focus:ring-teal-500/20"
            >
              <option value="">All Branches</option>
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          )}
          <Button
            onClick={handleOpenCreate}
            variant="primary"
            size="md"
            icon={<Plus className="w-4 h-4" />}
          >
            {t("add_staff")}
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      ) : error ? (
        <div className="p-8 text-center bg-rose-50 text-rose-700 rounded-2xl border border-rose-200">
          Failed to load staff records.
        </div>
      ) : staffList.length === 0 ? (
        <EmptyState
          icon={<Users className="w-7 h-7" />}
          title="No staff members registered"
          description="Add your first staff operator account to start serving counters."
          actionLabel={t("add_staff")}
          onAction={handleOpenCreate}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {staffList.map((st) => (
            <Card key={st.id} hover className="p-6 flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-slate-900 text-white font-bold flex items-center justify-center text-sm shadow-xs">
                      {st.fullName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-slate-900 leading-snug">
                        {st.fullName}
                      </h3>
                      <span className="text-xs text-slate-400 block">{st.email}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => handleOpenEdit(st)}
                      className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                      title="Edit Staff"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDeactivate(st.id)}
                      className="p-1.5 text-slate-400 hover:text-rose-600 rounded-xl hover:bg-rose-50 transition-colors"
                      title="Deactivate Account"
                    >
                      <UserX className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <div className="mt-4 p-3 bg-slate-50 rounded-xl border border-slate-100 text-xs space-y-1.5">
                  <div className="flex items-center justify-between text-slate-600">
                    <span className="text-slate-400">Branch:</span>
                    <strong className="text-slate-800">{st.branchName || "Not Assigned"}</strong>
                  </div>
                  <div className="flex items-center justify-between text-slate-600">
                    <span className="text-slate-400">Counter Desk:</span>
                    <strong className="text-teal-700 font-bold">
                      {st.activeCounterName || "Not Assigned"}
                    </strong>
                  </div>
                  {st.phone && (
                    <div className="flex items-center justify-between text-slate-600">
                      <span className="text-slate-400">Phone:</span>
                      <span className="text-slate-800 font-mono">{st.phone}</span>
                    </div>
                  )}
                </div>
              </div>

              <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                <Badge variant={st.status === "ACTIVE" ? "success" : "neutral"} size="sm" dot>
                  {st.status || "ACTIVE"}
                </Badge>
                <span className="text-[11px] font-mono text-slate-400 uppercase">
                  Lang: {st.preferredLanguage || "en"}
                </span>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Staff Create/Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingStaff ? t("edit_staff") : t("add_staff")}
        description="Configure operator credentials and branch assignment."
      >
        {formError && (
          <div className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{formError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Full Name *
            </label>
            <input
              type="text"
              required
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="e.g. Priya Sharma"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Email Address *
            </label>
            <input
              type="email"
              required
              disabled={!!editingStaff}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. staff1@organization.com"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs disabled:bg-slate-100 disabled:text-slate-400"
            />
          </div>

          {!editingStaff && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Password * (min 8 characters)
              </label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="At least 8 characters"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
              />
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Phone Number
              </label>
              <input
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+91 9876543210"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Assigned Branch
              </label>
              <select
                value={branchId}
                onChange={(e) => setBranchId(e.target.value)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs bg-white"
              >
                <option value="">Select Branch...</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2.5 pt-4 border-t border-slate-100">
            <Button onClick={() => setShowModal(false)} variant="outline" size="sm">
              {t("cancel")}
            </Button>
            <Button type="submit" loading={submitting} variant="primary" size="sm">
              {submitting ? t("loading") : t("save")}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
export default StaffManagementPage;
