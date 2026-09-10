import { describe, expect, it, vi } from 'vitest'
import { PaymentApiError, createPayment, getPayment } from './payment-api'

const payment = {
  id: '7c02e8fe-9c21-4e13-81bc-b85185203b19',
  merchantId: 'northstar-coffee',
  amount: 42.5,
  currency: 'USD',
  status: 'RECEIVED',
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

  it('retrieves a payment by ID', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(payment), { status: 200 }))

    await expect(getPayment(payment.id)).resolves.toEqual(payment)
    expect(fetchMock).toHaveBeenCalledWith(`/api/v1/payments/${payment.id}`)
  })
})
