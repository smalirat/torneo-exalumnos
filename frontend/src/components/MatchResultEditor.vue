<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TeamBadge from './TeamBadge.vue'
import AppIcon from './AppIcon.vue'
import { matchApi, teamApi } from '../api/endpoints'
import type { EventType, Match, Player } from '../api/types'
import { t } from '../i18n'

// Admin editor for a match result: presence + per-player events (+ penalties for drawn specials).
// The score is derived from the events, like the backend does.
const props = defineProps<{ match: Match }>()
const emit = defineEmits<{ saved: []; close: [] }>()

type Side = 'home' | 'away'
interface Draft { side: Side; playerId: number; type: EventType }

const EVENT_TYPES: EventType[] = ['GOAL', 'OWN_GOAL', 'YELLOW_CARD', 'RED_CARD']

const rosters = ref<Record<Side, Player[]>>({ home: [], away: [] })
const present = ref<Record<Side, boolean>>({ home: props.match.homePresent, away: props.match.awayPresent })
const events = ref<Draft[]>([])
const picked = ref<Record<Side, number | null>>({ home: null, away: null })
const penalties = ref<Record<Side, number | null>>({ home: props.match.homePenalties, away: props.match.awayPenalties })
const error = ref('')
const busy = ref(false)

const teams = computed(() => ({ home: props.match.homeTeam, away: props.match.awayTeam }))
const walkover = computed(() => !present.value.home || !present.value.away)

const score = computed(() => {
  if (walkover.value) return { home: present.value.home ? 3 : 0, away: present.value.away ? 3 : 0 }
  const count = (side: Side, type: EventType) => events.value.filter((e) => e.side === side && e.type === type).length
  return {
    home: count('home', 'GOAL') + count('away', 'OWN_GOAL'),
    away: count('away', 'GOAL') + count('home', 'OWN_GOAL'),
  }
})
const needsPenalties = computed(() =>
  props.match.type !== 'REGULAR' && !walkover.value && score.value.home === score.value.away)

onMounted(async () => {
  const [home, away, existing] = await Promise.all([
    teamApi.players(props.match.homeTeam.id),
    teamApi.players(props.match.awayTeam.id),
    props.match.status === 'FINISHED' ? matchApi.events(props.match.id) : Promise.resolve([]),
  ])
  rosters.value = { home, away }
  picked.value = { home: home[0]?.id ?? null, away: away[0]?.id ?? null }
  events.value = existing.map((e) => ({
    side: e.team.id === props.match.homeTeam.id ? 'home' : 'away', playerId: e.player.id, type: e.type,
  }))
})

function addEvent(side: Side, type: EventType) {
  const playerId = picked.value[side]
  if (playerId) events.value.push({ side, playerId, type })
}

const playerName = (side: Side, id: number) => rosters.value[side].find((p) => p.id === id)?.fullName ?? '?'
const sideEvents = (side: Side) => events.value.map((e, i) => ({ ...e, i })).filter((e) => e.side === side)

