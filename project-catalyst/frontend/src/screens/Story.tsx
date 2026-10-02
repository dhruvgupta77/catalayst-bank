import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { Badge, Button, Card } from '../components/ui'
import { actionLabel, auditLabel, clock, rand, sourceLabel } from '../format'
import { useStore } from '../store'
import type { FinancialSignal, PipelineStage } from '../types'
import { ActionRow, Degradation, WhyPanel } from './Journey'

const STEPS = ['Financial context', 'Liquidity event', 'Catalyst analysis', 'Recommendation', 'Customer decision', 'Action + audit']

const sevTone = (s: string) => (s === 'HIGH' ? 'red' : s === 'MEDIUM' ? 'amber' : 'slate') as 'red' | 'amber' | 'slate'
const stateStyle: Record<string, string> = {
  DONE: 'bg-emerald-500 text-white', DEGRADED: 'bg-amber-400 text-white', WAITING: 'bg-sky-500 text-white animate-pulse',
  BLOCKED: 'bg-rose-500 text-white', FAILED: 'bg-rose-500 text-white', DECLINED: 'bg-slate-500 text-white',
  SKIPPED: 'bg-slate-500 text-white', PENDING: 'bg-slate-200 text-slate-500',
}
const stateIcon: Record<string, string> = { DONE: '✓', DEGRADED: '!', WAITING: '…', BLOCKED: '✕', FAILED: '✕', DECLINED: '–', SKIPPED: '–', PENDING: '' }

function Signals({ signals }: { signals: FinancialSignal[] }) {
  return (
    <ul className="space-y-2">
      {signals.map(s => (
        <li key={s.id} className="rounded-xl bg-slate-50 p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2"><b>{s.title}</b><Badge tone={sevTone(s.severity)}>{s.severity.toLowerCase()}</Badge></div>
          <div className="mt-1 text-slate-600">{s.detail}</div>
          <div className="mt-1 text-xs text-slate-400">Rule: {s.basis}</div>
        </li>
      ))}
      {signals.length === 0 && <li className="text-sm text-slate-500">No financial-pressure signals in the demo data.</li>}
    </ul>
  )
}

function StageChip({ s }: { s: PipelineStage }) {
  return (
    <div className="flex flex-1 flex-col items-center text-center" title={s.detail}>
      <span className={`grid h-9 w-9 place-items-center rounded-full text-sm font-bold ${stateStyle[s.state] ?? stateStyle.PENDING}`}>{stateIcon[s.state] ?? ''}</span>
      <span className="mt-1 text-xs font-semibold uppercase tracking-wide">{s.label}</span>
      <span className="text-[10px] text-slate-400">{s.atMs != null ? `+${s.atMs} ms` : s.state.toLowerCase()}</span>
    </div>
  )
}

