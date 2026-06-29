import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const STORAGE_KEY = 'fraud-ops-tenant';

interface TenantContextValue {
  tenantId: string;
  setTenantId: (id: string) => void;
  tenants: string[];
  loading: boolean;
}

const TenantContext = createContext<TenantContextValue | null>(null);

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenants, setTenants] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [tenantId, setTenantIdState] = useState<string>(
    () => localStorage.getItem(STORAGE_KEY) || import.meta.env.VITE_TENANT_ID || 'default'
  );

  const setTenantId = useCallback((id: string) => {
    localStorage.setItem(STORAGE_KEY, id);
    setTenantIdState(id);
  }, []);

  useEffect(() => {
    fetch(`${API_BASE}/api/tenants`)
      .then((res) => res.json())
      .then((data: string[]) => {
        setTenants(data);
        if (data.length > 0 && !data.includes(tenantId)) {
          setTenantId(data[0]);
        }
      })
      .catch(() => setTenants([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <TenantContext.Provider value={{ tenantId, setTenantId, tenants, loading }}>
      {children}
    </TenantContext.Provider>
  );
}

export function useTenant() {
  const ctx = useContext(TenantContext);
  if (!ctx) throw new Error('useTenant must be used within TenantProvider');
  return ctx;
}
