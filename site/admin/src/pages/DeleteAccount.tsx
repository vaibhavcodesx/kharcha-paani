import { useEffect, useId, useState } from 'react'
import { useAuth } from '../auth'
import { Segmented } from '../controls'
import {
  REMOVE_BATCH, chunk, describeHeirs, emailMatches, parseLeftover, summariseCleanup,
  type CleanupOutcome, type FoundAccount,
} from '../deleteflow'
import { formatDay } from '../format'
import { AdminError, callRpc } from '../rpc'
import { supabase } from '../supabase'
import { Sheet } from '../ui'

// Deleting an account, as a short guided flow in one sheet: look up, see what it does, type the email, delete, then
// remove the photo files the database cannot remove. Every guard is also enforced by the server.

type Step =
  | { kind: 'ask' }
  | { kind: 'looking' }
  | { kind: 'preview'; account: FoundAccount }
  | { kind: 'deleting'; account: FoundAccount }
  | { kind: 'cleaning'; account: FoundAccount; total: number }
  | { kind: 'done'; account: FoundAccount; outcome: CleanupOutcome; logged: boolean }
  | { kind: 'stale'; account: FoundAccount }

async function removePhotos(paths: string[]): Promise<string[][]> {
  const failed: string[][] = []
  for (const batch of chunk(paths, REMOVE_BATCH)) {
    const { data, error } = await supabase.storage.from('receipts').remove(batch)
    if (error) {
      failed.push(batch)
      continue
    }
    const gone = new Set((data ?? []).map((o) => o.name))
    const left = batch.filter((p) => !gone.has(p))
    if (left.length > 0) failed.push(left)
  }
  return failed
}

