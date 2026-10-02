export const rand = (n: number) => 'R' + Math.round(n).toLocaleString('en-ZA').replace(/\u00a0/g, ' ')

export const clock = (iso: string) => {
  const d = new Date(iso)
  const p = (n: number, l = 2) => String(n).padStart(l, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`
}

export const actionLabel: Record<string, { title: string; icon: string; tone: string }> = {
  DEBT_REPAYMENT: { title: 'Pay down high-interest debt', icon: '💳', tone: 'amber' },
  SAVINGS: { title: 'Build emergency savings', icon: '🛟', tone: 'sky' },
  INVESTMENT: { title: 'Invest for the long term', icon: '🌱', tone: 'emerald' },
}
export const sourceLabel = (type: string, source: string) =>
  type === 'TWO_POT_PAYOUT' ? 'Two-Pot Savings Pot'
    : source.toLowerCase().split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')

export const auditLabel: Record<string, string> = {
  EVENT_RECEIVED: 'Liquidity event received',
  CONTEXT_ENRICHED: 'Context assembled',
  SIGNALS_DETECTED: 'Financial signals detected (illustrative)',
  RULES_EVALUATED: 'Rules evaluated',
  RECOMMENDATION_GENERATED: 'Recommendation generated',
  RECOMMENDATION_PUBLISHED: 'Options shown to customer',
  CUSTOMER_VIEWED: 'Customer viewed',
  CUSTOMER_MODIFIED: 'Customer modified',
  CUSTOMER_APPROVED: 'Customer consented',
  CUSTOMER_DECLINED: 'Customer declined',
  NO_ACTION_EXECUTED: 'No action executed',
  POLICY_BLOCKED: 'Blocked by policy rule',
  ACTION_BLOCKED: 'Action blocked by compliance rule',
  ACTION_REJECTED: 'Action refused: no valid consent',
  ACTION_EXECUTED: 'Action executed (simulated)',
  ACTION_FAILED: 'Action failed',
  ACTION_SKIPPED: 'Option not taken',
  DUPLICATE_EVENT_DETECTED: 'Duplicate event ignored',
}