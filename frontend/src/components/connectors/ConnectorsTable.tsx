import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { ConnectorResponse, RuleResponse } from '@/lib/types';

interface ConnectorsTableProps {
  connectors: ConnectorResponse[];
  rules: RuleResponse[];
  onEdit: (connector: ConnectorResponse) => void;
  onDelete: (connector: ConnectorResponse) => void;
  onToggleStatus: (connector: ConnectorResponse) => void;
  onTest: (connector: ConnectorResponse) => void;
}

export function ConnectorsTable({ connectors, rules, onEdit, onDelete, onToggleStatus, onTest }: ConnectorsTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>URL</TableHead>
          <TableHead>Bound Rules</TableHead>
          <TableHead>Retries</TableHead>
          <TableHead>Created</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {connectors.length === 0 && (
          <TableRow>
            <TableCell colSpan={8} className="text-center text-slate-500 py-8">
              No connectors defined yet. Create one to get started.
            </TableCell>
          </TableRow>
        )}
        {connectors.map((connector) => (
          <TableRow key={connector.id}>
            <TableCell>
              <div>
                <div className="font-medium">{connector.name}</div>
                {connector.description && (
                  <div className="text-sm text-slate-500">{connector.description}</div>
                )}
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="secondary">{connector.type}</Badge>
            </TableCell>
            <TableCell>
              <Badge
                className="cursor-pointer"
                variant={connector.status === 'ACTIVE' ? 'default' : 'secondary'}
                onClick={() => onToggleStatus(connector)}
              >
                {connector.status}
              </Badge>
            </TableCell>
            <TableCell className="text-sm text-slate-600 max-w-48 truncate" title={connector.config.url}>
              {connector.config.url}
            </TableCell>
            <TableCell className="text-sm text-slate-600">
              {connector.ruleIds.length > 0 ? (
                <span>
                  {connector.ruleIds.map(id => {
                    const rule = rules.find(r => r.id === id);
                    return rule?.name || `#${id}`;
                  }).join(', ')}
                </span>
              ) : (
                <span className="text-slate-400">None</span>
              )}
            </TableCell>
            <TableCell className="text-sm text-slate-600">
              {connector.retryAttempts}
            </TableCell>
            <TableCell className="text-sm">
              {new Date(connector.createdAt).toLocaleDateString()}
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end gap-1">
                <Button variant="outline" size="sm" onClick={() => onTest(connector)}>
                  Test
                </Button>
                <Button variant="outline" size="sm" onClick={() => onEdit(connector)}>
                  Edit
                </Button>
                <Button variant="outline" size="sm" className="text-red-500" onClick={() => onDelete(connector)}>
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
