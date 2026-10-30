import { ApiResponse, UserDto, AuthResponse } from "../types/auth";
import { TokenDocument, TokenDocumentStatus } from "../types/queue";

// Base URL from Vite environment
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

/**
 * Centralized API client using fetch.
 * 
 * Design decisions:
 * - accessToken stored only in AuthContext memory (never localStorage/sessionStorage)
 * - refreshToken handled exclusively by browser HttpOnly cookie
 * - credentials: "include" automatically attaches the refresh cookie
 * - Single-flight token refresh: only one concurrent refresh request
 * - Max one retry per request after 401
 * - Refresh failure triggers auth state cleanup and login redirect
 */

/** Error response shape from the backend API. */
export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
}

/** Consistent result type matching the backend ApiResponse envelope. */
export interface ApiResult<T> {
  success: boolean;
  data?: T | null;
  error: ApiError | null;
}

/**
 * Build request options with auth header and cookie support.
 * The Authorization header is set if a token is available.
 * credentials: "include" ensures the browser sends HttpOnly refresh cookies.
 */
export function buildOptions(
  token: string | null,
  body?: BodyInit
): RequestInit {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  return {
    credentials: "include" as RequestCredentials,
    headers,
    ...(body ? { body } : {}),
  };
}

/**
 * Parse the API response and return a consistent result type.
 * The backend returns ApiResponse<T> wrapping the actual data.
 * Reads body as text first to avoid "body stream already read" errors.
 */
export async function parseResponse<T>(response: Response): Promise<ApiResult<T>> {
  const rawBody = await response.text();

  if (!response.ok) {
    let parsed: ApiResponse<T> | null = null;
    try {
      parsed = JSON.parse(rawBody) as ApiResponse<T>;
    } catch {
      // Not JSON — use raw text
    }

    return {
      success: false,
      error: {
        code: `${response.status}`,
        message: parsed?.error?.message ?? response.statusText,
        details: parsed?.error?.details ?? rawBody,
      },
      data: null,
    };
  }

  const envelope = JSON.parse(rawBody) as ApiResponse<T>;

  if (envelope.error) {
    return {
      success: false,
      error: {
        code: envelope.error.code,
        message: envelope.error.message,
        details: envelope.error.details,
      },
      data: null,
    };
  }

  return {
    success: true,
    data: envelope.data ?? null,
    error: null,
  };
}

/** In-memory access token. Never persisted to storage. */
let inMemoryAccessToken: string | null = null;

/** Set the in-memory access token. Called by AuthContext after refresh/login. */
export function setAccessToken(token: string | null): void {
  inMemoryAccessToken = token;
}

/** Single-flight token refresh. Only one refresh request runs at a time. */
export let refreshPromise: Promise<ApiResult<{ accessToken: string }>> | null = null;
/** Map of request IDs waiting for a refresh resolution */
const waitingResolveMap = new Map<
  string,
  { resolve: (result: ApiResult<{ accessToken: string }> | null) => void }
>();

/**
 * Refresh the access token using the browser-automatic HttpOnly refresh cookie.
 * Returns the new access token result, or null on failure.
 * Single-flight pattern: only one concurrent refresh request runs at a time.
 */
