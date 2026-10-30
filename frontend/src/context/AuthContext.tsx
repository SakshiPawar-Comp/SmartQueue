import { createContext, useContext, useState, useEffect, ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  login,
  register as apiRegister,
  logout,
  refreshToken,
  getCurrentToken,
  setAccessToken as storeAccessToken,
  ApiResult,
} from "../lib/apiClient";
import { UserDto, AuthResponse } from "../types/auth";

/** The authentication context value type. */
interface AuthContextValue {
  user: UserDto | null;
  accessToken: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string, role: "CUSTOMER" | "ORGANIZATION_ADMIN", organizationCode?: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

/** The authentication context props type. */
interface AuthProviderProps {
  children: ReactNode;
}

/** Mapping of HTTP status codes to user-friendly messages. */
const authErrorMessages: Record<number, string> = {
  400: "Invalid request data. Please check your input and try again.",
  401: "Authentication failed. Please check your credentials.",
  403: "You don't have permission to access this resource.",
  409: "Account conflict. This email may already be registered.",
  422: "Validation failed. Please check your input and try again.",
  500: "Server error. Please try again later.",
};

/** The auth context object. */
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** AuthContext provides authentication state and actions. */
export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserDto | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  /** Attempt to restore authentication on page mount. */
  useEffect(() => {
    restoreAuthOnMount();
  }, []);

  /** Restore authentication using the browser HttpOnly refresh cookie. */
  async function restoreAuthOnMount() {
    setIsLoading(true);
    try {
      const result = await refreshToken();

      if (result.success && result.data) {
        storeAccessToken(result.data.accessToken);
        setAccessToken(result.data.accessToken);
        await fetchUserInfoWithToken(result.data.accessToken);
      } else {
        setUser(null);
        setAccessToken(null);
        storeAccessToken(null);
      }
    } catch {
      setUser(null);
      setAccessToken(null);
      storeAccessToken(null);
    } finally {
      setIsLoading(false);
    }
  }

  /** Fetch user info using a specific token (avoids stale state closure). */
  async function fetchUserInfoWithToken(token: string) {
    try {
      const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL ?? ""}/api/auth/me`,
        {
          credentials: "include",
          headers: { Authorization: `Bearer ${token}` },
        }
      );
      const data: ApiResult<UserDto> = await response.json();
      if (data.success && data.data) {
        setUser(data.data);
      } else {
        setUser(null);
      }
    } catch (error) {
      console.error("[Auth] Failed to fetch user info:", error);
      setUser(null);
    }
  }

  /** Fetch user info using the current in-memory token. */
  async function fetchUserInfo() {
    const token = getCurrentToken();
    if (!token) {
      setUser(null);
      return;
    }
    await fetchUserInfoWithToken(token);
  }

  /** Handle API errors and return user-friendly messages. */
  function mapApiError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    if (typeof error === "object" && error !== null) {
      const e = error as { code?: string; message?: string };
      return e.message || authErrorMessages[Number(e.code) as keyof typeof authErrorMessages] || "An unexpected error occurred.";
    }
    return "An unexpected error occurred.";
  }

  /** Login the user with email and password. */
  async function handleLogin(email: string, password: string) {
    setIsLoading(true);
    try {
      const result: ApiResult<AuthResponse> = await login({ email, password });
      if (result.success && result.data) {
        setUser(result.data.user);
        storeAccessToken(result.data.accessToken);
        setAccessToken(result.data.accessToken);
      } else {
        throw new Error(mapApiError(result.error));
      }
    } catch (error) {
      console.error("[Login] Login failed:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }

  /** Register a new user. */
  async function handleRegister(fullName: string, email: string, password: string, role: "CUSTOMER" | "ORGANIZATION_ADMIN", organizationCode?: string) {
    setIsLoading(true);
    try {
      const result: ApiResult<AuthResponse> = await apiRegister({ fullName, email, password, role, organizationCode });
      if (result.success && result.data) {
        setUser(result.data.user);
        storeAccessToken(result.data.accessToken);
        setAccessToken(result.data.accessToken);
      } else {
        throw new Error(mapApiError(result.error));
      }
    } catch (error) {
      console.error("[Register] Registration failed:", error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  }

  /** Log out the user. */
  async function handleLogout() {
    setIsLoading(true);
    try {
      await logout();
    } catch {
      // Ignore logout errors — always clear local state
    } finally {
      setUser(null);
      setAccessToken(null);
      storeAccessToken(null);
      navigate("/login");
      setIsLoading(false);
    }
  }

  /** Refresh the user's authentication token. */
  async function handleRefreshUser() {
    setIsLoading(true);
    try {
      const result = await refreshToken();
      if (result.success && result.data) {
        storeAccessToken(result.data.accessToken);
        setAccessToken(result.data.accessToken);
        await fetchUserInfoWithToken(result.data.accessToken);
      } else {
        setUser(null);
        setAccessToken(null);
        storeAccessToken(null);
      }
    } catch (error) {
      console.error("[Auth] Refresh user failed:", error);
      setUser(null);
      setAccessToken(null);
      storeAccessToken(null);
    } finally {
      setIsLoading(false);
    }
  }

  const authenticatedValue: AuthContextValue = {
    user,
    accessToken,
    isLoading,
    login: handleLogin,
    register: handleRegister,
    logout: handleLogout,
    refreshUser: handleRefreshUser,
  };

  return (
    <AuthContext.Provider value={authenticatedValue}>
      {children}
    </AuthContext.Provider>
  );
};

/** Access the authentication context. */
export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);
  if (!context) {
    return {
      user: null,
      accessToken: null,
      isLoading: false,
      login: async () => {},
      register: async () => {},
      logout: async () => {},
      refreshUser: async () => {},
    };
  }
  return context;
};
