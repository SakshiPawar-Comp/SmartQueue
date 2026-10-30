import { UserDto } from "./auth";

export enum QueueStatus {
  ACTIVE = "ACTIVE",
  PAUSED = "PAUSED",
  CLOSED = "CLOSED",
}

export enum TokenStatus {
  WAITING = "WAITING",
  CALLED = "CALLED",
  SERVING = "SERVING",
  HOLD = "HOLD",
  COMPLETED = "COMPLETED",
  SKIPPED = "SKIPPED",
  CANCELLED = "CANCELLED",
  EXPIRED = "EXPIRED",
}

export enum TokenPriority {
  VIP = "VIP",
  PRIORITY = "PRIORITY",
  NORMAL = "NORMAL",
}

export interface Organization {
  id: UUID;
  name: string;
  slug: string;
  status: string;
}

export interface Branch {
  id: UUID;
  organizationId: UUID;
  name: string;
  code?: string;
  timezone?: string;
  address?: string;
  status: string;
}

export interface Service {
  id: UUID;
  organizationId: UUID;
  branchId?: UUID;
  name: string;
  code?: string;
  description?: string;
  status: string;
  requiresDocumentVerification?: boolean;
  requiredDocumentTypes?: string;
}

export interface Counter {
  id: UUID;
  organizationId: UUID;
  branchId: UUID;
  branchName?: string;
  name: string;
  code?: string;
  description?: string;
  status: string;
  assignedStaffUserId?: UUID;
  assignedStaffName?: string;
  assignedQueueIds?: UUID[];
  createdAt: string;
  updatedAt: string;
}

