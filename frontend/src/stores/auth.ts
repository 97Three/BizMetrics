import { defineStore } from 'pinia'
import client from '../api/client'
import type { AuthSession, User, RegisterDTO } from '../types/user'

export interface AccessContext {
  company: { id: number; name: string; entityType: string }
  companies: { id: number; name: string; entityType: string }[]
  permissions: string[]
  modules: { enabled: string[] }
}

const tokenValid = () => {
  const token = localStorage.getItem('token')
  const expires = localStorage.getItem('tokenExpiresAt')
  if (!token || (expires && Date.parse(expires) <= Date.now())) {
    localStorage.removeItem('token')
    localStorage.removeItem('tokenExpiresAt')
    return false
  }
  return true
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null, accessContext: null as AccessContext | null, ready: false }),
  getters: { authenticated: () => tokenValid() },
  actions: {
    async hydrate() {
      if (!tokenValid()) { this.ready = true; return }
      const me = await client.get<User>('/auth/me')
      this.user = me.data
      try {
        const context = await client.get<AccessContext>('/platform/access-context')
        this.setContext(context.data)
      } catch { this.accessContext = null }
      this.ready = true
    },
    async login(email: string, password: string) {
      const response = await client.post<AuthSession>('/auth/login', { email, password })
      localStorage.removeItem('activeCompanyId')
      localStorage.removeItem('activeSubjectType')
      localStorage.setItem('token', response.data.token)
      localStorage.setItem('tokenExpiresAt', response.data.tokenExpiresAt)
      this.user = response.data.user
      try {
        const context = await client.get<AccessContext>('/platform/access-context')
        this.setContext(context.data)
        this.ready = true
      } catch {
        // Authentication stays valid if the optional access context is unavailable.
        this.ready = true
      }
    },
    async register(data: RegisterDTO) {
      const response = await client.post<AuthSession>('/auth/register', data)
      localStorage.removeItem('activeCompanyId')
      localStorage.removeItem('activeSubjectType')
      localStorage.setItem('token', response.data.token)
      localStorage.setItem('tokenExpiresAt', response.data.tokenExpiresAt)
      this.user = response.data.user
      try {
        const context = await client.get<AccessContext>('/platform/access-context')
        this.setContext(context.data)
        this.ready = true
      } catch {
        this.ready = true
      }
    },
    setContext(context: AccessContext) {
      this.accessContext = context
      const id = Number(localStorage.getItem('activeCompanyId'))
      const selected = context.companies.find(company => company.id === id) || context.company
      localStorage.setItem('activeCompanyId', String(selected.id))
      localStorage.setItem('activeSubjectType', selected.entityType)
    },
    async chooseCompany(id: number) {
      const response = await client.get<AccessContext>('/platform/access-context', { params: { companyId: id } })
      this.setContext(response.data)
    },
    async logout() {
      try { await client.post('/auth/logout') } catch { /* Local logout still succeeds */ }
      for (const key of ['token','tokenExpiresAt','activeCompanyId','activeSubjectType']) localStorage.removeItem(key)
      this.user = null
      this.accessContext = null
      this.ready = true
    },
  },
})
