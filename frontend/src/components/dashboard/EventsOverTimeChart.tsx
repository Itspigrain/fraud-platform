// frontend/src/components/dashboard/EventsOverTimeChart.tsx

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { AggBucket } from '@/lib/types';

export function EventsOverTimeChart({ data }: { data: AggBucket[] }) {
  const chartData = data.map((d) => ({
    date: new Date(d.key).toLocaleDateString(),
    count: d.count,
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium">Events Over Time</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="date" fontSize={12} />
            <YAxis fontSize={12} />
            <Tooltip />
            <Line type="monotone" dataKey="count" stroke="#3b82f6" strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
