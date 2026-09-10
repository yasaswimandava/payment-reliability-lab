export interface Payment {
  id: string
  merchantId: string
  amount: number
  currency: string
  status: string
  createdAt: string
  replayed: boolean
}

export interface CreatePaymentInput {
  merchantId: string
  idempotencyKey: string
  amount: string
  currency: string
}

export interface CreatePaymentResult {
  payment: Payment
  replayed: boolean
}

interface ProblemResponse {
  title?: string
  detail?: string
}

export class PaymentApiError extends Error {
  readonly status: number
  readonly title: string

  constructor(status: number, title: string, detail: string) {
    super(detail)
    this.name = 'PaymentApiError'
    this.status = status
    this.title = title
  }
}

async function readProblem(response: Response): Promise<PaymentApiError> {
  let problem: ProblemResponse = {}

  try {
    problem = (await response.json()) as ProblemResponse
  } catch {
    // A useful HTTP error is still returned when an intermediary sends no JSON body.
  }

  const title = problem.title ?? 'Payment request failed'
  const detail = problem.detail ?? `The API returned HTTP ${response.status}.`
  return new PaymentApiError(response.status, title, detail)
}

async function requirePayment(response: Response): Promise<Payment> {
  if (!response.ok) {
    throw await readProblem(response)
  }

  return (await response.json()) as Payment
}

export async function createPayment(
  input: CreatePaymentInput,
): Promise<CreatePaymentResult> {
  const response = await fetch('/api/v1/payments', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Merchant-Id': input.merchantId,
      'Idempotency-Key': input.idempotencyKey,
    },
    body: JSON.stringify({
      amount: Number(input.amount),
      currency: input.currency,
    }),
  })
  const payment = await requirePayment(response)

  return {
    payment,
    replayed: response.headers.get('Idempotency-Replayed') === 'true',
  }
}

export async function getPayment(paymentId: string): Promise<Payment> {
  const response = await fetch(`/api/v1/payments/${encodeURIComponent(paymentId)}`)
  return requirePayment(response)
}
