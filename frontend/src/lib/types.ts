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
  eventTime: string;
  attributes: Record<string, unknown>;
}

export interface AlertDocument {
  alertId: string;
  eventId: string;
  ruleId: string;
  severity: string;
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
  q?: string;
  eventType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
}

export type SchemaFieldType =
  | 'KEYWORD' | 'TEXT' | 'INTEGER' | 'LONG' | 'DOUBLE'
  | 'BOOLEAN' | 'DATE' | 'IP' | 'GEO_POINT';

export interface SchemaFieldDefinition {
  name: string;
  type: SchemaFieldType;
  required: boolean;
  description: string | null;
}

export interface SchemaResponse {
  id: number;
  tenantId: string;
  eventType: string;
  displayName: string | null;
  description: string | null;
  fields: SchemaFieldDefinition[];
  createdAt: string;
  updatedAt: string;
}

export interface SchemaRequest {
  eventType: string;
  displayName?: string;
  description?: string;
  fields: SchemaFieldDefinition[];
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
export type RuleType = 'CONDITION' | 'VELOCITY' | 'LLM_EVALUATOR';

export interface RuleResponse {
  id: number;
  tenantId: string;
  eventType: string;
  name: string;
  description: string | null;
  ruleType: RuleType;
  status: RuleStatus;
  conditions: RuleCondition[];
  groupByField: string | null;
  timeWindowMinutes: number | null;
  threshold: number | null;
  promptTemplate: string | null;
  evaluationIntervalMinutes: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RuleRequest {
  eventType: string;
  name: string;
  description?: string;
  ruleType?: RuleType;
  status?: RuleStatus;
  conditions?: RuleCondition[];
  groupByField?: string;
  timeWindowMinutes?: number;
  threshold?: number;
  promptTemplate?: string;
  evaluationIntervalMinutes?: number;
}

export interface AlertSearchParams {
  q?: string;
  ruleId?: string;
  severity?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
}
