// frontend/src/lib/types.ts

export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SearchResponse<T> {
  results: T[];
  page: PageInfo;
}

export interface EventDocument {
  id: string;
  tenantId: string;
  eventType: string;
  customerId: string;
  sourceIp: string;
  deviceId: string;
  email: string;
  phoneNumber: string;
  eventTime: string;
  attributes: Record<string, unknown>;
  riskScore: number;
}

export interface AlertDocument {
  alertId: string;
  eventId: string;
  customerId: string;
  ruleId: string;
  severity: string;
  riskScore: number;
  reason: string;
  detectedAt: string;
}

export interface AggBucket {
  key: string | number;
  count: number;
}

export interface EventStatsResponse {
  aggregations: {
    eventsOverTime: AggBucket[];
    eventCountByType: AggBucket[];
    riskScoreDistribution: AggBucket[];
  };
}

export interface AlertStatsResponse {
  aggregations: {
    countByRule: AggBucket[];
    countBySeverity: AggBucket[];
    alertsOverTime: AggBucket[];
  };
}

export interface EventSearchParams {
  customerId?: string;
  eventType?: string;
  sourceIp?: string;
  riskScoreMin?: number;
  riskScoreMax?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
}

export interface AlertSearchParams {
  customerId?: string;
  ruleId?: string;
  severity?: string;
  riskScoreMin?: number;
  riskScoreMax?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
}
