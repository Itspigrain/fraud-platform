// frontend/src/components/alerts/AlertsTable.tsx

import { Fragment, useState } from 'react';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { AlertDocument, PageInfo } from '@/lib/types';

function severityBadge(severity: string) {
  switch (severity) {
    case 'CRITICAL': return <Badge variant="destructive">{severity}</Badge>;
    case 'HIGH': return <Badge variant="secondary" className="bg-orange-100 text-orange-800">{severity}</Badge>;
    case 'MEDIUM': return <Badge variant="secondary" className="bg-yellow-100 text-yellow-800">{severity}</Badge>;
    case 'LOW': return <Badge variant="secondary" className="bg-green-100 text-green-800">{severity}</Badge>;
    default: return <Badge variant="secondary">{severity}</Badge>;
  }
}

interface AlertsTableProps {
  alerts: AlertDocument[];
  page: PageInfo;
  onPageChange: (page: number) => void;
  onSort: (field: string) => void;
  sortField: string;
  sortDir: string;
}

export function AlertsTable({ alerts, page, onPageChange, onSort, sortField, sortDir }: AlertsTableProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const sortIcon = (field: string) => {
    if (sortField !== field) return ' ↕';
    return sortDir === 'asc' ? ' ↑' : ' ↓';
  };

  return (
    <div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="cursor-pointer" onClick={() => onSort('detectedAt')}>
              Time{sortIcon('detectedAt')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('customerId')}>
              Customer{sortIcon('customerId')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('ruleId')}>
              Rule{sortIcon('ruleId')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('severity')}>
              Severity{sortIcon('severity')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('riskScore')}>
              Risk{sortIcon('riskScore')}
            </TableHead>
            <TableHead>Reason</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {alerts.map((a) => (
            <Fragment key={a.alertId}>
              <TableRow
                className="cursor-pointer hover:bg-slate-50"
                onClick={() => setExpandedId(expandedId === a.alertId ? null : a.alertId)}
              >
                <TableCell className="text-sm">{new Date(a.detectedAt).toLocaleString()}</TableCell>
                <TableCell className="font-mono text-sm">{a.customerId}</TableCell>
                <TableCell className="font-mono text-sm">{a.ruleId}</TableCell>
                <TableCell>{severityBadge(a.severity)}</TableCell>
                <TableCell>{a.riskScore}</TableCell>
                <TableCell className="text-sm max-w-xs truncate">{a.reason}</TableCell>
              </TableRow>
              {expandedId === a.alertId && (
                <TableRow>
                  <TableCell colSpan={6} className="bg-slate-50 p-4">
                    <div className="grid grid-cols-3 gap-4 text-sm">
                      <div><span className="text-slate-500">Alert ID:</span> <span className="font-mono">{a.alertId}</span></div>
                      <div><span className="text-slate-500">Event ID:</span> <span className="font-mono">{a.eventId}</span></div>
                      <div><span className="text-slate-500">Reason:</span> {a.reason}</div>
                    </div>
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          ))}
        </TableBody>
      </Table>

      <div className="flex items-center justify-between mt-4">
        <span className="text-sm text-slate-500">
          {page.totalElements} results — page {page.number + 1} of {page.totalPages}
        </span>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page.number === 0}
            onClick={() => onPageChange(page.number - 1)}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page.number + 1 >= page.totalPages}
            onClick={() => onPageChange(page.number + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  );
}
