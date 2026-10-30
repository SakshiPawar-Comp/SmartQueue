import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getBranchesByOrg,
  getServicesByBranch,
  getQueues,
  createQueue,
  updateQueue,
  deleteQueue,
  openQueue,
  closeQueue,
  QueueStatus,
  BranchDto,
  ServiceDto,
  QueueResponse,
} from "../../lib/apiClient";
import { QueueQRCodeModal } from "../../components/QueueQRCodeModal";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import {
  ListOrdered,
  Plus,
  Edit2,
  QrCode,
  AlertTriangle,
  Building2,
  Stethoscope,
  Power,
  ShieldAlert,
  ShieldCheck,
  Trash2,
} from "lucide-react";

export const QueueManagementPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [filterBranchId, setFilterBranchId] = useState<string>("");
  const [filterServiceId, setFilterServiceId] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");

  const [showModal, setShowModal] = useState(false);
  const [editingQueue, setEditingQueue] = useState<QueueResponse | null>(null);
  const [qrModalQueue, setQrModalQueue] = useState<QueueResponse | null>(null);

  // Form states
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tokenPrefix, setTokenPrefix] = useState("A");
  const [branchId, setBranchId] = useState("");
  const [serviceId, setServiceId] = useState("");
  const [capacity, setCapacity] = useState<number>(0);
  const [isPriorityEnabled, setIsPriorityEnabled] = useState<boolean>(false);
  const [defaultServiceTimeMinutes, setDefaultServiceTimeMinutes] = useState<number>(5);
  const [requiresDocumentVerification, setRequiresDocumentVerification] = useState<boolean>(false);

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

  const activeBranchIdForForm = branchId || filterBranchId || (branches.length > 0 ? branches[0].id : "");

  const { data: branchServices = [] } = useQuery<ServiceDto[]>({
    queryKey: ["adminServices", activeBranchIdForForm],
    queryFn: async () => {
      if (!activeBranchIdForForm) return [];
      const res = await getServicesByBranch(activeBranchIdForForm);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!activeBranchIdForForm,
  });

  const { data: queues = [], isLoading, error } = useQuery<QueueResponse[]>({
    queryKey: ["adminQueuesList", filterBranchId, filterServiceId, filterStatus],
    queryFn: async () => {
      const res = await getQueues({
        branchId: filterBranchId || undefined,
        serviceId: filterServiceId || undefined,
        status: filterStatus || undefined,
      });
      if (!res.success || !res.data) throw new Error(res.error?.message || "Failed to load queues");
      return res.data;
    },
  });

  const handleOpenCreate = () => {
    setEditingQueue(null);
    setName("");
    setDescription("");
    setTokenPrefix("A");
    setBranchId(branches.length > 0 ? branches[0].id : "");
    setServiceId("");
    setCapacity(0);
    setIsPriorityEnabled(false);
    setDefaultServiceTimeMinutes(5);
    setRequiresDocumentVerification(false);
    setFormError(null);
    setShowModal(true);
  };

  const handleOpenEdit = (q: QueueResponse) => {
    setEditingQueue(q);
    setName(q.name);
    setDescription(q.description || "");
    setTokenPrefix(q.tokenPrefix);
    setBranchId(q.branchId || "");
    setServiceId(q.serviceId || "");
    setCapacity(q.capacity || 0);
    setIsPriorityEnabled(q.isPriorityEnabled || false);
    setDefaultServiceTimeMinutes(q.defaultServiceTimeMinutes || 5);
    setRequiresDocumentVerification(q.requiresDocumentVerification || false);
    setFormError(null);
    setShowModal(true);
  };

  const handleToggleStatus = async (queue: QueueResponse) => {
    try {
      if (queue.status === QueueStatus.ACTIVE) {
        await closeQueue(queue.id);
      } else {
        await openQueue(queue.id);
      }
      await queryClient.invalidateQueries({ queryKey: ["adminQueuesList"] });
    } catch {
      alert("Failed to toggle queue state");
    }
  };

  const handleDeleteQueue = async (queue: QueueResponse) => {
    if (
      !window.confirm(
        `Are you sure you want to permanently remove queue "${queue.name}"? This will cancel any active tokens and remove the queue.`
      )
    ) {
      return;
    }
    try {
      const res = await deleteQueue(queue.id);
      if (res.success) {
        await queryClient.invalidateQueries({ queryKey: ["adminQueuesList"] });
      } else {
        alert(res.error?.message || "Failed to delete queue");
      }
    } catch {
      alert("Failed to delete queue due to a network error.");
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError("Queue name is required");
      return;
    }
    setSubmitting(true);
    setFormError(null);

    try {
      if (editingQueue) {
        const res = await updateQueue(editingQueue.id, {
          name: name.trim(),
          description: description.trim() || undefined,
          branchId: branchId || undefined,
          serviceId: serviceId || undefined,
          capacity: capacity > 0 ? capacity : undefined,
          isPriorityEnabled,
          defaultServiceTimeMinutes,
          requiresDocumentVerification,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to update queue");
      } else {
        const res = await createQueue({
          name: name.trim(),
          description: description.trim() || undefined,
          tokenPrefix: tokenPrefix.trim().toUpperCase() || "A",
          branchId: branchId || undefined,
          serviceId: serviceId || undefined,
          capacity: capacity > 0 ? capacity : undefined,
          isPriorityEnabled,
          defaultServiceTimeMinutes,
          requiresDocumentVerification,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to create queue");
      }

      await queryClient.invalidateQueries({ queryKey: ["adminQueuesList"] });
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
            <Badge variant="warning" size="sm" className="font-mono uppercase font-bold">
              QUEUE ENGINE
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("queues_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Configure line capacities, VIP priority rules, service times, and QR endpoints.
          </p>
        </div>
        <div>
          <Button
            onClick={handleOpenCreate}
            variant="primary"
            size="md"
            icon={<Plus className="w-4 h-4" />}
          >
            {t("add_queue")}
          </Button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 p-4 bg-white rounded-2xl border border-slate-200 shadow-xs">
        {branches.length > 0 && (
          <select
            value={filterBranchId}
            onChange={(e) => setFilterBranchId(e.target.value)}
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

        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-3 py-2 border border-slate-300 rounded-xl text-xs font-semibold bg-white text-slate-800 shadow-xs outline-none focus:ring-2 focus:ring-teal-500/20"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Open / Active</option>
          <option value="CLOSED">Closed</option>
          <option value="PAUSED">Paused</option>
        </select>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <CardSkeleton />
          <CardSkeleton />
        </div>
      ) : error ? (
        <div className="p-8 text-center bg-rose-50 text-rose-700 rounded-2xl border border-rose-200">
          Failed to load queues.
        </div>
      ) : queues.length === 0 ? (
        <EmptyState
          icon={<ListOrdered className="w-7 h-7" />}
          title="No queues configured"
          description="Create your first queue line to start issuing digital tokens."
          actionLabel={t("add_queue")}
          onAction={handleOpenCreate}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {queues.map((q) => {
            const isOpen = q.status === QueueStatus.ACTIVE;
            return (
              <Card key={q.id} hover className="p-6 flex flex-col justify-between">
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <h3 className="text-base font-bold text-slate-900 leading-snug">{q.name}</h3>
                      <div className="flex items-center gap-1.5 mt-1">
                        <span className="px-2 py-0.5 text-[11px] font-mono font-bold rounded-md bg-slate-100 text-slate-700 border border-slate-200">
                          Prefix: {q.tokenPrefix}
                        </span>
                        {q.isPriorityEnabled && (
                          <Badge variant="purple" size="sm" className="font-bold">
                            VIP ENABLED
                          </Badge>
                        )}
                        {q.requiresDocumentVerification && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                            <ShieldCheck className="w-3 h-3 text-amber-600" /> DOCS REQ
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => setQrModalQueue(q)}
                        className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                        title="View QR Code & Share Link"
                      >
                        <QrCode className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleOpenEdit(q)}
                        className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                        title="Edit Queue"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeleteQueue(q)}
                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded-xl hover:bg-rose-50 transition-colors"
                        title="Delete / Remove Queue"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {q.description && (
                    <p className="text-xs text-slate-600 mt-3 leading-relaxed line-clamp-2">
                      {q.description}
                    </p>
                  )}

                  {/* Metrics Bar */}
                  <div className="grid grid-cols-3 gap-2 mt-4 p-3 bg-slate-50 rounded-xl border border-slate-100 text-center text-xs">
                    <div>
                      <span className="text-slate-400 block text-[10px]">Serving</span>
                      <strong className="font-mono font-bold text-slate-900">
                        {q.currentTokenNumber || "-"}
                      </strong>
                    </div>
                    <div>
                      <span className="text-slate-400 block text-[10px]">Waiting</span>
                      <strong className="font-bold text-teal-700">{q.waitingCount ?? 0}</strong>
                    </div>
                    <div>
                      <span className="text-slate-400 block text-[10px]">Capacity</span>
                      <strong className="font-bold text-slate-700">
                        {q.capacity ? `${q.capacity}` : "∞"}
                      </strong>
                    </div>
                  </div>
                </div>

                <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                  <Badge variant={isOpen ? "success" : "neutral"} size="sm" dot>
                    {q.status}
                  </Badge>

                  <Button
                    onClick={() => handleToggleStatus(q)}
                    variant="outline"
                    size="xs"
                    className={
                      isOpen
                        ? "text-rose-600 border-rose-200 hover:bg-rose-50"
                        : "text-emerald-600 border-emerald-200 hover:bg-emerald-50"
                    }
                  >
                    {isOpen ? t("close_queue") : t("open_queue")}
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Queue Create/Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingQueue ? t("edit_queue") : t("add_queue")}
        description="Configure queue parameters, token prefix, and priority policy."
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
              {t("queue_name")} *
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

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Token Prefix *
              </label>
              <input
                type="text"
                required
                maxLength={5}
                value={tokenPrefix}
                onChange={(e) => setTokenPrefix(e.target.value.toUpperCase())}
                placeholder="A"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs uppercase font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Max Capacity
              </label>
              <input
                type="number"
                min={0}
                value={capacity}
                onChange={(e) => setCapacity(parseInt(e.target.value) || 0)}
                placeholder="0 for unlimited"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
              />
            </div>
          </div>

          {!editingQueue && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  Branch
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
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  Service
                </label>
                <select
                  value={serviceId}
                  onChange={(e) => setServiceId(e.target.value)}
                  className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs bg-white"
                >
                  <option value="">Select Service...</option>
                  {branchServices.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Avg Service Time (mins)
              </label>
              <input
                type="number"
                min={1}
                value={defaultServiceTimeMinutes}
                onChange={(e) => setDefaultServiceTimeMinutes(parseInt(e.target.value) || 5)}
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
              />
            </div>
            <div className="flex items-center pt-6">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={isPriorityEnabled}
                  onChange={(e) => setIsPriorityEnabled(e.target.checked)}
                  className="rounded text-teal-600 focus:ring-teal-500 h-4 w-4"
                />
                <span className="text-xs font-bold text-slate-700">Enable VIP Priority</span>
              </label>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Description
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Standard consultation queue for registered citizens"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          {/* AI Document Verification Toggle */}
          <div className="flex items-start gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-2xl">
            <input
              id="queueReqDoc"
              type="checkbox"
              checked={requiresDocumentVerification}
              onChange={(e) => setRequiresDocumentVerification(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 cursor-pointer"
            />
            <label htmlFor="queueReqDoc" className="text-xs text-slate-700 cursor-pointer">
              <span className="font-bold block text-slate-900">Requires AI Document Verification</span>
              Enforce document analysis gatekeeping before customers can join or be served in this queue.
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

      {/* QR Code Modal */}
      {qrModalQueue && (
        <QueueQRCodeModal
          isOpen={!!qrModalQueue}
          onClose={() => setQrModalQueue(null)}
          queueId={qrModalQueue.id}
          queueName={qrModalQueue.name}
        />
      )}
    </div>
  );
};
export default QueueManagementPage;
