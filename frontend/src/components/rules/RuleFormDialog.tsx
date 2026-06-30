import { useState, useEffect, useCallback, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { ConditionBuilder } from './ConditionBuilder';
import { fetchRules, validateRule } from '@/lib/api';
import type { DependencyCondition, RuleCondition, RuleRequest, RuleResponse, RuleType, ValidationError } from '@/lib/types';

interface RuleFormDialogProps {
  rule?: RuleResponse | null;
  onSave: (request: RuleRequest) => void;
  onCancel: () => void;
}

export function RuleFormDialog({ rule, onSave, onCancel }: RuleFormDialogProps) {
  const [eventType, setEventType] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [ruleType, setRuleType] = useState<RuleType>('CONDITION');
  const [conditions, setConditions] = useState<RuleCondition[]>([
    { field: '', operator: 'GREATER_THAN', value: '' },
  ]);
  const [groupByField, setGroupByField] = useState('');
  const [timeWindowMinutes, setTimeWindowMinutes] = useState(10);
  const [threshold, setThreshold] = useState(5);
  const [promptTemplate, setPromptTemplate] = useState('');
  const [evaluationIntervalMinutes, setEvaluationIntervalMinutes] = useState(5);
  const [verdict, setVerdict] = useState('');
  const [severity, setSeverity] = useState('');
  const [dependsOn, setDependsOn] = useState<number[]>([]);
  const [dependencyCondition, setDependencyCondition] = useState<DependencyCondition>('ALL');
  const [availableRules, setAvailableRules] = useState<RuleResponse[]>([]);
  const [validationErrors, setValidationErrors] = useState<ValidationError[]>([]);
  const [validating, setValidating] = useState(false);

  useEffect(() => {
    fetchRules().then(setAvailableRules).catch(() => {});
  }, []);

  useEffect(() => {
    if (rule) {
      setEventType(rule.eventType || '');
      setName(rule.name);
      setDescription(rule.description || '');
      setRuleType(rule.ruleType || 'CONDITION');
      setConditions(rule.conditions?.length ? rule.conditions : [{ field: '', operator: 'GREATER_THAN', value: '' }]);
      setGroupByField(rule.groupByField || '');
      setTimeWindowMinutes(rule.timeWindowMinutes || 10);
      setThreshold(rule.threshold || 5);
      setPromptTemplate(rule.promptTemplate || '');
      setEvaluationIntervalMinutes(rule.evaluationIntervalMinutes || 5);
      setVerdict(rule.verdict || '');
      setSeverity(rule.severity || '');
      setDependsOn(rule.dependsOn || []);
      setDependencyCondition(rule.dependencyCondition || 'ALL');
    }
  }, [rule]);

  const buildRequest = (): RuleRequest => {
    const request: RuleRequest = {
      eventType,
      name,
      description: description || undefined,
      ruleType,
      status: rule?.status ?? 'ACTIVE',
    };

    if (ruleType === 'CONDITION') {
      request.conditions = conditions;
    } else if (ruleType === 'VELOCITY') {
      request.groupByField = groupByField;
      request.timeWindowMinutes = timeWindowMinutes;
      request.threshold = threshold;
    } else {
      request.promptTemplate = promptTemplate;
      request.timeWindowMinutes = timeWindowMinutes;
      request.evaluationIntervalMinutes = evaluationIntervalMinutes;
    }

    request.verdict = verdict || undefined;
    request.severity = severity || undefined;
    request.dependsOn = dependsOn.length > 0 ? dependsOn : undefined;
    request.dependencyCondition = dependsOn.length > 0 ? dependencyCondition : undefined;
    return request;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const request = buildRequest();
    setValidating(true);
    setValidationErrors([]);
    try {
      const errors = await validateRule(request);
      setValidationErrors(errors);
      if (errors.length > 0) {
        setValidating(false);
        return;
      }
    } catch {
      // validation endpoint unavailable — proceed without client-side validation
    }
    setValidating(false);
    onSave(request);
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

  const isConditionValid = conditions.length > 0 && conditions.every(c => c.field.trim() && c.value.trim());
  const isVelocityValid = groupByField.trim() && timeWindowMinutes > 0 && threshold > 0;
  const isLlmValid = promptTemplate.trim() && timeWindowMinutes > 0 && evaluationIntervalMinutes > 0;
  const isValid = eventType.trim() && name.trim() && (
    ruleType === 'CONDITION' ? isConditionValid :
    ruleType === 'VELOCITY' ? isVelocityValid :
    isLlmValid
  );

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <Card className="relative overflow-auto" style={{ width: size.width, height: size.height }}>
        <CardHeader>
          <CardTitle>{rule ? 'Edit Rule' : 'Create Rule'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-slate-700">Event Type</label>
                <Input
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                  placeholder="e.g. purchase, login"
                  className="mt-1"
                  required
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">Name</label>
                <Input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g., High Value Purchase"
                  className="mt-1"
                />
              </div>
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

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium text-slate-700">Verdict</label>
                <Input
                  value={verdict}
                  onChange={(e) => setVerdict(e.target.value)}
                  placeholder="e.g. BLOCK, REVIEW, FLAG"
                  className="mt-1"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">Severity</label>
                <Input
                  value={severity}
                  onChange={(e) => setSeverity(e.target.value)}
                  placeholder="e.g. CRITICAL, HIGH, MEDIUM, LOW"
                  className="mt-1"
                />
              </div>
            </div>

            <div>
              <label className="text-sm font-medium text-slate-700">Rule Type</label>
              <div className="flex gap-2 mt-1">
                <button
                  type="button"
                  onClick={() => setRuleType('CONDITION')}
                  className={`px-4 py-2 rounded-md text-sm font-medium border transition-colors ${
                    ruleType === 'CONDITION'
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  Condition
                </button>
                <button
                  type="button"
                  onClick={() => setRuleType('VELOCITY')}
                  className={`px-4 py-2 rounded-md text-sm font-medium border transition-colors ${
                    ruleType === 'VELOCITY'
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  Velocity
                </button>
                <button
                  type="button"
                  onClick={() => setRuleType('LLM_EVALUATOR')}
                  className={`px-4 py-2 rounded-md text-sm font-medium border transition-colors ${
                    ruleType === 'LLM_EVALUATOR'
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  LLM Evaluator
                </button>
              </div>
            </div>

            {ruleType === 'CONDITION' ? (
              <ConditionBuilder conditions={conditions} onChange={setConditions} />
            ) : ruleType === 'VELOCITY' ? (
              <div className="space-y-3 rounded-lg border border-slate-200 p-4">
                <p className="text-sm text-slate-500">
                  Fire when the number of events grouped by a field exceeds a threshold within a time window.
                </p>
                <div>
                  <label className="text-sm font-medium text-slate-700">Group By Field</label>
                  <Input
                    value={groupByField}
                    onChange={(e) => setGroupByField(e.target.value)}
                    placeholder="e.g. customerId, sourceIp"
                    className="mt-1"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium text-slate-700">Time Window (minutes)</label>
                    <Input
                      type="number"
                      min={1}
                      value={timeWindowMinutes}
                      onChange={(e) => setTimeWindowMinutes(Number(e.target.value))}
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-700">Threshold (max events)</label>
                    <Input
                      type="number"
                      min={1}
                      value={threshold}
                      onChange={(e) => setThreshold(Number(e.target.value))}
                      className="mt-1"
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div className="space-y-3 rounded-lg border border-slate-200 p-4">
                <p className="text-sm text-slate-500">
                  Runs on a schedule, fetches recent events, and sends them to an LLM for fraud pattern analysis.
                </p>
                <div>
                  <label className="text-sm font-medium text-slate-700">Prompt Template</label>
                  <textarea
                    value={promptTemplate}
                    onChange={(e) => setPromptTemplate(e.target.value)}
                    placeholder="e.g. Analyze these purchase events for card-testing patterns..."
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm min-h-[100px]"
                    required
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-sm font-medium text-slate-700">Time Window (minutes)</label>
                    <p className="text-xs text-slate-400 mt-0.5">How far back to fetch events for each evaluation</p>
                    <Input
                      type="number"
                      min={1}
                      value={timeWindowMinutes}
                      onChange={(e) => setTimeWindowMinutes(Number(e.target.value))}
                      className="mt-1"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-700">Evaluation Interval (minutes)</label>
                    <p className="text-xs text-slate-400 mt-0.5">How often the LLM re-evaluates the event batch</p>
                    <Input
                      type="number"
                      min={1}
                      value={evaluationIntervalMinutes}
                      onChange={(e) => setEvaluationIntervalMinutes(Number(e.target.value))}
                      className="mt-1"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* Dependencies */}
            {availableRules.filter(r => r.id !== rule?.id && r.eventType === eventType).length > 0 && (
              <div className="space-y-3 rounded-lg border border-slate-200 p-4">
                <label className="text-sm font-medium text-slate-700">Dependencies (optional)</label>
                <p className="text-xs text-slate-400">
                  This rule will only fire if its dependency rules also match the event.
                </p>
                <div className="flex gap-2 mb-2">
                  <button
                    type="button"
                    onClick={() => setDependencyCondition('ALL')}
                    className={`px-3 py-1 rounded text-xs font-medium border transition-colors ${
                      dependencyCondition === 'ALL'
                        ? 'bg-slate-900 text-white border-slate-900'
                        : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    ALL must match
                  </button>
                  <button
                    type="button"
                    onClick={() => setDependencyCondition('ANY')}
                    className={`px-3 py-1 rounded text-xs font-medium border transition-colors ${
                      dependencyCondition === 'ANY'
                        ? 'bg-slate-900 text-white border-slate-900'
                        : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                    }`}
                  >
                    ANY must match
                  </button>
                </div>
                <div className="space-y-1 max-h-32 overflow-y-auto">
                  {availableRules
                    .filter(r => r.id !== rule?.id && r.eventType === eventType)
                    .map(r => (
                      <label key={r.id} className="flex items-center gap-2 text-sm cursor-pointer">
                        <input
                          type="checkbox"
                          checked={dependsOn.includes(r.id)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setDependsOn([...dependsOn, r.id]);
                            } else {
                              setDependsOn(dependsOn.filter(id => id !== r.id));
                            }
                          }}
                          className="rounded"
                        />
                        <span>{r.name}</span>
                        <span className="text-xs text-slate-400">({r.ruleType})</span>
                      </label>
                    ))}
                </div>
              </div>
            )}

            {validationErrors.length > 0 && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3">
                <p className="text-sm font-medium text-red-800 mb-1">Validation Errors</p>
                <ul className="text-sm text-red-700 list-disc list-inside space-y-0.5">
                  {validationErrors.map((err, i) => (
                    <li key={i}>
                      <span className="font-medium">{err.field}</span>: {err.message}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={onCancel}>
                Cancel
              </Button>
              <Button type="submit" disabled={!isValid || validating}>
                {validating ? 'Validating...' : rule ? 'Update' : 'Create'}
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
