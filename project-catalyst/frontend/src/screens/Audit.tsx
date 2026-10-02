import { Badge, Card } from '../components/ui'
import { auditLabel, clock } from '../format'
import { useStore } from '../store'

const tone = (r: string) => (r === 'OK' ? 'green' : r === 'DEGRADED' || r === 'IGNORED' ? 'amber' : 'red') as 'green' | 'amber' | 'red'
const actorTone = (a: string) => (a === 'CUSTOMER' ? 'blue' : a === 'AI_AGENT' ? 'violet' : 'slate') as 'blue' | 'violet' | 'slate'

export default function Audit() {
  const { timeline } = useStore()
  if (!timeline) return <Card className="p-8 text-slate-500">Trigger an event to see its audit trail.</Card>
  return (
    <Card className="p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold">Decision journey</h2>
        <span className="text-xs text-slate-500">event {timeline.eventId} · correlation {timeline.entries[0]?.correlationId}</span>
      </div>
      <ol className="mt-6 space-y-0 border-l-2 border-slate-200 pl-6">
        {timeline.entries.map(e => (
          <li key={e.seq} className="relative pb-6 slide-in">
            <span className={`absolute -left-[33px] top-1 h-3 w-3 rounded-full ring-4 ring-white ${e.result === 'OK' ? 'bg-emerald-500' : e.result === 'FAILURE' || e.result === 'DENIED' ? 'bg-rose-500' : 'bg-amber-400'}`} />
            <div className="flex flex-wrap items-center gap-2">
              <b className="text-sm">{auditLabel[e.action] ?? e.action}</b><code className="text-[10px] text-slate-400">{e.action}</code>
              <Badge tone={actorTone(e.actor)}>{e.actor}</Badge>
              <Badge tone={tone(e.result)}>{e.result}</Badge>
              <span className="font-mono text-xs text-slate-500">{clock(e.timestamp)} (+{e.offsetMs} ms)</span>
            </div>
            <div className="mt-1 text-sm text-slate-600">{e.detail}</div>
          </li>
        ))}
      </ol>
    </Card>
  )
}