export function DeleteAccountSheet({ open, initialEmail, onClose, onDeleted }: {
  open: boolean
  initialEmail: string | null
  onClose: () => void
  onDeleted: () => void
}) {
  const { signIn } = useAuth()
  const [step, setStep] = useState<Step>({ kind: 'ask' })
  const [email, setEmail] = useState('')
  const [typed, setTyped] = useState('')
  const [keepName, setKeepName] = useState<'former' | 'keep'>('former')
  const [error, setError] = useState<string | null>(null)
  const emailId = useId()
  const typedId = useId()

  const lookup = async (address: string) => {
    setError(null)
    setStep({ kind: 'looking' })
    try {
      const rows = await callRpc<FoundAccount[]>(supabase, 'admin_find_account', { p_email: address })
      const account = rows[0]
      if (!account || account.deleted) {
        setStep({ kind: 'ask' })
        setError('No live account has that email. It may already be deleted, or the address may differ from the sign-in one.')
        return
      }
      setTyped('')
      setKeepName('former')
      setStep({ kind: 'preview', account })
    } catch (e) {
      setStep({ kind: 'ask' })
      setError((e as AdminError).message)
    }
  }

  // Each time the sheet opens: start from the person it was opened for, or ask for an email.
  useEffect(() => {
    if (!open) return
    setError(null)
    setTyped('')
    setEmail(initialEmail ?? '')
    if (initialEmail) void lookup(initialEmail)
    else setStep({ kind: 'ask' })
  }, [open, initialEmail])

  const run = async (account: FoundAccount) => {
    setError(null)
    setStep({ kind: 'deleting', account })
    try {
      const rows = await callRpc<unknown>(supabase, 'admin_delete_account', {
        p_user: account.user_id,
        p_confirm_email: typed,
        p_keep_name: keepName === 'keep',
      })
      const paths = parseLeftover(rows)
      setStep({ kind: 'cleaning', account, total: paths.length })
      const failed = paths.length > 0 ? await removePhotos(paths) : []
      const outcome = summariseCleanup(paths, failed)
      let logged = true
      if (paths.length > 0) {
        try {
          await callRpc<number>(supabase, 'admin_record_photo_cleanup', {
            p_user: account.user_id,
            p_removed: outcome.kind === 'done' || outcome.kind === 'partial' ? outcome.removed : 0,
          })
        } catch {
          logged = false
        }
      }
      onDeleted()
      setStep({ kind: 'done', account, outcome, logged })
    } catch (e) {
      const err = e as AdminError
      if (err.kind === 'signed-out') {
        setStep({ kind: 'stale', account })
      } else {
        setStep({ kind: 'preview', account })
        setError(err.message)
      }
    }
  }

  const account = 'account' in step ? step.account : null
  const busy = step.kind === 'looking' || step.kind === 'deleting' || step.kind === 'cleaning'

  return (
    <Sheet open={open} onClose={() => !busy && onClose()} title={step.kind === 'done' ? 'Account deleted' : 'Delete an account'}>
      {(step.kind === 'ask' || step.kind === 'looking') && (
        <form className="fieldrow" onSubmit={(e) => { e.preventDefault(); if (email.trim()) void lookup(email.trim()) }}>
          <label htmlFor={emailId}>The email the person signed in with</label>
          <input id={emailId} className="field" type="email" value={email} autoComplete="off" autoCapitalize="off" spellCheck={false} onChange={(e) => setEmail(e.target.value)} />
          <p className="muted">Check that the request came from that Google account before you go on.</p>
          {error && <p className="fielderr" role="alert">{error}</p>}
          <div className="actions">
            <button type="button" className="btn" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={!email.trim() || step.kind === 'looking'}>{step.kind === 'looking' ? 'Looking' : 'Look up'}</button>
          </div>
        </form>
      )}

      {(step.kind === 'preview' || step.kind === 'deleting') && account && (
        <>
          <dl className="facts">
            <dt>Account</dt><dd>{account.email}</dd>
            <dt>Name</dt><dd>{account.display_name ?? 'none'}</dd>
            <dt>Joined</dt><dd className="num">{formatDay(account.created_at)}</dd>
            <dt>Last sign-in</dt><dd className="num">{formatDay(account.last_sign_in_at)}</dd>
            <dt>Groups</dt><dd><span className="num">{account.groups}</span> (made <span className="num">{account.groups_created}</span>)</dd>
            <dt>Bill photos</dt><dd className="num">{account.photos}</dd>
          </dl>
          <p>
            The sign-in and everything private go: personal expenses, private splits, friends who are not on the app, the profile details.
            Expenses in shared groups stay, because other members’ balances depend on them. {describeHeirs(account.heirs)}
          </p>
          <div className="fieldrow">
            <span>On past group expenses, show them as</span>
            <Segmented label="Name on shared history" value={keepName} onChange={setKeepName}
              options={[{ id: 'former', label: 'Former member' }, { id: 'keep', label: 'Keep their name' }]} disabled={step.kind === 'deleting'} />
          </div>
          <div className="fieldrow">
            <label htmlFor={typedId}>Type <strong className="num">{account.email}</strong> to confirm</label>
            <input id={typedId} className="field" value={typed} autoComplete="off" autoCapitalize="off" spellCheck={false} onChange={(e) => setTyped(e.target.value)} disabled={step.kind === 'deleting'} />
          </div>
          <p className="warn"><span>This cannot be undone. Take a database dump first if you have not today.</span></p>
          {error && <p className="fielderr" role="alert">{error}</p>}
          <div className="actions">
            <button className="btn" onClick={onClose} disabled={step.kind === 'deleting'}>Cancel</button>
            <button className="btn btn-danger" disabled={!emailMatches(typed, account.email) || step.kind === 'deleting'} onClick={() => void run(account)}>
              {step.kind === 'deleting' ? 'Deleting' : 'Delete permanently'}
            </button>
          </div>
        </>
      )}

      {step.kind === 'cleaning' && (
        <p role="status">Account deleted. Removing {step.total} bill {step.total === 1 ? 'photo' : 'photos'} from storage. Keep this open.</p>
      )}

      {step.kind === 'stale' && (
        <>
          <p>Deleting needs a sign-in from the last 10 minutes, and this one is older. Sign in again, then repeat the steps. Nothing was deleted.</p>
          <div className="actions">
            <button className="btn" onClick={onClose}>Cancel</button>
            <button className="btn btn-primary" onClick={() => void signIn()}>Sign in again</button>
          </div>
        </>
      )}

      {step.kind === 'done' && (
        <>
          <p>
            {step.account.email} was deleted. Shared groups kept their history.{' '}
            {step.outcome.kind === 'none' && 'No bill photos were left behind.'}
            {step.outcome.kind === 'done' && <>All <span className="num">{step.outcome.removed}</span> bill {step.outcome.removed === 1 ? 'photo was' : 'photos were'} removed from storage.</>}
          </p>
          {step.outcome.kind === 'partial' && <LeftoverList removed={step.outcome.removed} failed={step.outcome.failed} />}
          {!step.logged && <p className="muted">The photo cleanup could not be added to the Log. The deletion itself is logged.</p>}
          <div className="actions"><button className="btn btn-primary" onClick={onClose}>Done</button></div>
        </>
      )}
    </Sheet>
  )
}

function LeftoverList({ removed, failed }: { removed: number; failed: string[] }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(failed.join('\n'))
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }
  return (
    <div className="fieldrow">
      <p className="warn"><span><span className="num">{removed}</span> {removed === 1 ? 'photo was' : 'photos were'} removed, but <span className="num">{failed.length}</span> could not be. Delete {failed.length === 1 ? 'this file' : 'these files'} by hand in Supabase, Storage, receipts:</span></p>
      <textarea className="field paths" readOnly value={failed.join('\n')} rows={Math.min(8, failed.length)} aria-label="Files to delete by hand" />
      <button className="btn btn-small" onClick={() => void copy()}>{copied ? 'Copied' : 'Copy the list'}</button>
    </div>
  )
}
