// frontend/src/pages/DashboardPage.tsx

import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { fetchEventStats, fetchAlertStats } from '@/lib/api';
import { StatCard } from '@/components/dashboard/StatCard';
import { EventsOverTimeChart } from '@/components/dashboard/EventsOverTimeChart';
import { AlertsByRuleChart } from '@/components/dashboard/AlertsByRuleChart';
import { RiskDistributionChart } from '@/components/dashboard/RiskDistributionChart';
import type { EventStatsResponse, AlertStatsResponse } from '@/lib/types';

export function DashboardPage() {
  const [eventStats, setEventStats] = useState<EventStatsResponse | null>(null);
  const [alertStats, setAlertStats] = useState<AlertStatsResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    setLoading(true);
    try {
      const [es, as] = await Promise.all([fetchEventStats(), fetchAlertStats()]);
      setEventStats(es);
      setAlertStats(as);
    } catch (err) {
      toast.error('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div className="grid grid-cols-4 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-lg" />
          ))}
        </div>
        <div className="grid grid-cols-3 gap-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-72 rounded-lg" />
          ))}
        </div>
      </div>
    );
  }

  if (!eventStats || !alertStats) return null;

  const totalEvents = eventStats.aggregations.eventsOverTime.reduce((s, b) => s + b.count, 0);
  const totalAlerts = alertStats.aggregations.alertsOverTime.reduce((s, b) => s + b.count, 0);
  const riskDist = eventStats.aggregations.riskScoreDistribution;
  const totalForAvg = riskDist.reduce((s, b) => s + b.count, 0);
  const weightedSum = riskDist.reduce((s, b) => s + Number(b.key) * b.count, 0);
  const avgRisk = totalForAvg > 0 ? (weightedSum / totalForAvg).toFixed(1) : '0';
  const todayKey = new Date().toISOString().slice(0, 10);
  const eventsToday = eventStats.aggregations.eventsOverTime
    .find((b) => String(b.key).startsWith(todayKey))?.count ?? 0;

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-end">
        <Button variant="outline" size="sm" onClick={loadData}>Refresh</Button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <StatCard title="Total Events" value={totalEvents} />
        <StatCard title="Total Alerts" value={totalAlerts} />
        <StatCard title="Avg Risk Score" value={avgRisk} />
        <StatCard title="Events Today" value={eventsToday} />
      </div>

      <div className="grid grid-cols-3 gap-4">
        <EventsOverTimeChart data={eventStats.aggregations.eventsOverTime} />
        <AlertsByRuleChart data={alertStats.aggregations.countByRule} />
        <RiskDistributionChart data={eventStats.aggregations.riskScoreDistribution} />
      </div>
    </div>
  );
}
