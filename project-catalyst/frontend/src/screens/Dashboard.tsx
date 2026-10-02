import { Badge, Button, Card } from '../components/ui'
import { rand } from '../format'
import { useStore } from '../store'

export default function Dashboard() {
  const { customer, recent, stage, reco, setTab } = useStore()
  if (!customer) return <Card className="p-8 text-slate-500">Connecting to Catalyst Bank…</Card>
  const bal = (t: string) => customer.accounts.find(a => a.type === t)?.balance ?? 0
  const h = customer.health
  const last = recent[0]
  const covColor = h.emergencyCoveragePercent >= 100 ? 'bg-emerald-500' : h.emergencyCoveragePercent >= 50 ? 'bg-amber-400' : 'bg-rose-400'
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-6 lg:col-span-2">
        <Card className="overflow-hidden">
          <div className="bg-gradient-to-br from-ink to-brand-700 p-6 text-white">
            <div className="text-sm text-brand-100">Hello, {customer.name.split(' ')[0]}</div>
            <div className="mt-4 text-xs uppercase tracking-wide text-brand-100">Current account</div>
            <div className="text-4xl font-bold tabular-nums">{rand(bal('CURRENT'))}</div>
          </div>
          <div className="grid grid-cols-2 divide-x divide-slate-100 sm:grid-cols-4">
            {[['Emergency savings', 'SAVINGS'], ['Investments', 'INVESTMENT'], ['Credit card owed', 'CREDIT_CARD'], ['Personal loan', 'PERSONAL_LOAN']].map(([l, t]) => (
              <div key={t} className="p-4">
                <div className="text-xs text-slate-500">{l}</div>
                <div className="mt-1 font-semibold tabular-nums">{rand(bal(t))}</div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-6">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Recent liquidity event</h2>
            {last && <Badge tone={last.status === 'RECOMMENDED' ? 'green' : 'blue'}>{last.status.toLowerCase()}</Badge>}
          </div>
          {last ? (
            <div className="mt-4 flex items-center justify-between rounded-xl bg-brand-50 p-4">
              <div>
                <div className="text-sm text-slate-600">{last.label}</div>
                <div className="text-2xl font-bold tabular-nums text-brand-700">+{rand(last.amount)}</div>
              </div>
              {['review', 'approve'].includes(stage) && <Button onClick={() => setTab('journey')}>Review your options</Button>}
              {stage === 'done' && <Badge tone="green">Actions completed</Badge>}
            </div>
          ) : (
            <p className="mt-3 text-sm text-slate-500">No recent inflows. Use the Event Simulator to send one.</p>
          )}
        </Card>

        {reco && ['review', 'approve'].includes(stage) && (
          <Card className="p-6">
            <h2 className="font-semibold">Options you may want to consider</h2>
            <ul className="mt-3 space-y-2">
              {reco.actions.map(a => (
                <li key={a.id} className="flex justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                  <span>{a.reason}</span><b className="tabular-nums">{rand(a.amount)}</b>
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>

      <Card className="h-fit p-6">
        <h2 className="font-semibold">Financial health</h2>
        <div className="mt-4">
          <div className="flex justify-between text-sm"><span>Emergency fund</span><b>{h.emergencyCoveragePercent}% of target</b></div>
          <div className="mt-2 h-2 rounded-full bg-slate-100">
            <div className={`h-2 rounded-full ${covColor}`} style={{ width: `${Math.min(100, h.emergencyCoveragePercent)}%` }} />
          </div>
        </div>
        <dl className="mt-5 space-y-3 text-sm">
          <div className="flex justify-between"><dt className="text-slate-500">High-interest debt</dt><dd className="font-semibold tabular-nums">{rand(h.highInterestDebt)}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Total debt</dt><dd className="font-semibold tabular-nums">{rand(h.totalDebt)}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Credit score</dt><dd className="font-semibold">{h.creditScore}</dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Risk profile</dt><dd><Badge>{h.riskProfile.toLowerCase()}</Badge></dd></div>
          <div className="flex justify-between"><dt className="text-slate-500">Monthly salary</dt><dd className="font-semibold tabular-nums">{rand(customer.monthlySalary)}</dd></div>
        </dl>
      </Card>
    </div>
  )
}