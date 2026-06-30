import { useState, useEffect, useCallback, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { fetchRules } from '@/lib/api';
import type { ConnectorRequest, ConnectorResponse, RuleResponse } from '@/lib/types';

interface ConnectorFormDialogProps {
  connector?: ConnectorResponse | null;
  onSave: (request: ConnectorRequest) => void;
  onCancel: () => void;
}

export function ConnectorFormDialog({ connector, onSave, onCancel }: ConnectorFormDialogProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [url, setUrl] = useState('');
  const [method, setMethod] = useState('POST');
  const [headers, setHeaders] = useState<{ key: string; value: string }[]>([]);
  const [timeoutMs, setTimeoutMs] = useState(5000);
  const [ruleIds, setRuleIds] = useState<number[]>([]);
  const [retryAttempts, setRetryAttempts] = useState(3);
  const [retryDelayMs, setRetryDelayMs] = useState(1000);
  const [availableRules, setAvailableRules] = useState<RuleResponse[]>([]);

  useEffect(() => {
    fetchRules().then(setAvailableRules).catch(() => {});
  }, []);

  useEffect(() => {
    if (connector) {
      setName(connector.name);
      setDescription(connector.description || '');
      setUrl(connector.config.url || '');
      setMethod(connector.config.method || 'POST');
      setTimeoutMs(connector.config.timeoutMs || 5000);
      setRetryAttempts(connector.retryAttempts);
      setRetryDelayMs(connector.retryDelayMs);
      setRuleIds(connector.ruleIds || []);

      const h = connector.config.headers || {};
      setHeaders(Object.entries(h).map(([key, value]) => ({ key, value })));
    }
  }, [connector]);

  const buildRequest = (): ConnectorRequest => {
    const headerMap: Record<string, string> = {};
    headers.filter(h => h.key.trim()).forEach(h => { headerMap[h.key] = h.value; });

    return {
      name,
      description: description || undefined,
      type: 'WEBHOOK',
      status: connector?.status ?? 'ACTIVE',
      config: {
        url,
        method,
        headers: Object.keys(headerMap).length > 0 ? headerMap : undefined,
        timeoutMs,
      },
      ruleIds,
      retryAttempts,
      retryDelayMs,
    };
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(buildRequest());
  };

  const [size, setSize] = useState({ width: 672, height: 600 });
  const resizing = useRef(false);
  const startPos = useRef({ x: 0, y: 0, w: 0, h: 0 });

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    resizing.current = true;
    startPos.current = { x: e.clientX, y: e.clientY, w: size.width, h: size.height };
    const onMouseMove = (ev: MouseEvent) => {
      if (!resizing.current) return;
      setSize({
        width: Math.max(400, startPos.current.w + (ev.clientX - startPos.current.x)),
        height: Math.max(300, startPos.current.h + (ev.clientY - startPos.current.y)),
      });
    };
    const onMouseUp = () => {
      resizing.current = false;
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    };
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
  }, [size]);

  const isValid = name.trim() && url.trim();

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <Card className="relative overflow-auto" style={{ width: size.width, height: size.height }}>
        <CardHeader>
          <CardTitle>{connector ? 'Edit Connector' : 'Create Connector'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-slate-700">Name</label>
                <Input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. slack-fraud-alerts"
                  className="mt-1"
                  required
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">Description</label>
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Optional description"
                  className="mt-1"
                />
              </div>
            </div>

            <div className="space-y-3 rounded-lg border border-slate-200 p-4">
              <label className="text-sm font-medium text-slate-700">Webhook Configuration</label>
              <div>
                <label className="text-sm text-slate-600">URL</label>
                <Input
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://hooks.slack.com/services/..."
                  className="mt-1"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm text-slate-600">HTTP Method</label>
                  <div className="flex gap-2 mt-1">
                    {['POST', 'PUT'].map(m => (
                      <button
                        key={m}
                        type="button"
                        onClick={() => setMethod(m)}
                        className={`px-3 py-1.5 rounded text-sm font-medium border transition-colors ${
                          method === m
                            ? 'bg-slate-900 text-white border-slate-900'
                            : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                        }`}
                      >
                        {m}
                      </button>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="text-sm text-slate-600">Timeout (ms)</label>
                  <Input
                    type="number"
                    min={100}
                    value={timeoutMs}
                    onChange={(e) => setTimeoutMs(Number(e.target.value))}
                    className="mt-1"
                  />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <label className="text-sm text-slate-600">Headers</label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setHeaders([...headers, { key: '', value: '' }])}
                  >
                    + Add Header
                  </Button>
                </div>
                {headers.map((h, i) => (
                  <div key={i} className="flex gap-2 mt-1">
                    <Input
                      value={h.key}
                      onChange={(e) => {
                        const updated = [...headers];
                        updated[i] = { ...h, key: e.target.value };
                        setHeaders(updated);
                      }}
                      placeholder="Header name"
                      className="flex-1"
                    />
                    <Input
                      value={h.value}
                      onChange={(e) => {
                        const updated = [...headers];
                        updated[i] = { ...h, value: e.target.value };
                        setHeaders(updated);
                      }}
                      placeholder="Header value"
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="text-red-500"
                      onClick={() => setHeaders(headers.filter((_, j) => j !== i))}
                    >
                      x
                    </Button>
                  </div>
                ))}
              </div>
            </div>

            <div className="space-y-3 rounded-lg border border-slate-200 p-4">
              <label className="text-sm font-medium text-slate-700">Rule Bindings</label>
              <p className="text-xs text-slate-400">
                This connector will fire when any of the selected rules match an event.
              </p>
              <div className="space-y-1 max-h-32 overflow-y-auto">
                {availableRules.length === 0 ? (
                  <p className="text-sm text-slate-400">No rules available</p>
                ) : (
                  availableRules.map(r => (
                    <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer">
                      <input
                        type="checkbox"
                        checked={ruleIds.includes(r.id)}
                        onChange={(e) => {
                          if (e.target.checked) {
                            setRuleIds([...ruleIds, r.id]);
                          } else {
                            setRuleIds(ruleIds.filter(id => id !== r.id));
                          }
                        }}
                        className="rounded"
                      />
                      <span>{r.name}</span>
                      <span className="text-xs text-slate-400">({r.eventType})</span>
                    </label>
                  ))
                )}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-slate-700">Retry Attempts</label>
                <Input
                  type="number"
                  min={0}
                  max={10}
                  value={retryAttempts}
                  onChange={(e) => setRetryAttempts(Number(e.target.value))}
                  className="mt-1"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">Retry Delay (ms)</label>
                <Input
                  type="number"
                  min={100}
                  value={retryDelayMs}
                  onChange={(e) => setRetryDelayMs(Number(e.target.value))}
                  className="mt-1"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={onCancel}>
                Cancel
              </Button>
              <Button type="submit" disabled={!isValid}>
                {connector ? 'Update' : 'Create'}
              </Button>
            </div>
          </form>
        </CardContent>
        <div
          onMouseDown={onMouseDown}
          className="absolute bottom-0 right-0 w-4 h-4 cursor-se-resize"
          style={{
            background: 'linear-gradient(135deg, transparent 50%, #94a3b8 50%)',
            borderRadius: '0 0 var(--radius) 0',
          }}
        />
      </Card>
    </div>
  );
}
