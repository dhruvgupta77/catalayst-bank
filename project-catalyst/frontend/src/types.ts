export interface Scenario { type: string; label: string; amount: number; source: string }
export interface IngestResponse { eventId: string; correlationId: string; status: string; duplicate: boolean; message: string }
export interface EventView {
  id: string; correlationId: string; type: string; label: string; customerId: string; amount: number
  currency: string; source: string; status: string; receivedAt: string; recommendationId: string | null
}
export interface ActionView {
  id: string; type: string; amount: number; priority: number; reason: string; impact: string | null
  productName: string; status: string; progress: number; progressNote: string | null; failureReason: string | null
}
export interface Narrative {
  summary: string; recommendation: string; reasoning: string[]; alternatives: string[]
  confidence: number | null; rankedOptions: string[]
}
export interface RuleOutcome { ruleId: string; passed: boolean; detail: string }
export interface RecommendationView {
  id: string; eventId: string; status: string; source: string; fallbackReason: string | null
  narrative: Narrative; actions: ActionView[]; rules: RuleOutcome[]; reducedContext: boolean
  contextNotes: string[]; keptInAccount: number; allocatable: number; event: EventView
  generationMs: number; createdAt: string; signals: FinancialSignal[]
}
export interface AccountView { type: string; name: string; balance: number; interestRate: number }
export interface CustomerView {
  id: string; name: string; age: number; monthlySalary: number; accounts: AccountView[]
  health: { emergencyCoveragePercent: number; highInterestDebt: number; totalDebt: number; creditScore: number; riskProfile: string }
}
export interface AuditEntry {
  seq: number; action: string; actor: string; result: string; detail: string; correlationId: string
  customerId: string; timestamp: string; offsetMs: number
}
export interface TimelineView {
  eventId: string; latencyTargetMs: number; msToRecommendation: number; withinTarget: boolean
  label: string; entries: AuditEntry[]
}
export interface DemoStatus {
  bus: string; aiMode: string; aiSource: string; profiles: string[]; creditBureauDown: boolean
  aiDown: boolean; complianceBlock: boolean; latencyTargetMs: number; metrics: Record<string, number>; principle: string
}export interface FinancialSignal { id: string; title: string; severity: string; detail: string; basis: string }
export interface ContextSource { name: string; kind: string; status: string; detail: string }
export interface FinancialContextView {
  label: string; customerId: string; customerName: string; monthlySalary: number; currentBalance: number
  emergencySavings: number; emergencyTarget: number; investmentBalance: number; creditCardBalance: number | null
  creditCardRate: number | null; loanBalance: number; monthlyLoanPayment: number; riskProfile: string
  reducedContext: boolean; notes: string[]; signals: FinancialSignal[]; sources: ContextSource[]; disclaimer: string
}
export interface PipelineStage { key: string; label: string; state: string; atMs: number | null; detail: string }
export interface PipelineAgent { name: string; role: string; kind: string; implementation: string; status: string; detail: string }
export interface PipelineView {
  eventId: string; correlationId: string; customerId: string; eventType: string; eventLabel: string; amount: number
  source: string; status: string; latencyTargetMs: number; msToRecommendation: number; stages: PipelineStage[]
  agents: PipelineAgent[]; contextConsidered: ContextSource[]; principle: string
}