export async function refreshToken(): Promise<ApiResult<{ accessToken: string }>> {
  // If a refresh is already in progress, wait for it
  if (refreshPromise) {
    return refreshPromise.then((result) => result ?? null);
  }

  // Start a new refresh request
  refreshPromise = (async () => {
    try {
      const refreshResponse = await fetch(`${apiBaseUrl}/api/auth/refresh`, {
        method: "POST",
        credentials: "include",
      });

      if (!refreshResponse.ok) {
        // Refresh failed - clear auth state
        await clearAuthState();
        return {
          success: false,
          error: {
            code: `${refreshResponse.status}`,
            message: refreshResponse.statusText,
            details: await refreshResponse.text(),
          },
          data: null,
        };
      }

      const result: ApiResult<{ accessToken: string }> = await parseResponse<
        { accessToken: string }
      >(refreshResponse);

      if (!result.success || !result.data) {
        await clearAuthState();
        return {
          success: false,
          error: result.error ?? { code: "refresh-failed", message: "Token refresh failed" },
          data: null,
        };
      }

      // Store the new token in memory (called by AuthContext)
      console.debug("[API] Access token refreshed");

      return {
        success: true,
        data: result.data,
        error: null,
      };
    } catch (error) {
      console.error("[API] Refresh request failed:", error);
      await clearAuthState();
      return {
        success: false,
        error: { code: "refresh-error", message: "Network error during token refresh" },
        data: null,
      };
    } finally {
      // Release the refresh promise and resolve all waiting requests
      const previousPromise = refreshPromise as Promise<ApiResult<{ accessToken: string }>> | null;
      refreshPromise = null;

      // Resolve all waiting requests with the new result
      waitingResolveMap.forEach(({ resolve }) => {
        if (previousPromise !== null) {
          previousPromise.then(
            (result: ApiResult<{ accessToken: string }> | null) => resolve(result ?? null),
            (err: ApiError | null) => resolve(null)
          );
        }
      });
      waitingResolveMap.clear();
    }
  })();

  return refreshPromise.then((result) => result ?? null);
}

/** Clear authentication state when refresh fails. */
async function clearAuthState(): Promise<void> {
  refreshPromise = null;
  waitingResolveMap.clear();
}

/**
 * Get the current access token from memory.
 * Returns null if no token is stored.
 */
export function getCurrentToken(): string | null {
  return inMemoryAccessToken;
}

/**
 * Register a new user.
 * Returns the auth response with user and accessToken.
 * On success, stores the access token in memory.
 */
export async function register(
  dto: { email: string; password: string; fullName: string; role: "CUSTOMER" | "ORGANIZATION_ADMIN"; organizationCode?: string }
): Promise<ApiResult<AuthResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/auth/register`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(dto),
  });

  const result = await parseResponse<AuthResponse>(response);
  if (result.success && result.data?.accessToken) {
    inMemoryAccessToken = result.data.accessToken;
  }
  return result;
}

/**
 * Login user.
 * Returns the auth response with user and accessToken.
 * On success, stores the access token in memory (refresh cookie handled by browser).
 */
export async function login(
  dto: { email: string; password: string }
): Promise<ApiResult<AuthResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(dto),
  });

  const result = await parseResponse<AuthResponse>(response);
  if (result.success && result.data?.accessToken) {
    inMemoryAccessToken = result.data.accessToken;
  }
  return result;
}

/** 
 * Log out user.
 * Calls the backend logout endpoint which clears the refresh cookie.
 */
export async function logout(): Promise<ApiResult<{ success: boolean }>> {
  const response = await fetch(`${apiBaseUrl}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
  });

  return parseResponse<{ success: boolean }>(response);
}

/** Queue API methods */

export interface CreateQueueRequest {
  name: string;
  description?: string;
  tokenPrefix?: string;
  branchId?: string;
  serviceId?: string;
  capacity?: number;
  isPriorityEnabled?: boolean;
  defaultServiceTimeMinutes?: number;
  requiresDocumentVerification?: boolean;
}

export interface UpdateQueueRequest {
  name?: string;
  description?: string;
  status?: QueueStatus;
  branchId?: string;
  serviceId?: string;
  capacity?: number;
  isPriorityEnabled?: boolean;
  defaultServiceTimeMinutes?: number;
  requiresDocumentVerification?: boolean;
}

/** Queue API response DTOs */
export interface QueueResponse {
  id: string;
  organizationId: string;
  branchId?: string;
  serviceId?: string;
  name: string;
  description?: string;
  status: QueueStatus;
  tokenPrefix: string;
  currentTokenNumber: string;
  capacity?: number;
  isPriorityEnabled?: boolean;
  defaultServiceTimeMinutes?: number;
  requiresDocumentVerification?: boolean;
  waitingCount?: number;
  estimatedWaitMinutes?: number;
  createdAt: string;
  updatedAt: string;
}