async function save() {
  busy.value = true
  error.value = ''
  try {
    await matchApi.score(props.match.id, {
      homePresent: present.value.home,
      awayPresent: present.value.away,
      events: walkover.value ? [] : events.value.map(({ playerId, type }) => ({ playerId, type })),
      homePenalties: needsPenalties.value ? penalties.value.home : null,
      awayPenalties: needsPenalties.value ? penalties.value.away : null,
    })
    emit('saved')
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('common.genericError')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <form class="modal card" @submit.prevent="save">
      <header class="head">
        <h2>{{ t('results.title') }}</h2>
        <button type="button" class="icon-btn" :title="t('common.close')" @click="emit('close')">
          <AppIcon name="close" />
        </button>
      </header>

      <div class="scoreboard">
        <span class="team"><TeamBadge :name="teams.home.name" :size="40" /> {{ teams.home.name }}</span>
        <strong class="score">{{ score.home }} - {{ score.away }}</strong>
        <span class="team right">{{ teams.away.name }} <TeamBadge :name="teams.away.name" :size="40" /></span>
      </div>
      <p v-if="walkover" class="hint">{{ t('results.walkoverHint') }}</p>

      <div class="sides">
        <section v-for="side in (['home', 'away'] as const)" :key="side" class="side">
          <label class="check">
            <input v-model="present[side]" type="checkbox" /> {{ t('results.showedUp') }}
          </label>

          <template v-if="!walkover">
            <ul class="events">
              <li v-for="e in sideEvents(side)" :key="e.i">
                <span :class="['tag', e.type]">{{ t(`results.types.${e.type}`) }}</span>
                <span class="pname">{{ playerName(side, e.playerId) }}</span>
                <button type="button" class="icon-btn" :title="t('teams.removePlayer')" @click="events.splice(e.i, 1)">
                  <AppIcon name="close" :size="16" />
                </button>
              </li>
            </ul>
            <p v-if="rosters[side].length === 0" class="muted small">{{ t('results.noRoster') }}</p>
            <template v-else>
              <select v-model="picked[side]" class="select" :aria-label="t('results.player')">
                <option v-for="p in rosters[side]" :key="p.id" :value="p.id">{{ p.jerseyNo }} · {{ p.fullName }}</option>
              </select>
              <div class="type-buttons">
                <button v-for="type in EVENT_TYPES" :key="type" type="button" :class="['btn', 'secondary', 'small-btn', type]"
                        @click="addEvent(side, type)">
                  + {{ t(`results.types.${type}`) }}
                </button>
              </div>
            </template>
          </template>
        </section>
      </div>

      <div v-if="needsPenalties" class="penalties">
        <span>{{ t('results.penalties') }}</span>
        <input v-model.number="penalties.home" class="input" type="number" min="0" required :aria-label="teams.home.name" />
        <span>-</span>
        <input v-model.number="penalties.away" class="input" type="number" min="0" required :aria-label="teams.away.name" />
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <footer class="actions">
        <button type="button" class="btn secondary" @click="emit('close')">{{ t('standings.cancel') }}</button>
        <button class="btn" :disabled="busy">{{ t('standings.save') }}</button>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed; inset: 0; background: rgba(20, 39, 90, 0.45);
  display: flex; align-items: flex-start; justify-content: center; padding: 6vh 16px; z-index: 10; overflow-y: auto;
}
.modal { width: min(820px, 100%); }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.head h2 { font-size: 20px; color: var(--navy); }
.icon-btn { background: none; border: 0; color: var(--muted); cursor: pointer; display: inline-flex; }
.scoreboard { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 12px; padding: 10px 0; }
.team { display: inline-flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 500; }
.team.right { justify-content: flex-end; }
.score { font-size: 32px; color: var(--navy); min-width: 90px; text-align: center; }
.hint { text-align: center; color: var(--danger); font-size: 14px; }
.sides { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 8px; }
.side { background: var(--row-alt); border-radius: 10px; padding: 12px; }
.check { display: flex; align-items: center; gap: 8px; font-weight: 500; margin-bottom: 8px; }
.events { list-style: none; margin: 0 0 10px; padding: 0; }
.events li { display: flex; align-items: center; gap: 8px; padding: 4px 0; }
.pname { flex: 1; }
.tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px; background: #dde2ec; color: var(--navy); }
.tag.YELLOW_CARD { background: #f6d55c; color: #5a4500; }
.tag.RED_CARD { background: #e05a4f; color: #fff; }
.tag.OWN_GOAL { background: #c9cfdb; }
.type-buttons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.small-btn { padding: 4px 10px; font-size: 13px; }
.small { font-size: 13px; }
.penalties { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 16px; font-weight: 500; }
.penalties .input { width: 70px; text-align: center; }
.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 18px; }
</style>
