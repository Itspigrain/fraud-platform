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

export interface RuleCondition {
  field: string;
  operator: ConditionOperator;
  value: string;
}

export type ConditionOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GREATER_THAN'
  | 'LESS_THAN'
  | 'GREATER_THAN_OR_EQUAL'
  | 'LESS_THAN_OR_EQUAL'
  | 'CONTAINS'
  | 'IN';

export type RuleStatus = 'ACTIVE' | 'INACTIVE';
export type RuleType = 'CONDITION' | 'VELOCITY';

export interface RuleResponse {
  id: number;
  tenantId: string;
  name: string;
  description: string | null;
  ruleType: RuleType;
  status: RuleStatus;
  conditions: RuleCondition[];
  groupByField: string | null;
  timeWindowMinutes: number | null;
  threshold: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RuleRequest {
  name: string;
  description?: string;
  ruleType?: RuleType;
  status?: RuleStatus;
  conditions?: RuleCondition[];
  groupByField?: string;
  timeWindowMinutes?: number;
  threshold?: number;
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
