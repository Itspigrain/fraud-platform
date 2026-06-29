// frontend/src/pages/RulesPage.tsx

import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

const TENANT_ID = import.meta.env.VITE_TENANT_ID || 'tenant-1';

interface Rule {
  id: string;
  prompt: string;
  severity: string;
  score: number;
  decisionImpact: string;
}

interface RulesResponse {
  aiEnabled: boolean;
  inferenceEndpoint: string;
  ruleCount: number;
  decisionThresholds: Record<string, string>;
  rules: Rule[];
}

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

const severityColor: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-800 border border-red-200',
  HIGH:     'bg-orange-100 text-orange-800 border border-orange-200',
  MEDIUM:   'bg-yellow-100 text-yellow-800 border border-yellow-200',
  LOW:      'bg-green-100 text-green-800 border border-green-200',
};

function SeverityBadge({ s }: { s: string }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${severityColor[s] ?? 'bg-slate-100 text-slate-700'}`}>
      {s}
    </span>
  );
}

function ScoreBar({ score }: { score: number }) {
  const color = score >= 80 ? 'bg-red-500' : score >= 60 ? 'bg-orange-400' : score >= 40 ? 'bg-yellow-400' : 'bg-green-400';
  return (
    <div className="flex items-center gap-2">
      <div className="w-24 h-2 bg-slate-200 rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${score}%` }} />
      </div>
      <span className="text-sm font-semibold tabular-nums text-slate-700">{score}</span>
    </div>
  );
}

interface RuleFormProps {
  initial?: Partial<Rule>;
  nextNumber: number;
  onClose: () => void;
}

