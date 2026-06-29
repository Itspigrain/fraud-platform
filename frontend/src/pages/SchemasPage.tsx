import { useEffect, useState, useCallback } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { SchemaFormDialog } from '@/components/schemas/SchemaFormDialog';
import { fetchSchemas, createSchema, updateSchema, deleteSchema } from '@/lib/api';
import type { SchemaResponse, SchemaRequest } from '@/lib/types';

export function SchemasPage() {
  const [schemas, setSchemas] = useState<SchemaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingSchema, setEditingSchema] = useState<SchemaResponse | null>(null);

  const loadSchemas = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchSchemas();
      setSchemas(data);
    } catch {
      toast.error('Failed to load schemas');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadSchemas(); }, [loadSchemas]);

  const handleSave = async (request: SchemaRequest) => {
    try {
      if (editingSchema) {
        await updateSchema(editingSchema.eventType, request);
        toast.success('Schema updated');
      } else {
        await createSchema(request);
        toast.success('Schema created');
      }
      setShowForm(false);
      setEditingSchema(null);
      loadSchemas();
    } catch {
      toast.error('Failed to save schema');
    }
  };

  const handleDelete = async (schema: SchemaResponse) => {
    if (!confirm(`Delete schema "${schema.eventType}"?`)) return;
    try {
      await deleteSchema(schema.eventType);
      toast.success('Schema deleted');
      loadSchemas();
    } catch {
      toast.error('Failed to delete schema');
    }
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex justify-between items-center">
        <div />
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={loadSchemas}>Refresh</Button>
          <Button size="sm" onClick={() => { setEditingSchema(null); setShowForm(true); }}>
            + New Schema
          </Button>
        </div>
      </div>

      {loading ? (
        <Skeleton className="h-96 rounded-lg" />
      ) : schemas.length === 0 ? (
        <div className="text-center text-slate-500 py-12">
          No schemas defined yet. Create one to start ingesting events.
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {schemas.map((schema) => (
            <div key={schema.id} className="bg-white border rounded-lg p-4 space-y-3">
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="font-semibold text-sm">{schema.displayName || schema.eventType}</h3>
                  <p className="text-xs text-slate-500 font-mono">{schema.eventType}</p>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="sm"
                    onClick={() => { setEditingSchema(schema); setShowForm(true); }}>
                    Edit
                  </Button>
                  <Button variant="ghost" size="sm" className="text-red-600"
                    onClick={() => handleDelete(schema)}>
                    Delete
                  </Button>
                </div>
              </div>
              {schema.description && (
                <p className="text-xs text-slate-600">{schema.description}</p>
              )}
              <div className="space-y-1">
                <p className="text-xs font-medium text-slate-700">
                  {schema.fields.length} field{schema.fields.length !== 1 ? 's' : ''}
                </p>
                <div className="flex flex-wrap gap-1">
                  {schema.fields.map((f) => (
                    <span key={f.name} className={`text-xs px-2 py-0.5 rounded-full ${
                      f.required ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-600'
                    }`}>
                      {f.name}: {f.type}
                    </span>
                  ))}
                </div>
              </div>
              <p className="text-xs text-slate-400">
                Updated {new Date(schema.updatedAt).toLocaleDateString()}
              </p>
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <SchemaFormDialog
          schema={editingSchema}
          onSave={handleSave}
          onCancel={() => { setShowForm(false); setEditingSchema(null); }}
        />
      )}
    </div>
  );
}
