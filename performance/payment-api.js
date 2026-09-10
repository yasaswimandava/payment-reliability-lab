import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";

export const options = {
  scenarios: {
    idempotent_payment_creation: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "30s",
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:create-payment}": ["p(95)<500"],
    "http_req_duration{name:replay-payment}": ["p(95)<500"],
  },
};

export default function () {
  const operation = `${__VU}-${__ITER}-${Date.now()}`;
  const headers = {
    "Content-Type": "application/json",
    "X-Merchant-Id": `load-merchant-${__VU}`,
    "Idempotency-Key": `load-order-${operation}`,
  };
  const body = JSON.stringify({ amount: 42.5, currency: "USD" });

  const created = http.post(`${baseUrl}/api/v1/payments`, body, {
    headers,
    tags: { name: "create-payment" },
  });
  const createdBody = parseJson(created);

  check(created, {
    "first attempt is created": (response) => response.status === 201,
    "first attempt is not replayed": (response) =>
      response.headers["Idempotency-Replayed"] === "false",
    "first attempt returns an id": () => Boolean(createdBody?.id),
  });

  const replayed = http.post(`${baseUrl}/api/v1/payments`, body, {
    headers,
    tags: { name: "replay-payment" },
  });
  const replayedBody = parseJson(replayed);

  check(replayed, {
    "retry is replayed": (response) => response.status === 200,
    "retry exposes replay header": (response) =>
      response.headers["Idempotency-Replayed"] === "true",
    "retry returns original payment": () =>
      Boolean(createdBody?.id) && replayedBody?.id === createdBody.id,
  });

  sleep(0.1);
}

function parseJson(response) {
  try {
    return response.json();
  } catch {
    return null;
  }
}
