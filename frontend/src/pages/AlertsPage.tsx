// frontend/src/pages/AlertsPage.tsx

import { useEffect, useState, useCallback } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { fetchAlerts } from '@/lib/api';
import { AlertsFilter } from '@/components/alerts/AlertsFilter';
import { AlertsTable } from '@/components/alerts/AlertsTable';
import type { AlertDocument, PageInfo, AlertSearchParams } from '@/lib/types';

const DEFAULT_FILTERS: AlertSearchParams = { page: 0, size: 20, sort: 'detectedAt', direction: 'desc' };

export function AlertsPage() {
  const [filters, setFilters] = useState<AlertSearchParams>(DEFAULT_FILTERS);
  const [alerts, setAlerts] = useState<AlertDocument[]>([]);
  const [page, setPage] = useState<PageInfo>({ number: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async (params: AlertSearchParams) => {
    setLoading(true);
    try {
      const res = await fetchAlerts(params);
      setAlerts(res.results);
      setPage(res.page);
    } catch (_err) {
      toast.error('Failed to load alerts');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(filters); }, []);

  const handleSearch = () => {
    const params = { ...filters, page: 0 };
    setFilters(params);
    loadData(params);
  };

  const handleReset = () => {
    setFilters(DEFAULT_FILTERS);
    loadData(DEFAULT_FILTERS);
  };

  const handlePageChange = (newPage: number) => {
    const params = { ...filters, page: newPage };
    setFilters(params);
    loadData(params);
  };

  const handleSort = (field: string) => {
    const direction = filters.sort === field && filters.direction === 'desc' ? 'asc' : 'desc';
    const params = { ...filters, sort: field, direction, page: 0 };
    setFilters(params);
    loadData(params);
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex justify-between items-start">
        <AlertsFilter filters={filters} onChange={setFilters} onSearch={handleSearch} onReset={handleReset} />
        <Button variant="outline" size="sm" onClick={() => loadData(filters)}>Refresh</Button>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : (
        <AlertsTable
          alerts={alerts}
          page={page}
          onPageChange={handlePageChange}
          onSort={handleSort}
          sortField={filters.sort ?? 'detectedAt'}
          sortDir={filters.direction ?? 'desc'}
        />
      )}
    </div>
  );
}
