import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { RulesTable } from '@/components/rules/RulesTable';
import { RuleFormDialog } from '@/components/rules/RuleFormDialog';
import { fetchRules, createRule, updateRule, deleteRule } from '@/lib/api';
import type { RuleResponse, RuleRequest } from '@/lib/types';

export function RulesPage() {
  const navigate = useNavigate();
  const [rules, setRules] = useState<RuleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingRule, setEditingRule] = useState<RuleResponse | null>(null);

  const loadRules = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchRules();
      setRules(data);
    } catch {
      toast.error('Failed to load rules');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadRules(); }, [loadRules]);

  const handleSave = async (request: RuleRequest) => {
    try {
      if (editingRule) {
        await updateRule(editingRule.id, request);
        toast.success('Rule updated');
      } else {
        await createRule(request);
        toast.success('Rule created');
      }
      setShowForm(false);
      setEditingRule(null);
      loadRules();
    } catch {
      toast.error('Failed to save rule');
    }
  };

  const handleDelete = async (rule: RuleResponse) => {
    if (!confirm(`Delete rule "${rule.name}"?`)) return;
    try {
      await deleteRule(rule.id);
      toast.success('Rule deleted');
      loadRules();
    } catch {
      toast.error('Failed to delete rule');
    }
  };

  const handleToggleStatus = async (rule: RuleResponse) => {
    try {
      const newStatus = rule.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      await updateRule(rule.id, { eventType: rule.eventType, name: rule.name, conditions: rule.conditions, status: newStatus });
      toast.success(`Rule ${newStatus === 'ACTIVE' ? 'activated' : 'deactivated'}`);
      loadRules();
    } catch {
      toast.error('Failed to update rule status');
    }
  };

  const handleEdit = (rule: RuleResponse) => {
    setEditingRule(rule);
    setShowForm(true);
  };

  const handleViewResults = (rule: RuleResponse) => {
    navigate(`/rules/${rule.id}/results`);
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex justify-between items-center">
        <div />
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={loadRules}>Refresh</Button>
          <Button size="sm" onClick={() => { setEditingRule(null); setShowForm(true); }}>
            + New Rule
          </Button>
        </div>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : (
        <RulesTable
          rules={rules}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onToggleStatus={handleToggleStatus}
          onViewResults={handleViewResults}
        />
      )}

      {showForm && (
        <RuleFormDialog
          rule={editingRule}
          onSave={handleSave}
          onCancel={() => { setShowForm(false); setEditingRule(null); }}
        />
      )}
    </div>
  );
}
