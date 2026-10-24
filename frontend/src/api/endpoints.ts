import { api, del, download, get, patch, post, put, upload } from './client'
import type {
  BuildDefinition, BuildProposal, CardRow, Category, CategoryHistory, CollageItem, CollagePhoto, InviteRes, MyRequests, RequestItem, RequestType, ImportHistory, ImportPreview, Match, MatchDetails, MatchEvent, Me, Player,
  Registration, Sanction, ScoreReq, ScorerRow, Season, SeasonStatRow, SlotReq, StandingRow, TeamRef, TeamRegistration,
  TeamSummary, TokenRes, Tournament, TournamentStatus, YellowTally, Zone,
} from './types'

export const leagueApi = {
  info: () => get<{ title: string }>('/api/public/league'),
}

export const authApi = {
  login: (username: string, password: string) => post<TokenRes>('/api/auth/login', { username, password }),
  /** Signing up goes with a team: a new one (teamName) or one without captain (claimTeamId); both need approval */
  register: (username: string, password: string, team: SignUpTeam, contact: Contact) =>
    post<TokenRes>('/api/auth/register', { username, password, ...team, ...contact }),
  me: () => get<Me>('/api/auth/me'),
  /** "Mis datos": the user's own email and phone */
  updateMe: (contact: Contact) => put<Me>('/api/auth/me', contact),
  /** Captain invitations: teams created by the Excel / an admin start with a pending captain */
  invite: (teamId: number) => post<InviteRes>(`/api/admin/teams/${teamId}/invite`),
  inviteInfo: (token: string) => get<{ teamName: string }>(`/api/auth/invites/${token}`),
  claim: (token: string, username: string, password: string, contact: Contact) =>
    post<TokenRes>('/api/auth/claim', { token, username, password, ...contact }),
  acceptInvite: (token: string) => post<Me>(`/api/me/invites/${token}/accept`),
}

export type SignUpTeam = { teamName: string } | { claimTeamId: number }

/** A captain's contact, asked when signing up (private: the user and the admin) */
export type Contact = { email: string; phone: string }

/** Body for creating/updating a tournament (status is ignored on create). */
export interface TournamentInput {
  name: string
  /** Informative only: shown to the public, they do not open or close registration */
  registrationStart: string | null
  registrationEnd: string | null
  matchStart: string | null
  matchEnd: string | null
  seasonYear: number | null
  stage: 'APERTURA' | 'CLAUSURA' | null
}

export const tournamentApi = {
  list: () => get<Tournament[]>('/api/public/tournaments'),
  fixtures: (id: number) => get<Match[]>(`/api/public/tournaments/${id}/fixtures`),
  create: (body: TournamentInput) => post<Tournament>('/api/admin/tournaments', body),
  update: (id: number, body: TournamentInput) => put<Tournament>(`/api/admin/tournaments/${id}`, body),
  changeStatus: (id: number, status: TournamentStatus, finishActive = false) =>
    patch<Tournament>(`/api/admin/tournaments/${id}/status`, { status, finishActive }),
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
  withdraw: (registrationId: number, date: string | null) =>
    post<Registration>(`/api/admin/registrations/${registrationId}/withdraw`, { date }),
  undoWithdraw: (registrationId: number) => api<Registration>('DELETE', `/api/admin/registrations/${registrationId}/withdraw`),
  assignZone: (registrationId: number, zoneId: number) =>
    patch<Registration>(`/api/admin/registrations/${registrationId}/zone`, { zoneId }),
  createZone: (categoryId: number, name: string) => post<Zone>(`/api/admin/categories/${categoryId}/zones`, { name }),
  drawZones: (categoryId: number, registrationIds: number[]) =>
    post<Registration[]>(`/api/admin/categories/${categoryId}/zone-draw`, { registrationIds }),
  /** Banner (cover) or logo of the edition */
  setImage: (id: number, kind: 'banner' | 'logo', file: Blob) => {
    const form = new FormData()
    form.append('file', file, kind === 'banner' ? 'banner.jpg' : 'logo.png')
    return upload<Tournament>(`/api/admin/tournaments/${id}/images/${kind}`, form, 'PUT')
  },
  removeImage: (id: number, kind: 'banner' | 'logo') => api<Tournament>('DELETE', `/api/admin/tournaments/${id}/images/${kind}`),
  /** Admin: a team straight into a zone (an existing one, or a new one by name) and out of the edition */
  addZoneTeam: (zoneId: number, team: { teamId: number } | { teamName: string }) =>
    post<Registration>(`/api/admin/zones/${zoneId}/teams`, team),
  removeRegistration: (registrationId: number) => del(`/api/admin/registrations/${registrationId}`),
  /** Interzonal / intercategory match days crossed by the current table positions (preview saves nothing) */
  interzonal: (id: number, preview: boolean, body: {
    pairs: { zoneA: number; zoneB: number }[]
    rounds: number
    firstRound: number | null
    start: string | null
    minutesBetween: number
    crossing: 'SAME' | 'INVERSE'
    venue: string | null
  }) => post<Match[]>(`/api/admin/tournaments/${id}/interzonal?preview=${preview}`, body),
  buildProposal: (id: number, sourceId: number) => get<BuildProposal>(`/api/admin/tournaments/${id}/build/proposal?from=${sourceId}`),
  build: (id: number, body: {
    assignments: { teamId: number; categoryId: number }[]
    zoneMode: 'BALANCED' | 'RANDOM'
    fixtureStart: string | null
    minutesBetween: number
    interzonal: { pairs: { zoneA: number; zoneB: number }[]; rounds: number; crossing: 'SAME' | 'INVERSE' } | null
    definitions: BuildDefinition[]
  }) => post<{ teams: number; matches: number; definitions: number }>(`/api/admin/tournaments/${id}/build`, body),
}

