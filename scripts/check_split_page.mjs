// Runs the arithmetic inside site/split.html against the same cases the app's ItemSplitTest uses
// (app/src/test/resources/item-split-vectors.json), so the page a friend opens and the app that
// saves the expense can't drift apart by a paisa.
//
//   node scripts/check_split_page.mjs            check, exit 1 on any difference
//   node scripts/check_split_page.mjs --fill     write what the page computes into the file as the
//                                                expected values (review the diff before committing)
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const page = readFileSync(join(root, 'site', 'split.html'), 'utf8')
const source = page.split('/* split-math:start */')[1]?.split('/* split-math:end */')[0]
if (!source) {
  console.error('site/split.html has no split-math markers')
  process.exit(1)
}
const { computeShares, rupees } = new Function(`${source}; return { computeShares, rupees };`)()

const vectorsPath = join(root, 'app', 'src', 'test', 'resources', 'item-split-vectors.json')
const vectors = JSON.parse(readFileSync(vectorsPath, 'utf8'))
const fill = process.argv.includes('--fill')
let failed = 0

for (const vector of vectors.cases) {
  const got = computeShares(vector.split)
  const actual = {
    people: got.people,
    unclaimed_count: got.unclaimedCount,
    unclaimed_paise: got.unclaimedPaise,
    items_paise: got.itemsPaise,
    extras_paise: got.extrasPaise,
  }
  if (fill) {
    vector.expect = actual
  } else if (JSON.stringify(actual) !== JSON.stringify(vector.expect)) {
    failed++
    console.error(`FAIL  ${vector.name}\n  expected ${JSON.stringify(vector.expect)}\n  got      ${JSON.stringify(actual)}`)
  } else {
    console.log(`PASS  ${vector.name}`)
  }
}

for (const [paise, text] of Object.entries(vectors.rupees)) {
  if (rupees(Number(paise)) !== text) {
    failed++
    console.error(`FAIL  rupees(${paise}): expected ${text}, got ${rupees(Number(paise))}`)
  }
}

if (fill) {
  writeFileSync(vectorsPath, JSON.stringify(vectors, null, 2) + '\n')
  console.log(`filled ${vectors.cases.length} cases`)
} else {
  console.log(failed === 0 ? `\nall ${vectors.cases.length} cases match` : `\n${failed} failed`)
  process.exit(failed === 0 ? 0 : 1)
}