function RuleFormModal({ initial, nextNumber, onClose }: RuleFormProps) {
  const isEdit = !!initial?.id;
  const [id, setId] = useState(initial?.id ?? '');
  const [prompt, setPrompt] = useState(initial?.prompt ?? '');
  const [severity, setSeverity] = useState(initial?.severity ?? 'HIGH');
  const [score, setScore] = useState(initial?.score ?? 50);
  const [copied, setCopied] = useState(false);

  const decisionLabel = score <= 30 ? 'ALLOW alone' : score <= 60 ? 'FLAG alone' : 'BLOCK alone';

  const generateEnvVars = () => {
    const ruleId = id.toUpperCase().replace(/\s+/g, '_');
    const n = nextNumber;
    return [
      `# Add to .env:`,
      `FRAUD_RULE_${n}_ID=${ruleId}`,
      `FRAUD_RULE_${n}_PROMPT=${prompt}`,
      `FRAUD_RULE_${n}_SEVERITY=${severity}`,
      `FRAUD_RULE_${n}_SCORE=${score}`,
      ``,
      `# Add to docker-compose.yml under fraud-app environment:`,
      `      - FRAUD_RULE_${n}_ID=\${FRAUD_RULE_${n}_ID}`,
      `      - FRAUD_RULE_${n}_PROMPT=\${FRAUD_RULE_${n}_PROMPT}`,
      `      - FRAUD_RULE_${n}_SEVERITY=\${FRAUD_RULE_${n}_SEVERITY}`,
      `      - FRAUD_RULE_${n}_SCORE=\${FRAUD_RULE_${n}_SCORE}`,
      ``,
      `# Then restart:`,
      `sudo docker compose up -d fraud-app`,
    ].join('\n');
  };

  const [showEnvVars, setShowEnvVars] = useState(false);

  const copyToClipboard = async () => {
    if (!id.trim() || !prompt.trim()) {
      toast.error('Rule ID and prompt are required');
      return;
    }
    const text = generateEnvVars();
    // Try clipboard first, fall back to showing text
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 3000);
      toast.success('Copied to clipboard');
    } catch {
      // Clipboard blocked (HTTP/Safari) — show the text inline
      setShowEnvVars(true);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="p-6 border-b border-slate-200">
          <h2 className="text-lg font-semibold text-slate-900">
            {isEdit ? `Rule: ${initial?.id}` : `New Rule #${nextNumber}`}
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            {isEdit ? 'Rules are managed via .env — edit there to modify.' : 'Define a fraud detection rule in plain English.'}
          </p>
        </div>

        <div className="p-6 space-y-5">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-slate-700">Rule ID</label>
            <input type="text" value={id}
              onChange={e => setId(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))}
              disabled={isEdit}
              placeholder="e.g. DORMANT_ACCOUNT_RISK"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50 disabled:text-slate-500" />
            <p className="text-xs text-slate-400">Uppercase and underscores only. Shown in alerts.</p>
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-medium text-slate-700">Detection Prompt</label>
            <textarea value={prompt} onChange={e => setPrompt(e.target.value)} disabled={isEdit} rows={8}
              placeholder="Describe the fraud pattern in plain English. Include: what triggers the rule, false positive guidance, and severity gradients..."
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none disabled:bg-slate-50 disabled:text-slate-500" />
            <p className="text-xs text-slate-400">The LLM uses this as detection criteria evaluated against each event with 24h customer history.</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-sm font-medium text-slate-700">Default Severity</label>
              <select value={severity} onChange={e => setSeverity(e.target.value)} disabled={isEdit}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50">
                {SEVERITIES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="text-sm font-medium text-slate-700">Risk Score (0–100)</label>
              <div className="flex items-center gap-3">
                <input type="range" min={0} max={100} value={score}
                  onChange={e => setScore(Number(e.target.value))} disabled={isEdit}
                  className="flex-1 disabled:opacity-50" />
                <span className="text-sm font-bold w-8 text-right tabular-nums text-slate-700">{score}</span>
              </div>
              <p className="text-xs text-slate-400">{decisionLabel}</p>
            </div>
          </div>

          <div className="bg-slate-50 rounded-lg p-4 text-sm">
            <p className="font-medium text-slate-700 mb-1">Score thresholds</p>
            <p className="text-slate-500">
              0–30 <span className="text-green-600 font-medium">ALLOW</span> &nbsp;·&nbsp;
              31–60 <span className="text-yellow-600 font-medium">FLAG</span> &nbsp;·&nbsp;
              61–100 <span className="text-red-600 font-medium">BLOCK</span>
            </p>
          </div>

          {!isEdit && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 space-y-2">
              <p className="text-sm font-semibold text-amber-800">After copying:</p>
              <ol className="text-xs text-amber-700 space-y-1 list-decimal list-inside">
                <li>Paste into <code className="bg-amber-100 px-1 rounded">.env</code> and <code className="bg-amber-100 px-1 rounded">docker-compose.yml</code></li>
                <li>Run: <code className="bg-amber-100 px-1 rounded">sudo docker compose up -d fraud-app</code></li>
                <li>Return here and click <strong>Hot Reload</strong> to verify</li>
              </ol>
            </div>
          )}
        </div>

        {showEnvVars && (
          <div className="mx-6 mb-4 rounded-lg border border-slate-200 bg-slate-50 overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2 bg-slate-100 border-b border-slate-200">
              <span className="text-xs font-semibold text-slate-600">Paste these into .env and docker-compose.yml</span>
              <button onClick={() => setShowEnvVars(false)} className="text-slate-400 hover:text-slate-600 text-xs">✕ Close</button>
            </div>
            <pre className="p-4 text-xs font-mono text-slate-700 whitespace-pre-wrap overflow-auto max-h-64 select-all">
              {generateEnvVars()}
            </pre>
          </div>
        )}
        <div className="p-6 border-t border-slate-200 flex justify-end gap-3">
          <Button variant="outline" onClick={onClose}>{isEdit ? 'Close' : 'Cancel'}</Button>
          {!isEdit && (
            <Button onClick={copyToClipboard}>
              {copied ? 'Copied!' : showEnvVars ? 'Copy to Clipboard' : 'Generate Env Vars'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

export function RulesPage() {
  const [data, setData] = useState<RulesResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [viewRule, setViewRule] = useState<Rule | null>(null);
  const [reloading, setReloading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await fetch('/rules', { headers: { 'X-Tenant-Id': TENANT_ID } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      setData(await res.json());
    } catch { toast.error('Failed to load rules'); }
    finally { setLoading(false); }
  };

  const reload = async () => {
    setReloading(true);
    try {
      const res = await fetch('/rules/reload', { method: 'POST', headers: { 'X-Tenant-Id': TENANT_ID } });
      const d = await res.json();
      toast.success(`Reloaded — ${d.rulesAfter} rules active`);
      await load();
    } catch { toast.error('Reload failed'); }
    finally { setReloading(false); }
  };

  useEffect(() => { load(); }, []);

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Fraud Rules</h1>
          <p className="text-sm text-slate-500 mt-1">
            AI-powered rules evaluated in a single LLM call per event using ECH inference.
            Stored in <code className="bg-slate-100 px-1 rounded text-xs">.env</code> — no code changes needed.
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <Button variant="outline" size="sm" onClick={reload} disabled={reloading}>
            {reloading ? 'Reloading...' : 'Hot Reload'}
          </Button>
          <Button size="sm" onClick={() => setShowAdd(true)}>+ Add Rule</Button>
        </div>
      </div>

      {data && (
        <div className="grid grid-cols-4 gap-4">
          <div className="bg-white border border-slate-200 rounded-lg p-4">
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Rules Active</p>
            <p className="text-3xl font-bold text-slate-900 mt-1">{data.ruleCount}</p>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-4">
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">AI Engine</p>
            <p className={`text-sm font-semibold mt-2 ${data.aiEnabled ? 'text-green-600' : 'text-slate-400'}`}>
              {data.aiEnabled ? '● Active' : '○ Disabled'}
            </p>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-4 col-span-2">
            <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Inference Endpoint</p>
            <p className="text-xs font-mono text-slate-700 mt-2 truncate">{data.inferenceEndpoint}</p>
          </div>
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-lg p-4 flex gap-8">
        {[['ALLOW', '0–30', 'green'], ['FLAG', '31–60', 'yellow'], ['BLOCK', '61–100', 'red']].map(([k, v, c]) => (
          <div key={k} className="flex items-center gap-2">
            <span className={`w-2.5 h-2.5 rounded-full bg-${c}-500`} />
            <span className="text-sm font-semibold text-slate-700">{k}</span>
            <span className="text-sm text-slate-400">{v}</span>
          </div>
        ))}
        <p className="text-xs text-slate-400 self-center ml-4">Scores sum across all rules that fire, capped at 100</p>
      </div>

      {loading ? (
        <div className="space-y-3">{[1,2,3].map(i => <Skeleton key={i} className="h-24 rounded-lg" />)}</div>
      ) : (
        <div className="space-y-3">
          {data?.rules.map((rule, i) => (
            <div key={rule.id} onClick={() => setViewRule(rule)}
              className="bg-white border border-slate-200 rounded-lg p-5 hover:border-indigo-300 hover:shadow-sm transition-all cursor-pointer">
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-3 min-w-0">
                  <span className="text-xs text-slate-400 font-mono mt-0.5 w-5 shrink-0">{i + 1}</span>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span className="font-mono text-sm font-bold text-slate-900">{rule.id}</span>
                      <SeverityBadge s={rule.severity} />
                    </div>
                    <p className="text-sm text-slate-500 line-clamp-2 leading-relaxed">{rule.prompt}</p>
                  </div>
                </div>
                <div className="shrink-0 space-y-1.5 text-right">
                  <ScoreBar score={rule.score} />
                  <p className="text-xs text-slate-400">{rule.decisionImpact}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && data?.ruleCount === 0 && (
        <div className="text-center py-16 text-slate-400">
          <p className="text-lg font-medium">No rules configured</p>
          <p className="text-sm mt-1">Add a rule to start detecting fraud patterns.</p>
          <Button className="mt-4" onClick={() => setShowAdd(true)}>+ Add First Rule</Button>
        </div>
      )}

      {showAdd && <RuleFormModal nextNumber={(data?.ruleCount ?? 0) + 1} onClose={() => { setShowAdd(false); load(); }} />}
      {viewRule && <RuleFormModal initial={viewRule} nextNumber={(data?.ruleCount ?? 0) + 1} onClose={() => setViewRule(null)} />}
    </div>
  );
}
