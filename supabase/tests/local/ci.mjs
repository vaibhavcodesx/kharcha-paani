// Runs EVERY supabase/tests/*_test.sql on an in-memory Postgres (PGlite), the way CI does, and exits 1 on any
// unexpected failure. Each file gets a fresh database with the Supabase-style bootstrap and every migration replayed.
//   cd supabase/tests/local && npm install --no-save --ignore-scripts --save-exact @electric-sql/pglite@0.5.16
//   node supabase/tests/local/ci.mjs [name-fragment]
//
// Two kinds of test file exist: those that end by selecting a result table (n, test, passed, detail), and the older
// ones that end by raising an error whose message says "(rolled back)" and carries their results. Both are handled.
// `known-failures.txt` lists checks that fail only because the local stand-in differs from Supabase; see its header.
import { PGlite } from '@electric-sql/pglite'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const testsDir = join(here, '..')
const migrationsDir = join(testsDir, '..', 'migrations')
const only = process.argv[2]

const known = new Set(
  (existsSync(join(here, 'known-failures.txt')) ? readFileSync(join(here, 'known-failures.txt'), 'utf8') : '')
    .split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#')),
)

const bootstrap = readFileSync(join(here, 'bootstrap.sql'), 'utf8')
const migrations = readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort()
  .map((f) => [f, readFileSync(join(migrationsDir, f), 'utf8')])

async function runFile(file) {
  const db = new PGlite()
  await db.exec(bootstrap)
  for (const [name, sql] of migrations) {
    try {
      await db.exec(sql)
    } catch (e) {
      return { checks: [], fatal: `migration ${name} failed: ${e.message}` }
    }
  }
  try {
    const results = await db.exec(readFileSync(join(testsDir, file), 'utf8'))
    const table = results.find((r) => r.fields.some((f) => f.name === 'passed'))
    if (table) return { checks: table.rows.map((r) => ({ name: r.test, passed: r.passed, detail: r.detail })) }
    return { checks: [], fatal: 'the file produced no result table and did not raise a result message' }
  } catch (e) {
    const m = String(e.message ?? e)
    // The older style: a raised message that ends the rolled-back test and says how it went.
    if (/\(rolled back\)/.test(m) && !/FAIL/.test(m.replace(/raise exception 'FAIL[^']*'/g, ''))) {
      return { checks: [{ name: m.split('\n')[0].slice(0, 120), passed: true }] }
    }
    return { checks: [], fatal: m.split('\n')[0].slice(0, 300) }
  }
}

const files = readdirSync(testsDir).filter((f) => f.endsWith('_test.sql') && (!only || f.includes(only))).sort()
let unexpected = 0
let total = 0
let passedTotal = 0
for (const file of files) {
  const { checks, fatal } = await runFile(file)
  if (fatal) {
    unexpected++
    console.log(`FAIL  ${file}  ->  ${fatal}`)
    continue
  }
  const bad = checks.filter((c) => !c.passed)
  const surprises = bad.filter((c) => !known.has(`${file}: ${c.name}`))
  const forgiven = bad.length - surprises.length
  const fixed = [...known].filter((k) => k.startsWith(`${file}: `) && checks.some((c) => c.passed && `${file}: ${c.name}` === k))
  total += checks.length
  passedTotal += checks.length - bad.length
  console.log(`${surprises.length ? 'FAIL' : 'ok  '}  ${file}  ${checks.length - bad.length}/${checks.length}${forgiven ? `  (${forgiven} known local difference${forgiven > 1 ? 's' : ''})` : ''}`)
  for (const c of surprises) console.log(`        FAIL  ${c.name}  ->  ${c.detail}`)
  for (const k of fixed) console.log(`        note: now passes, remove from known-failures.txt: ${k}`)
  unexpected += surprises.length
}
console.log(`\n${passedTotal}/${total} checks passed across ${files.length} files; ${unexpected} unexpected failure${unexpected === 1 ? '' : 's'}`)
process.exit(unexpected === 0 && files.length > 0 ? 0 : 1)