/** QueueToken API response DTO */
export interface QueueTokenResponse {
  id: string;
  queueId: string;
  queueName?: string;
  organizationId?: string;
  branchId?: string;
  serviceId?: string;
  customerId?: string;
  tokenNumber: string;
  status: TokenStatus;
  position?: number;
  peopleAhead?: number;
  estimatedWaitMinutes?: number;
  calledAt?: string;
  completedAt?: string;
  heldAt?: string;
  skippedAt?: string;
  cancelledAt?: string;
  priority?: string;
  servedByCounterId?: string;
  servedByUserId?: string;
  createdAt: string;
  updatedAt: string;
  expectedTravelTimeMinutes?: number;
  expectedTurnTime?: string;
  recommendedDepartureTime?: string;
  requiresDocumentVerification?: boolean;
  documentVerificationStatus?: string;
  documentCount?: number;
}

/** Queue statistics */
export interface QueueStatistics {
  totalTokens: number;
  waitingTokens: number;
  calledTokens: number;
  completedTokens: number;
  holdTokens: number;
  skippedTokens: number;
}

/** ETA/position estimate */
export interface QueueEstimate {
  position: number;
  estimatedWaitMinutes: number;
}

/** Queue status enum */
export enum QueueStatus {
  ACTIVE = "ACTIVE",
  PAUSED = "PAUSED",
  CLOSED = "CLOSED",
}

/** Token status enum */
export enum TokenStatus {
  WAITING = "WAITING",
  CALLED = "CALLED",
  HOLD = "HOLD",
  COMPLETED = "COMPLETED",
  SKIPPED = "SKIPPED",
  CANCELLED = "CANCELLED",
}

/** Create a new queue (org admin / branch admin / super admin only) */
export async function createQueue(request: CreateQueueRequest): Promise<ApiResult<QueueResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/queues`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(request)),
  });

  return parseResponse<QueueResponse>(response);
}

/** Open a queue */
export async function openQueue(queueId: string): Promise<ApiResult<QueueResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}/open`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueResponse>(response);
}

/** Close a queue */
export async function closeQueue(queueId: string): Promise<ApiResult<QueueResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}/close`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueResponse>(response);
}

/** Delete / remove a queue */
export async function deleteQueue(queueId: string): Promise<ApiResult<void>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}`, {
    method: "DELETE",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<void>(response);
}

/** Update queue */
export async function updateQueue(
  queueId: string,
  request: UpdateQueueRequest
): Promise<ApiResult<QueueResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(request)),
  });

  return parseResponse<QueueResponse>(response);
}

/** Get all queues for the current tenant organization */
export async function getQueues(params?: {
  organizationId?: string;
  branchId?: string;
  serviceId?: string;
  status?: string;
}): Promise<ApiResult<QueueResponse[]>> {
  const qParams = new URLSearchParams();
  if (params?.organizationId) qParams.append("organizationId", params.organizationId);
  if (params?.branchId) qParams.append("branchId", params.branchId);
  if (params?.serviceId) qParams.append("serviceId", params.serviceId);
  if (params?.status) qParams.append("status", params.status);

  const url = qParams.toString() ? `${apiBaseUrl}/api/queues?${qParams.toString()}` : `${apiBaseUrl}/api/queues`;
  const response = await fetch(url, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueResponse[]>(response);
}

/** Get a specific queue by ID */
export async function getQueue(queueId: string): Promise<ApiResult<QueueResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueResponse>(response);
}

/** Generate a new token for an ACTIVE queue */
export async function generateToken(queueId: string, priority?: string): Promise<ApiResult<QueueTokenResponse>> {
  const url = priority ? `${apiBaseUrl}/api/queues/${queueId}/tokens?priority=${encodeURIComponent(priority)}` : `${apiBaseUrl}/api/queues/${queueId}/tokens`;
  const response = await fetch(url, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Get all tokens for a queue */
export async function getQueueTokens(queueId: string): Promise<ApiResult<QueueTokenResponse[]>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}/tokens`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse[]>(response);
}

/** Get waiting tokens count for a queue */
export async function getWaitingCount(queueId: string): Promise<ApiResult<number>> {
  const response = await fetch(`${apiBaseUrl}/api/queues/${queueId}/tokens/waiting`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<number>(response);
}

/** Call the next waiting token (staff/admin only) with optional forceCall override */
export async function callNext(
  queueId: string,
  counterId?: string,
  forceCall?: boolean
): Promise<ApiResult<QueueTokenResponse>> {
  const params = new URLSearchParams();
  if (counterId) params.append("counterId", counterId);
  if (forceCall) params.append("forceCall", "true");
  const url = `${apiBaseUrl}/api/queues/${queueId}/next${params.toString() ? `?${params.toString()}` : ""}`;
  const response = await fetch(url, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Hold a called token */
export async function holdToken(tokenId: string): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/hold`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Resume a held token */
export async function resumeToken(tokenId: string): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/resume`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Skip a token */
export async function skipToken(tokenId: string): Promise<ApiResult<void>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/skip`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  const result = await parseResponse<any>(response);
  return {
    success: result.success,
    data: null,
    error: result.error ?? { code: "skip-failed", message: "Skip operation failed" },
  };
}

