import type { Sanction } from './api/types'

/** Still to serve (pending or at the tribunal) vs already served */
export function splitSanctions(rows: Sanction[]) {
  return {
    open: rows.filter((s) => s.status !== 'SERVED'),
    served: rows.filter((s) => s.status === 'SERVED'),
  }
}
