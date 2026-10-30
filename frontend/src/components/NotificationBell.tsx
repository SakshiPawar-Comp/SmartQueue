import React, { useState, useEffect, useRef } from "react";
import { useAuth } from "../context/AuthContext";
import { useLanguage } from "../i18n/LanguageContext";
import { getMyNotifications, getUnreadNotificationCount, markNotificationAsRead, markAllNotificationsAsRead } from "../lib/apiClient";
import { NotificationItem } from "../types/queue";
import { NotificationPreferencesModal } from "./NotificationPreferencesModal";
import { Bell, Check, CheckCheck, Settings, X, ExternalLink } from "lucide-react";
import { useNavigate } from "react-router-dom";

export const NotificationBell: React.FC = () => {
  const { user } = useAuth();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [isOpen, setIsOpen] = useState(false);
  const [isPrefOpen, setIsPrefOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (user?.id) {
      loadUnreadCount();
    }
  }, [user?.id]);

  useEffect(() => {
    if (isOpen && user?.id) {
      loadNotifications();
    }
  }, [isOpen, user?.id]);

  // Click outside to close
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const loadUnreadCount = async () => {
    const res = await getUnreadNotificationCount();
    if (res.success && res.data) {
      setUnreadCount(res.data.unreadCount);
    }
  };

  const loadNotifications = async () => {
    setLoading(true);
    const res = await getMyNotifications(0, 15);
    if (res.success && res.data) {
      setNotifications(res.data);
    }
    setLoading(false);
  };

  const handleMarkAsRead = async (id: string, tokenId?: string) => {
    const res = await markNotificationAsRead(id);
    if (res.success && res.data) {
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    }
    if (tokenId) {
      setIsOpen(false);
      navigate(`/token/${tokenId}`);
    }
  };

  const handleMarkAllAsRead = async () => {
    const res = await markAllNotificationsAsRead();
    if (res.success) {
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setUnreadCount(0);
    }
  };

  if (!user) return null;

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Bell Trigger Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-gray-600 hover:text-indigo-600 rounded-lg hover:bg-gray-100 transition focus:outline-none"
        aria-label={t("notifications")}
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-white bg-red-500 rounded-full animate-pulse">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {/* Notifications Dropdown Panel */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-xl shadow-2xl border border-gray-100 z-50 overflow-hidden animate-fadeIn">
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 bg-gray-50 border-b border-gray-100">
            <div className="flex items-center space-x-2">
              <span className="font-semibold text-gray-900 text-sm">{t("notifications_title")}</span>
              {unreadCount > 0 && (
                <span className="text-xs bg-indigo-100 text-indigo-700 font-medium px-2 py-0.5 rounded-full">
                  {unreadCount} new
                </span>
              )}
            </div>
            <div className="flex items-center space-x-1">
              {unreadCount > 0 && (
                <button
                  onClick={handleMarkAllAsRead}
                  title={t("mark_all_read")}
                  className="p-1.5 text-gray-500 hover:text-indigo-600 rounded hover:bg-gray-100 text-xs flex items-center space-x-1"
                >
                  <CheckCheck className="w-4 h-4" />
                </button>
              )}
              <button
                onClick={() => {
                  setIsOpen(false);
                  setIsPrefOpen(true);
                }}
                title={t("notification_preferences")}
                className="p-1.5 text-gray-500 hover:text-indigo-600 rounded hover:bg-gray-100"
              >
                <Settings className="w-4 h-4" />
              </button>
              <button
                onClick={() => setIsOpen(false)}
                className="p-1.5 text-gray-400 hover:text-gray-600 rounded hover:bg-gray-100"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* List Content */}
          <div className="max-h-96 overflow-y-auto divide-y divide-gray-50">
            {loading ? (
              <div className="py-8 text-center text-xs text-gray-500">{t("loading")}</div>
            ) : notifications.length === 0 ? (
              <div className="py-8 text-center text-xs text-gray-500 px-4">
                {t("no_notifications")}
              </div>
            ) : (
              notifications.map((item) => (
                <div
                  key={item.id}
                  onClick={() => handleMarkAsRead(item.id, item.tokenId)}
                  className={`p-4 transition cursor-pointer hover:bg-indigo-50/50 flex items-start space-x-3 ${
                    !item.isRead ? "bg-indigo-50/20" : "bg-white"
                  }`}
                >
                  <div
                    className={`w-2 h-2 mt-1.5 rounded-full flex-shrink-0 ${
                      !item.isRead ? "bg-indigo-600" : "bg-transparent"
                    }`}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between">
                      <p className={`text-xs font-semibold ${!item.isRead ? "text-gray-900" : "text-gray-700"}`}>
                        {item.title}
                      </p>
                      <span className="text-[10px] text-gray-400 ml-2 whitespace-nowrap">
                        {new Date(item.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                      </span>
                    </div>
                    <p className="text-xs text-gray-600 mt-1 line-clamp-2">{item.message}</p>
                    {item.tokenNumber && (
                      <div className="mt-2 flex items-center space-x-1 text-[11px] text-indigo-600 font-medium">
                        <span>Token #{item.tokenNumber}</span>
                        <ExternalLink className="w-3 h-3" />
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Preferences Modal */}
      <NotificationPreferencesModal
        isOpen={isPrefOpen}
        onClose={() => setIsPrefOpen(false)}
      />
    </div>
  );
};
