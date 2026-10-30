import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getBranchesByOrg,
  getServicesByBranch,
  createService,
  updateService,
  BranchDto,
  ServiceDto,
} from "../../lib/apiClient";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import { Stethoscope, Building2, Plus, Edit2, AlertTriangle, Layers, ShieldCheck } from "lucide-react";

export const ServiceManagementPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [selectedBranchId, setSelectedBranchId] = useState<string>("");
  const [showModal, setShowModal] = useState(false);
  const [editingService, setEditingService] = useState<ServiceDto | null>(null);
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [requiresDocumentVerification, setRequiresDocumentVerification] = useState(false);
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

  const activeBranchId = selectedBranchId || (branches.length > 0 ? branches[0].id : "");

  const { data: services = [], isLoading, error } = useQuery<ServiceDto[]>({
    queryKey: ["adminServices", activeBranchId],
    queryFn: async () => {
      if (!activeBranchId) return [];
      const res = await getServicesByBranch(activeBranchId);
      if (!res.success || !res.data) throw new Error(res.error?.message || "Failed to load services");
      return res.data;
    },
    enabled: !!activeBranchId,
  });

  const handleOpenCreate = () => {
    setEditingService(null);
    setName("");
    setCode("");
    setDescription("");
    setRequiresDocumentVerification(false);
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (svc: ServiceDto) => {
    setEditingService(svc);
    setName(svc.name);
    setCode(svc.code || "");
    setDescription(svc.description || "");
    setRequiresDocumentVerification(svc.requiresDocumentVerification || false);
    setFormError(null);
    setShowModal(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Service name is required");
      return;
    }
    if (!activeBranchId) {
      setFormError("Branch is required");
      return;
    }
    setSubmitting(true);
    setFormError(null);

    try {
      if (editingService) {
        const res = await updateService(editingService.id, {
          name: name.trim(),
          description: description.trim() || undefined,
          requiresDocumentVerification,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to update service");
      } else {
        const res = await createService({
          name: name.trim(),
          code: code.trim().toUpperCase() || undefined,
          description: description.trim() || undefined,
          branchId: activeBranchId,
          requiresDocumentVerification,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to create service");
      }

      await queryClient.invalidateQueries({ queryKey: ["adminServices"] });
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
            <Badge variant="info" size="sm" className="font-mono uppercase font-bold">
              CATALOG &amp; SERVICES
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("services_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Configure department offerings, consultation workflows, and service types.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {branches.length > 1 && (
            <select
              value={activeBranchId}
              onChange={(e) => setSelectedBranchId(e.target.value)}
              className="px-3 py-2 border border-slate-300 rounded-xl text-xs font-semibold bg-white text-slate-800 shadow-xs outline-none focus:ring-2 focus:ring-teal-500/20"
            >
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
            {t("add_service")}
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
          Failed to load service records.
        </div>
      ) : services.length === 0 ? (
        <EmptyState
          icon={<Stethoscope className="w-7 h-7" />}
          title="No services configured"
          description="Create your first department service to start assigning queues and serving customers."
          actionLabel={t("add_service")}
          onAction={handleOpenCreate}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {services.map((svc) => (
            <Card key={svc.id} hover className="p-6 flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 leading-snug">{svc.name}</h3>
                    {svc.code && (
                      <span className="inline-block mt-1 px-2 py-0.5 text-[11px] font-mono font-bold rounded-md bg-slate-100 text-slate-700 border border-slate-200">
                        {svc.code}
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => handleOpenEdit(svc)}
                    className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                    title="Edit Service"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>

                {svc.description && (
                  <p className="text-xs text-slate-600 mt-3 leading-relaxed line-clamp-2">
                    {svc.description}
                  </p>
                )}
              </div>

              <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                <div className="flex items-center gap-1.5">
                  <Badge variant={svc.status === "ACTIVE" ? "success" : "neutral"} size="sm" dot>
                    {svc.status}
                  </Badge>
                  {svc.requiresDocumentVerification && (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                      <ShieldCheck className="w-3 h-3 text-amber-600" /> Docs Required
                    </span>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Service Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingService ? t("edit_service") : t("add_service")}
        description="Configure department offerings and descriptions."
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
              {t("service_name")} *
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. General Consultation"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          {!editingService && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                {t("service_code")}
              </label>
              <input
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="e.g. CC-GC"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs uppercase font-mono"
              />
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              {t("service_description")}
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. In-person consultation and general advice"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          {/* AI Document Verification Toggle */}
          <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-2xl">
            <input
              id="svcReqDoc"
              type="checkbox"
              checked={requiresDocumentVerification}
              onChange={(e) => setRequiresDocumentVerification(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 cursor-pointer"
            />
            <label htmlFor="svcReqDoc" className="text-xs text-slate-700 cursor-pointer">
              <span className="font-bold block text-slate-900">Requires Customer Document Analysis</span>
              Customers must upload and have their identity/documents verified by AI before joining queues under this service.
            </label>
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
export default ServiceManagementPage;
