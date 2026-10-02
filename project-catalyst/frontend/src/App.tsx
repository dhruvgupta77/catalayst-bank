import { StoreProvider, useStore, type Tab } from './store'
import { PrincipleBanner } from './components/ui'
import Story from './screens/Story'
import Dashboard from './screens/Dashboard'
import Simulator from './screens/Simulator'
import Journey from './screens/Journey'
import Audit from './screens/Audit'
import Presenter from './screens/Presenter'

const tabs: { id: Tab; label: string }[] = [
  { id: 'story', label: 'Demo Story' },
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'simulate', label: 'Event Simulator' },
  { id: 'journey', label: 'Your Options' },
  { id: 'audit', label: 'Audit Trail' },
  { id: 'presenter', label: 'Presenter View' },
]

function Shell() {
  const { tab, setTab, customer, error, stage } = useStore()
  return (
    <div className="mx-auto max-w-6xl px-4 pb-16">
      <header className="flex flex-wrap items-center justify-between gap-3 py-5">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand-600 text-lg font-black text-white">C</div>
          <div>
            <div className="text-lg font-bold leading-tight">Catalyst Bank</div>
            <div className="text-xs text-slate-500">Every meaningful inflow is a decision moment.</div>
          </div>
        </div>
        {customer && (
          <div className="flex items-center gap-2 rounded-full bg-white px-3 py-1.5 text-sm shadow-sm ring-1 ring-slate-200">
            <span className="grid h-7 w-7 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">
              {customer.name.split(' ').map(p => p[0]).join('')}
            </span>
            {customer.name}
          </div>
        )}
      </header>
      <PrincipleBanner />
      <nav className="mt-4 flex flex-wrap gap-1 rounded-2xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
        {tabs.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`relative rounded-xl px-4 py-2 text-sm font-medium transition ${tab === t.id ? 'bg-ink text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
            {t.label}
            {t.id === 'journey' && ['review', 'approve'].includes(stage) && (
              <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-amber-400 ring-2 ring-white" />
            )}
          </button>
        ))}
      </nav>
      {error && <div className="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-800 ring-1 ring-rose-200">{error}</div>}
      <main className="mt-6">
        {tab === 'story' && <Story />}
        {tab === 'dashboard' && <Dashboard />}
        {tab === 'simulate' && <Simulator />}
        {tab === 'journey' && <Journey />}
        {tab === 'audit' && <Audit />}
        {tab === 'presenter' && <Presenter />}
      </main>
    </div>
  )
}

export default function App() {
  return (
    <StoreProvider>
      <Shell />
    </StoreProvider>
  )
}