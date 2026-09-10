import { type FormEvent, useState } from 'react'
import {
  PaymentApiError,
  configureProvider,
  createPayment,
  getPayment,
  getProviderStatus,
  type Payment,
  type ProviderMode,
  type ProviderSimulatorStatus,
} from './lib/payment-api'

type ResultKind = 'created' | 'replayed' | 'located'

interface VisibleResult {
  kind: ResultKind
  payment: Payment
}

interface VisibleError {
  status?: number
  title: string
  detail: string
}

const initialPaymentForm = {
  merchantId: 'northstar-coffee',
  idempotencyKey: 'order-1042',
  amount: '42.50',
  currency: 'USD',
}

const providerScenarios: Array<{
  mode: ProviderMode
  label: string
  description: string
}> = [
  { mode: 'HEALTHY', label: 'Healthy', description: 'Approve on the first attempt.' },
  {
    mode: 'TRANSIENT_THEN_SUCCESS',
    label: 'Retry twice, then approve',
    description: 'Return two retriable 503 responses before recovering.',
  },
  { mode: 'DECLINE', label: 'Decline', description: 'Return a valid business decline.' },
  {
    mode: 'UNAVAILABLE',
    label: 'Unavailable',
    description: 'Exhaust the retry budget and route the event to the DLT.',
  },
  { mode: 'TIMEOUT', label: 'Timeout', description: 'Respond after the client deadline.' },
]

function formatProviderMode(mode: ProviderMode): string {
  return mode.toLowerCase().replaceAll('_', ' ')
}

function presentError(error: unknown): VisibleError {
  if (error instanceof PaymentApiError) {
    return {
      status: error.status,
      title: error.title,
      detail: error.message,
    }
  }

  return {
    title: 'API connection unavailable',
    detail: 'Start the Spring Boot service on port 8080 and try again.',
  }
}

function formatMoney(payment: Payment): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: payment.currency,
  }).format(payment.amount)
}

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(new Date(value))
}

function PaymentResult({ result }: { result: VisibleResult }) {
  const { payment, kind } = result
  const title =
    kind === 'located'
      ? 'Payment located'
      : kind === 'replayed'
        ? 'Safe replay returned'
        : 'Payment accepted'
  const effectLabel =
    kind === 'located'
      ? 'Durable record retrieved'
      : kind === 'replayed'
        ? 'Existing effect replayed'
        : 'New business effect'

  return (
    <div className="result-state result-state--success">
      <div className="result-heading">
        <div>
          <p className="micro-label">Operation result</p>
          <h3>{title}</h3>
        </div>
        <span className="status-chip">{payment.status}</span>
      </div>

      <div className="effect-line">
        <span className="pulse-dot" aria-hidden="true" />
        <span>{effectLabel}</span>
      </div>

      <dl className="payment-readout">
        <div className="payment-readout__wide">
          <dt>Payment ID</dt>
          <dd>{payment.id}</dd>
        </div>
        <div>
          <dt>Merchant</dt>
          <dd>{payment.merchantId}</dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>{formatMoney(payment)}</dd>
        </div>
        <div className="payment-readout__wide">
          <dt>Committed at</dt>
          <dd>{formatTimestamp(payment.createdAt)}</dd>
        </div>
        {payment.providerReference && (
          <div className="payment-readout__wide">
            <dt>Provider reference</dt>
            <dd>{payment.providerReference}</dd>
          </div>
        )}
      </dl>
    </div>
  )
}

function ErrorResult({ error }: { error: VisibleError }) {
  return (
    <div className="result-state result-state--error" role="alert">
      <div className="result-heading">
        <div>
          <p className="micro-label">Operation blocked</p>
          <h3>{error.title}</h3>
        </div>
        {error.status && <span className="status-chip status-chip--error">HTTP {error.status}</span>}
      </div>
      <p className="error-detail">{error.detail}</p>
      <p className="error-guidance">
        No ambiguous payment should be retried with changed details. Use a new
        idempotency key for a new logical operation.
      </p>
    </div>
  )
}

