// frontend/src/components/dashboard/RiskDistributionChart.tsx

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import type { AggBucket } from '@/lib/types';

const COLORS = ['#22c55e', '#eab308', '#f97316', '#ef4444'];

export function RiskDistributionChart({ data }: { data: AggBucket[] }) {
  const chartData = data
    .filter((d) => d.count > 0)
    .map((d) => ({
      name: `Score ${d.key}`,
      value: d.count,
    }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium">Risk Score Distribution</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={250}>
          <PieChart>
            <Pie data={chartData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} label>
              {chartData.map((_, i) => (
                <Cell key={i} fill={COLORS[i % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
