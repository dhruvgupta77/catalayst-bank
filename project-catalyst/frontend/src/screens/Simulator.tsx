import { Card } from '../components/ui'
import { rand } from '../format'
import { useStore } from '../store'

const icons: Record<string, string> = { SALARY: '💼', BONUS: '🎁', TWO_POT_PAYOUT: '🏦', TAX_REFUND: '🧾', INSURANCE_PAYOUT: '🛡️' }
const names: Record<string, string> = { TWO_POT_PAYOUT: 'Two-Pot' }

export default function Simulator() {
  const { scenarios, trigger } = useStore()
  return (
    <div className="space-y-6">
      <Card className="p-6">
        <h2 className="text-lg font-semibold">Liquidity Event Simulator</h2>
        <p className="mt-1 text-sm text-slate-600">
          A liquidity event is any meaningful inbound transaction. Pick one: the same pipeline detects it,
          understands the customer, and prepares options. Different events, different recommendations.
        </p>
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {scenarios.map(s => (
            <button key={s.type} onClick={() => trigger(s.type)}
              className="group rounded-2xl bg-slate-50 p-4 text-left ring-1 ring-slate-200 transition hover:-translate-y-0.5 hover:bg-brand-50 hover:ring-brand-500">
              <div className="text-2xl">{icons[s.type]}</div>
              <div className="mt-3 font-semibold">{names[s.type] ?? s.label}</div>
              <div className="text-lg font-bold tabular-nums text-brand-700">{rand(s.amount)}</div>
              <div className="mt-1 text-xs text-slate-500">from {s.source.replace('_', ' ').toLowerCase()}</div>
            </button>
          ))}
        </div>
      </Card>
      <Card className="p-6">
        <h3 className="font-semibold">The pattern behind every event</h3>
        <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
          {['Detect', 'Understand', 'Decide', 'Recommend', 'Consent', 'Act', 'Audit'].map((s, i, a) => (
            <span key={s} className="flex items-center gap-2">
              <span className="rounded-full bg-ink px-3 py-1 text-white">{s}</span>
              {i < a.length - 1 && <span className="text-slate-400">→</span>}
            </span>
          ))}
        </div>
        <p className="mt-4 text-sm text-slate-600">Two-Pot is the opening story. Liquidity events are the platform.</p>
      </Card>
    </div>
  )
}