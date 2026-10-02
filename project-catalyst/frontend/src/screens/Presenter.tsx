import { Badge, Button, Card } from '../components/ui'
import { clock } from '../format'
import { useStore } from '../store'

const pipeline = [
  ['EVENT_RECEIVED', 'Liquidity Event Received'],
  ['CONTEXT_ENRICHED', 'Customer Context Retrieved'],
  ['RULES_EVALUATED', 'Rules Evaluated'],
  ['RECOMMENDATION_GENERATED', 'Recommendation Generated'],
  ['RECOMMENDATION_PUBLISHED', 'Mobile Experience Updated'],
]

export default function Presenter() {
  const { timeline, status, setToggles, duplicate, reset, notice, reco, stage, pipeline: trace } = useStore()
  const byAction = new Map(timeline?.entries.map(e => [e.action, e]))
  const declined = stage === 'declined'
  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <Card className="p-6 lg:col-span-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">Demo Processing Timeline</h2>
          {timeline && (
            <div className="flex items-center gap-2 text-sm">
              <span className="text-slate-500">target ≤ {timeline.latencyTargetMs} ms (configurable)</span>
              {timeline.msToRecommendation >= 0 && (
                <Badge tone={timeline.withinTarget ? 'green' : 'amber'}>{timeline.msToRecommendation} ms measured</Badge>
              )}
            </div>
          )}
        </div>
        <p className="mt-1 text-xs text-slate-500">Measured on this local demo run. It is not a production latency claim.</p>
        <ol className="mt-6 space-y-4">
          {pipeline.map(([k, label]) => {
            const e = byAction.get(k)
            return (
              <li key={k} className={`flex items-center gap-4 transition ${e ? 'opacity-100' : 'opacity-30'}`}>
                <span className="w-28 font-mono text-sm tabular-nums text-slate-600">{e ? clock(e.timestamp) : '--:--:--.---'}</span>
                <span className={`h-3 w-3 rounded-full ${e ? 'bg-brand-500' : 'bg-slate-300'}`} />
                <span className="flex-1 font-medium">{label}</span>
                <span className="text-xs text-slate-500">{e ? `+${e.offsetMs} ms` : ''}</span>
              </li>
            )
          })}
        </ol>
        {timeline && (
          <div className="mt-6 h-2 rounded-full bg-slate-100">
            <div className={`h-2 rounded-full ${timeline.withinTarget ? 'bg-brand-500' : 'bg-amber-400'}`}
              style={{ width: `${Math.min(100, Math.max(0, timeline.msToRecommendation) / timeline.latencyTargetMs * 100)}%` }} />
          </div>
        )}
        {notice && <div className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">🔁 {notice}</div>}
        {declined && <div className="mt-5 rounded-xl bg-slate-100 px-4 py-3 text-sm">Recommendation declined. No financial action executed.</div>}
        {reco?.reducedContext && (
          <div className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">
            {reco.contextNotes.join(' → ')}
          </div>
        )}
        {reco?.source === 'RULES_FALLBACK' && (
          <div className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">AI unavailable → deterministic rules fallback used.</div>
        )}
      </Card>

      <div className="space-y-6">
        {trace && (
          <Card className="p-6">
            <h3 className="font-semibold">Event trace</h3>
            <p className="mt-1 break-all font-mono text-[11px] text-slate-500">{trace.eventId} · {trace.correlationId}</p>
            <ul className="mt-3 space-y-2 text-xs">
              {trace.stages.map(s => (
                <li key={s.key} className="flex items-center justify-between gap-2">
                  <span className="font-medium">{s.label}</span>
                  <span className="text-slate-500">{s.state.toLowerCase()}{s.atMs != null ? ` · +${s.atMs} ms` : ""}</span>
                </li>
              ))}
            </ul>
            <h4 className="mt-4 text-xs font-semibold uppercase tracking-wide text-slate-500">Agents</h4>
            <ul className="mt-2 space-y-1 text-xs">
              {trace.agents.map(a => <li key={a.name} className="flex justify-between gap-2"><span>{a.name}</span><b>{a.kind === "AI" ? "AI" : "rules"}</b></li>)}
            </ul>
          </Card>
        )}
        <Card className="p-6">
          <h3 className="font-semibold">Failure demonstrations</h3>
          <div className="mt-4 space-y-3 text-sm">
            <label className="flex cursor-pointer items-center justify-between rounded-xl bg-slate-50 p-3">
              <span>Credit bureau down</span>
              <input type="checkbox" className="h-4 w-4 accent-amber-600" checked={status?.creditBureauDown ?? false}
                onChange={e => setToggles({ creditBureauDown: e.target.checked })} />
            </label>
            <label className="flex cursor-pointer items-center justify-between rounded-xl bg-slate-50 p-3">
              <span>Compliance rule fails (blocks action)</span>
              <input type="checkbox" className="h-4 w-4 accent-rose-600" checked={status?.complianceBlock ?? false}
                onChange={e => setToggles({ complianceBlock: e.target.checked })} />
            </label>
            <label className="flex cursor-pointer items-center justify-between rounded-xl bg-slate-50 p-3">
              <span>AI unavailable</span>
              <input type="checkbox" className="h-4 w-4 accent-amber-600" checked={status?.aiDown ?? false}
                onChange={e => setToggles({ aiDown: e.target.checked })} />
            </label>
            <Button variant="outline" className="w-full" onClick={duplicate}>Send duplicate event</Button>
            <p className="text-xs text-slate-500">Decline, Modify (try an amount above the limit for a policy block) and missing consent are shown on the Demo Story screen and via the API.</p>
            <Button variant="danger" className="w-full" onClick={reset}>Reset demo data</Button>
          </div>
        </Card>
        <Card className="p-6">
          <h3 className="font-semibold">Platform</h3>
          {status && (
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-slate-500">Event bus</dt><dd>{status.bus === 'kafka' ? 'Kafka' : 'In-memory (offline)'}</dd></div>
              <div className="flex justify-between gap-4"><dt className="text-slate-500">AI</dt><dd className="text-right">{status.aiSource}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-500">Profiles</dt><dd>{status.profiles.join(', ') || 'default'}</dd></div>
            </dl>
          )}
          <h4 className="mt-5 text-xs font-semibold uppercase tracking-wide text-slate-500">Demo metrics</h4>
          <ul className="mt-2 space-y-1 text-xs">
            {status && Object.entries(status.metrics).map(([k, v]) => (
              <li key={k} className="flex justify-between font-mono"><span>{k}</span><b>{v}</b></li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  )
}