export interface StaffUser {
  id: UUID;
  email: string;
  fullName: string;
  phone?: string;
  preferredLanguage?: string;
  status: string;
  organizationId: UUID;
  branchId?: UUID;
  branchName?: string;
  activeCounterId?: UUID;
  activeCounterName?: string;
  assignedQueueIds?: UUID[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminOverview {
  totalBranches: number;
  totalServices: number;
  totalCounters: number;
  totalQueues: number;
  totalActiveTokens: number;
  totalStaff: number;
}

export interface Queue {
  id: UUID;
  organizationId: UUID;
  branchId?: UUID;
  serviceId?: UUID;
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

export interface QueueToken {
  id: UUID;
  queueId: UUID;
  tokenNumber: string;
  status: TokenStatus;
  priority?: TokenPriority;
  servedByCounterId?: UUID;
  servedByUserId?: UUID;
  calledAt?: string;
  completedAt?: string;
  heldAt?: string;
  skippedAt?: string;
  cancelledAt?: string;
  requiresDocumentVerification?: boolean;
  documentVerificationStatus?: string;
  documentCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerTokenResponse {
  id: UUID;
  queueId: UUID;
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
  requiresDocumentVerification?: boolean;
  documentVerificationStatus?: string;
  documentCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface QueueEvent {
  queueId: UUID;
  organizationId: UUID;
  eventType: string;
  queueStatus: QueueStatus;
  currentTokenNumber: string;
  waitingCount: number;
  tokenId?: UUID;
  tokenNumber?: string;
  tokenStatus?: TokenStatus;
  timestamp: string;
}

export interface QueueStatistics {
  totalTokens: number;
  waitingTokens: number;
  calledTokens: number;
  completedTokens: number;
  holdTokens: number;
  skippedTokens: number;
}

export interface QueueStatusEstimate {
  position: number;
  estimatedWaitMinutes: number;
}

export type UUID = string;

// Phase 5: Notification & QR Types

export enum NotificationChannel {
  IN_APP = "IN_APP",
  EMAIL = "EMAIL",
  SMS = "SMS",
  PUSH = "PUSH",
}

export enum NotificationEventType {
  TOKEN_CREATED = "TOKEN_CREATED",
  TOKEN_CALLED = "TOKEN_CALLED",
  POSITION_NEAR = "POSITION_NEAR",
  TOKEN_HOLD = "TOKEN_HOLD",
  TOKEN_RESUMED = "TOKEN_RESUMED",
  TOKEN_SKIPPED = "TOKEN_SKIPPED",
  TOKEN_COMPLETED = "TOKEN_COMPLETED",
  TOKEN_CANCELLED = "TOKEN_CANCELLED",
  QUEUE_CLOSED = "QUEUE_CLOSED",
}

export interface NotificationItem {
  id: UUID;
  organizationId: UUID;
  userId?: UUID;
  tokenId?: UUID;
  tokenNumber?: string;
  queueId?: UUID;
  queueName?: string;
  channel: NotificationChannel;
  eventType: NotificationEventType;
  status: string;
  title: string;
  message: string;
  isRead: boolean;
  readAt?: string;
  createdAt: string;
}

export interface UserNotificationPreference {
  id: UUID;
  userId: UUID;
  inAppEnabled: boolean;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
  positionNearThreshold: number;
}

export interface QrCodeInfo {
  deepLinkUrl: string;
  qrDataUri: string;
  targetType: string;
  targetId: UUID;
  targetName: string;
}

// Phase 6: Analytics, Reports & AI Types

export interface OperationalOverview {
  totalTokens: number;
  completedTokens: number;
  cancelledTokens: number;
  skippedTokens: number;
  waitingTokens: number;
  servingTokens: number;
  avgWaitMinutes: number;
  medianWaitMinutes: number;
  maxWaitMinutes: number;
  avgServiceMinutes: number;
  medianServiceMinutes: number;
  maxServiceMinutes: number;
  throughputPerHour: number;
  completionRate: number;
  cancellationRate: number;
  skipRate: number;
}

export interface QueueAnalytics {
  queueId: UUID;
  queueName: string;
  tokenPrefix: string;
  branchName?: string;
  serviceName?: string;
  totalTokens: number;
  completedTokens: number;
  cancelledTokens: number;
  skippedTokens: number;
  currentWaiting: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
  peakHour: string;
  throughputPerHour: number;
}

export interface ServiceAnalytics {
  serviceId: UUID;
  serviceName: string;
  branchName?: string;
  totalTokens: number;
  completedTokens: number;
  cancelledTokens: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
}

export interface CounterAnalytics {
  counterId: UUID;
  counterName: string;
  branchName?: string;
  tokensServed: number;
  avgServiceMinutes: number;
  utilizationPercentage: number;
  activeHours: number;
  idleHours: number;
}

export interface StaffAnalytics {
  staffUserId: UUID;
  staffName: string;
  email: string;
  tokensServed: number;
  avgServiceMinutes: number;
  totalServingMinutes: number;
}

export interface PeakHour {
  hour: number;
  hourLabel: string;
  tokenCount: number;
  completedCount: number;
  avgWaitMinutes: number;
}

export interface WaitTimeDistribution {
  p50Minutes: number;
  p75Minutes: number;
  p90Minutes: number;
  p95Minutes: number;
  avgWaitMinutes: number;
  maxWaitMinutes: number;
  normalPriorityAvgMinutes: number;
  priorityAvgMinutes: number;
  vipPriorityAvgMinutes: number;
}

export interface AiEtaPrediction {
  position: number;
  estimatedWaitMinutes: number;
  predictionType: string;
  confidenceScore?: number;
  factors: Record<string, any>;
  modelVersion: string;
}

export interface ForecastPoint {
  hourOffset: number;
  predictedCount: number;
  lowerBound: number;
  upperBound: number;
}

export interface AiForecast {
  status: string;
  forecast: ForecastPoint[];
  message: string;
  modelVersion: string;
}

export interface AnomalyItem {
  metricName: string;
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  queueId?: UUID;
  queueName?: string;
  observedValue: number;
  baselineMean: number;
  deviationFactor: number;
  message: string;
  timestamp: string;
}

export interface AiAnomaly {
  anomalies: AnomalyItem[];
  modelVersion: string;
}

export interface RecommendationItem {
  id: string;
  category: string;
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  title: string;
  description: string;
  evidence: string;
  targetQueueId?: UUID;
}

export interface AiRecommendation {
  recommendations: RecommendationItem[];
  modelVersion: string;
}

// Phase 7: Document Verification Types
export enum DocumentVerificationStatus {
  PENDING = "PENDING",
  VERIFIED = "VERIFIED",
  REJECTED = "REJECTED",
  MANUALLY_APPROVED = "MANUALLY_APPROVED",
}

export interface TokenDocument {
  id: string;
  tokenId: string;
  tokenNumber?: string;
  documentType: string;
  fileName: string;
  fileContentType?: string;
  fileData?: string;
  verificationStatus: DocumentVerificationStatus;
  aiConfidence?: number;
  aiModelVersion?: string;
  extractedData?: string;
  failureReason?: string;
  reviewedByUserId?: string;
  reviewedByName?: string;
  reviewedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface TokenDocumentStatus {
  tokenId: string;
  tokenNumber: string;
  isEligibleForService: boolean;
  status: string; // VERIFIED, PENDING, REJECTED, MISSING, NOT_REQUIRED
  documentCount: number;
  requiresVerification: boolean;
}