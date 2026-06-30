import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { RuleResponse } from '@/lib/types';

interface RulesTableProps {
  rules: RuleResponse[];
  onEdit: (rule: RuleResponse) => void;
  onDelete: (rule: RuleResponse) => void;
  onToggleStatus: (rule: RuleResponse) => void;
  onViewResults: (rule: RuleResponse) => void;
}

export function RulesTable({ rules, onEdit, onDelete, onToggleStatus, onViewResults }: RulesTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Verdict</TableHead>
          <TableHead>Severity</TableHead>
          <TableHead>Config</TableHead>
          <TableHead>Created</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rules.length === 0 && (
          <TableRow>
            <TableCell colSpan={8} className="text-center text-slate-500 py-8">
              No rules defined yet. Create one to get started.
            </TableCell>
          </TableRow>
        )}
        {rules.map((rule) => (
          <TableRow key={rule.id}>
            <TableCell>
              <div>
                <div className="font-medium">{rule.name}</div>
                {rule.description && (
                  <div className="text-sm text-slate-500">{rule.description}</div>
                )}
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="secondary">
                {rule.ruleType === 'VELOCITY' ? 'Velocity' : rule.ruleType === 'LLM_EVALUATOR' ? 'LLM Evaluator' : 'Condition'}
              </Badge>
            </TableCell>
            <TableCell>
              <Badge
                className="cursor-pointer"
                variant={rule.status === 'ACTIVE' ? 'default' : 'secondary'}
                onClick={() => onToggleStatus(rule)}
              >
                {rule.status}
              </Badge>
            </TableCell>
            <TableCell>
              <Badge variant="outline">{rule.verdict || 'REVIEW'}</Badge>
            </TableCell>
            <TableCell>
              <Badge variant="outline">{rule.severity || 'HIGH'}</Badge>
            </TableCell>
            <TableCell className="text-sm text-slate-600">
              {rule.ruleType === 'VELOCITY' ? (
                <span>{rule.groupByField} &gt; {rule.threshold} / {rule.timeWindowMinutes}m</span>
              ) : rule.ruleType === 'LLM_EVALUATOR' ? (
                <span>every {rule.evaluationIntervalMinutes}m / {rule.timeWindowMinutes}m window</span>
              ) : (
                <span>{rule.conditions.length} condition{rule.conditions.length !== 1 ? 's' : ''}</span>
              )}
            </TableCell>
            <TableCell className="text-sm">
              {new Date(rule.createdAt).toLocaleDateString()}
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end gap-1">
                <Button variant="outline" size="sm" onClick={() => onViewResults(rule)}>
                  Results
                </Button>
                <Button variant="outline" size="sm" onClick={() => onEdit(rule)}>
                  Edit
                </Button>
                <Button variant="outline" size="sm" className="text-red-500" onClick={() => onDelete(rule)}>
                  Delete
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
