import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { EventsTable } from '@/components/events/EventsTable';
import { fetchRule, fetchRuleResults } from '@/lib/api';
import type { EventDocument, PageInfo, RuleResponse, EventSearchParams } from '@/lib/types';

const DEFAULT_PARAMS: EventSearchParams = { page: 0, size: 20, sort: 'eventTime', direction: 'desc' };

export function RuleResultsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const ruleId = Number(id);

  const [rule, setRule] = useState<RuleResponse | null>(null);
  const [events, setEvents] = useState<EventDocument[]>([]);
  const [page, setPage] = useState<PageInfo>({ number: 0, size: 20, totalElements: 0, totalPages: 0 });
  const [params, setParams] = useState<EventSearchParams>(DEFAULT_PARAMS);
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async (searchParams: EventSearchParams) => {
    setLoading(true);
    try {
      const [ruleData, resultsData] = await Promise.all([
        fetchRule(ruleId),
        fetchRuleResults(ruleId, searchParams),
      ]);
      setRule(ruleData);
      setEvents(resultsData.results);
      setPage(resultsData.page);
    } catch {
      toast.error('Failed to load rule results');
    } finally {
      setLoading(false);
    }
  }, [ruleId]);

  useEffect(() => { loadData(params); }, [loadData]);

  const handlePageChange = (newPage: number) => {
    const updated = { ...params, page: newPage };
    setParams(updated);
    loadData(updated);
  };

  const handleSort = (field: string) => {
    const direction = params.sort === field && params.direction === 'desc' ? 'asc' : 'desc';
    const updated = { ...params, sort: field, direction, page: 0 };
    setParams(updated);
    loadData(updated);
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <Button variant="outline" size="sm" onClick={() => navigate('/rules')}>
            &larr; Back to Rules
          </Button>
          {rule && (
            <div className="mt-2">
              <h2 className="text-lg font-semibold">{rule.name}</h2>
              {rule.description && <p className="text-sm text-slate-500">{rule.description}</p>}
              <p className="text-xs text-slate-400 mt-1">
                {rule.conditions.length} condition{rule.conditions.length !== 1 ? 's' : ''} — {rule.status}
              </p>
            </div>
          )}
        </div>
        <Button variant="outline" size="sm" onClick={() => loadData(params)}>Refresh</Button>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : (
        <EventsTable
          events={events}
          page={page}
          onPageChange={handlePageChange}
          onSort={handleSort}
          sortField={params.sort ?? 'eventTime'}
          sortDir={params.direction ?? 'desc'}
        />
      )}
    </div>
  );
}
