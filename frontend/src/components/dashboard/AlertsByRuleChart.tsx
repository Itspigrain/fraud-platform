// frontend/src/components/dashboard/AlertsByRuleChart.tsx

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { AggBucket } from '@/lib/types';

export function AlertsByRuleChart({ data }: { data: AggBucket[] }) {
  const chartData = data.map((d) => ({
    rule: String(d.key),
    count: d.count,
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium">Alerts by Rule</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={250}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="rule" fontSize={12} />
            <YAxis fontSize={12} />
            <Tooltip />
            <Bar dataKey="count" fill="#f59e0b" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
