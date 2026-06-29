// frontend/src/components/layout/Sidebar.tsx

import { NavLink } from 'react-router-dom';
import { useState } from 'react';
import { useTenant } from '@/lib/tenant-context';

const navItems = [
  { to: '/', label: 'Dashboard', icon: '▦' },
  { to: '/schemas', label: 'Schemas', icon: '◈' },
  { to: '/events', label: 'Events', icon: '⚡' },
  { to: '/alerts', label: 'Alerts', icon: '⚠' },
  { to: '/rules', label: 'Rules', icon: '⚙' },
];

function formatTenantLabel(id: string) {
  return id.replace(/^tenant-/, '').replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);
  const { tenantId, setTenantId, tenants, loading } = useTenant();

  return (
    <aside className={`bg-slate-900 text-white flex flex-col transition-all ${collapsed ? 'w-16' : 'w-56'}`}>
      <div className="flex items-center justify-between p-4 border-b border-slate-700">
        {!collapsed && <span className="font-bold text-lg">Fraud Ops</span>}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="text-slate-400 hover:text-white"
        >
          {collapsed ? '→' : '←'}
        </button>
      </div>
      <nav className="flex-1 py-4">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 text-sm transition-colors ${
                isActive
                  ? 'bg-slate-800 text-white border-r-2 border-blue-400'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`
            }
          >
            <span className="text-lg">{item.icon}</span>
            {!collapsed && <span>{item.label}</span>}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-slate-700 p-3">
        {collapsed ? (
          <div className="flex justify-center" title={tenantId}>
            <span className="text-xs text-slate-400">T</span>
          </div>
        ) : loading ? (
          <div className="text-xs text-slate-500">Loading tenants...</div>
        ) : (
          <>
            <label className="block text-[10px] uppercase tracking-wider text-slate-500 mb-1.5">Tenant</label>
            <select
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              className="w-full bg-slate-800 border border-slate-600 rounded-md px-2 py-1.5 text-sm text-slate-200 outline-none focus:border-blue-400 cursor-pointer"
            >
              {tenants.map((t) => (
                <option key={t} value={t}>
                  {formatTenantLabel(t)}
                </option>
              ))}
            </select>
          </>
        )}
      </div>
    </aside>
  );
}
