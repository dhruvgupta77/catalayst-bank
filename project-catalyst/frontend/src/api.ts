import type {
  ActionView, CustomerView, DemoStatus, EventView, FinancialContextView, IngestResponse, PipelineView, RecommendationView, Scenario,
  TimelineView,
} from './types'

async function call<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    let msg = res.statusText
    try { msg = (await res.json()).message ?? msg } catch { /* ignore */ }
    throw new Error(msg)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  scenarios: () => call<Scenario[]>('GET', '/api/demo/scenarios'),
  status: () => call<DemoStatus>('GET', '/api/demo/status'),
  toggles: (t: { creditBureauDown?: boolean; aiDown?: boolean; complianceBlock?: boolean }) => call<DemoStatus>('POST', '/api/demo/toggles', t),
  reset: () => call<DemoStatus>('POST', '/api/demo/reset'),
  trigger: (scenario: string, idempotencyKey?: string) =>
    call<IngestResponse>('POST', '/api/demo/liquidity-events', { scenario, idempotencyKey }),
  replay: (eventId: string) => call<void>('POST', `/api/demo/liquidity-events/${eventId}/replay`),
  event: (id: string) => call<EventView>('GET', `/api/liquidity-events/${id}`),
  recentEvents: () => call<EventView[]>('GET', '/api/liquidity-events?customerId=CUST-1001'),
  recommendation: (id: string) => call<RecommendationView>('GET', `/api/recommendations/${id}`),
  viewed: (id: string) => call<RecommendationView>('POST', `/api/recommendations/${id}/viewed`, {}),
  consent: (id: string, actionIds: string[], amounts?: Record<string, number>) =>
    call<RecommendationView>('POST', `/api/recommendations/${id}/consent`, { actionIds, confirmed: true, amounts }),
  decline: (id: string) => call<RecommendationView>('POST', `/api/recommendations/${id}/decline`, {}),
  action: (id: string) => call<ActionView>('GET', `/api/actions/${id}`),
  financialContext: () => call<FinancialContextView>('GET', '/api/customers/CUST-1001/financial-context'),
  pipeline: (eventId: string) => call<PipelineView>('GET', `/api/pipeline/${eventId}`),
  customer: () => call<CustomerView>('GET', '/api/customers/CUST-1001'),
  audit: (eventId: string) => call<TimelineView>('GET', `/api/audit/${eventId}`),
}