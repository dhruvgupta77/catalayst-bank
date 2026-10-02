import { useEffect, useState } from 'react'
import { Badge, Button, Card } from '../components/ui'
import { actionLabel, rand } from '../format'
import { useStore } from '../store'
import type { ActionView, RecommendationView } from '../types'

const stepNames: Record<string, string> = {
  EVENT_RECEIVED: 'Liquidity event received',
  CONTEXT_ENRICHED: 'Customer context retrieved',
  RULES_EVALUATED: 'Rules evaluated',
  RECOMMENDATION_GENERATED: 'Recommendation generated',
  RECOMMENDATION_PUBLISHED: 'Your options are ready',
}

function Processing() {
  const { timeline, event } = useStore()
  const done = new Set(timeline?.entries.map(e => e.action) ?? [])
  return (
    <Card className="mx-auto max-w-xl p-8 text-center slide-in">
      <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-brand-100 border-t-brand-600" />
      <h2 className="mt-5 text-xl font-semibold">We spotted {event ? `your ${event.label.toLowerCase()}` : 'a new inflow'}</h2>
      <p className="mt-1 text-sm text-slate-500">Preparing options tailored to your situation…</p>
      <ul className="mx-auto mt-6 max-w-sm space-y-2 text-left text-sm">
        {Object.entries(stepNames).map(([k, v]) => (
          <li key={k} className={`flex items-center gap-2 ${done.has(k) ? 'text-ink' : 'text-slate-300'}`}>
            <span>{done.has(k) ? '✅' : '○'}</span>{v}
          </li>
        ))}
      </ul>
    </Card>
  )
}

