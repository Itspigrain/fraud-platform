import { useEffect, useState, useCallback } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { ConnectorsTable } from '@/components/connectors/ConnectorsTable';
import { ConnectorFormDialog } from '@/components/connectors/ConnectorFormDialog';
import { fetchConnectors, fetchRules, createConnector, updateConnector, deleteConnector, testConnector } from '@/lib/api';
import type { ConnectorResponse, ConnectorRequest, RuleResponse } from '@/lib/types';

export function ConnectorsPage() {
  const [connectors, setConnectors] = useState<ConnectorResponse[]>([]);
  const [rules, setRules] = useState<RuleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingConnector, setEditingConnector] = useState<ConnectorResponse | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [c, r] = await Promise.all([fetchConnectors(), fetchRules()]);
      setConnectors(c);
      setRules(r);
    } catch {
      toast.error('Failed to load connectors');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSave = async (request: ConnectorRequest) => {
    try {
      if (editingConnector) {
        await updateConnector(editingConnector.id, request);
        toast.success('Connector updated');
      } else {
        await createConnector(request);
        toast.success('Connector created');
      }
      setShowForm(false);
      setEditingConnector(null);
      loadData();
    } catch {
      toast.error('Failed to save connector');
    }
  };

  const handleDelete = async (connector: ConnectorResponse) => {
    if (!confirm(`Delete connector "${connector.name}"?`)) return;
    try {
      await deleteConnector(connector.id);
      toast.success('Connector deleted');
      loadData();
    } catch {
      toast.error('Failed to delete connector');
    }
  };

  const handleToggleStatus = async (connector: ConnectorResponse) => {
    try {
      const newStatus = connector.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      await updateConnector(connector.id, {
        name: connector.name,
        config: connector.config,
        ruleIds: connector.ruleIds,
        status: newStatus,
      });
      toast.success(`Connector ${newStatus === 'ACTIVE' ? 'activated' : 'deactivated'}`);
      loadData();
    } catch {
      toast.error('Failed to update connector status');
    }
  };

  const handleTest = async (connector: ConnectorResponse) => {
    try {
      const result = await testConnector(connector.id);
      if (result.success) {
        toast.success(`Webhook OK — ${result.statusCode} in ${result.responseTimeMs}ms`);
      } else {
        toast.error(`Webhook failed — ${result.error || `status ${result.statusCode}`}`);
      }
    } catch {
      toast.error('Failed to test connector');
    }
  };

  const handleEdit = (connector: ConnectorResponse) => {
    setEditingConnector(connector);
    setShowForm(true);
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex justify-between items-center">
        <div />
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={loadData}>Refresh</Button>
          <Button size="sm" onClick={() => { setEditingConnector(null); setShowForm(true); }}>
            + New Connector
          </Button>
        </div>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : (
        <ConnectorsTable
          connectors={connectors}
          rules={rules}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onToggleStatus={handleToggleStatus}
          onTest={handleTest}
        />
      )}

      {showForm && (
        <ConnectorFormDialog
          connector={editingConnector}
          onSave={handleSave}
          onCancel={() => { setShowForm(false); setEditingConnector(null); }}
        />
      )}
    </div>
  );
}
