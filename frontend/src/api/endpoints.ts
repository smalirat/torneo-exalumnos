import { del, get, patch, post } from './client'
import type {
  Category, Match, MatchEvent, Me, Player, Registration, ScoreReq, StandingRow, TeamSummary, TokenRes, Tournament, Zone,
} from './types'

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
  registrations: (id: number) => get<Registration[]>(`/api/public/tournaments/${id}/registrations`),
  assignZone: (registrationId: number, zoneId: number) =>
    patch<Registration>(`/api/admin/registrations/${registrationId}/zone`, { zoneId }),
  createZone: (categoryId: number, name: string) => post<Zone>(`/api/admin/categories/${categoryId}/zones`, { name }),
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

export const matchApi = {
  events: (matchId: number) => get<MatchEvent[]>(`/api/public/matches/${matchId}/events`),
  score: (matchId: number, body: ScoreReq) => patch<Match>(`/api/admin/matches/${matchId}/score`, body),
}

export const zoneApi = {
  standings: (zoneId: number) => get<StandingRow[]>(`/api/public/zones/${zoneId}/standings`),
  fixtures: (zoneId: number) => get<Match[]>(`/api/public/zones/${zoneId}/fixtures`),
  roundRobin: (zoneId: number, start: string, minutesBetween: number) =>
    post<Match[]>(`/api/admin/zones/${zoneId}/fixtures/round-robin?start=${encodeURIComponent(start)}&minutesBetween=${minutesBetween}`),
  addMatch: (zoneId: number, homeTeamId: number, awayTeamId: number, scheduledAt: string | null) =>
    post<Match[]>('/api/admin/fixtures/manual', { zoneId, fixtures: [{ homeTeamId, awayTeamId, scheduledAt }] }),
}
