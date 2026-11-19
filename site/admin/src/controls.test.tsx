// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { ConfirmSheet, EditableNumber } from './controls'

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function () {
    this.open = false
  }
})

afterEach(() => {
  cleanup()
})

describe('EditableNumber', () => {
  it('shows the value', () => {
    render(<EditableNumber label="test limit" value={42} max={100} onSave={async () => {}} />)
    expect(screen.getByText('42')).toBeDefined()
    expect(screen.getByRole('button', { name: /change test limit/i })).toBeDefined()
  })

  it('shows custom nonePhrase when value is null', () => {
    render(<EditableNumber label="test limit" value={null} nonePhrase="no limit set" max={100} onSave={async () => {}} />)
    expect(screen.getByText('no limit set')).toBeDefined()
  })

  it('Change button is disabled when disabled prop is true', () => {
    render(<EditableNumber label="test limit" value={42} max={100} disabled={true} onSave={async () => {}} />)
    expect((screen.getByRole('button', { name: /change test limit/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('Change opens a field with Save and Cancel', async () => {
    const user = userEvent.setup()
    render(<EditableNumber label="test limit" value={42} max={100} onSave={async () => {}} />)
    await user.click(screen.getByRole('button', { name: /change test limit/i }))
    expect(screen.getByRole('textbox')).toBeDefined()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDefined()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDefined()
  })

  it('Save with a bad value (empty, -1, above max) shows message and does not call onSave', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<EditableNumber label="test limit" value={42} max={100} onSave={onSave} />)
    await user.click(screen.getByRole('button', { name: /change test limit/i }))
    const input = screen.getByRole('textbox')

    // empty
    await user.clear(input)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('alert')).toBeDefined()
    expect(onSave).not.toHaveBeenCalled()

    // -1
    await user.type(input, '-1')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('alert')).toBeDefined()
    expect(onSave).not.toHaveBeenCalled()

    // above max (101)
    await user.clear(input)
    await user.type(input, '101')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('alert')).toBeDefined()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('a good value calls onSave with the number and shows was N afterwards', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<EditableNumber label="test limit" value={42} max={100} onSave={onSave} />)
    await user.click(screen.getByRole('button', { name: /change test limit/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '55')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSave).toHaveBeenCalledWith(55)
    expect(screen.getByRole('status').textContent).toBe('was 42')
  })

  it('an onSave that throws shows its message and keeps editing', async () => {
    const onSave = vi.fn().mockRejectedValue(new Error('Server rejected change'))
    const user = userEvent.setup()
    render(<EditableNumber label="test limit" value={42} max={100} onSave={onSave} />)
    await user.click(screen.getByRole('button', { name: /change test limit/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '50')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('alert')).toBeDefined()
    expect(screen.getByText('Server rejected change')).toBeDefined()
    expect(screen.getByRole('textbox')).toBeDefined()
  })

  it("'deferred' closes the field without was", async () => {
    const onSave = vi.fn().mockResolvedValue('deferred')
    const user = userEvent.setup()
    render(<EditableNumber label="test limit" value={42} max={100} onSave={onSave} />)
    await user.click(screen.getByRole('button', { name: /change test limit/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '60')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(onSave).toHaveBeenCalledWith(60)
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('status')).toBeNull()
  })
})

describe('ConfirmSheet', () => {
  it('with expected="PAUSE": confirm button is disabled until exactly PAUSE is typed', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(
      <ConfirmSheet
        open={true}
        onClose={() => {}}
        title="Pause AI for everyone?"
        expected="PAUSE"
        confirmLabel="Pause AI"
        onConfirm={onConfirm}
      >
        <p>Confirmation message</p>
      </ConfirmSheet>
    )

    const confirmBtn = screen.getByRole('button', { name: 'Pause AI' })
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true)

    const input = screen.getByRole('textbox')
    await user.type(input, 'PAUS')
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true)

    await user.type(input, 'E')
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(false)

    await user.click(confirmBtn)
    expect(onConfirm).toHaveBeenCalled()
  })

  it('with plain confirm without expected text: button is enabled immediately and calls onConfirm', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(
      <ConfirmSheet
        open={true}
        onClose={() => {}}
        title="Resume AI?"
        confirmLabel="Resume AI"
        onConfirm={onConfirm}
      >
        <p>Resume message</p>
      </ConfirmSheet>
    )

    const confirmBtn = screen.getByRole('button', { name: 'Resume AI' })
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(false)
    await user.click(confirmBtn)
    expect(onConfirm).toHaveBeenCalled()
  })

  it('cancel button calls onClose', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(
      <ConfirmSheet
        open={true}
        onClose={onClose}
        title="Confirm action"
        confirmLabel="Do it"
        onConfirm={async () => {}}
      >
        <p>Action details</p>
      </ConfirmSheet>
    )

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onClose).toHaveBeenCalled()
  })
})
