import { createServer } from "node:http";

const port = Number.parseInt(process.env.ALERT_RECEIVER_PORT ?? "9080", 10);
const notifications = [];

function json(response, statusCode, body) {
  response.writeHead(statusCode, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  const body = Buffer.concat(chunks).toString("utf8");
  return body ? JSON.parse(body) : {};
}

createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", "http://localhost");

  if (request.method === "GET" && url.pathname === "/health") {
    json(response, 200, { status: "UP" });
    return;
  }

  if (url.pathname === "/notifications" && request.method === "GET") {
    json(response, 200, { notifications });
    return;
  }

  if (url.pathname === "/notifications" && request.method === "DELETE") {
    notifications.length = 0;
    json(response, 200, { notifications });
    return;
  }

  if (url.pathname === "/alerts" && request.method === "POST") {
    try {
      const payload = await readJson(request);
      notifications.push({
        receivedAt: new Date().toISOString(),
        status: payload.status ?? "unknown",
        alertNames: Array.from(
          new Set(
            (payload.alerts ?? [])
              .map((alert) => alert?.labels?.alertname)
              .filter((name) => typeof name === "string" && name.length > 0),
          ),
        ),
      });
      notifications.splice(0, Math.max(0, notifications.length - 100));
      json(response, 200, { accepted: true });
    } catch (error) {
      json(response, 400, {
        accepted: false,
        error: error instanceof Error ? error.message : "Invalid JSON payload",
      });
    }
    return;
  }

  json(response, 404, { error: "Not found" });
}).listen(port, "0.0.0.0", () => {
  process.stdout.write(`mock-alert-receiver listening on ${port}\n`);
});
