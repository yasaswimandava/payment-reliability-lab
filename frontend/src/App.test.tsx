import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

const payment = {
  id: '7c02e8fe-9c21-4e13-81bc-b85185203b19',
  merchantId: 'northstar-coffee',
  amount: 42.5,
  currency: 'USD',
  status: 'RECEIVED',
  createdAt: '2026-09-09T23:30:00Z',
  replayed: false,
}

describe('Payment Reliability Console', () => {
  it('explains the reliability contract', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: /make every retry safe/i }),
    ).toBeInTheDocument()
    expect(screen.getByText(/exactly one business effect/i)).toBeInTheDocument()
    expect(screen.getByText(/phase 1 · foundation online/i)).toBeInTheDocument()
  })

  it('creates a payment and shows its operational result', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(payment), {
        status: 201,
        headers: { 'Idempotency-Replayed': 'false' },
      }),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.clear(screen.getByLabelText(/merchant id/i))
    await user.type(screen.getByLabelText(/merchant id/i), 'northstar-coffee')
    await user.clear(screen.getByLabelText(/idempotency key/i))
    await user.type(screen.getByLabelText(/idempotency key/i), 'order-1042')
    await user.clear(screen.getByLabelText(/^amount/i))
    await user.type(screen.getByLabelText(/^amount/i), '42.50')
    await user.click(screen.getByRole('button', { name: /send payment/i }))

    expect(await screen.findByText(/payment accepted/i)).toBeInTheDocument()
    expect(screen.getByText(payment.id)).toBeInTheDocument()
    expect(screen.getByText('RECEIVED')).toBeInTheDocument()
    expect(screen.getByText(/new business effect/i)).toBeInTheDocument()
  })

  it('makes an idempotency conflict understandable', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Idempotency key conflict',
          detail: 'The key belongs to a different payload.',
        }),
        { status: 409 },
      ),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: /send payment/i }))

    expect(await screen.findByText('Idempotency key conflict')).toBeInTheDocument()
    expect(screen.getByText(/different payload/i)).toBeInTheDocument()
    expect(screen.getByText(/409/)).toBeInTheDocument()
  })

  it('retrieves an existing payment', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(payment), { status: 200 }),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.type(screen.getByLabelText(/payment id/i), payment.id)
    await user.click(screen.getByRole('button', { name: /find payment/i }))

    expect(await screen.findByText(/payment located/i)).toBeInTheDocument()
    expect(screen.getByText(payment.id)).toBeInTheDocument()
  })
})
