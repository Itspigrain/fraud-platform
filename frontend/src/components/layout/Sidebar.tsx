// frontend/src/components/layout/Sidebar.tsx

import { NavLink } from 'react-router-dom';
import { useState } from 'react';

const navItems = [
  { to: '/', label: 'Dashboard', icon: '▦' },
  { to: '/events', label: 'Events', icon: '⚡' },
  { to: '/alerts', label: 'Alerts', icon: '⚠' },
  { to: '/rules', label: 'Rules', icon: '⚙' },
];

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

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
    </aside>
  );
}
