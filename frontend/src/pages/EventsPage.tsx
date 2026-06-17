// frontend/src/pages/EventsPage.tsx

import { useEffect, useState, useCallback } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { fetchEvents } from '@/lib/api';
import { EventsFilter } from '@/components/events/EventsFilter';
import { EventsTable } from '@/components/events/EventsTable';
import type { EventDocument, PageInfo, EventSearchParams } from '@/lib/types';

const DEFAULT_FILTERS: EventSearchParams = { page: 0, size: 20, sort: 'eventTime', direction: 'desc' };

export function EventsPage() {
  const [filters, setFilters] = useState<EventSearchParams>(DEFAULT_FILTERS);
  const [events, setEvents] = useState<EventDocument[]>([]);
  const [page, setPage] = useState<PageInfo>({ number: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async (params: EventSearchParams) => {
    setLoading(true);
    try {
      const res = await fetchEvents(params);
      setEvents(res.results);
      setPage(res.page);
    } catch (_err) {
      toast.error('Failed to load events');
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
        <EventsFilter filters={filters} onChange={setFilters} onSearch={handleSearch} onReset={handleReset} />
        <Button variant="outline" size="sm" onClick={() => loadData(filters)}>Refresh</Button>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : (
        <EventsTable
          events={events}
          page={page}
          onPageChange={handlePageChange}
          onSort={handleSort}
          sortField={filters.sort ?? 'eventTime'}
          sortDir={filters.direction ?? 'desc'}
        />
      )}
    </div>
  );
}