export default function App() {
  const [form, setForm] = useState(initialPaymentForm)
  const [paymentId, setPaymentId] = useState('')
  const [result, setResult] = useState<VisibleResult | null>(null)
  const [error, setError] = useState<VisibleError | null>(null)
  const [pendingAction, setPendingAction] = useState<'create' | 'lookup' | null>(null)
  const [providerMode, setProviderMode] = useState<ProviderMode>('HEALTHY')
  const [providerStatus, setProviderStatus] = useState<ProviderSimulatorStatus | null>(null)
  const [providerPending, setProviderPending] = useState(false)
  const [providerError, setProviderError] = useState<string | null>(null)

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPendingAction('create')
    setError(null)

    try {
      const response = await createPayment(form)
      setResult({
        kind: response.replayed ? 'replayed' : 'created',
        payment: response.payment,
      })
      setPaymentId(response.payment.id)
    } catch (requestError) {
      setResult(null)
      setError(presentError(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPendingAction('lookup')
    setError(null)

    try {
      const payment = await getPayment(paymentId.trim())
      setResult({ kind: 'located', payment })
    } catch (requestError) {
      setResult(null)
      setError(presentError(requestError))
    } finally {
      setPendingAction(null)
    }
  }

  async function handleProviderConfiguration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProviderPending(true)
    setProviderError(null)

    try {
      setProviderStatus(await configureProvider(providerMode))
    } catch (requestError) {
      setProviderError(presentError(requestError).detail)
    } finally {
      setProviderPending(false)
    }
  }

  async function handleProviderRefresh() {
    setProviderPending(true)
    setProviderError(null)

    try {
      setProviderStatus(await getProviderStatus())
    } catch (requestError) {
      setProviderError(presentError(requestError).detail)
    } finally {
      setProviderPending(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Payment Reliability Lab home">
          <span className="brand-mark" aria-hidden="true">PR</span>
          <span>
            Payment Reliability
            <small>Operations console</small>
          </span>
        </a>
        <nav aria-label="Primary navigation">
          <a href="#workbench">Workbench</a>
          <a href="#simulator">Fault lab</a>
          <a href="#guarantees">Guarantees</a>
          <a href="#roadmap">Roadmap</a>
        </nav>
        <a
          className="source-link"
          href="https://github.com/yasaswimandava/payment-reliability-lab"
          target="_blank"
          rel="noreferrer"
        >
          View source <span aria-hidden="true">↗</span>
        </a>
      </header>

      <main id="top">
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-copy">
            <p className="eyebrow"><span>Payment systems</span> / reliability lab</p>
            <h1 id="hero-title">Make every retry <em>safe.</em></h1>
            <p className="hero-summary">
              A hands-on control room for the failure modes behind payment APIs.
              Delivery may repeat. The outcome must remain exactly one business effect.
            </p>
            <a className="hero-action" href="#workbench">
              Run a payment scenario <span aria-hidden="true">↓</span>
            </a>
          </div>

          <div className="system-signal" aria-label="Current project phase">
            <div className="signal-orbit" aria-hidden="true">
              <span className="signal-core">04</span>
            </div>
            <p>Phase 4 · Failure recovery online</p>
            <span>React → Spring Boot → Kafka → Provider</span>
          </div>
        </section>

        <section className="fact-rail" aria-label="System guarantees">
          <div><span>01</span><p>Identity scope<strong>Merchant + key</strong></p></div>
          <div><span>02</span><p>Commit model<strong>Atomic write</strong></p></div>
          <div><span>03</span><p>Concurrency arbiter<strong>PostgreSQL</strong></p></div>
          <div><span>04</span><p>Coverage floor<strong>80% enforced</strong></p></div>
        </section>

        <section className="workbench" id="workbench" aria-labelledby="workbench-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow"><span>Interactive</span> / API workbench</p>
              <h2 id="workbench-title">Test the contract.</h2>
            </div>
            <p>
              Send the same request twice to observe a safe replay. Change the amount
              while keeping the key to surface a deliberate conflict.
            </p>
          </div>

          <div className="console-frame">
            <div className="console-controls">
              <form className="control-form" onSubmit={handleCreate}>
                <div className="form-heading">
                  <span className="step-number">A</span>
                  <div><h3>Create payment</h3><p>POST /api/v1/payments</p></div>
                </div>

                <label>
                  <span>Merchant ID</span>
                  <input
                    required
                    maxLength={100}
                    value={form.merchantId}
                    onChange={(event) => setForm({ ...form, merchantId: event.target.value })}
                  />
                </label>
                <label>
                  <span>Idempotency key</span>
                  <input
                    required
                    maxLength={200}
                    value={form.idempotencyKey}
                    onChange={(event) => setForm({ ...form, idempotencyKey: event.target.value })}
                  />
                  <small>Keep this value unchanged when retrying the same operation.</small>
                </label>
                <div className="field-pair">
                  <label>
                    <span>Amount</span>
                    <input
                      required
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={form.amount}
                      onChange={(event) => setForm({ ...form, amount: event.target.value })}
                    />
                  </label>
                  <label>
                    <span>Currency</span>
                    <select
                      value={form.currency}
                      onChange={(event) => setForm({ ...form, currency: event.target.value })}
                    >
                      <option value="USD">USD</option>
                      <option value="EUR">EUR</option>
                      <option value="GBP">GBP</option>
                      <option value="CAD">CAD</option>
                    </select>
                  </label>
                </div>
                <button className="primary-button" type="submit" disabled={pendingAction !== null}>
                  {pendingAction === 'create' ? 'Sending…' : 'Send payment'}
                  <span aria-hidden="true">→</span>
                </button>
              </form>

              <div className="control-divider" />

              <form className="control-form control-form--lookup" onSubmit={handleLookup}>
                <div className="form-heading">
                  <span className="step-number">B</span>
                  <div><h3>Retrieve payment</h3><p>GET /api/v1/payments/:id</p></div>
                </div>
                <label>
                  <span>Payment ID</span>
                  <input
                    required
                    value={paymentId}
                    placeholder="Paste a payment UUID"
                    onChange={(event) => setPaymentId(event.target.value)}
                  />
                </label>
                <button className="secondary-button" type="submit" disabled={pendingAction !== null}>
                  {pendingAction === 'lookup' ? 'Searching…' : 'Find payment'}
                </button>
              </form>
            </div>

            <aside className="console-output" aria-live="polite" aria-label="API result">
              <div className="output-bar">
                <span><i aria-hidden="true" /> Live response</span>
                <code>localhost:8080</code>
              </div>
              {result && <PaymentResult result={result} />}
              {error && <ErrorResult error={error} />}
              {!result && !error && (
                <div className="empty-state">
                  <span className="empty-glyph" aria-hidden="true">↳</span>
                  <p>Awaiting a transaction</p>
                  <small>Start the backend, then send a payment scenario.</small>
                </div>
              )}
            </aside>
          </div>
        </section>

        <section className="simulator" id="simulator" aria-labelledby="simulator-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow"><span>Fault injection</span> / provider simulator</p>
              <h2 id="simulator-title">Break it on purpose.</h2>
            </div>
            <p>
              Select a synthetic provider behavior, then create a payment above. The
              backend applies strict timeouts, bounded retries, circuit breaking, and
              dead-letter routing without exposing a customer to duplicate effects.
            </p>
          </div>

          <div className="simulator-console">
            <form className="simulator-control" onSubmit={handleProviderConfiguration}>
              <label htmlFor="provider-mode">Provider behavior</label>
              <select
                id="provider-mode"
                value={providerMode}
                onChange={(event) => setProviderMode(event.target.value as ProviderMode)}
              >
                {providerScenarios.map((scenario) => (
                  <option key={scenario.mode} value={scenario.mode}>{scenario.label}</option>
                ))}
              </select>
              <p>
                {providerScenarios.find((scenario) => scenario.mode === providerMode)?.description}
              </p>
              <button className="primary-button" type="submit" disabled={providerPending}>
                {providerPending ? 'Applying…' : 'Apply scenario'}
                <span aria-hidden="true">→</span>
              </button>
            </form>

            <div className="simulator-status" aria-live="polite">
              <div className="simulator-status__heading">
                <div>
                  <p className="micro-label">Provider state</p>
                  <h3>{providerStatus ? 'Scenario armed' : 'Status not loaded'}</h3>
                </div>
                <button type="button" onClick={handleProviderRefresh} disabled={providerPending}>
                  Refresh
                </button>
              </div>
              {providerStatus ? (
                <dl>
                  <div><dt>Active mode</dt><dd>{formatProviderMode(providerStatus.mode)}</dd></div>
                  <div><dt>Total attempts</dt><dd>{providerStatus.attempts}</dd></div>
                  <div><dt>Completed</dt><dd>{providerStatus.successfulAuthorizations}</dd></div>
                </dl>
              ) : (
                <p className="simulator-placeholder">
                  Apply a scenario or refresh to read the running simulator.
                </p>
              )}
              {providerError && <p className="simulator-error" role="alert">{providerError}</p>}
            </div>
          </div>
        </section>

        <section className="guarantees" id="guarantees" aria-labelledby="guarantees-title">
          <div className="guarantee-intro">
            <p className="eyebrow"><span>Under pressure</span> / system behavior</p>
            <h2 id="guarantees-title">Designed for the unhappy path.</h2>
          </div>
          <ol className="guarantee-list">
            <li><span>01</span><div><h3>Response lost</h3><p>The retry returns the committed payment rather than creating another effect.</p></div><strong>Replay</strong></li>
            <li><span>02</span><div><h3>Payload changed</h3><p>The same key cannot silently authorize a different amount or currency.</p></div><strong>409</strong></li>
            <li><span>03</span><div><h3>Requests race</h3><p>The database constraint chooses one winner and the loser becomes a replay.</p></div><strong>1 commit</strong></li>
            <li><span>04</span><div><h3>Write interrupted</h3><p>Payment and idempotency records commit together or roll back together.</p></div><strong>Atomic</strong></li>
          </ol>
        </section>

        <section className="roadmap" id="roadmap" aria-labelledby="roadmap-title">
          <div>
            <p className="eyebrow"><span>Next signal</span> / build sequence</p>
            <h2 id="roadmap-title">Reliability grows in layers.</h2>
          </div>
          <div className="roadmap-track">
            <article className="roadmap-item roadmap-item--complete"><span>Complete</span><h3>Interactive foundation</h3><p>Idempotency, atomic persistence, and an operations console.</p></article>
            <article className="roadmap-item roadmap-item--complete"><span>Complete</span><h3>Event integrity</h3><p>Transactional outbox, Kafka, and idempotent consumers.</p></article>
            <article className="roadmap-item roadmap-item--active"><span>Now</span><h3>Failure recovery</h3><p>Provider faults, bounded retries, circuit breaking, and DLT operations.</p></article>
            <article className="roadmap-item"><span>Next</span><h3>Production evidence</h3><p>Telemetry, load tests, CI, and trace-driven demonstrations.</p></article>
          </div>
        </section>
      </main>

      <footer>
        <p>Payment Reliability Lab <span>·</span> Built by Yasaswi Mandava</p>
        <p>Independent portfolio project <span>·</span> Synthetic data only</p>
      </footer>
    </div>
  )
}
