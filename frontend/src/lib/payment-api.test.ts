import { describe, expect, it, vi } from 'vitest'
import {
  PaymentApiError,
  configureProvider,
  createPayment,
  getPayment,
  getOperationsOverview,
  getProviderStatus,
} from './payment-api'

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

describe('payment API client', () => {
  it('creates a payment with the reliability headers', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(payment), {
        status: 201,
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Replayed': 'false',
        },
      }),
    )

    const result = await createPayment({
      merchantId: 'northstar-coffee',
      idempotencyKey: 'order-1042',
      amount: '42.50',
      currency: 'USD',
    })

    expect(result).toEqual({ payment, replayed: false })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/payments', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Merchant-Id': 'northstar-coffee',
        'Idempotency-Key': 'order-1042',
      },
      body: JSON.stringify({ amount: 42.5, currency: 'USD' }),
    })
  })

  it('reports a problem response with its HTTP status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Idempotency key conflict',
          detail: 'The key belongs to another request.',
        }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const request = createPayment({
      merchantId: 'northstar-coffee',
      idempotencyKey: 'order-1042',
      amount: '99.00',
      currency: 'USD',
    })

    await expect(request).rejects.toEqual(
      expect.objectContaining<Partial<PaymentApiError>>({
        status: 409,
        title: 'Idempotency key conflict',
        message: 'The key belongs to another request.',
      }),
    )
  })

  it('identifies a replay from the response header', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ...payment, replayed: true }), {
        status: 200,
        headers: { 'Idempotency-Replayed': 'true' },
      }),
    )

    const result = await createPayment({
      merchantId: 'northstar-coffee',
      idempotencyKey: 'order-1042',
      amount: '42.50',
      currency: 'USD',
    })

    expect(result.replayed).toBe(true)
  })

  it('provides a useful fallback when an error has no JSON body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 503 }),
    )

    await expect(getPayment(payment.id)).rejects.toEqual(
      expect.objectContaining<Partial<PaymentApiError>>({
        status: 503,
        title: 'Payment request failed',
        message: 'The API returned HTTP 503.',
      }),
    )
  })

  it('retrieves a payment by ID', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(payment), { status: 200 }))

    await expect(getPayment(payment.id)).resolves.toEqual(payment)
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/payments/${payment.id}`)
  })

  it('reads and configures the provider simulator', async () => {
    const healthy = {
      mode: 'HEALTHY',
      attempts: 0,
      successfulAuthorizations: 0,
    }
    const unavailable = { ...healthy, mode: 'UNAVAILABLE' }
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify(healthy), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(unavailable), { status: 200 }))

    await expect(getProviderStatus()).resolves.toEqual(healthy)
    await expect(configureProvider('UNAVAILABLE')).resolves.toEqual(unavailable)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/simulator/provider')
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/simulator/provider', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mode: 'UNAVAILABLE' }),
    })
  })

  it('reads the durable operations overview', async () => {
    const overview = {
      health: 'HEALTHY',
      generatedAt: '2026-09-10T02:00:00Z',
      payments: { total: 12, received: 2, stale: 1, authorized: 9, declined: 1 },
      outbox: { unpublished: 1, stale: 0, processing: 0, failed: 0 },
      provider: { circuitState: 'CLOSED' },
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(overview), { status: 200 }),
    )

    await expect(getOperationsOverview()).resolves.toEqual(overview)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/operations/overview')
  })
})