export default function Story() {
  const { stage, event, reco, fctx, pipeline, timeline, scenarios, trigger, approve, decline, reset, error, setTab } = useStore()
  const [step, setStep] = useState(1)
  const [maxStep, setMaxStep] = useState(1)
  const [pause, setPause] = useState(true)
  const [ack, setAck] = useState(false)
  const [modifying, setModifying] = useState(false)
  const [amounts, setAmounts] = useState<Record<string, number>>({})
  const [included, setIncluded] = useState<Set<string>>(new Set())
  const [countdown, setCountdown] = useState<number | null>(null)
  const viewed = useRef<string | null>(null)

  const go = (n: number) => { setStep(n); setMaxStep(m => Math.max(m, n)) }

  const run = async (scenario: string) => {
    setAck(false); setModifying(false); setCountdown(null)
    await trigger(scenario, true)
    go(2)
  }

  const recommendReady = pipeline?.stages.find(s => s.key === 'RECOMMEND')?.state === 'DONE' || pipeline?.stages.find(s => s.key === 'RECOMMEND')?.state === 'DEGRADED'

  useEffect(() => { if (stage === 'idle') { setStep(1); setMaxStep(1) } }, [stage])
  useEffect(() => {
    if (reco) { setAmounts(Object.fromEntries(reco.actions.map(a => [a.id, a.amount]))); setIncluded(new Set(reco.actions.map(a => a.id))) }
  }, [reco?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  // Presenter pacing: with "pause" on, steps 2 and 4 wait for the presenter; otherwise they advance on a short dwell.
  useEffect(() => {
    if (step === 2 && !pause) { const h = setTimeout(() => go(3), 3500); return () => clearTimeout(h) }
    if (step === 3 && recommendReady) { const h = setTimeout(() => go(4), 2500); return () => clearTimeout(h) }
    if (step === 4 && !pause && reco) { const h = setTimeout(() => go(5), 6000); return () => clearTimeout(h) }
  }, [step, pause, recommendReady, reco])

  // The customer viewing the options is audited once, when they are actually shown (step 4).
  useEffect(() => {
    if (step === 4 && reco && viewed.current !== reco.id && reco.status === 'GENERATED') {
      viewed.current = reco.id
      api.viewed(reco.id).catch(() => {})
    }
  }, [step, reco])

  useEffect(() => {
    if (['executing', 'done', 'declined'].includes(stage)) go(6)
  }, [stage])

  // Hands-free demo only: the presenter simulates the customer tapping "Accept" after a visible countdown.
  useEffect(() => {
    if (step !== 5 || pause || !reco || stage !== 'review' || modifying) { setCountdown(null); return }
    setCountdown(6)
    const h = setInterval(() => setCountdown(c => (c === null ? null : c - 1)), 1000)
    return () => clearInterval(h)
  }, [step, pause, reco?.id, stage, modifying]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (countdown === 0 && reco) { setCountdown(null); approve(reco.actions.map(a => a.id)) }
  }, [countdown]) // eslint-disable-line react-hooks/exhaustive-deps

  const unusual = (id: string) => reco && amounts[id] > (reco.actions.find(a => a.id === id)?.amount ?? 0)
  const submitModify = () => {
    if (!reco) return
    const ids = reco.actions.filter(a => included.has(a.id)).map(a => a.id)
    const changed = Object.fromEntries(ids.filter(id => amounts[id] !== reco.actions.find(a => a.id === id)?.amount).map(id => [id, amounts[id]]))
    approve(ids, changed)
  }

  return (
    <div className="space-y-6">
      <Card className="p-4">
        <ol className="flex flex-wrap items-center gap-2">
          {STEPS.map((label, i) => {
            const n = i + 1
            const reached = n <= maxStep
            return (
              <li key={label} className="flex items-center gap-2">
                <button disabled={!reached} onClick={() => setStep(n)}
                  className={`flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold transition ${step === n ? 'bg-ink text-white' : reached ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-400'}`}>
                  <span>{n}</span>{label}
                </button>
                {n < STEPS.length && <span className="text-slate-300">›</span>}
              </li>
            )
          })}
        </ol>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
          <span>Detect → Understand → Decide → Recommend → Consent → Act → Audit</span>
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" className="h-4 w-4 accent-emerald-600" checked={pause} onChange={e => setPause(e.target.checked)} />
            Pause for audience at the event and at the recommendation
          </label>
        </div>
      </Card>

      {error && step >= 5 && <div className="rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-800 ring-1 ring-rose-200">⛔ {error}</div>}

      {step === 1 && (
        <div className="space-y-6 slide-in">
          <Card className="p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-xl font-bold">Financial vulnerability creates difficult financial decisions</h2>
              <Badge tone="violet">Demo financial context</Badge>
            </div>
            <p className="mt-1 text-sm text-slate-600">
              The withdrawal itself is not the problem. Pressure on a customer's finances is. Here is a fictional customer, Thabo Mokoena.
            </p>
            {fctx && (
              <dl className="mt-5 grid grid-cols-2 gap-3 text-sm md:grid-cols-3">
                {([
                  ['Monthly salary', rand(fctx.monthlySalary)], ['Current account', rand(fctx.currentBalance)],
                  ['Credit card', fctx.creditCardBalance == null ? 'unavailable' : `${rand(fctx.creditCardBalance)} @ ${fctx.creditCardRate}%`],
                  ['Emergency savings', `${rand(fctx.emergencySavings)} of ${rand(fctx.emergencyTarget)}`],
                  ['Investments', rand(fctx.investmentBalance)], ['Risk profile', fctx.riskProfile.charAt(0) + fctx.riskProfile.slice(1).toLowerCase()],
                ] as const).map(([k, v]) => (
                  <div key={k} className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">{k}</dt><dd className="mt-0.5 font-semibold">{v}</dd></div>
                ))}
              </dl>
            )}
          </Card>
          <Card className="p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="font-semibold">Financial vulnerability signals</h3>
              <Badge tone="amber">Illustrative demo signals</Badge>
            </div>
            <p className="mb-3 mt-1 text-xs text-slate-500">{fctx?.disclaimer}</p>
            {fctx?.reducedContext && <div className="mb-3 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-900 ring-1 ring-amber-200">{fctx.notes.map(n => <div key={n}>⚠️ {n}</div>)}</div>}
            <Signals signals={fctx?.signals ?? []} />
          </Card>
          <Card className="p-6">
            <h3 className="font-semibold">Now money arrives</h3>
            <p className="mt-1 text-sm text-slate-600">Today a bank may only see a withdrawal after the customer has already decided. Catalyst treats the inflow as a decision moment.</p>
            <div className="mt-4 flex flex-wrap gap-3">
              <Button onClick={() => run('TWO_POT_PAYOUT')}>▶ Run demo: Two-Pot payout R45,000</Button>
              <Button variant="ghost" onClick={reset}>Reset demo data</Button>
            </div>
            <div className="mt-5 text-xs font-semibold uppercase tracking-wide text-slate-400">Same platform, other liquidity events</div>
            <div className="mt-2 flex flex-wrap gap-2">
              {scenarios.filter(s => s.type !== 'TWO_POT_PAYOUT').map(s => (
                <Button key={s.type} variant="outline" onClick={() => run(s.type)}>{s.label} · {rand(s.amount)}</Button>
              ))}
            </div>
          </Card>
        </div>
      )}

      {step === 2 && event && (
        <Card className="mx-auto max-w-2xl p-10 text-center slide-in">
          <div className="text-xs font-bold uppercase tracking-[0.3em] text-brand-700">Liquidity event</div>
          <div className="mt-4 text-6xl font-black tabular-nums text-ink">{rand(event.amount)}</div>
          <div className="mt-2 text-lg font-semibold">{event.label} received</div>
          <div className="text-sm text-slate-500">Source: {sourceLabel(event.type, event.source)}</div>
          <p className="mt-6 rounded-xl bg-slate-50 p-3 text-sm text-slate-600"><b>Two-Pot is the use case. Liquidity events are the platform.</b></p>
          {pause ? (
            <div className="mt-6">
              <div className="text-xl font-semibold text-brand-700">“What should the bank do next?”</div>
              <p className="mt-1 text-xs text-slate-500">Ask the audience, then continue.</p>
              <Button className="mt-4" onClick={() => go(3)}>Continue to Catalyst analysis</Button>
            </div>
          ) : <p className="mt-6 text-xs text-slate-400">Continuing automatically…</p>}
        </Card>
      )}

      {step === 3 && (
        <div className="space-y-6 slide-in">
          <Card className="p-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-semibold">Catalyst analysis</h2>
              {pipeline && <span className="font-mono text-xs text-slate-500">event {pipeline.eventId} · correlation {pipeline.correlationId}</span>}
            </div>
            <div className="mt-5 flex items-start">{(pipeline?.stages ?? []).map(s => <StageChip key={s.key} s={s} />)}</div>
            {pipeline && pipeline.msToRecommendation >= 0 && (
              <p className="mt-4 text-xs text-slate-500">Demo Processing Timeline: {pipeline.msToRecommendation} ms to recommendation on this local run (target ≤ {pipeline.latencyTargetMs} ms; not a production claim).</p>
            )}
          </Card>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card className="p-6">
              <h3 className="font-semibold">Agents and what each one does</h3>
              <ul className="mt-3 space-y-3 text-sm">
                {pipeline?.agents.map(a => (
                  <li key={a.name} className="rounded-xl bg-slate-50 p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <b>{a.name}</b>
                      <Badge tone={a.kind === 'AI' ? 'violet' : a.kind === 'DETERMINISTIC_FALLBACK' ? 'amber' : 'blue'}>
                        {a.kind === 'AI' ? 'AI' : a.kind === 'DETERMINISTIC_FALLBACK' ? 'Rules fallback' : 'Deterministic'}
                      </Badge>
                      <Badge tone={a.status === 'DONE' ? 'green' : a.status === 'DEGRADED' ? 'amber' : 'slate'}>{a.status.toLowerCase()}</Badge>
                    </div>
                    <div className="mt-1 text-slate-600">{a.role}</div>
                    <div className="mt-1 text-xs text-slate-400">{a.implementation}</div>
                  </li>
                ))}
              </ul>
            </Card>
            <Card className="p-6">
              <h3 className="font-semibold">Context being considered</h3>
              <p className="mt-1 text-xs text-slate-500">The AI only sees what is listed here, never the customer's name or ID.</p>
              <ul className="mt-3 space-y-2 text-sm">
                {pipeline?.contextConsidered.map(c => (
                  <li key={c.name} className="flex items-start justify-between gap-3 rounded-xl bg-slate-50 p-3">
                    <div><b>{c.name}</b><div className="text-xs text-slate-500">{c.detail}</div></div>
                    <Badge tone={c.status === 'OK' ? 'green' : c.status === 'CACHED' ? 'amber' : 'slate'}>{c.kind.includes('MOCK') ? `mock · ${c.status.toLowerCase()}` : c.status.toLowerCase()}</Badge>
                  </li>
                ))}
              </ul>
              {recommendReady && <Button className="mt-4" onClick={() => go(4)}>Show the recommendation</Button>}
            </Card>
          </div>
        </div>
      )}

      {step === 4 && reco && (
        <div className="space-y-6 slide-in">
          <Card className="p-6">
            <div className="text-sm text-slate-500">{reco.event.label} · {sourceLabel(reco.event.type, reco.event.source)}</div>
            <h2 className="mt-1 text-3xl font-bold">You received <span className="text-brand-700">{rand(reco.event.amount)}</span></h2>
            <p className="mt-2 text-slate-600">Potential options based on the available demo context. They are illustrative, not financial advice, and every one is your choice.</p>
            <p className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">{reco.narrative.recommendation}</p>
          </Card>
          <Degradation reco={reco} />
          {reco.signals.length > 0 && (
            <Card className="p-6"><h3 className="font-semibold">Signals considered <span className="text-xs font-normal text-slate-400">(illustrative demo signals)</span></h3><div className="mt-3"><Signals signals={reco.signals} /></div></Card>
          )}
          <div className="grid gap-3 md:grid-cols-3">
            {reco.actions.map((a, i) => (
              <Card key={a.id} className="p-5">
                <div className="text-3xl">{actionLabel[a.type].icon}</div>
                <div className="mt-2 flex items-center gap-2 font-semibold">{actionLabel[a.type].title}{i === 0 && <Badge tone="green">first</Badge>}</div>
                <div className="mt-1 text-2xl font-bold tabular-nums">{rand(a.amount)}</div>
                <div className="mt-1 text-xs text-slate-500">{a.reason}</div>
                {a.impact && <div className="mt-1 text-xs text-slate-400">{a.impact}</div>}
              </Card>
            ))}
            {reco.actions.length === 0 && <Card className="p-5 text-sm text-slate-600 md:col-span-3">No options needed right now. Keeping the money in your account is perfectly fine.</Card>}
          </div>
          <WhyPanel reco={reco} />
          <div className="rounded-xl bg-ink px-4 py-3 text-center text-sm text-white">AI recommends · Rules constrain · The customer decides · The system audits</div>
          <div className="text-center">
            {pause && <div className="mb-3 text-xl font-semibold text-brand-700">“Would you accept this recommendation?”</div>}
            <Button onClick={() => go(5)}>Continue to the customer decision</Button>
          </div>
        </div>
      )}

      {step === 5 && reco && (
        <Card className="mx-auto max-w-2xl p-8 slide-in">
          <h2 className="text-2xl font-bold">Your decision</h2>
          <p className="mt-1 text-sm text-slate-500">Nothing happens until you consent. You can accept, change the amounts, or decline.</p>
          <ul className="mt-5 divide-y divide-slate-100">
            {reco.actions.map(a => (
              <li key={a.id} className="flex items-center justify-between gap-3 py-3">
                <div className="flex items-center gap-3">
                  {modifying && <input type="checkbox" className="h-4 w-4 accent-emerald-600" checked={included.has(a.id)}
                    onChange={() => setIncluded(s => { const n = new Set(s); n.has(a.id) ? n.delete(a.id) : n.add(a.id); return n })} />}
                  <div><div className="font-medium">{actionLabel[a.type].icon} {actionLabel[a.type].title}</div><div className="text-xs text-slate-500">To {a.productName}</div></div>
                </div>
                {modifying ? (
                  <div className="text-right">
                    <input type="number" min={0} step={100} value={amounts[a.id] ?? a.amount}
                      onChange={e => setAmounts(s => ({ ...s, [a.id]: Number(e.target.value) }))}
                      className={`w-28 rounded-lg px-2 py-1 text-right tabular-nums ring-1 ${unusual(a.id) ? 'ring-rose-400' : 'ring-slate-300'}`} />
                    <div className="text-[10px] text-slate-400">rule-approved up to {rand(a.amount)}</div>
                  </div>
                ) : <b className="tabular-nums">{rand(a.amount)}</b>}
              </li>
            ))}
          </ul>
          <label className="mt-5 flex cursor-pointer items-start gap-3 text-sm">
            <input type="checkbox" className="mt-1 h-4 w-4 accent-emerald-600" checked={ack} onChange={e => setAck(e.target.checked)} />
            <span>I understand these changes and I choose to approve them. Returns on investments are not guaranteed.</span>
          </label>
          {countdown !== null && (
            <div className="mt-4 rounded-xl bg-sky-50 px-4 py-3 text-sm text-sky-900 ring-1 ring-sky-200">
              Hands-free demo: simulating the customer tapping “Accept” in {countdown}s. Choose Modify or Decline to take over.
            </div>
          )}
          <div className="mt-6 flex flex-wrap gap-3">
            {modifying
              ? <Button disabled={!ack || included.size === 0} onClick={() => { setCountdown(null); submitModify() }}>Confirm my changes</Button>
              : <Button disabled={!ack} onClick={() => { setCountdown(null); approve(reco.actions.map(a => a.id)) }}>Accept recommendation</Button>}
            <Button variant="outline" onClick={() => { setCountdown(null); setModifying(m => !m) }}>{modifying ? 'Back' : 'Modify'}</Button>
            <Button variant="danger" onClick={() => { setCountdown(null); decline() }}>Decline</Button>
          </div>
        </Card>
      )}

      {step === 6 && reco && (
        <div className="grid gap-6 lg:grid-cols-2 slide-in">
          <Card className="p-6">
            {stage === 'declined' ? (
              <>
                <div className="text-4xl">🤝</div>
                <h2 className="mt-2 text-xl font-bold">Recommendation declined</h2>
                <p className="mt-1 text-slate-600"><b>No financial action was executed.</b> Your money stays exactly where it is.</p>
              </>
            ) : (
              <>
                <h2 className="text-xl font-bold">{stage === 'done' ? 'Done: consent, action, audit' : 'Consent received. Carrying out your approved changes…'}</h2>
                <p className="mt-1 text-sm text-slate-500">Simulated actions only. No real banking system is connected.</p>
                <ul className="mt-4 space-y-3">{reco.actions.map(a => <ActionRow key={a.id} a={a} />)}</ul>
              </>
            )}
            {pipeline && <div className="mt-6 flex items-start">{pipeline.stages.map(s => <StageChip key={s.key} s={s} />)}</div>}
            <div className="mt-6 flex flex-wrap gap-3">
              <Button variant="outline" onClick={() => setTab('presenter')}>Presenter view</Button>
              <Button variant="ghost" onClick={reset}>Reset and run again</Button>
            </div>
          </Card>
          <Card className="p-6">
            <h3 className="font-semibold">Audit trail</h3>
            <ol className="mt-4 space-y-3 border-l-2 border-slate-200 pl-5">
              {(timeline?.entries ?? []).map(e => (
                <li key={e.seq} className="relative text-sm">
                  <span className={`absolute -left-[27px] top-1.5 h-2.5 w-2.5 rounded-full ring-4 ring-white ${e.result === 'OK' ? 'bg-emerald-500' : e.result === 'DENIED' || e.result === 'FAILURE' ? 'bg-rose-500' : 'bg-amber-400'}`} />
                  <div className="flex flex-wrap items-center gap-2"><b>{auditLabel[e.action] ?? e.action}</b><span className="font-mono text-[11px] text-slate-400">{clock(e.timestamp)}</span></div>
                  <div className="text-xs text-slate-500">{e.actor} · {e.detail}</div>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      )}
    </div>
  )
}
