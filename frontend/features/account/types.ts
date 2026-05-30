export interface ReceiptQuotaInfo {
  used: number;
  limit: number;
  remaining: number | null;   // null when unlimited
  unlimited: boolean;
  canProcess: boolean;
}

export interface Account {
  id: string;
  email: string;
  fullName: string;
  role: string;
  createdAt: string;
  receiptQuota: ReceiptQuotaInfo;
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
