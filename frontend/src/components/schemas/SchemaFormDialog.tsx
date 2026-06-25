import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { SchemaFieldDefinition, SchemaFieldType, SchemaRequest, SchemaResponse } from '@/lib/types';

const FIELD_TYPES: SchemaFieldType[] = [
  'KEYWORD', 'TEXT', 'INTEGER', 'LONG', 'DOUBLE',
  'BOOLEAN', 'DATE', 'IP', 'GEO_POINT',
];

interface SchemaFormDialogProps {
  schema?: SchemaResponse | null;
  onSave: (request: SchemaRequest) => void;
  onCancel: () => void;
}

export function SchemaFormDialog({ schema, onSave, onCancel }: SchemaFormDialogProps) {
  const [eventType, setEventType] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [description, setDescription] = useState('');
  const [fields, setFields] = useState<SchemaFieldDefinition[]>([
    { name: '', type: 'KEYWORD', required: false, description: null },
  ]);

  useEffect(() => {
    if (schema) {
      setEventType(schema.eventType);
      setDisplayName(schema.displayName || '');
      setDescription(schema.description || '');
      setFields(schema.fields.length ? schema.fields : [
        { name: '', type: 'KEYWORD', required: false, description: null },
      ]);
    }
  }, [schema]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const validFields = fields.filter(f => f.name.trim() !== '');
    onSave({
      eventType,
      displayName: displayName || undefined,
      description: description || undefined,
      fields: validFields,
    });
  };

  const addField = () => {
    setFields([...fields, { name: '', type: 'KEYWORD', required: false, description: null }]);
  };

  const removeField = (index: number) => {
    setFields(fields.filter((_, i) => i !== index));
  };

  const updateField = (index: number, updates: Partial<SchemaFieldDefinition>) => {
    setFields(fields.map((f, i) => i === index ? { ...f, ...updates } : f));
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <Card className="w-full max-w-2xl max-h-[90vh] overflow-auto">
        <CardHeader>
          <CardTitle>{schema ? 'Edit Schema' : 'New Schema'}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium">Event Type</label>
                <Input
                  value={eventType}
                  onChange={(e) => setEventType(e.target.value)}
                  placeholder="e.g. purchase"
                  disabled={!!schema}
                  required
                />
              </div>
              <div>
                <label className="text-sm font-medium">Display Name</label>
                <Input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="e.g. Purchase Event"
                />
              </div>
            </div>

            <div>
              <label className="text-sm font-medium">Description</label>
              <Input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description"
              />
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <label className="text-sm font-medium">Fields</label>
                <Button type="button" variant="outline" size="sm" onClick={addField}>
                  + Add Field
                </Button>
              </div>

              {fields.map((field, index) => (
                <div key={index} className="flex gap-2 items-center">
                  <Input
                    className="flex-1"
                    value={field.name}
                    onChange={(e) => updateField(index, { name: e.target.value })}
                    placeholder="Field name"
                  />
                  <select
                    className="h-9 rounded-md border px-3 text-sm"
                    value={field.type}
                    onChange={(e) => updateField(index, { type: e.target.value as SchemaFieldType })}
                  >
                    {FIELD_TYPES.map((t) => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                  <label className="flex items-center gap-1 text-sm whitespace-nowrap">
                    <input
                      type="checkbox"
                      checked={field.required}
                      onChange={(e) => updateField(index, { required: e.target.checked })}
                    />
                    Required
                  </label>
                  <Button type="button" variant="ghost" size="sm"
                    onClick={() => removeField(index)} className="text-red-500">
                    x
                  </Button>
                </div>
              ))}
            </div>

            <div className="flex gap-2 justify-end pt-2">
              <Button type="button" variant="outline" onClick={onCancel}>Cancel</Button>
              <Button type="submit">{schema ? 'Update' : 'Create'}</Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
