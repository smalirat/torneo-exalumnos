// Mirrors backend ResponseDtos / AuthDtos (dto/ResponseDtos.java)

export type TournamentStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'LIVE' | 'COMPLETE'

export interface Tournament {
  id: number
  name: string
  location: string | null
  registrationStart: string | null
  registrationEnd: string | null
  matchStart: string | null
  matchEnd: string | null
  status: TournamentStatus
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

export interface Match {
  id: number
  tournamentId: number
  zoneId: number | null
  type: 'REGULAR' | 'PROMOTION' | 'FINAL'
  homeTeam: TeamRef
  awayTeam: TeamRef
  scheduledAt: string | null
  status: 'SCHEDULED' | 'FINISHED'
  homeGoals: number
  awayGoals: number
  homePenalties: number | null
  awayPenalties: number | null
  winner: TeamRef | null
  homePresent: boolean
  awayPresent: boolean
}

export interface TeamSummary {
  id: number
  name: string
  captainId: number | null
  playerCount: number
}

export interface Player {
  id: number
  fullName: string
  jerseyNo: number
  teamId: number
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

export interface ScoreReq {
  homePresent: boolean
  awayPresent: boolean
  events: { playerId: number; type: EventType }[]
  homePenalties: number | null
  awayPenalties: number | null
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
