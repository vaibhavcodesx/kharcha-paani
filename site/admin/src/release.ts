// The app-version switch in words. Pure, so the sentence the owner reads before confirming is tested.
// Rules mirror the server (admin_set_app_versions); the server enforces them, the page explains them first.

export interface Facts {
  min: number
  recommended: number
  update_screen: number | null
  latest_published: number | null
}

export type Plan =
  | { ok: true; raises: boolean; sentence: string }
  | { ok: false; reason: string }

/** Checks a proposed change and, when allowed, writes who it affects. */
export function planVersionChange(facts: Facts, nextMin: number, nextRecommended: number): Plan {
  if (!Number.isInteger(nextMin) || !Number.isInteger(nextRecommended) || nextMin < 0 || nextRecommended < 0) {
    return { ok: false, reason: 'Version codes are whole numbers from 0 up.' }
  }
  if (nextRecommended < nextMin) return { ok: false, reason: 'The recommended version cannot be below the minimum.' }
  const raises = nextMin > facts.min
  if (raises && facts.update_screen === null) {
    return { ok: false, reason: 'Record which version has the update screen first. Without it, phones below the minimum stop syncing and cannot say why.' }
  }
  const ceiling = facts.latest_published ?? 0
  if (nextMin > ceiling || nextRecommended > ceiling) {
    return {
      ok: false,
      reason: facts.latest_published === null
        ? 'Record the latest version published on Play first.'
        : `Neither number can be above ${facts.latest_published}, the latest version on Play.`,
    }
  }
  if (!raises) {
    return { ok: true, raises: false, sentence: nextMin === facts.min && nextRecommended === facts.recommended ? 'Nothing changes.' : 'Lowering is safe: nobody gets locked out.' }
  }
  return {
    ok: true,
    raises: true,
    sentence: `Phones on version ${nextMin - 1} or lower stop syncing until they update. They can still open and export their data. Phones older than 1.8 ignore this.`,
  }
}

/** Release facts only go up (the server allows a typo fix for 10 minutes); this is the plain pre-check. */
export function checkFacts(current: { update_screen: number | null; latest_published: number | null }, update: number | null, latest: number | null): string | null {
  if ((update !== null && update < 0) || (latest !== null && latest < 0)) return 'Version codes cannot be negative.'
  if (update !== null && latest !== null && update > latest) return 'The update screen cannot be newer than the latest version on Play.'
  void current
  return null
}

/** The pair to propose when one number is edited. Raising the minimum lifts the recommended version with it, because
 *  the recommended version can never be below the minimum. */
export function proposeVersions(facts: Facts, edited: 'min' | 'recommended', value: number): { min: number; recommended: number } {
  if (edited === 'min') return { min: value, recommended: Math.max(facts.recommended, value) }
  return { min: facts.min, recommended: value }
}
