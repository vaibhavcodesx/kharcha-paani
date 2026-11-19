// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OverviewProvider } from '../overview'
import { Today } from './Today'
import { supabase } from '../supabase'

vi.mock('../supabase', () => ({
  supabase: {
    rpc: vi.fn(),
  },
}))

const mockRpc = vi.mocked(supabase.rpc)

const fakeOverview = (overrides = {}) => ({
  as_of: new Date().toISOString(),
  people: { total: 37, active_7d: 21, deleted: 2 },
  plans: { free: 33, friends: 4, paid: 0 },
  ai_today: [
    { feature: 'bill', used: 38, ceiling: 400 },
    { feature: 'insights', used: 750, ceiling: 800 },
    { feature: 'statement', used: 80, ceiling: 80 },
  ],
  ai_paused: false,
  storage: { objects: 311, bytes: 182 * 1024 ** 2, leftover_objects: 0, leftover_bytes: 0 },
  db: { bytes: 41 * 1024 ** 2 },
  versions: { min: 0, recommended: 0, update_screen: null, latest_published: null },
  ...overrides,
})

describe('Today page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows the people sentence and one state word per AI kind', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview(),
      error: null,
      status: 200,
    } as any)

    render(
      <OverviewProvider>
        <Today go={() => {}} />
      </OverviewProvider>
    )

    expect(await screen.findByText('37')).toBeDefined()
    expect(screen.getByText(/active this week/i)).toBeDefined()
    expect(screen.getByText(/deleted/i)).toBeDefined()

    expect(screen.getByText('Plenty left')).toBeDefined()
    expect(screen.getByText('Getting close')).toBeDefined()
    expect(screen.getByText('At the limit')).toBeDefined()
  })

  it('shows the paused banner when ai_paused is true', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview({ ai_paused: true }),
      error: null,
      status: 200,
    } as any)

    render(
      <OverviewProvider>
        <Today go={() => {}} />
      </OverviewProvider>
    )

    expect(await screen.findByText('AI reads are paused')).toBeDefined()
    expect(screen.getByText(/Phones cannot read bills or answer questions until you resume/i)).toBeDefined()
  })

  it('shows an error panel with Try again when the call fails', async () => {
    mockRpc.mockResolvedValue({
      data: null,
      error: { code: 'XX000', message: 'Database connection failed' },
      status: 500,
    } as any)

    render(
      <OverviewProvider>
        <Today go={() => {}} />
      </OverviewProvider>
    )

    expect(await screen.findByText('Today could not load')).toBeDefined()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeDefined()
  })

  it('navigates to the limits tab when clicking Limits button in AI section', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview(),
      error: null,
      status: 200,
    } as any)

    const go = vi.fn()
    const user = userEvent.setup()
    render(
      <OverviewProvider>
        <Today go={go} />
      </OverviewProvider>
    )

    const limitsBtn = await screen.findByRole('button', { name: 'Limits' })
    await user.click(limitsBtn)
    expect(go).toHaveBeenCalledWith('limits')
  })
})
