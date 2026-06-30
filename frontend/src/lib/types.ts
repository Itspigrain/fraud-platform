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
  verdict: string;
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
export type DependencyCondition = 'ALL' | 'ANY';

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
  verdict: string | null;
  severity: string | null;
  dependsOn: number[] | null;
  dependencyCondition: DependencyCondition | null;
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
  verdict?: string;
  severity?: string;
  dependsOn?: number[];
  dependencyCondition?: DependencyCondition;
}

export interface ValidationError {
  field: string;
  message: string;
}

export interface AlertSearchParams {
  q?: string;
  ruleId?: string;
  severity?: string;
  verdict?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
  direction?: string;
}

// --- Connectors ---

export type ConnectorType = 'WEBHOOK';
export type ConnectorStatus = 'ACTIVE' | 'INACTIVE';

export interface WebhookConfig {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  timeoutMs?: number;
}

export interface ConnectorResponse {
  id: number;
  tenantId: string;
  name: string;
  description: string | null;
  type: ConnectorType;
  status: ConnectorStatus;
  config: WebhookConfig;
  ruleIds: number[];
  retryAttempts: number;
  retryDelayMs: number;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectorRequest {
  name: string;
  description?: string;
  type?: ConnectorType;
  status?: ConnectorStatus;
  config: WebhookConfig;
  ruleIds: number[];
  retryAttempts?: number;
  retryDelayMs?: number;
}

export interface ConnectorTestResult {
  success: boolean;
  statusCode?: number;
  error?: string;
  responseTimeMs: number;
}
