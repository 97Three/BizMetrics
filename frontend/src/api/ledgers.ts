import client from './client'
import type { Transaction, TransactionQuery } from '../types/transaction'
import type { Budget, BudgetQuery } from '../types/budget'
import type { ReceiptVoucher, ReceiptQuery, ReceiptSummary } from '../types/receipt'
import type { Account, AccountSummary } from '../types/account'

export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export interface TransactionSummary { income: number; expense: number; net: number; rows: number; largeCount: number; reviewCount: number }
export interface RecurringItem { id: string; name: string; type: 1 | 2; amount: number; frequency: 'daily' | 'weekly' | 'monthly' | 'yearly'; interval: number; startDate: string; endDate?: string; lastExecuted?: string; nextExecution: string; status: number; executionCount: number; note?: string }
const companyId = () => {
  const value = Number(localStorage.getItem('activeCompanyId'))
  return Number.isFinite(value) && value > 0 ? value : undefined
}

export const ledgerApi = {
  transactions: {
    list: (params: TransactionQuery) => client.get<Page<Transaction>>('/transactions', { params }),
    summary: (params: Omit<TransactionQuery, 'page' | 'size'>) => client.get<TransactionSummary>('/transactions/summary', { params }),
    get: (id: number) => client.get<Transaction>(`/transactions/${id}`),
  },
  budgets: {
    list: (params: BudgetQuery) => client.get<Page<Budget>>('/budgets', { params }),
    get: (id: number) => client.get<Budget>(`/budgets/${id}`),
  },
  receipts: {
    list: (params: ReceiptQuery) => client.get<Page<ReceiptVoucher>>('/receipts', { params: { ...params, companyId: companyId() } }),
    summary: () => client.get<ReceiptSummary>('/receipts/summary', { params: { companyId: companyId() } }),
    auditLogs: (id: number) => client.get<Array<{ action: string; summary: string; actorName: string; createdAt: string }>>(`/receipts/${id}/audit-logs`),
  },
  accounts: {
    list: () => client.get<Account[]>('/accounts'),
    summary: () => client.get<AccountSummary>('/accounts/summary'),
    get: (id: number) => client.get<Account>(`/accounts/${id}`),
  },
  recurring: { list: () => client.get<RecurringItem[]>('/recurring') },
}
