// frontend/src/lib/api.ts

import type {
  SearchResponse,
  EventDocument,
  AlertDocument,
  EventStatsResponse,
  AlertStatsResponse,
  EventSearchParams,
  AlertSearchParams,
  RuleResponse,
  RuleRequest,
  SchemaResponse,
  SchemaRequest,
} from './types';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const TENANT_ID = import.meta.env.VITE_TENANT_ID || 'default';

function buildQuery(params: Record<string, unknown>): string {
  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value));
    }
  }
  return searchParams.toString();
}

async function fetchJson<T>(path: string, params: Record<string, unknown> = {}): Promise<T> {
  const query = buildQuery(params);
  const url = `${API_BASE}${path}${query ? `?${query}` : ''}`;
  const res = await fetch(url, {
    headers: {
      'X-Tenant-Id': TENANT_ID,
    },
  });
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

async function mutateJson<T>(path: string, method: string, body?: unknown): Promise<T> {
  const url = `${API_BASE}${path}`;
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': TENANT_ID,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// --- Schemas ---

export function fetchSchemas() {
  return fetchJson<SchemaResponse[]>('/schemas');
}

export function fetchSchema(eventType: string) {
  return fetchJson<SchemaResponse>(`/schemas/${eventType}`);
}

export function createSchema(request: SchemaRequest) {
  return mutateJson<SchemaResponse>('/schemas', 'POST', request);
}

export function updateSchema(eventType: string, request: SchemaRequest) {
  return mutateJson<SchemaResponse>(`/schemas/${eventType}`, 'PUT', request);
}

export function deleteSchema(eventType: string) {
  return mutateJson<void>(`/schemas/${eventType}`, 'DELETE');
}

// --- Events ---

export function fetchEvents(params: EventSearchParams = {}) {
  return fetchJson<SearchResponse<EventDocument>>('/search/events', params);
}

export function fetchEventStats() {
  return fetchJson<EventStatsResponse>('/search/events/stats');
}

// --- Alerts ---

export function fetchAlerts(params: AlertSearchParams = {}) {
  return fetchJson<SearchResponse<AlertDocument>>('/search/alerts', params);
}

export function fetchAlertStats() {
  return fetchJson<AlertStatsResponse>('/search/alerts/stats');
}

// --- Rules ---

export function fetchRules() {
  return fetchJson<RuleResponse[]>('/api/rules');
}

export function fetchRule(id: number) {
  return fetchJson<RuleResponse>(`/api/rules/${id}`);
}

export function createRule(request: RuleRequest) {
  return mutateJson<RuleResponse>('/api/rules', 'POST', request);
}

export function updateRule(id: number, request: RuleRequest) {
  return mutateJson<RuleResponse>(`/api/rules/${id}`, 'PUT', request);
}

export function deleteRule(id: number, deleteIndex = false) {
  return mutateJson<void>(`/api/rules/${id}?deleteIndex=${deleteIndex}`, 'DELETE');
}

export function fetchRuleResults(ruleId: number, params: EventSearchParams = {}) {
  return fetchJson<SearchResponse<EventDocument>>(`/api/rules/${ruleId}/results`, params);
}