/** Complete a called token */
export async function completeToken(tokenId: string): Promise<ApiResult<void>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/complete`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  const result = await parseResponse<any>(response);
  return {
    success: result.success,
    data: null,
    error: result.error ?? { code: "complete-failed", message: "Complete operation failed" },
  };
}

/** Customer leave queue */
export async function leaveToken(tokenId: string): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/leave`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Cancel a token (admin or customer) */
export async function cancelToken(tokenId: string): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/cancel`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Update expected travel time */
export async function updateTravelTime(tokenId: string, travelTimeMinutes: number): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/travel-time`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify({ travelTimeMinutes })),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Get token by ID */
export async function getTokenById(tokenId: string): Promise<ApiResult<QueueTokenResponse>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueTokenResponse>(response);
}

/** Get token estimate (position + ETA) */
export async function getTokenEstimate(queueId: string, tokenId: string): Promise<ApiResult<QueueEstimate>> {
  const response = await fetch(`${apiBaseUrl}/api/tokens/${tokenId}/estimate`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });

  return parseResponse<QueueEstimate>(response);
}

// -------------------------------------------------------------
// PHASE 3 API METHODS: DISCOVERY, CUSTOMER PORTAL, PROFILE, HISTORY
// -------------------------------------------------------------

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
  status: string;
}

export interface BranchDto {
  id: string;
  organizationId: string;
  name: string;
  code?: string;
  address?: string;
  status: string;
}

export interface ServiceDto {
  id: string;
  organizationId: string;
  branchId?: string;
  name: string;
  code?: string;
  description?: string;
  status: string;
  requiresDocumentVerification?: boolean;
  requiredDocumentTypes?: string;
}

