// frontend/src/lib/api.ts

import type {
  SearchResponse,
  EventDocument,
  AlertDocument,
  EventStatsResponse,
  AlertStatsResponse,
  EventSearchParams,
  AlertSearchParams,
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

export function fetchEvents(params: EventSearchParams = {}) {
  return fetchJson<SearchResponse<EventDocument>>('/search/events', params);
}

export function fetchAlerts(params: AlertSearchParams = {}) {
  return fetchJson<SearchResponse<AlertDocument>>('/search/alerts', params);
}

export function fetchEventStats() {
  return fetchJson<EventStatsResponse>('/search/events/stats');
}

export function fetchAlertStats() {
  return fetchJson<AlertStatsResponse>('/search/alerts/stats');
}
