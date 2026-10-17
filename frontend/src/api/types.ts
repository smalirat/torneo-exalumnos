// Mirrors backend ResponseDtos / AuthDtos (dto/ResponseDtos.java)

/** Moved by hand by the admin; only one edition can be OPEN or LIVE at a time */
export type TournamentStatus = 'DRAFT' | 'OPEN' | 'LIVE' | 'COMPLETE'

export interface Tournament {
  id: number
  name: string
  location: string | null
  registrationStart: string | null
  registrationEnd: string | null
  matchStart: string | null
  matchEnd: string | null
  status: TournamentStatus
  seasonId: number | null
  seasonYear: number | null
  stage: 'APERTURA' | 'CLAUSURA' | null
}

export interface Season {
  id: number
  year: number
  tournaments: Tournament[]
}

export interface Zone {
  id: number
  categoryId: number
  name: string
  displayOrder: number
}

export interface Category {
  id: number
  tournamentId: number
  name: string
  displayOrder: number
  zoneCount: number
  zones: Zone[]
}

export interface TeamRef {
  id: number
  name: string
  /** URL of the crest image; null/absent when the team has none (then no badge is shown) */
  crest?: string | null
}

export interface StandingRow {
  position: number
  team: TeamRef
  points: number
  played: number
  won: number
  drawn: number
  lost: number
  goalsFor: number
  goalsAgainst: number
  goalDiff: number
  presencePoints: number
  fairPlay: number
}

export interface SlotRes {
  zoneId: number
  zoneName: string
  zoneCount: number
  categoryName: string
  tournamentId: number
  tournamentName: string
  position: number
}

/** One side of a promotion/final: a fixed team or "position N of zone X". */
export type SlotReq = { teamId: number } | { zoneId: number; position: number }

export interface Match {
  id: number
  tournamentId: number
  zoneId: number | null
  type: 'REGULAR' | 'PROMOTION' | 'FINAL'
  homeTeam: TeamRef
  awayTeam: TeamRef
  homeSource: SlotRes | null
  awaySource: SlotRes | null
  scheduledAt: string | null
  round: number | null
  roundLabel: string | null
  venue: string | null
  pitch: string | null
  notes: string | null
  status: 'SCHEDULED' | 'FINISHED'
  homeGoals: number
  awayGoals: number
  homePenalties: number | null
  awayPenalties: number | null
  winner: TeamRef | null
  homePresent: boolean
  awayPresent: boolean
  /** Walkover already decided whose date has not arrived: not counted in the table yet */
  advanceWalkover: boolean
}

export interface TeamSummary {
  id: number
  name: string
  crest?: string | null
  captainId: number
  /** null while the captain is pending (nobody claimed the team yet) */
  captainUsername: string | null
  captainPending: boolean
  playerCount: number
}

export interface InviteRes { token: string; teamName: string }

export interface Player {
  id: number
  fullName: string
  jerseyNo: number
  teamId: number
}

export interface TeamRegistration {
  registrationId: number
  tournament: Tournament
  categoryId: number | null
  categoryName: string | null
  zoneId: number | null
  zoneName: string | null
  zoneCount: number
}

export interface Registration {
  id: number
  tournamentId: number
  categoryId: number | null
  zoneId: number | null
  team: TeamRef
  approved: boolean
}

export type EventType = 'GOAL' | 'OWN_GOAL' | 'YELLOW_CARD' | 'RED_CARD'

export interface PlayerRef {
  id: number
  fullName: string
  jerseyNo: number
}

export interface MatchEvent {
  id: number
  matchId: number
  player: PlayerRef
  team: TeamRef
  type: EventType
}

export interface ScorerRow {
  player: PlayerRef
  team: TeamRef
  goals: number
}

export interface CardRow {
  player: PlayerRef
  team: TeamRef
  yellowCards: number
  redCards: number
}

export interface ScoreReq {
  homePresent: boolean
  awayPresent: boolean
  homeGoals?: number | null
  awayGoals?: number | null
  events: { playerId: number; type: EventType }[]
  homePenalties: number | null
  awayPenalties: number | null
}

export interface MatchDetails {
  scheduledAt: string | null
  round: number | null
  roundLabel: string | null
  venue: string | null
  pitch: string | null
  notes: string | null
}

export interface SeasonStatRow {
  position: number
  player: PlayerRef
  team: TeamRef
  value: number
}

export interface Sanction {
  id: number
  seasonId: number
  tournamentId: number | null
  player: PlayerRef
  team: TeamRef
  type: 'DIRECT_RED' | 'DOUBLE_YELLOW' | 'YELLOW_ACCUMULATION' | 'OTHER'
  matchesSuspended: number
  matchesServed: number
  pending: number
  status: 'PENDING' | 'SERVED' | 'TRIBUNAL'
  notes: string | null
  source: 'EXCEL' | 'WEB' | null
}

export interface YellowTally {
  player: PlayerRef
  team: TeamRef
  yellowCards: number
  suspensions: number
}

/** Champions' collage photo. x, y, w: fractions of the board width (top-left corner, width); z: layer. */
export interface CollagePhoto {
  id: number
  url: string
  width: number
  height: number
  x: number
  y: number
  w: number
  z: number
  featured: boolean
}

export type CollageItem = Pick<CollagePhoto, 'id' | 'x' | 'y' | 'w' | 'z'>

export interface CategoryHistory {
  categoryName: string
  champions: { id: number; year: number; categoryName: string; team: TeamRef }[]
  topChampions: { team: TeamRef; titles: number }[]
}

export interface ImportCounter {
  entity: string
  created: number
  updated: number
  unchanged: number
  conflicts: number
}

export interface ImportConflict {
  key: string
  entity: string
  description: string
  excelValue: string
  webValue: string
  overwritten: boolean
}

export interface ImportPreview {
  importId: number
  fileName: string
  status: 'PREVIEW' | 'APPLIED'
  seasonYear: number | null
  counters: ImportCounter[]
  changes: { entity: string; action: 'CREATE' | 'UPDATE'; description: string }[]
  conflicts: ImportConflict[]
  warnings: string[]
  notices: string[]
}

export interface ImportHistory {
  id: number
  fileName: string
  uploadedBy: string | null
  createdAt: string
  appliedAt: string | null
  status: 'PREVIEW' | 'APPLIED'
  seasonYear: number | null
  summary: string | null
}

export interface TokenRes {
  token: string
  tokenType: string
  expiresInMinutes: number
}

export interface Me {
  id: number
  username: string
  role: 'ADMIN' | 'CAPTAIN'
  teamIds: number[]
}
