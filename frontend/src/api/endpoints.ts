import { get, post } from './client'
import type { Category, Match, Me, StandingRow, TokenRes, Tournament } from './types'

export const authApi = {
  login: (username: string, password: string) => post<TokenRes>('/api/auth/login', { username, password }),
  register: (username: string, password: string) => post<TokenRes>('/api/auth/register', { username, password }),
  me: () => get<Me>('/api/auth/me'),
}

export interface NewTournament {
  name: string
  location?: string
  registrationStart: string
  registrationEnd: string
}

export const tournamentApi = {
  list: () => get<Tournament[]>('/api/public/tournaments'),
  create: (body: NewTournament) => post<Tournament>('/api/admin/tournaments', body),
  categories: (id: number) => get<Category[]>(`/api/public/tournaments/${id}/categories`),
  createCategory: (id: number, name: string) => post<Category>(`/api/admin/tournaments/${id}/categories`, { name }),
}

export const zoneApi = {
  standings: (zoneId: number) => get<StandingRow[]>(`/api/public/zones/${zoneId}/standings`),
  fixtures: (zoneId: number) => get<Match[]>(`/api/public/zones/${zoneId}/fixtures`),
}
