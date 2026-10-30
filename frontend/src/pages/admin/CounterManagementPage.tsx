import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../../context/AuthContext";
import { useTranslation } from "../../i18n/LanguageContext";
import {
  getBranchesByOrg,
  getCounters,
  createCounter,
  updateCounter,
  assignStaffToCounter,
  assignQueuesToCounter,
  getStaffList,
  getQueuesByBranch,
  BranchDto,
  CounterDto,
  StaffUserDto,
  QueueResponse,
} from "../../lib/apiClient";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Modal } from "../../components/ui/Modal";
import { EmptyState } from "../../components/ui/EmptyState";
import { CardSkeleton } from "../../components/ui/LoadingSkeleton";
import { Monitor, Users, ListOrdered, Plus, Edit2, UserCheck, Layers, AlertTriangle } from "lucide-react";

export const CounterManagementPage: React.FC = () => {
  const { user } = useAuth();
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [selectedBranchId, setSelectedBranchId] = useState<string>("");
  const [showCounterModal, setShowCounterModal] = useState(false);
  const [showStaffModal, setShowStaffModal] = useState(false);
  const [showQueueModal, setShowQueueModal] = useState(false);

  const [activeCounter, setActiveCounter] = useState<CounterDto | null>(null);

  // Counter Form
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  // Assignment states
  const [selectedStaffUserId, setSelectedStaffUserId] = useState<string>("");
  const [selectedQueueIds, setSelectedQueueIds] = useState<string[]>([]);

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

  const { data: counters = [], isLoading, error } = useQuery<CounterDto[]>({
    queryKey: ["adminCounters", activeBranchId],
    queryFn: async () => {
      if (!activeBranchId) return [];
      const res = await getCounters({ branchId: activeBranchId });
      if (!res.success || !res.data) throw new Error(res.error?.message || "Failed to load counters");
      return res.data;
    },
    enabled: !!activeBranchId,
  });

  // Query staff for assignment
  const { data: staffList = [] } = useQuery<StaffUserDto[]>({
    queryKey: ["adminStaff", activeBranchId],
    queryFn: async () => {
      if (!activeBranchId) return [];
      const res = await getStaffList(activeBranchId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!activeBranchId && showStaffModal,
  });

  // Query queues for assignment
  const { data: branchQueues = [] } = useQuery<QueueResponse[]>({
    queryKey: ["adminBranchQueues", activeBranchId],
    queryFn: async () => {
      if (!activeBranchId) return [];
      const res = await getQueuesByBranch(activeBranchId);
      return res.success && res.data ? res.data : [];
    },
    enabled: !!activeBranchId && showQueueModal,
  });

  const handleOpenCreate = () => {
    setActiveCounter(null);
    setName("");
    setCode("");
    setDescription("");
    setModalError(null);
    setShowCounterModal(true);
  };

  const handleOpenEdit = (counter: CounterDto) => {
    setActiveCounter(counter);
    setName(counter.name);
    setCode(counter.code || "");
    setDescription(counter.description || "");
    setModalError(null);
    setShowCounterModal(true);
  };

  const handleOpenStaffAssignment = (counter: CounterDto) => {
    setActiveCounter(counter);
    setSelectedStaffUserId(counter.assignedStaffUserId || "");
    setModalError(null);
    setShowStaffModal(true);
  };

  const handleOpenQueueAssignment = (counter: CounterDto) => {
    setActiveCounter(counter);
    setSelectedQueueIds(counter.assignedQueueIds || []);
    setModalError(null);
    setShowQueueModal(true);
  };

  const handleSubmitCounter = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setModalError("Counter name is required");
      return;
    }
    if (!activeBranchId) {
      setModalError("Branch is required");
      return;
    }
    setSubmitting(true);
    setModalError(null);

    try {
      if (activeCounter) {
        const res = await updateCounter(activeCounter.id, {
          name: name.trim(),
          description: description.trim() || undefined,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to update counter");
      } else {
        const res = await createCounter({
          name: name.trim(),
          code: code.trim().toUpperCase() || undefined,
          description: description.trim() || undefined,
          branchId: activeBranchId,
        });
        if (!res.success) throw new Error(res.error?.message || "Failed to create counter");
      }

      await queryClient.invalidateQueries({ queryKey: ["adminCounters"] });
      setShowCounterModal(false);
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAssignStaff = async () => {
    if (!activeCounter || !selectedStaffUserId) return;
    setSubmitting(true);
    setModalError(null);
    try {
      const res = await assignStaffToCounter(activeCounter.id, selectedStaffUserId);
      if (!res.success) throw new Error(res.error?.message || "Failed to assign staff");
      await queryClient.invalidateQueries({ queryKey: ["adminCounters"] });
      setShowStaffModal(false);
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "Assignment failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleAssignQueues = async () => {
    if (!activeCounter) return;
    setSubmitting(true);
    setModalError(null);
    try {
      const res = await assignQueuesToCounter(activeCounter.id, selectedQueueIds);
      if (!res.success) throw new Error(res.error?.message || "Failed to assign queues");
      await queryClient.invalidateQueries({ queryKey: ["adminCounters"] });
      setShowQueueModal(false);
    } catch (err) {
      setModalError(err instanceof Error ? err.message : "Assignment failed");
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
            <Badge variant="emerald" size="sm" className="font-mono uppercase font-bold">
              DESKS &amp; HARDWARE
            </Badge>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-1.5">
            {t("counters_title")}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Configure service desks, associate staff operators, and map queue routing.
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
            {t("add_counter")}
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
          Failed to load counter records.
        </div>
      ) : counters.length === 0 ? (
        <EmptyState
          icon={<Monitor className="w-7 h-7" />}
          title="No counters configured"
          description="Create your first desk counter to start assigning staff operators and routing customer queues."
          actionLabel={t("add_counter")}
          onAction={handleOpenCreate}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {counters.map((c) => (
            <Card key={c.id} hover className="p-6 flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="text-base font-bold text-slate-900 leading-snug">{c.name}</h3>
                    {c.code && (
                      <span className="inline-block mt-1 px-2 py-0.5 text-[11px] font-mono font-bold rounded-md bg-slate-100 text-slate-700 border border-slate-200">
                        {c.code}
                      </span>
                    )}
                  </div>
                  <button
                    onClick={() => handleOpenEdit(c)}
                    className="p-1.5 text-slate-400 hover:text-teal-600 rounded-xl hover:bg-slate-100 transition-colors"
                    title="Edit Counter"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>

                {/* Operator Assignment Card */}
                <div className="mt-4 p-3 bg-slate-50 rounded-xl border border-slate-100 text-xs space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400 font-semibold uppercase text-[10px]">
                      Assigned Staff
                    </span>
                    <button
                      onClick={() => handleOpenStaffAssignment(c)}
                      className="text-teal-700 font-bold hover:underline"
                    >
                      {c.assignedStaffName ? "Change" : "Assign"}
                    </button>
                  </div>
                  <div className="font-bold text-slate-900 flex items-center gap-1.5">
                    <UserCheck className="w-3.5 h-3.5 text-teal-600" />
                    <span>{c.assignedStaffName || "Unassigned"}</span>
                  </div>
                </div>

                {/* Queues Assignment Card */}
                <div className="mt-2.5 p-3 bg-slate-50 rounded-xl border border-slate-100 text-xs space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400 font-semibold uppercase text-[10px]">
                      Served Queues ({c.assignedQueueIds?.length || 0})
                    </span>
                    <button
                      onClick={() => handleOpenQueueAssignment(c)}
                      className="text-teal-700 font-bold hover:underline"
                    >
                      Configure
                    </button>
                  </div>
                  <div className="font-semibold text-slate-700 text-[11px]">
                    {c.assignedQueueIds && c.assignedQueueIds.length > 0 ? (
                      <span className="text-teal-700 font-bold">
                        {c.assignedQueueIds.length} queue{c.assignedQueueIds.length > 1 ? "s" : ""} active
                      </span>
                    ) : (
                      <span className="text-slate-400 italic">No queues assigned</span>
                    )}
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs">
                <Badge variant={c.status === "ACTIVE" ? "success" : "neutral"} size="sm" dot>
                  {c.status}
                </Badge>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Counter Create/Edit Modal */}
      <Modal
        isOpen={showCounterModal}
        onClose={() => setShowCounterModal(false)}
        title={activeCounter ? t("edit_counter") : t("add_counter")}
        description="Configure physical counter desk parameters."
      >
        {modalError && (
          <div className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{modalError}</span>
          </div>
        )}

        <form onSubmit={handleSubmitCounter} className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              {t("counter_name")} *
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Counter 1"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          {!activeCounter && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                {t("counter_code")}
              </label>
              <input
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="e.g. C1"
                className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs uppercase font-mono"
              />
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              {t("counter_description")}
            </label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Consultation & Intake desk"
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs"
            />
          </div>

          <div className="flex justify-end gap-2.5 pt-4 border-t border-slate-100">
            <Button onClick={() => setShowCounterModal(false)} variant="outline" size="sm">
              {t("cancel")}
            </Button>
            <Button type="submit" loading={submitting} variant="primary" size="sm">
              {submitting ? t("loading") : t("save")}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Staff Assignment Modal */}
      <Modal
        isOpen={showStaffModal}
        onClose={() => setShowStaffModal(false)}
        title="Assign Staff Operator"
        description={`Assign an operator to ${activeCounter?.name}`}
      >
        {modalError && (
          <div className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{modalError}</span>
          </div>
        )}

        <div className="space-y-4">
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Select Staff Member
            </label>
            <select
              value={selectedStaffUserId}
              onChange={(e) => setSelectedStaffUserId(e.target.value)}
              className="w-full px-3.5 py-2.5 border border-slate-300 rounded-xl text-sm text-slate-900 focus:ring-2 focus:ring-teal-500/20 focus:border-teal-500 outline-none shadow-xs bg-white cursor-pointer"
            >
              <option value="">Choose a staff user...</option>
              {staffList.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName} ({s.email})
                </option>
              ))}
            </select>
          </div>

          <div className="flex justify-end gap-2.5 pt-4 border-t border-slate-100">
            <Button onClick={() => setShowStaffModal(false)} variant="outline" size="sm">
              {t("cancel")}
            </Button>
            <Button
              onClick={handleAssignStaff}
              loading={submitting}
              disabled={!selectedStaffUserId}
              variant="primary"
              size="sm"
            >
              Assign Staff
            </Button>
          </div>
        </div>
      </Modal>

      {/* Queue Assignment Modal */}
      <Modal
        isOpen={showQueueModal}
        onClose={() => setShowQueueModal(false)}
        title="Assign Queues to Counter"
        description={`Select which queues ${activeCounter?.name} is authorized to serve.`}
      >
        {modalError && (
          <div className="mb-4 p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-xl flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-rose-500 shrink-0" />
            <span>{modalError}</span>
          </div>
        )}

        <div className="space-y-4">
          <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
            {branchQueues.map((q) => {
              const isChecked = selectedQueueIds.includes(q.id);
              return (
                <label
                  key={q.id}
                  className={`flex items-center justify-between p-3 rounded-xl border transition-all cursor-pointer ${
                    isChecked
                      ? "bg-teal-50 border-teal-300 text-teal-950 font-bold"
                      : "bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedQueueIds([...selectedQueueIds, q.id]);
                        } else {
                          setSelectedQueueIds(selectedQueueIds.filter((id) => id !== q.id));
                        }
                      }}
                      className="rounded text-teal-600 focus:ring-teal-500 h-4 w-4"
                    />
                    <span className="text-sm">{q.name}</span>
                  </div>
                  <span className="font-mono text-xs text-slate-500">{q.tokenPrefix}</span>
                </label>
              );
            })}
          </div>

          <div className="flex justify-end gap-2.5 pt-4 border-t border-slate-100">
            <Button onClick={() => setShowQueueModal(false)} variant="outline" size="sm">
              {t("cancel")}
            </Button>
            <Button onClick={handleAssignQueues} loading={submitting} variant="primary" size="sm">
              Save Queue Routing
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
export default CounterManagementPage;
