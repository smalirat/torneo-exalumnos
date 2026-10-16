import { del, get, post } from './client'
import type { Category, Match, Me, Player, Registration, StandingRow, TeamSummary, TokenRes, Tournament } from './types'

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

export const teamApi = {
  list: () => get<TeamSummary[]>('/api/teams'),
  create: (name: string) => post<{ id: number; name: string }>('/api/teams/register', { name }),
  players: (teamId: number) => get<Player[]>(`/api/public/teams/${teamId}/players`),
  addPlayer: (teamId: number, fullName: string, jerseyNo: number) =>
    post<Player[]>(`/api/teams/${teamId}/players`, { players: [{ fullName, jerseyNo }] }),
  deletePlayer: (teamId: number, playerId: number) => del(`/api/teams/${teamId}/players/${playerId}`),
  registerIn: (teamId: number, tournamentId: number) =>
    post<Registration>(`/api/teams/${teamId}/tournaments/${tournamentId}/register`),
}

export const zoneApi = {
  standings: (zoneId: number) => get<StandingRow[]>(`/api/public/zones/${zoneId}/standings`),
  fixtures: (zoneId: number) => get<Match[]>(`/api/public/zones/${zoneId}/fixtures`),
}