export interface CustomerTokenDto {
  id: string;
  queueId: string;
  tokenNumber: string;
  status: TokenStatus;
  queueName?: string;
  organizationName?: string;
  branchName?: string;
  serviceName?: string;
  currentServingToken?: string;
  position: number;
  peopleAhead: number;
  estimatedWaitMinutes: number;
  calledAt?: string;
  completedAt?: string;
  heldAt?: string;
  skippedAt?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerHistoryResponseDto {
  items: CustomerTokenDto[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/** Get list of active organizations */
export async function getOrganizations(): Promise<ApiResult<OrganizationDto[]>> {
  const response = await fetch(`${apiBaseUrl}/api/organizations`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<OrganizationDto[]>(response);
}

/** Get branches for organization */
export async function getBranchesByOrg(orgId: string): Promise<ApiResult<BranchDto[]>> {
  const response = await fetch(`${apiBaseUrl}/api/organizations/${orgId}/branches`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<BranchDto[]>(response);
}

/** Get services for branch */
export async function getServicesByBranch(branchId: string): Promise<ApiResult<ServiceDto[]>> {
  const response = await fetch(`${apiBaseUrl}/api/branches/${branchId}/services`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<ServiceDto[]>(response);
}

/** Get queues for branch */
export async function getQueuesByBranch(branchId: string): Promise<ApiResult<QueueResponse[]>> {
  const response = await fetch(`${apiBaseUrl}/api/branches/${branchId}/queues`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QueueResponse[]>(response);
}

/** Get queues for service */
export async function getQueuesByService(serviceId: string): Promise<ApiResult<QueueResponse[]>> {
  const response = await fetch(`${apiBaseUrl}/api/services/${serviceId}/queues`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QueueResponse[]>(response);
}

/** Get active tokens for authenticated customer */
export async function getCustomerActiveTokens(): Promise<ApiResult<CustomerTokenDto[]>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/tokens/active`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<CustomerTokenDto[]>(response);
}

/** Get paginated token history for customer */
export async function getCustomerHistory(
  statusFilter?: string,
  page: number = 0,
  size: number = 10
): Promise<ApiResult<CustomerHistoryResponseDto>> {
  const params = new URLSearchParams();
  if (statusFilter && statusFilter !== "ALL") {
    params.append("status", statusFilter);
  }
  params.append("page", String(page));
  params.append("size", String(size));

  const response = await fetch(`${apiBaseUrl}/api/customers/me/tokens/history?${params.toString()}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<CustomerHistoryResponseDto>(response);
}

/** Get customer profile */
export async function getCustomerProfile(): Promise<ApiResult<UserDto>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/profile`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<UserDto>(response);
}

/** Update customer profile */
export async function updateCustomerProfile(data: {
  fullName?: string;
  phone?: string;
  preferredLanguage?: string;
}): Promise<ApiResult<UserDto>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/profile`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<UserDto>(response);
}

/** Change customer password */
export async function changeCustomerPassword(data: {
  currentPassword: string;
  newPassword: string;
}): Promise<ApiResult<void>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/change-password`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<void>(response);
}

// -------------------------------------------------------------
// PHASE 4 API METHODS: BRANCH, SERVICE, COUNTER, STAFF & ADMIN
// -------------------------------------------------------------

export interface CounterDto {
  id: string;
  organizationId: string;
  branchId: string;
  branchName?: string;
  name: string;
  code?: string;
  description?: string;
  status: string;
  assignedStaffUserId?: string;
  assignedStaffName?: string;
  assignedQueueIds?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface StaffUserDto {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  preferredLanguage?: string;
  status: string;
  organizationId: string;
  branchId?: string;
  branchName?: string;
  activeCounterId?: string;
  activeCounterName?: string;
  assignedQueueIds?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminOverviewDto {
  totalBranches: number;
  totalServices: number;
  totalCounters: number;
  totalQueues: number;
  totalActiveTokens: number;
  totalStaff: number;
}

/** Branch Admin APIs */
export async function createBranch(data: {
  name: string;
  code: string;
  timezone?: string;
  address?: string;
}): Promise<ApiResult<BranchDto>> {
  const response = await fetch(`${apiBaseUrl}/api/branches`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<BranchDto>(response);
}

export async function updateBranch(
  branchId: string,
  data: { name?: string; address?: string; timezone?: string; status?: string }
): Promise<ApiResult<BranchDto>> {
  const response = await fetch(`${apiBaseUrl}/api/branches/${branchId}`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<BranchDto>(response);
}

/** Service Admin APIs */
export async function createService(data: {
  name: string;
  code?: string;
  description?: string;
  branchId?: string;
  requiresDocumentVerification?: boolean;
  requiredDocumentTypes?: string;
}): Promise<ApiResult<ServiceDto>> {
  const response = await fetch(`${apiBaseUrl}/api/services`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<ServiceDto>(response);
}

export async function updateService(
  serviceId: string,
  data: {
    name?: string;
    description?: string;
    branchId?: string;
    status?: string;
    requiresDocumentVerification?: boolean;
    requiredDocumentTypes?: string;
  }
): Promise<ApiResult<ServiceDto>> {
  const response = await fetch(`${apiBaseUrl}/api/services/${serviceId}`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<ServiceDto>(response);
}

/** Counter APIs */
export async function getCounters(params?: {
  organizationId?: string;
  branchId?: string;
}): Promise<ApiResult<CounterDto[]>> {
  const qParams = new URLSearchParams();
  if (params?.organizationId) qParams.append("organizationId", params.organizationId);
  if (params?.branchId) qParams.append("branchId", params.branchId);

  const url = qParams.toString() ? `${apiBaseUrl}/api/counters?${qParams.toString()}` : `${apiBaseUrl}/api/counters`;
  const response = await fetch(url, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<CounterDto[]>(response);
}

export async function getCounterById(counterId: string): Promise<ApiResult<CounterDto>> {
  const response = await fetch(`${apiBaseUrl}/api/counters/${counterId}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<CounterDto>(response);
}

export async function createCounter(data: {
  branchId: string;
  name: string;
  code?: string;
  description?: string;
}): Promise<ApiResult<CounterDto>> {
  const response = await fetch(`${apiBaseUrl}/api/counters`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<CounterDto>(response);
}

export async function updateCounter(
  counterId: string,
  data: { name?: string; code?: string; description?: string; status?: string }
): Promise<ApiResult<CounterDto>> {
  const response = await fetch(`${apiBaseUrl}/api/counters/${counterId}`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<CounterDto>(response);
}

export async function assignStaffToCounter(
  counterId: string,
  userId: string
): Promise<ApiResult<CounterDto>> {
  const response = await fetch(`${apiBaseUrl}/api/counters/${counterId}/assign-staff`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify({ userId })),
  });
  return parseResponse<CounterDto>(response);
}

export async function assignQueuesToCounter(
  counterId: string,
  queueIds: string[]
): Promise<ApiResult<CounterDto>> {
  const response = await fetch(`${apiBaseUrl}/api/counters/${counterId}/assign-queues`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify({ queueIds })),
  });
  return parseResponse<CounterDto>(response);
}

export async function callNextByCounter(counterId: string, forceCall?: boolean): Promise<ApiResult<QueueTokenResponse>> {
  const url = `${apiBaseUrl}/api/counters/${counterId}/next${forceCall ? "?forceCall=true" : ""}`;
  const response = await fetch(url, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QueueTokenResponse>(response);
}

/** Staff Management APIs */
export async function getStaffList(branchId?: string): Promise<ApiResult<StaffUserDto[]>> {
  const url = branchId ? `${apiBaseUrl}/api/staff?branchId=${encodeURIComponent(branchId)}` : `${apiBaseUrl}/api/staff`;
  const response = await fetch(url, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<StaffUserDto[]>(response);
}

export async function createStaff(data: {
  email: string;
  fullName: string;
  phone?: string;
  password: string;
  branchId?: string;
  preferredLanguage?: string;
}): Promise<ApiResult<StaffUserDto>> {
  const response = await fetch(`${apiBaseUrl}/api/staff`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<StaffUserDto>(response);
}

export async function updateStaff(
  staffId: string,
  data: { fullName?: string; phone?: string; preferredLanguage?: string; branchId?: string; status?: string }
): Promise<ApiResult<StaffUserDto>> {
  const response = await fetch(`${apiBaseUrl}/api/staff/${staffId}`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<StaffUserDto>(response);
}

export async function deactivateStaff(staffId: string): Promise<ApiResult<void>> {
  const response = await fetch(`${apiBaseUrl}/api/staff/${staffId}`, {
    method: "DELETE",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<void>(response);
}

export async function getStaffAssignments(): Promise<ApiResult<StaffUserDto>> {
  const response = await fetch(`${apiBaseUrl}/api/staff/me/assignments`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<StaffUserDto>(response);
}

/** Admin Overview API */
export async function getAdminOverview(): Promise<ApiResult<AdminOverviewDto>> {
  const response = await fetch(`${apiBaseUrl}/api/admin/overview`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<AdminOverviewDto>(response);
}

// ==========================================
// Phase 5: Notification & QR APIs
// ==========================================

import {
  NotificationItem,
  UserNotificationPreference,
  QrCodeInfo,
  OperationalOverview,
  QueueAnalytics,
  ServiceAnalytics,
  CounterAnalytics,
  StaffAnalytics,
  PeakHour,
  WaitTimeDistribution,
  AiEtaPrediction,
  AiForecast,
  AiAnomaly,
  AiRecommendation,
} from "../types/queue";

export async function getMyNotifications(page = 0, size = 20): Promise<ApiResult<NotificationItem[]>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notifications?page=${page}&size=${size}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<NotificationItem[]>(response);
}

export async function getUnreadNotificationCount(): Promise<ApiResult<{ unreadCount: number }>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notifications/unread-count`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<{ unreadCount: number }>(response);
}

export async function markNotificationAsRead(id: string): Promise<ApiResult<NotificationItem>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notifications/${id}/read`, {
    method: "PATCH",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<NotificationItem>(response);
}

export async function markAllNotificationsAsRead(): Promise<ApiResult<{ markedReadCount: number }>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notifications/read-all`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<{ markedReadCount: number }>(response);
}

export async function getNotificationPreferences(): Promise<ApiResult<UserNotificationPreference>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notification-preferences`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<UserNotificationPreference>(response);
}

export async function updateNotificationPreferences(
  data: Partial<Omit<UserNotificationPreference, "id" | "userId">>
): Promise<ApiResult<UserNotificationPreference>> {
  const response = await fetch(`${apiBaseUrl}/api/customers/me/notification-preferences`, {
    method: "PUT",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<UserNotificationPreference>(response);
}

export async function getAdminNotifications(page = 0, size = 50): Promise<ApiResult<NotificationItem[]>> {
  const response = await fetch(`${apiBaseUrl}/api/admin/notifications?page=${page}&size=${size}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<NotificationItem[]>(response);
}

export async function getQueueQr(queueId: string, size = 300, baseUrl?: string): Promise<ApiResult<QrCodeInfo>> {
  const origin = baseUrl || (typeof window !== "undefined" ? window.location.origin : "");
  const query = new URLSearchParams({ size: String(size) });
  if (origin) query.append("baseUrl", origin);
  const response = await fetch(`${apiBaseUrl}/api/qr/queue/${queueId}?${query.toString()}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QrCodeInfo>(response);
}

export async function getBranchQr(branchId: string, size = 300, baseUrl?: string): Promise<ApiResult<QrCodeInfo>> {
  const origin = baseUrl || (typeof window !== "undefined" ? window.location.origin : "");
  const query = new URLSearchParams({ size: String(size) });
  if (origin) query.append("baseUrl", origin);
  const response = await fetch(`${apiBaseUrl}/api/qr/branch/${branchId}?${query.toString()}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QrCodeInfo>(response);
}

export async function getServiceQr(serviceId: string, size = 300, baseUrl?: string): Promise<ApiResult<QrCodeInfo>> {
  const origin = baseUrl || (typeof window !== "undefined" ? window.location.origin : "");
  const query = new URLSearchParams({ size: String(size) });
  if (origin) query.append("baseUrl", origin);
  const response = await fetch(`${apiBaseUrl}/api/qr/service/${serviceId}?${query.toString()}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QrCodeInfo>(response);
}

export async function getLanIp(): Promise<string | null> {
  try {
    const response = await fetch(`${apiBaseUrl}/api/qr/lan-ip`);
    const res = await parseResponse<{ ip: string }>(response);
    if (res.success && res.data?.ip && res.data.ip !== "127.0.0.1") {
      return res.data.ip;
    }
  } catch (err) {
    console.warn("Failed to fetch LAN IP from backend", err);
  }
  return null;
}

// ==========================================
// Phase 6: Analytics, Reports & AI APIs
// ==========================================

export async function getAnalyticsOverview(params?: {
  from?: string;
  to?: string;
  branchId?: string;
  serviceId?: string;
  queueId?: string;
}): Promise<ApiResult<OperationalOverview>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/overview${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<OperationalOverview>(response);
}

export async function getQueuesAnalytics(params?: {
  from?: string;
  to?: string;
  branchId?: string;
  serviceId?: string;
}): Promise<ApiResult<QueueAnalytics[]>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/queues${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<QueueAnalytics[]>(response);
}

export async function getServicesAnalytics(params?: {
  from?: string;
  to?: string;
  branchId?: string;
}): Promise<ApiResult<ServiceAnalytics[]>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/services${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<ServiceAnalytics[]>(response);
}

export async function getCountersAnalytics(params?: {
  from?: string;
  to?: string;
  branchId?: string;
}): Promise<ApiResult<CounterAnalytics[]>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/counters${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<CounterAnalytics[]>(response);
}

export async function getStaffAnalytics(params?: {
  from?: string;
  to?: string;
  branchId?: string;
}): Promise<ApiResult<StaffAnalytics[]>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/staff${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<StaffAnalytics[]>(response);
}

export async function getPeakHoursAnalytics(params?: {
  from?: string;
  to?: string;
  branchId?: string;
  queueId?: string;
}): Promise<ApiResult<PeakHour[]>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/peak-hours${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<PeakHour[]>(response);
}

export async function getWaitTimeDistribution(params?: {
  from?: string;
  to?: string;
  branchId?: string;
  queueId?: string;
}): Promise<ApiResult<WaitTimeDistribution>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/analytics/wait-times${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<WaitTimeDistribution>(response);
}

export async function downloadReportCsv(
  type: "daily" | "queues" | "services" | "summary",
  params?: Record<string, string>
): Promise<Blob | null> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null && v !== "") as [string, string][]
  ).toString();
  const url = `${apiBaseUrl}/api/reports/${type}/csv${query ? `?${query}` : ""}`;
  const response = await fetch(url, {
    method: "GET",
    headers: {
      ...(inMemoryAccessToken ? { Authorization: `Bearer ${inMemoryAccessToken}` } : {}),
    },
    credentials: "include",
  });
  if (!response.ok) return null;
  return await response.blob();
}

export async function getSummaryReport(params?: {
  from?: string;
  to?: string;
  branchId?: string;
  serviceId?: string;
  queueId?: string;
}): Promise<ApiResult<OperationalOverview>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/reports/summary${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<OperationalOverview>(response);
}

export async function predictEta(tokenId: string): Promise<ApiResult<AiEtaPrediction>> {
  const response = await fetch(`${apiBaseUrl}/api/ai/eta/${tokenId}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<AiEtaPrediction>(response);
}

export async function getDemandForecast(params?: {
  queueId?: string;
  serviceId?: string;
  horizonHours?: number;
}): Promise<ApiResult<AiForecast>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/ai/forecast${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<AiForecast>(response);
}

export async function getAnomalies(params?: {
  branchId?: string;
  queueId?: string;
}): Promise<ApiResult<AiAnomaly>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/ai/anomalies${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<AiAnomaly>(response);
}

export async function getRecommendations(params?: {
  branchId?: string;
  queueId?: string;
}): Promise<ApiResult<AiRecommendation>> {
  const query = new URLSearchParams(
    Object.entries(params || {}).filter(([_, v]) => v != null) as [string, string][]
  ).toString();
  const response = await fetch(`${apiBaseUrl}/api/ai/recommendations${query ? `?${query}` : ""}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<AiRecommendation>(response);
}

/** Document Verification APIs */
export interface UploadDocumentDto {
  documentType: string;
  fileName: string;
  fileContentType?: string;
  fileData: string;
}

export interface ReviewDocumentDto {
  status: string;
  notes?: string;
}

export async function uploadTokenDocument(
  tokenId: string,
  data: UploadDocumentDto
): Promise<ApiResult<TokenDocument>> {
  const response = await fetch(`${apiBaseUrl}/api/documents/tokens/${tokenId}/upload`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(data)),
  });
  return parseResponse<TokenDocument>(response);
}

export async function getTokenDocuments(
  tokenId: string
): Promise<ApiResult<TokenDocument[]>> {
  const response = await fetch(`${apiBaseUrl}/api/documents/tokens/${tokenId}`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<TokenDocument[]>(response);
}

export async function reviewTokenDocument(
  documentId: string,
  review: ReviewDocumentDto
): Promise<ApiResult<TokenDocument>> {
  const response = await fetch(`${apiBaseUrl}/api/documents/${documentId}/review`, {
    method: "POST",
    ...buildOptions(inMemoryAccessToken, JSON.stringify(review)),
  });
  return parseResponse<TokenDocument>(response);
}

export async function getTokenDocumentStatus(
  tokenId: string
): Promise<ApiResult<TokenDocumentStatus>> {
  const response = await fetch(`${apiBaseUrl}/api/documents/tokens/${tokenId}/status`, {
    method: "GET",
    ...buildOptions(inMemoryAccessToken),
  });
  return parseResponse<TokenDocumentStatus>(response);
}