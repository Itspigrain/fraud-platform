// frontend/src/components/alerts/AlertsFilter.tsx

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { AlertSearchParams } from '@/lib/types';

interface AlertsFilterProps {
  filters: AlertSearchParams;
  onChange: (filters: AlertSearchParams) => void;
  onSearch: () => void;
  onReset: () => void;
}

export function AlertsFilter({ filters, onChange, onSearch, onReset }: AlertsFilterProps) {
  const update = (field: keyof AlertSearchParams, value: string) => {
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
        <label className="text-xs text-slate-500">Rule</label>
        <Input
          placeholder="HIGH_VALUE..."
          value={filters.ruleId ?? ''}
          onChange={(e) => update('ruleId', e.target.value)}
          className="w-40"
        />
      </div>
      <div>
        <label className="text-xs text-slate-500">Severity</label>
        <Input
          placeholder="HIGH, MEDIUM..."
          value={filters.severity ?? ''}
          onChange={(e) => update('severity', e.target.value)}
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
