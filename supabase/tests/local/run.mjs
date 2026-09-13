// Replays supabase/migrations on an in-memory Postgres (PGlite) with a Supabase-style bootstrap,
// then runs a rolled-back test script and prints its results.
//   npm install @electric-sql/pglite && node supabase/tests/local/run.mjs [test.sql]
import { PGlite } from '@electric-sql/pglite'
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
// Overridable so a copy can run beside an existing node_modules without installing into the repo.
const testsDir = process.env.KP_TESTS_DIR ?? join(here, '..')
const migrationsDir = process.env.KP_MIGRATIONS_DIR ?? join(testsDir, '..', 'migrations')
const testFile = process.argv[2] ?? join(testsDir, 'rls_groups_test.sql')

const db = new PGlite()
await db.exec(readFileSync(join(testsDir, 'local', 'bootstrap.sql'), 'utf8'))
for (const file of readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort()) {
  try {
    await db.exec(readFileSync(join(migrationsDir, file), 'utf8'))
    console.log(`migrated  ${file}`)
  } catch (e) {
    console.error(`FAILED    ${file}: ${e.message}`)
    process.exit(2)
  }
}

const results = (await db.exec(readFileSync(testFile, 'utf8')))
  .find((r) => r.fields.some((f) => f.name === 'passed'))?.rows ?? []
let failed = 0
for (const r of results) {
  if (!r.passed) failed++
  console.log(`${r.passed ? 'PASS' : 'FAIL'}  ${String(r.n).padStart(2)}  ${r.test}${r.passed ? '' : `  ->  ${r.detail}`}`)
}
console.log(`\n${results.length - failed}/${results.length} passed`)
const leftovers = await db.query("select count(*)::int as n from auth.users")
console.log(`rolled back cleanly: ${leftovers.rows[0].n === 0}`)
process.exit(failed === 0 && results.length > 0 ? 0 : 1)
