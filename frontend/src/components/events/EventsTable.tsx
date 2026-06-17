// frontend/src/components/events/EventsTable.tsx

import { Fragment, useState } from 'react';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { EventDocument, PageInfo } from '@/lib/types';

function riskBadge(score: number) {
  if (score === 0) return <Badge variant="secondary" className="bg-green-100 text-green-800">{score}</Badge>;
  if (score < 30) return <Badge variant="secondary" className="bg-yellow-100 text-yellow-800">{score}</Badge>;
  if (score < 60) return <Badge variant="secondary" className="bg-orange-100 text-orange-800">{score}</Badge>;
  return <Badge variant="destructive">{score}</Badge>;
}

interface EventsTableProps {
  events: EventDocument[];
  page: PageInfo;
  onPageChange: (page: number) => void;
  onSort: (field: string) => void;
  sortField: string;
  sortDir: string;
}

export function EventsTable({ events, page, onPageChange, onSort, sortField, sortDir }: EventsTableProps) {
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
            <TableHead className="cursor-pointer" onClick={() => onSort('eventTime')}>
              Time{sortIcon('eventTime')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('customerId')}>
              Customer{sortIcon('customerId')}
            </TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('eventType')}>
              Type{sortIcon('eventType')}
            </TableHead>
            <TableHead>Tenant</TableHead>
            <TableHead>Source IP</TableHead>
            <TableHead className="cursor-pointer" onClick={() => onSort('riskScore')}>
              Risk{sortIcon('riskScore')}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {events.map((e) => (
            <Fragment key={e.id}>
              <TableRow
                className="cursor-pointer hover:bg-slate-50"
                onClick={() => setExpandedId(expandedId === e.id ? null : e.id)}
              >
                <TableCell className="text-sm">{new Date(e.eventTime).toLocaleString()}</TableCell>
                <TableCell className="font-mono text-sm">{e.customerId}</TableCell>
                <TableCell>{e.eventType}</TableCell>
                <TableCell>{e.tenantId}</TableCell>
                <TableCell className="font-mono text-sm">{e.sourceIp}</TableCell>
                <TableCell>{riskBadge(e.riskScore)}</TableCell>
              </TableRow>
              {expandedId === e.id && (
                <TableRow>
                  <TableCell colSpan={6} className="bg-slate-50 p-4">
                    <div className="grid grid-cols-4 gap-4 text-sm">
                      <div><span className="text-slate-500">Device:</span> {e.deviceId}</div>
                      <div><span className="text-slate-500">Email:</span> {e.email}</div>
                      <div><span className="text-slate-500">Phone:</span> {e.phoneNumber}</div>
                      <div><span className="text-slate-500">ID:</span> <span className="font-mono">{e.id}</span></div>
                    </div>
                    {e.attributes && Object.keys(e.attributes).length > 0 && (
                      <div className="mt-2">
                        <span className="text-slate-500 text-sm">Attributes:</span>
                        <pre className="text-xs bg-white p-2 rounded mt-1 border">{JSON.stringify(e.attributes, null, 2)}</pre>
                      </div>
                    )}
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
