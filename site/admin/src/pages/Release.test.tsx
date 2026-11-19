// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OverviewProvider } from '../overview'
import { Release } from './Release'
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
  ai_today: [],
  ai_paused: false,
  storage: { objects: 311, bytes: 182 * 1024 ** 2 },
  db: { bytes: 41 * 1024 ** 2 },
  versions: { min: 0, recommended: 0, update_screen: null, latest_published: null },
  ...overrides,
})

describe('Release page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows "No minimum is set" at min 0', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview({ versions: { min: 0, recommended: 0, update_screen: null, latest_published: null } }),
      error: null,
      status: 200,
      count: null,
      statusText: 'OK',
    })

    render(
      <OverviewProvider>
        <Release />
      </OverviewProvider>
    )

    expect(await screen.findByText(/No minimum is set, so every phone keeps syncing/i)).toBeDefined()
  })

  it('shows version warning sentence when min > 0', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview({ versions: { min: 14, recommended: 15, update_screen: 12, latest_published: 16 } }),
      error: null,
      status: 200,
      count: null,
      statusText: 'OK',
    })

    render(
      <OverviewProvider>
        <Release />
      </OverviewProvider>
    )

    expect(await screen.findByText(/Phones below version/i)).toBeDefined()
    expect(screen.getByText('14')).toBeDefined()
  })

  it('raising the minimum before update screen is recorded shows refusal and never calls admin_set_app_versions', async () => {
    mockRpc.mockResolvedValue({
      data: fakeOverview({ versions: { min: 0, recommended: 0, update_screen: null, latest_published: null } }),
      error: null,
      status: 200,
      count: null,
      statusText: 'OK',
    })

    const user = userEvent.setup()
    render(
      <OverviewProvider>
        <Release />
      </OverviewProvider>
    )

    const changeBtn = await screen.findByRole('button', { name: /change minimum version/i })
    await user.click(changeBtn)

    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '5')

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toBeDefined()
    expect(screen.getByText(/Record which version has the update screen first/i)).toBeDefined()

    expect(mockRpc).not.toHaveBeenCalledWith('admin_set_app_versions', expect.anything())
  })
})
