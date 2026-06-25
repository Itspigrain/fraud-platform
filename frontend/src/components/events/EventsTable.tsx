import { Fragment, useState } from 'react';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import type { EventDocument, PageInfo } from '@/lib/types';

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
            <TableHead className="cursor-pointer" onClick={() => onSort('eventType')}>
              Type{sortIcon('eventType')}
            </TableHead>
            <TableHead>Tenant</TableHead>
            <TableHead>Attributes</TableHead>
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
                <TableCell className="font-medium">{e.eventType}</TableCell>
                <TableCell className="text-sm">{e.tenantId}</TableCell>
                <TableCell className="text-sm text-slate-500">
                  {e.attributes ? Object.keys(e.attributes).slice(0, 3).join(', ') : '—'}
                  {e.attributes && Object.keys(e.attributes).length > 3 && ` +${Object.keys(e.attributes).length - 3}`}
                </TableCell>
              </TableRow>
              {expandedId === e.id && (
                <TableRow>
                  <TableCell colSpan={4} className="bg-slate-50 p-4">
                    <div className="text-sm space-y-2">
                      <div><span className="text-slate-500">ID:</span> <span className="font-mono">{e.id}</span></div>
                      {e.attributes && Object.keys(e.attributes).length > 0 && (
                        <div>
                          <span className="text-slate-500">Attributes:</span>
                          <pre className="text-xs bg-white p-2 rounded mt-1 border">{JSON.stringify(e.attributes, null, 2)}</pre>
                        </div>
                      )}
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
