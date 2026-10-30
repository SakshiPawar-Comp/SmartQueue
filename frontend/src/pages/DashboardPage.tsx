import { useAuth } from "../context/AuthContext";
import { getHighestRole } from "../lib/roles";
import { Header } from "../components/Header";
import { CustomerDashboard } from "./dashboard/CustomerDashboard";
import { StaffDashboard } from "./dashboard/StaffDashboard";
import { OrganizationAdminDashboard } from "./dashboard/OrganizationAdminDashboard";
import { SuperAdminDashboard } from "./dashboard/SuperAdminDashboard";

export const DashboardPage: React.FC = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin h-8 w-8 border-4 border-teal-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const highestRole = getHighestRole(user);

  function renderDashboard() {
    switch (highestRole) {
      case "SUPER_ADMIN":
        return <SuperAdminDashboard />;
      case "ORGANIZATION_ADMIN":
      case "BRANCH_ADMIN":
        return <OrganizationAdminDashboard />;
      case "STAFF":
        return <StaffDashboard />;
      case "CUSTOMER":
      default:
        return <CustomerDashboard />;
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Header />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {renderDashboard()}
      </main>
    </div>
  );
};
