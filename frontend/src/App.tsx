import { Routes, Route, Navigate } from "react-router-dom";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ProfilePage } from "./pages/ProfilePage";
import { DashboardPage } from "./pages/DashboardPage";
import { QueueDetailPage } from "./pages/QueueDetailPage";
import { TokenPage } from "./pages/TokenPage";
import { HistoryPage } from "./pages/HistoryPage";
import { ProtectedRoute } from "./components/ProtectedRoute";

import { DeepLinkJoinPage } from "./pages/DeepLinkJoinPage";

// Admin Management Pages
import { BranchManagementPage } from "./pages/admin/BranchManagementPage";
import { ServiceManagementPage } from "./pages/admin/ServiceManagementPage";
import { CounterManagementPage } from "./pages/admin/CounterManagementPage";
import { QueueManagementPage } from "./pages/admin/QueueManagementPage";
import { StaffManagementPage } from "./pages/admin/StaffManagementPage";
import { AnalyticsDashboardPage } from "./pages/admin/AnalyticsDashboardPage";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/join" element={<DeepLinkJoinPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/queues/:id" element={<QueueDetailPage />} />
        <Route path="/token/:id" element={<TokenPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/me" element={<ProfilePage />} />

        {/* Admin Management Routes */}
        <Route path="/admin/branches" element={<BranchManagementPage />} />
        <Route path="/admin/services" element={<ServiceManagementPage />} />
        <Route path="/admin/counters" element={<CounterManagementPage />} />
        <Route path="/admin/queues" element={<QueueManagementPage />} />
        <Route path="/admin/staff" element={<StaffManagementPage />} />
        <Route path="/admin/analytics" element={<AnalyticsDashboardPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}