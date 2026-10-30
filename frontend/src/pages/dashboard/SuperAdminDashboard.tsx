import { useAuth } from "../../context/AuthContext";
import { OrganizationAdminDashboard } from "./OrganizationAdminDashboard";

export const SuperAdminDashboard: React.FC = () => {
  const { user } = useAuth();

  return (
    <div className="space-y-6">
      <div className="bg-gradient-to-r from-teal-600 to-teal-700 rounded-xl p-5 text-white">
        <h1 className="text-lg font-bold">Super Admin Dashboard</h1>
        <p className="text-teal-100 text-sm mt-1">
          Platform administration for {user?.fullName}.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="bg-white rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-medium text-slate-700 mb-1">Organizations</div>
          <p className="text-xs text-slate-500">Organization management is coming in a future phase.</p>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-medium text-slate-700 mb-1">Branches</div>
          <p className="text-xs text-slate-500">Branch management is coming in a future phase.</p>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 p-4">
          <div className="text-sm font-medium text-slate-700 mb-1">Staff</div>
          <p className="text-xs text-slate-500">Staff assignment is coming in a future phase.</p>
        </div>
      </div>

      {/* Delegate to the full admin dashboard for queue management */}
      <div className="border-t border-slate-200 pt-6">
        <OrganizationAdminDashboard />
      </div>
    </div>
  );
};