/** URL of an edition's banner / logo, or null when it has none (the version busts the cache) */
export function editionImage(t: Pick<Tournament, 'id' | 'bannerVersion' | 'logoVersion'>, kind: 'banner' | 'logo') {
  const v = kind === 'banner' ? t.bannerVersion : t.logoVersion
  return v == null ? null : `/api/public/tournaments/${t.id}/images/${kind}?v=${v}`
}

export const teamApi = {
  list: () => get<TeamSummary[]>('/api/teams'),
  create: (name: string) => post<{ id: number; name: string }>('/api/teams/register', { name }),
  /** Teams without captain yet: a new captain can ask for one */
  unclaimed: () => get<TeamRef[]>('/api/public/teams/unclaimed'),
  rename: (teamId: number, name: string) => put<{ id: number; name: string }>(`/api/teams/${teamId}`, { name }),
  remove: (teamId: number) => del(`/api/teams/${teamId}`),
  get: (teamId: number) => get<TeamRef>(`/api/public/teams/${teamId}`),
  registrations: (teamId: number) => get<TeamRegistration[]>(`/api/public/teams/${teamId}/registrations`),
  /** Public roster; former = also the ones that left (to edit old results) */
  players: (teamId: number, former = false) => get<Player[]>(`/api/public/teams/${teamId}/players${former ? '?former=true' : ''}`),
  addPlayer: (teamId: number, fullName: string, jerseyNo: number, dni: string | null) =>
    post<Player[]>(`/api/teams/${teamId}/players`, { players: [{ fullName, jerseyNo, dni }] }),
  deletePlayer: (teamId: number, playerId: number) => del(`/api/teams/${teamId}/players/${playerId}`),
  /** Full roster for its captain or the admin, players waiting for approval included */
  roster: (teamId: number) => get<Player[]>(`/api/teams/${teamId}/roster`),
  /** Jersey number and/or DNI, saved directly (blank DNI = none) */
  updatePlayer: (teamId: number, playerId: number, change: { jerseyNo?: number; dni?: string }) =>
    patch<Player>(`/api/teams/${teamId}/players/${playerId}`, change),
  setCrest: (teamId: number, image: Blob) => {
    const form = new FormData()
    form.append('file', image, 'escudo.png')
    return upload<TeamRef>(`/api/teams/${teamId}/crest`, form, 'PUT')
  },
  removeCrest: (teamId: number) => api<TeamRef>('DELETE', `/api/teams/${teamId}/crest`),
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

export const collageApi = {
  list: () => get<CollagePhoto[]>('/api/public/collage'),
  upload: (file: Blob, name: string) => {
    const form = new FormData()
    form.append('file', file, name)
    return upload<CollagePhoto>('/api/admin/collage', form)
  },
  layout: (items: CollageItem[]) => put<CollagePhoto[]>('/api/admin/collage/layout', { items }),
  featured: (id: number, featured: boolean) => put<CollagePhoto[]>(`/api/admin/collage/${id}/featured`, { featured }),
  remove: (id: number) => del(`/api/admin/collage/${id}`),
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
  registrations: (zoneId: number) => get<Registration[]>(`/api/public/zones/${zoneId}/registrations`),
  roundRobin: (zoneId: number, start: string, minutesBetween: number) =>
    post<Match[]>(`/api/admin/zones/${zoneId}/fixtures/round-robin?start=${encodeURIComponent(start)}&minutesBetween=${minutesBetween}`),
  addMatch: (zoneId: number, homeTeamId: number, awayTeamId: number, scheduledAt: string | null) =>
    post<Match[]>('/api/admin/fixtures/manual', { zoneId, fixtures: [{ homeTeamId, awayTeamId, scheduledAt }] }),
  /** A whole match day at once: every pair with its time, the same round number and field */
  addRound: (zoneId: number, fixtures: { homeTeamId: number; awayTeamId: number; scheduledAt: string | null; round: number | null; venue: string | null; pitch: string | null }[]) =>
    post<Match[]>('/api/admin/fixtures/manual', { zoneId, fixtures }),
}

/** Captains' changes waiting for the admin */
export const requestApi = {
  list: () => get<RequestItem[]>('/api/admin/requests'),
  count: () => get<{ count: number }>('/api/admin/requests/count'),
  approve: (type: RequestType, id: number) => post<void>(`/api/admin/requests/${type}/${id}/approve`),
  reject: (type: RequestType, id: number, reason: string | null) => post<void>(`/api/admin/requests/${type}/${id}/reject`, { reason }),
  approveAll: (teamId: number) => post<{ approved: number }>(`/api/admin/requests/teams/${teamId}/approve-all`),
  mine: () => get<MyRequests>('/api/me/requests'),
  /** A user without team asks to be the captain of a team that has none */
  claim: (teamId: number) => post<void>('/api/me/claims', { teamId }),
}
