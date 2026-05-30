export type SubscriptionTier = "FREE" | "PRO" | "ENTERPRISE";

export interface SubscriptionInfo {
  tier: SubscriptionTier;
  tierDisplayName: string;
  monthlyPriceInr: number;
  startedAt: string;
  expiresAt: string | null;
}

export interface UsageInfo {
  budgets: number;
  budgetLimit: number;
  goals: number;
  goalLimit: number;
  receiptsThisMonth: number;
  receiptUploadLimit: number;
}

export interface Account {
  id: string;
  email: string;
  fullName: string;
  role: string;
  createdAt: string;
  subscription: SubscriptionInfo;
  usage: UsageInfo;
}

export interface AuditLogEntry {
  id: string;
  action: string;
  resourceType: string | null;
  resourceId:   string | null;
  ipAddress:    string | null;
  userAgent:    string | null;
  metadata:     string | null;
  createdAt:    string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
