import type { ReactNode } from 'react'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 ${className}`}>{children}</div>
}

export function Badge({ children, tone = 'slate' }: { children: ReactNode; tone?: 'slate' | 'green' | 'amber' | 'red' | 'blue' | 'violet' }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700', green: 'bg-emerald-100 text-emerald-800', amber: 'bg-amber-100 text-amber-800',
    red: 'bg-rose-100 text-rose-800', blue: 'bg-sky-100 text-sky-800', violet: 'bg-violet-100 text-violet-800',
  }
  return <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>
}

export function Button({ children, onClick, variant = 'primary', disabled, className = '' }: {
  children: ReactNode; onClick?: () => void; variant?: 'primary' | 'ghost' | 'danger' | 'outline'; disabled?: boolean; className?: string
}) {
  const v = {
    primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-slate-300',
    outline: 'bg-white text-brand-700 ring-1 ring-brand-500 hover:bg-brand-50 disabled:opacity-50',
    ghost: 'bg-transparent text-slate-600 hover:bg-slate-100',
    danger: 'bg-white text-rose-700 ring-1 ring-rose-300 hover:bg-rose-50',
  }
  return (
    <button onClick={onClick} disabled={disabled}
      className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed ${v[variant]} ${className}`}>
      {children}
    </button>
  )
}

export function PrincipleBanner() {
  return (
    <div className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-xl bg-ink px-4 py-2 text-xs text-white">
      <span><b className="text-brand-100">AI</b> recommends</span>
      <span><b className="text-brand-100">Rules</b> determine eligibility</span>
      <span><b className="text-brand-100">You</b> decide</span>
      <span className="text-slate-400">Nothing moves without your explicit consent.</span>
    </div>
  )
}