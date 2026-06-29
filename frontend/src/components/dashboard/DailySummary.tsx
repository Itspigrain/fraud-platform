// frontend/src/components/dashboard/DailySummary.tsx

import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { toast } from 'sonner';

interface SummaryResponse {
  tenantId: string;
  summary: string;
  generatedAt: string;
}

const TENANT_ID = import.meta.env.VITE_TENANT_ID || 'tenant-1';

export function DailySummary() {
  const [summary, setSummary] = useState<SummaryResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const generate = async () => {
    setLoading(true);
    try {
      const res = await fetch('/summary/daily', {
        headers: { 'X-Tenant-Id': TENANT_ID },
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data: SummaryResponse = await res.json();
      setSummary(data);
    } catch (err) {
      console.error('[DailySummary] error:', err);
      toast.error('Failed to generate summary');
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <Card className="col-span-3">
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <div>
          <CardTitle className="text-sm font-medium">Daily Alert Summary</CardTitle>
          <p className="text-xs text-slate-500 mt-1">
            AI-generated fraud intelligence briefing
          </p>
        </div>
        <Button size="sm" onClick={generate} disabled={loading} className="shrink-0">
          {loading ? 'Generating...' : summary ? 'Regenerate' : 'Generate Summary'}
        </Button>
      </CardHeader>
      <CardContent>
        {loading && (
          <div className="space-y-3">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
            <Skeleton className="h-4 w-4/6" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-3/4" />
          </div>
        )}

        {!loading && !summary && (
          <p className="text-sm text-slate-400 italic">
            Click Generate Summary to create an AI-powered digest of today's fraud alerts.
          </p>
        )}

        {!loading && summary && (
          <div className="space-y-3">
            <div className="prose prose-sm max-w-none
              prose-headings:text-slate-800 prose-headings:font-semibold
              prose-h1:text-base prose-h2:text-sm prose-h2:mt-4 prose-h3:text-sm
              prose-p:text-slate-600 prose-p:leading-relaxed prose-p:my-1
              prose-strong:text-slate-800 prose-strong:font-semibold
              prose-ul:my-1 prose-li:text-slate-600 prose-li:my-0.5
              prose-ol:my-1
              prose-hr:border-slate-200 prose-hr:my-3">
              <ReactMarkdown>{summary.summary}</ReactMarkdown>
            </div>
            <p className="text-xs text-slate-400 pt-2 border-t border-slate-100">
              Generated at {formatTime(summary.generatedAt)} · tenant: {summary.tenantId}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
