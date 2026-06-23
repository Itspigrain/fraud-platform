// frontend/src/App.tsx

import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { Toaster } from 'sonner';
import { Sidebar } from './components/layout/Sidebar';
import { Header } from './components/layout/Header';
import { DashboardPage } from './pages/DashboardPage';
import { EventsPage } from './pages/EventsPage';
import { AlertsPage } from './pages/AlertsPage';
import { RulesPage } from './pages/RulesPage';
import { RuleResultsPage } from './pages/RuleResultsPage';
import { SchemasPage } from './pages/SchemasPage';

const pageTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/schemas': 'Event Schemas',
  '/events': 'Events',
  '/alerts': 'Alerts',
  '/rules': 'Rules',
};

function Layout() {
  const location = useLocation();
  const title = pageTitles[location.pathname]
    || (location.pathname.startsWith('/rules/') ? 'Rule Results' : 'Fraud Ops');

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header title={title} />
        <main className="flex-1 overflow-auto">
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/schemas" element={<SchemasPage />} />
            <Route path="/events" element={<EventsPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="/rules/:id/results" element={<RuleResultsPage />} />
          </Routes>
        </main>
      </div>
      <Toaster position="top-right" />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Layout />
    </BrowserRouter>
  );
}