export function WhyPanel({ reco }: { reco: RecommendationView }) {
  const sourceLabel = reco.source === 'RULES_FALLBACK' ? 'Rules-only wording (AI unavailable)'
    : reco.source === 'AI_LLM' ? 'AI language model' : 'AI (deterministic demo mode)'
  return (
    <Card className="p-6">
      <h3 className="font-semibold">Why we’re suggesting this</h3>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <div className="rounded-xl bg-slate-50 p-4">
          <div className="flex items-center gap-2 text-sm font-semibold"><Badge tone="blue">Rules</Badge> decided what is eligible</div>
          <ul className="mt-3 space-y-2 text-sm">
            {reco.rules.map(r => (
              <li key={r.ruleId} className="flex gap-2">
                <span>{r.passed ? '✅' : '⛔'}</span>
                <span><b className="text-xs text-slate-500">{r.ruleId}</b><br />{r.detail}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="rounded-xl bg-violet-50 p-4">
          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold">
            <Badge tone="violet">AI</Badge> explained and ranked them
            <span className="text-xs font-normal text-slate-500">{sourceLabel}</span>
          </div>
          <ul className="mt-3 list-disc space-y-2 pl-5 text-sm">
            {reco.narrative.reasoning.map((r, i) => <li key={i}>{r}</li>)}
          </ul>
          <div className="mt-3 text-xs text-slate-500">
            {reco.narrative.confidence != null
              ? `Self-reported wording confidence ${Math.round(reco.narrative.confidence * 100)}% (not a calibrated probability)`
              : 'No AI confidence: wording generated from rules only'}
            {' · '}AI cannot change amounts or move money
          </div>
        </div>
      </div>
      {reco.narrative.alternatives.length > 0 && (
        <div className="mt-4 text-sm text-slate-600"><b>Other choices:</b> {reco.narrative.alternatives.join(' ')}</div>
      )}
    </Card>
  )
}

export function Degradation({ reco }: { reco: RecommendationView }) {
  return (
    <>
      {reco.reducedContext && (
        <div className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">
          {reco.contextNotes.map(n => <div key={n}>⚠️ {n}</div>)}
        </div>
      )}
      {reco.source === 'RULES_FALLBACK' && (
        <div className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">
          ⚠️ AI assistance is unavailable, so these options use deterministic rules only.
        </div>
      )}
    </>
  )
}

function Review({ reco, selected, toggle }: { reco: RecommendationView; selected: Set<string>; toggle: (id: string) => void }) {
  const { setConfirming, decline } = useStore()
  const n = reco.actions.length
  return (
    <div className="space-y-6 slide-in">
      <Card className="p-6">
        <div className="text-sm text-slate-500">{reco.event.label}</div>
        <h2 className="mt-1 text-3xl font-bold">You received <span className="text-brand-700">{rand(reco.event.amount)}</span></h2>
        <p className="mt-2 text-slate-600">
          {n > 0 ? `We found ${n} ${n === 1 ? 'way' : 'ways'} this money could work harder for you. Here are some options you may want to consider.`
            : 'Nothing needs your attention right now. Keeping it in your account is perfectly fine.'}
        </p>
        <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">{reco.narrative.recommendation}</p>
      </Card>
      <Degradation reco={reco} />
      <div className="space-y-3">
        {reco.actions.map((a, i) => {
          const meta = actionLabel[a.type]
          const on = selected.has(a.id)
          return (
            <button key={a.id} onClick={() => toggle(a.id)}
              className={`flex w-full items-center gap-4 rounded-2xl bg-white p-5 text-left shadow-sm ring-2 transition ${on ? 'ring-brand-500' : 'ring-transparent'}`}>
              <div className="text-3xl">{meta.icon}</div>
              <div className="flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">{meta.title}</span>
                  {i === 0 && <Badge tone="green">Suggested first</Badge>}
                </div>
                <div className="mt-1 text-sm text-slate-600">{a.reason} · {a.productName}</div>
                {a.impact && <div className="mt-1 text-xs text-slate-500">{a.impact}</div>}
              </div>
              <div className="text-right">
                <div className="text-xl font-bold tabular-nums">{rand(a.amount)}</div>
                <div className={`mt-1 text-xs ${on ? 'text-brand-700' : 'text-slate-400'}`}>{on ? '✓ included' : 'not included'}</div>
              </div>
            </button>
          )
        })}
      </div>
      <WhyPanel reco={reco} />
      <div className="flex flex-wrap gap-3">
        <Button disabled={selected.size === 0} onClick={() => setConfirming(true)}>Review my selection ({selected.size})</Button>
        <Button variant="danger" onClick={decline}>Not now: keep my money as is</Button>
      </div>
    </div>
  )
}

function Approve({ reco, selected }: { reco: RecommendationView; selected: Set<string> }) {
  const { setConfirming, approve, customer } = useStore()
  const [ack, setAck] = useState(false)
  const chosen = reco.actions.filter(a => selected.has(a.id))
  const total = chosen.reduce((s, a) => s + a.amount, 0)
  const current = customer?.accounts.find(a => a.type === 'CURRENT')?.balance ?? 0
  return (
    <Card className="mx-auto max-w-2xl p-8 slide-in">
      <h2 className="text-2xl font-bold">You are about to make the following financial changes.</h2>
      <p className="mt-1 text-sm text-slate-500">Nothing happens until you confirm below. You can go back and change your mind.</p>
      <ul className="mt-6 divide-y divide-slate-100">
        {chosen.map(a => (
          <li key={a.id} className="flex items-center justify-between py-3">
            <div><div className="font-medium">{actionLabel[a.type].icon} {actionLabel[a.type].title}</div><div className="text-xs text-slate-500">From current account to {a.productName}</div></div>
            <b className="tabular-nums">{rand(a.amount)}</b>
          </li>
        ))}
      </ul>
      <div className="mt-4 rounded-xl bg-slate-50 p-4 text-sm">
        <div className="flex justify-between"><span>Total moved from your current account</span><b className="tabular-nums">{rand(total)}</b></div>
        <div className="mt-1 flex justify-between"><span>Current account after these changes</span><b className="tabular-nums">{rand(current - total)}</b></div>
      </div>
      <label className="mt-6 flex cursor-pointer items-start gap-3 text-sm">
        <input type="checkbox" className="mt-1 h-4 w-4 accent-emerald-600" checked={ack} onChange={e => setAck(e.target.checked)} />
        <span>I understand these changes and I choose to approve them. Returns on investments are not guaranteed.</span>
      </label>
      <div className="mt-6 flex gap-3">
        <Button disabled={!ack} onClick={() => approve([...selected])}>Yes, approve these changes</Button>
        <Button variant="ghost" onClick={() => setConfirming(false)}>Go back</Button>
      </div>
    </Card>
  )
}

export function ActionRow({ a }: { a: ActionView }) {
  const meta = actionLabel[a.type]
  const tone = a.status === 'EXECUTED' ? 'bg-emerald-500' : a.status === 'FAILED' ? 'bg-rose-500' : 'bg-brand-500'
  return (
    <li className="rounded-xl bg-slate-50 p-4">
      <div className="flex items-center justify-between">
        <span className="font-medium">{meta.icon} {meta.title}</span>
        <span className="flex items-center gap-2"><b className="tabular-nums">{rand(a.amount)}</b>
          <Badge tone={a.status === 'EXECUTED' ? 'green' : a.status === 'FAILED' ? 'red' : a.status === 'CANCELLED' ? 'slate' : 'blue'}>{a.status.toLowerCase()}</Badge></span>
      </div>
      {a.status !== 'CANCELLED' && (
        <>
          <div className="mt-3 h-2 rounded-full bg-slate-200"><div className={`h-2 rounded-full transition-all duration-500 ${tone}`} style={{ width: `${a.progress}%` }} /></div>
          <div className="mt-1 text-xs text-slate-500">{a.failureReason ?? a.progressNote ?? 'Waiting…'}</div>
        </>
      )}
      {a.status === 'CANCELLED' && <div className="mt-1 text-xs text-slate-500">Not included in your selection</div>}
    </li>
  )
}

export function Execution({ reco, finished }: { reco: RecommendationView; finished: boolean }) {
  const { setTab } = useStore()
  return (
    <Card className="mx-auto max-w-2xl p-8 slide-in">
      <h2 className="text-2xl font-bold">{finished ? 'All done. Here’s what you chose.' : 'Carrying out your approved changes…'}</h2>
      <p className="mt-1 text-sm text-slate-500">Only the actions you approved are being processed.</p>
      <ul className="mt-6 space-y-3">{reco.actions.map(a => <ActionRow key={a.id} a={a} />)}</ul>
      {finished && <div className="mt-6 flex gap-3"><Button onClick={() => setTab('audit')}>See the full decision journey</Button><Button variant="outline" onClick={() => setTab('dashboard')}>Back to dashboard</Button></div>}
    </Card>
  )
}

export default function Journey() {
  const { stage, reco, confirming, setTab } = useStore()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  useEffect(() => { if (reco) setSelected(new Set(reco.actions.map(a => a.id))) }, [reco?.id]) // eslint-disable-line react-hooks/exhaustive-deps
  const toggle = (id: string) => setSelected(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })

  if (stage === 'idle') {
    return <Card className="p-10 text-center"><p className="text-slate-600">No pending options.</p><Button className="mt-4" onClick={() => setTab('simulate')}>Simulate a liquidity event</Button></Card>
  }
  if (stage === 'processing') return <Processing />
  if (stage === 'failed') return <Card className="p-8">We couldn’t prepare options for this event. Nothing has been changed.</Card>
  if (!reco) return null
  if (stage === 'declined') {
    return (
      <Card className="mx-auto max-w-xl p-8 text-center slide-in">
        <div className="text-4xl">🤝</div>
        <h2 className="mt-3 text-xl font-semibold">Recommendation declined</h2>
        <p className="mt-1 text-slate-600">No financial action executed. Your money stays exactly where it is.</p>
        <Button className="mt-5" variant="outline" onClick={() => setTab('audit')}>View audit trail</Button>
      </Card>
    )
  }
  if (stage === 'executing' || stage === 'done') return <Execution reco={reco} finished={stage === 'done'} />
  return confirming ? <Approve reco={reco} selected={selected} /> : <Review reco={reco} selected={selected} toggle={toggle} />
}