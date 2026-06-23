import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { RuleCondition, ConditionOperator } from '@/lib/types';

const CORE_FIELDS = [
  'riskScore',
  'eventType',
  'customerId',
  'sourceIp',
  'deviceId',
  'email',
  'phoneNumber',
];

const NUMERIC_FIELDS = new Set(['riskScore']);

const ALL_OPERATORS: { value: ConditionOperator; label: string; numeric?: boolean; string?: boolean }[] = [
  { value: 'EQUALS', label: '=', numeric: true, string: true },
  { value: 'NOT_EQUALS', label: '!=', numeric: true, string: true },
  { value: 'GREATER_THAN', label: '>', numeric: true },
  { value: 'LESS_THAN', label: '<', numeric: true },
  { value: 'GREATER_THAN_OR_EQUAL', label: '>=', numeric: true },
  { value: 'LESS_THAN_OR_EQUAL', label: '<=', numeric: true },
  { value: 'CONTAINS', label: 'contains', string: true },
  { value: 'IN', label: 'in', string: true },
];

function getOperators(field: string) {
  const isNumeric = NUMERIC_FIELDS.has(field) || field.startsWith('attributes.');
  if (isNumeric) return ALL_OPERATORS.filter(o => o.numeric);
  return ALL_OPERATORS.filter(o => o.string);
}

interface ConditionBuilderProps {
  conditions: RuleCondition[];
  onChange: (conditions: RuleCondition[]) => void;
}

export function ConditionBuilder({ conditions, onChange }: ConditionBuilderProps) {
  const updateCondition = (index: number, updates: Partial<RuleCondition>) => {
    const updated = conditions.map((c, i) => (i === index ? { ...c, ...updates } : c));
    onChange(updated);
  };

  const addCondition = () => {
    onChange([...conditions, { field: 'riskScore', operator: 'GREATER_THAN', value: '' }]);
  };

  const removeCondition = (index: number) => {
    onChange(conditions.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-3">
      <label className="text-sm font-medium text-slate-700">Conditions (all must match)</label>
      {conditions.map((condition, index) => (
        <div key={index} className="flex items-center gap-2">
          <div className="flex-1">
            <Input
              list="field-options"
              value={condition.field}
              onChange={(e) => updateCondition(index, { field: e.target.value })}
              placeholder="Field (e.g. riskScore, attributes.amount)"
              className="text-sm"
            />
          </div>
          <select
            value={condition.operator}
            onChange={(e) => updateCondition(index, { operator: e.target.value as ConditionOperator })}
            className="h-8 rounded-md border border-input bg-white px-2 text-sm"
          >
            {getOperators(condition.field).map(op => (
              <option key={op.value} value={op.value}>{op.label}</option>
            ))}
          </select>
          <div className="flex-1">
            <Input
              value={condition.value}
              onChange={(e) => updateCondition(index, { value: e.target.value })}
              placeholder="Value"
              className="text-sm"
            />
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => removeCondition(index)}
            className="text-red-500 hover:text-red-700 px-2"
          >
            x
          </Button>
        </div>
      ))}
      <datalist id="field-options">
        {CORE_FIELDS.map(f => <option key={f} value={f} />)}
      </datalist>
      <Button type="button" variant="outline" size="sm" onClick={addCondition}>
        + Add Condition
      </Button>
    </div>
  );
}
