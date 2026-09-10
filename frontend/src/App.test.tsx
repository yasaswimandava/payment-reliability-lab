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
  providerReference: null,
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
    expect(screen.getByText(/phase 4 · failure recovery online/i)).toBeInTheDocument()
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
    expect(screen.getByText('HTTP 409')).toBeInTheDocument()
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
    expect(screen.getByText(/durable record retrieved/i)).toBeInTheDocument()
  })

  it('explains how to recover when the backend is unavailable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: /send payment/i }))

    expect(await screen.findByText('API connection unavailable')).toBeInTheDocument()
    expect(screen.getByText(/start the spring boot service/i)).toBeInTheDocument()
  })

  it('lets an operator select and observe a provider failure mode', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          mode: 'TRANSIENT_THEN_SUCCESS',
          attempts: 0,
          successfulAuthorizations: 0,
        }),
        { status: 200 },
      ),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.selectOptions(
      screen.getByLabelText(/provider behavior/i),
      'TRANSIENT_THEN_SUCCESS',
    )
    await user.click(screen.getByRole('button', { name: /apply scenario/i }))

    expect(await screen.findByText(/transient then success/i)).toBeInTheDocument()
    expect(screen.getByText(/total attempts/i)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/simulator/provider', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'TRANSIENT_THEN_SUCCESS' }),
    })
  })

  it('shows an operations overview that answers recovery questions', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          health: 'ATTENTION',
          generatedAt: '2026-09-10T02:00:00Z',
          payments: { total: 12, received: 2, stale: 1, authorized: 9, declined: 1 },
          outbox: { unpublished: 3, stale: 1, processing: 1, failed: 1 },
          provider: { circuitState: 'OPEN' },
        }),
        { status: 200 },
      ),
    )
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: /refresh operations/i }))

    expect(await screen.findByText(/needs attention/i)).toBeInTheDocument()
    expect(screen.getByText(/2 awaiting authorization/i)).toBeInTheDocument()
    expect(screen.getByText(/provider circuit/i)).toBeInTheDocument()
    expect(screen.getByText('OPEN')).toBeInTheDocument()
  })
})
