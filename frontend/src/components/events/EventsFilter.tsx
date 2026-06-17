// frontend/src/components/events/EventsFilter.tsx

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { EventSearchParams } from '@/lib/types';

interface EventsFilterProps {
  filters: EventSearchParams;
  onChange: (filters: EventSearchParams) => void;
  onSearch: () => void;
  onReset: () => void;
}

export function EventsFilter({ filters, onChange, onSearch, onReset }: EventsFilterProps) {
  const update = (field: keyof EventSearchParams, value: string) => {
    onChange({ ...filters, [field]: value || undefined });
  };

  return (
    <div className="flex flex-wrap gap-3 items-end">
      <div>
        <label className="text-xs text-slate-500">Customer ID</label>
        <Input
          placeholder="cust-..."
          value={filters.customerId ?? ''}
          onChange={(e) => update('customerId', e.target.value)}
          className="w-40"
        />
      </div>
      <div>
        <label className="text-xs text-slate-500">Event Type</label>
        <Input
          placeholder="purchase, login..."
          value={filters.eventType ?? ''}
          onChange={(e) => update('eventType', e.target.value)}
          className="w-40"
        />
      </div>
      <div>
        <label className="text-xs text-slate-500">Tenant</label>
        <Input
          placeholder="tenant-1"
          value={filters.tenantId ?? ''}
          onChange={(e) => update('tenantId', e.target.value)}
          className="w-32"
        />
      </div>
      <div>
        <label className="text-xs text-slate-500">Risk Min</label>
        <Input
          type="number"
          placeholder="0"
          value={filters.riskScoreMin ?? ''}
          onChange={(e) => update('riskScoreMin', e.target.value)}
          className="w-20"
        />
      </div>
      <div>
        <label className="text-xs text-slate-500">Risk Max</label>
        <Input
          type="number"
          placeholder="100"
          value={filters.riskScoreMax ?? ''}
          onChange={(e) => update('riskScoreMax', e.target.value)}
          className="w-20"
        />
      </div>
      <Button onClick={onSearch} size="sm">Search</Button>
      <Button onClick={onReset} variant="outline" size="sm">Reset</Button>
    </div>
  );
}
