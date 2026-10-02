import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from './api'
import type {
  CustomerView, DemoStatus, EventView, FinancialContextView, PipelineView, RecommendationView, Scenario, TimelineView,
} from './types'

export type Tab = 'story' | 'dashboard' | 'simulate' | 'journey' | 'audit' | 'presenter'
export type Stage = 'idle' | 'processing' | 'review' | 'approve' | 'executing' | 'done' | 'declined' | 'failed'

interface Store {
  tab: Tab
  setTab: (t: Tab) => void
  scenarios: Scenario[]
  customer: CustomerView | null
  status: DemoStatus | null
  event: EventView | null
  reco: RecommendationView | null
  timeline: TimelineView | null
  pipeline: PipelineView | null
  fctx: FinancialContextView | null
  recent: EventView[]
  stage: Stage
  confirming: boolean
  setConfirming: (b: boolean) => void
  error: string | null
  notice: string | null
  trigger: (scenario: string, stay?: boolean) => Promise<void>
  duplicate: () => Promise<void>
  approve: (ids: string[], amounts?: Record<string, number>) => Promise<void>
  decline: () => Promise<void>
  reset: () => Promise<void>
  setToggles: (t: { creditBureauDown?: boolean; aiDown?: boolean; complianceBlock?: boolean }) => Promise<void>
}

const Ctx = createContext<Store | null>(null)
export const useStore = () => {
  const s = useContext(Ctx)
  if (!s) throw new Error('StoreProvider missing')
  return s
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [tab, setTab] = useState<Tab>('story')
  const [scenarios, setScenarios] = useState<Scenario[]>([])
  const [customer, setCustomer] = useState<CustomerView | null>(null)
  const [status, setStatus] = useState<DemoStatus | null>(null)
  const [eventId, setEventId] = useState<string | null>(null)
  const [event, setEvent] = useState<EventView | null>(null)
  const [reco, setReco] = useState<RecommendationView | null>(null)
  const [timeline, setTimeline] = useState<TimelineView | null>(null)
  const [pipeline, setPipeline] = useState<PipelineView | null>(null)
  const [fctx, setFctx] = useState<FinancialContextView | null>(null)
  const [recent, setRecent] = useState<EventView[]>([])
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const lastKey = useRef<string | null>(null)
  const viewedFor = useRef<string | null>(null)

  const guard = useCallback(async (fn: () => Promise<void>) => {
    try { setError(null); await fn() } catch (e) { setError((e as Error).message) }
  }, [])

  useEffect(() => { api.scenarios().then(setScenarios).catch(() => {}) }, [])

  useEffect(() => {
    const tick = async () => {
      try {
        const [c, s, r, f] = await Promise.all([api.customer(), api.status(), api.recentEvents(), api.financialContext()])
        setCustomer(c); setStatus(s); setRecent(r); setFctx(f)
        if (eventId) {
          const ev = await api.event(eventId)
          setEvent(ev)
          api.audit(eventId).then(setTimeline).catch(() => {})
          api.pipeline(eventId).then(setPipeline).catch(() => {})
          if (ev.recommendationId) setReco(await api.recommendation(ev.recommendationId))
        }
      } catch { /* backend not ready yet */ }
    }
    tick()
    const h = setInterval(tick, 500)
    return () => clearInterval(h)
  }, [eventId])

  // The customer "viewing" the recommendation is audited exactly once per recommendation.
  useEffect(() => {
    if (reco && tab === 'journey' && viewedFor.current !== reco.id && reco.status === 'GENERATED') {
      viewedFor.current = reco.id
      api.viewed(reco.id).catch(() => {})
    }
  }, [reco, tab])

  const stage: Stage = useMemo(() => {
    if (!event) return 'idle'
    if (event.status === 'FAILED') return 'failed'
    if (!reco || reco.eventId !== event.id) return 'processing'
    if (reco.status === 'DECLINED') return 'declined'
    if (reco.status === 'APPROVED') {
      const terminal = reco.actions.every(a => ['EXECUTED', 'FAILED', 'CANCELLED'].includes(a.status))
      return terminal ? 'done' : 'executing'
    }
    return confirming ? 'approve' : 'review'
  }, [event, reco, confirming])

  const value: Store = {
    tab, setTab, scenarios, customer, status, event, reco, timeline, pipeline, fctx, recent, stage, confirming, setConfirming, error, notice,
    trigger: (s, stay = false) => guard(async () => {
      const key = `ui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      lastKey.current = key
      const r = await api.trigger(s, key)
      setReco(null); setTimeline(null); setPipeline(null); setConfirming(false); setNotice(null)
      setEventId(r.eventId); setEvent(null)
      if (!stay) setTab('journey')
    }),
    duplicate: () => guard(async () => {
      if (!eventId || !lastKey.current) throw new Error('Trigger an event first')
      const r = await api.trigger(event?.type ?? 'BONUS', lastKey.current)
      await api.replay(eventId)
      setNotice(r.duplicate ? 'Duplicate event detected. No duplicate financial action executed.' : r.message)
    }),
    approve: (ids, amounts) => guard(async () => {
      if (!reco) return
      setReco(await api.consent(reco.id, ids, amounts)); setConfirming(false)
    }),
    decline: () => guard(async () => {
      if (!reco) return
      setReco(await api.decline(reco.id)); setConfirming(false)
    }),
    reset: () => guard(async () => {
      await api.reset(); setEventId(null); setEvent(null); setReco(null); setTimeline(null); setPipeline(null); setConfirming(false); setNotice(null)
      lastKey.current = null; viewedFor.current = null
    }),
    setToggles: t => guard(async () => { setStatus(await api.toggles(t)) }),
  }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}