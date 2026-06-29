import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { RuleCondition, ConditionOperator } from '@/lib/types';

const ALL_OPERATORS: { value: ConditionOperator; label: string }[] = [
  { value: 'EQUALS', label: '=' },
  { value: 'NOT_EQUALS', label: '!=' },
  { value: 'GREATER_THAN', label: '>' },
  { value: 'LESS_THAN', label: '<' },
  { value: 'GREATER_THAN_OR_EQUAL', label: '>=' },
  { value: 'LESS_THAN_OR_EQUAL', label: '<=' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'IN', label: 'in' },
];

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
    onChange([...conditions, { field: '', operator: 'EQUALS', value: '' }]);
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
              value={condition.field}
              onChange={(e) => updateCondition(index, { field: e.target.value })}
              placeholder="Field (e.g. amount, sourceIp)"
              className="text-sm"
            />
          </div>
          <select
            value={condition.operator}
            onChange={(e) => updateCondition(index, { operator: e.target.value as ConditionOperator })}
            className="h-8 rounded-md border border-input bg-white px-2 text-sm"
          >
            {ALL_OPERATORS.map(op => (
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
      <Button type="button" variant="outline" size="sm" onClick={addCondition}>
        + Add Condition
      </Button>
    </div>
  );
}
