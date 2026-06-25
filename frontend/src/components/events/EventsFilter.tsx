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
        <label className="text-xs text-slate-500">Search</label>
        <Input
          placeholder="Full-text search..."
          value={filters.q ?? ''}
          onChange={(e) => update('q', e.target.value)}
          className="w-48"
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
      <Button onClick={onSearch} size="sm">Search</Button>
      <Button onClick={onReset} variant="outline" size="sm">Reset</Button>
    </div>
  );
}
