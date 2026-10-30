import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import { getBranchesByOrg, createBranch, updateBranch, BranchDto } from "../../lib/apiClient";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card, CardContent } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import { Building2, MapPin, Plus, Edit2, AlertTriangle } from "lucide-react";

export const BranchManagementPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [showModal, setShowModal] = useState(false);
  const [editingBranch, setEditingBranch] = useState<BranchDto | null>(null);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [address, setAddress] = useState("");
  const [timezone, setTimezone] = useState("UTC");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const orgId = user?.organizationId ?? "";

  const { data: branches = [], isLoading, error } = useQuery<BranchDto[]>({
    queryKey: ["adminBranches", orgId],
    queryFn: async () => {
      if (!orgId) return [];
      const res = await getBranchesByOrg(orgId);
      if (!res.success || !res.data) throw new Error(res.error?.message || "Failed to load branches");
      return res.data;
    },
    enabled: !!orgId,
  });

  const handleOpenCreate = () => {
    setEditingBranch(null);
    setName("");
    setCode("");
    setAddress("");
    setTimezone("UTC");
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (branch: BranchDto) => {
    setEditingBranch(branch);
    setName(branch.name);
    setCode(branch.code || "");
    setAddress(branch.address || "");
    setFormError(null);
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Branch name is required");
      return;
    }
    setSubmitting(true);
    setFormError(null);

    try {
      if (editingBranch) {
        const res = await updateBranch(editingBranch.id, {
          name: name.trim(),
          address: address.trim() || undefined,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to update branch");
      } else {
        if (!code.trim()) {
          setFormError("Branch code is required");
          setSubmitting(false);
          return;
        }
        const res = await createBranch({
          name: name.trim(),
          code: code.trim().toUpperCase(),
          address: address.trim() || undefined,
          timezone,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to create branch");
      }

      await queryClient.invalidateQueries({ queryKey: ["adminBranches"] });
      setShowModal(false);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 border-b border-slate-200 gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Badge variant="primary" size="sm" className="font-mono uppercase font-bold">
              FACILITY MANAGEMENT
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("branches_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Manage physical office locations, facility addresses, and branch routing codes.
          </p>
        </div>
        <div>
          <Button
            onClick={handleOpenCreate}
            variant="primary"
            size="md"
            icon={<Plus className="w-4 h-4" />}
          >
            {t("add_branch")}
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
          Failed to load branch records.
        </div>
      ) : branches.length === 0 ? (
        <EmptyState
          icon={<Building2 className="w-7 h-7" />}
          title="No branches configured"
          description="Create your organization's first physical branch to begin configuring service queues and counter desks."
          actionLabel={t("add_branch")}
          onAction={handleOpenCreate}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {branches.map((b) => (
            <Card key={b.id} hover className="p-6 flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 leading-snug">{b.name}</h3>
                    <span className="inline-block mt-1 px-2 py-0.5 text-[11px] font-mono font-bold rounded-md bg-slate-100 text-slate-700 border border-slate-200">
                      {b.code || "N/A"}
                    </span>
                  </div>
                  <button
                    onClick={() => handleOpenEdit(b)}
                    className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                    title="Edit Branch"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>

                {b.address && (
                  <p className="text-xs text-slate-600 mt-3 flex items-start gap-1.5 leading-relaxed">
                    <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                    <span>{b.address}</span>
                  </p>
                )}
              </div>

              <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                <Badge variant={b.status === "ACTIVE" ? "success" : "neutral"} size="sm" dot>
                  {b.status}
                </Badge>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingBranch ? t("edit_branch") : t("add_branch")}
        description="Provide physical branch location details."
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
              {t("branch_name")} *
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Central Branch"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          {!editingBranch && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                {t("branch_code")} *
              </label>
              <input
                type="text"
                required
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="e.g. CC-CENTRAL"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs uppercase font-mono"
              />
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              {t("branch_address")}
            </label>
            <textarea
              rows={2}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="e.g. 101 MG Road, Pune, Maharashtra 411001"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
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
export default BranchManagementPage;
