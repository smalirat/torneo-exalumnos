import { del, download, get, patch, post, put, upload } from './client'
import type {
  CardRow, Category, CategoryHistory, ImportHistory, ImportPreview, Match, MatchDetails, MatchEvent, Me, Player,
  Registration, Sanction, ScoreReq, ScorerRow, Season, SeasonStatRow, SlotReq, StandingRow, TeamRef, TeamRegistration,
  TeamSummary, TokenRes, Tournament, TournamentStatus, YellowTally, Zone,
} from './types'

export const authApi = {
  login: (username: string, password: string) => post<TokenRes>('/api/auth/login', { username, password }),
  register: (username: string, password: string) => post<TokenRes>('/api/auth/register', { username, password }),
  me: () => get<Me>('/api/auth/me'),
}

/** Body for creating/updating a tournament (status is ignored on create). */
export interface TournamentInput {
  name: string
  location: string | null
  registrationStart: string
  registrationEnd: string
  matchStart: string | null
  matchEnd: string | null
  status: TournamentStatus
  seasonYear: number | null
  stage: 'APERTURA' | 'CLAUSURA' | null
}

export const tournamentApi = {
  list: () => get<Tournament[]>('/api/public/tournaments'),
  create: (body: TournamentInput) => post<Tournament>('/api/admin/tournaments', body),
  update: (id: number, body: TournamentInput) => put<Tournament>(`/api/admin/tournaments/${id}`, body),
  remove: (id: number) => del(`/api/admin/tournaments/${id}`),
  renameCategory: (categoryId: number, name: string) => put<Category>(`/api/admin/categories/${categoryId}`, { name }),
  removeCategory: (categoryId: number) => del(`/api/admin/categories/${categoryId}`),
  renameZone: (zoneId: number, name: string) => put<Zone>(`/api/admin/zones/${zoneId}`, { name }),
  removeZone: (zoneId: number) => del(`/api/admin/zones/${zoneId}`),
  categories: (id: number) => get<Category[]>(`/api/public/tournaments/${id}/categories`),
  createCategory: (id: number, name: string) => post<Category>(`/api/admin/tournaments/${id}/categories`, { name }),
  registrations: (id: number) => get<Registration[]>(`/api/public/tournaments/${id}/registrations`),
  scorers: (id: number) => get<ScorerRow[]>(`/api/public/tournaments/${id}/scorers`),
  yellowCards: (id: number) => get<YellowTally[]>(`/api/public/tournaments/${id}/yellow-cards`),
  cards: (id: number) => get<CardRow[]>(`/api/public/tournaments/${id}/cards`),
  assignZone: (registrationId: number, zoneId: number) =>
    patch<Registration>(`/api/admin/registrations/${registrationId}/zone`, { zoneId }),
  createZone: (categoryId: number, name: string) => post<Zone>(`/api/admin/categories/${categoryId}/zones`, { name }),
  drawZones: (categoryId: number, registrationIds: number[]) =>
    post<Registration[]>(`/api/admin/categories/${categoryId}/zone-draw`, { registrationIds }),
}

export const teamApi = {
  list: () => get<TeamSummary[]>('/api/teams'),
  create: (name: string) => post<{ id: number; name: string }>('/api/teams/register', { name }),
  rename: (teamId: number, name: string) => put<{ id: number; name: string }>(`/api/teams/${teamId}`, { name }),
  remove: (teamId: number) => del(`/api/teams/${teamId}`),
  get: (teamId: number) => get<TeamRef>(`/api/public/teams/${teamId}`),
  registrations: (teamId: number) => get<TeamRegistration[]>(`/api/public/teams/${teamId}/registrations`),
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
  remove: (matchId: number) => del(`/api/admin/matches/${matchId}`),
  updateDetails: (matchId: number, body: MatchDetails) => patch<Match>(`/api/admin/matches/${matchId}`, body),
}

export interface NewSpecialMatch {
  type: 'PROMOTION' | 'FINAL'
  scheduledAt: string | null
  home: SlotReq
  away: SlotReq
}

export const playoffApi = {
  list: (tournamentId: number) => get<Match[]>(`/api/public/tournaments/${tournamentId}/special-matches`),
  create: (tournamentId: number, body: NewSpecialMatch) =>
    post<Match>(`/api/admin/tournaments/${tournamentId}/special-matches`, body),
  resolve: (matchId: number) => post<Match>(`/api/admin/matches/${matchId}/resolve`),
}

export const seasonApi = {
  list: () => get<Season[]>('/api/public/seasons'),
  scorers: (id: number) => get<SeasonStatRow[]>(`/api/public/seasons/${id}/scorers`),
  figures: (id: number) => get<SeasonStatRow[]>(`/api/public/seasons/${id}/figures`),
  goalkeepers: (id: number) => get<SeasonStatRow[]>(`/api/public/seasons/${id}/goalkeepers`),
  sanctions: (id: number) => get<Sanction[]>(`/api/public/seasons/${id}/sanctions`),
}

export const championApi = {
  history: () => get<CategoryHistory[]>('/api/public/champions'),
}

export const importApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return upload<ImportPreview>('/api/admin/imports', form)
  },
  apply: (id: number, overwrite: string[]) => post<ImportPreview>(`/api/admin/imports/${id}/apply`, { overwrite }),
  history: () => get<ImportHistory[]>('/api/admin/imports'),
  export: (seasonId: number, year: number) => download(`/api/admin/imports/export/${seasonId}`, `TORNEO ${year}.xlsx`),
}

export const zoneApi = {
  standings: (zoneId: number) => get<StandingRow[]>(`/api/public/zones/${zoneId}/standings`),
  fixtures: (zoneId: number) => get<Match[]>(`/api/public/zones/${zoneId}/fixtures`),
  roundRobin: (zoneId: number, start: string, minutesBetween: number) =>
    post<Match[]>(`/api/admin/zones/${zoneId}/fixtures/round-robin?start=${encodeURIComponent(start)}&minutesBetween=${minutesBetween}`),
  addMatch: (zoneId: number, homeTeamId: number, awayTeamId: number, scheduledAt: string | null) =>
    post<Match[]>('/api/admin/fixtures/manual', { zoneId, fixtures: [{ homeTeamId, awayTeamId, scheduledAt }] }),
}
