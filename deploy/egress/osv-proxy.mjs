import http from "node:http";
import net from "node:net";
import { pathToFileURL } from "node:url";

export function createOsvProxy({
  targetHost = "api.osv.dev",
  targetPort = 443,
  listenHost = "0.0.0.0",
  listenPort = 3128,
  maxConnections = 32,
} = {}) {
  let activeConnections = 0;

  const server = http.createServer((_request, response) => {
    response.writeHead(405, { Connection: "close", "Content-Type": "text/plain" });
    response.end("CONNECT required\n");
  });

  server.on("connect", (request, clientSocket, head) => {
    const expectedAuthority = `${targetHost}:${targetPort}`;
    if (request.url !== expectedAuthority) {
      clientSocket.end("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n");
      return;
    }
    if (activeConnections >= maxConnections) {
      clientSocket.end("HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n");
      return;
    }

    activeConnections += 1;
    let released = false;
    const release = () => {
      if (!released) {
        released = true;
        activeConnections -= 1;
      }
    };
    const upstream = net.connect({ host: targetHost, port: targetPort });
    upstream.setTimeout(15_000);
    clientSocket.setTimeout(15_000);

    upstream.once("connect", () => {
      clientSocket.write("HTTP/1.1 200 Connection Established\r\n\r\n");
      if (head.length > 0) {
        upstream.write(head);
      }
      upstream.pipe(clientSocket);
      clientSocket.pipe(upstream);
    });
    upstream.on("timeout", () => upstream.destroy());
    clientSocket.on("timeout", () => clientSocket.destroy());
    upstream.on("error", () => clientSocket.destroy());
    clientSocket.on("error", () => upstream.destroy());
    upstream.once("close", release);
    clientSocket.once("close", release);
  });

  return {
    listen(callback) {
      return server.listen(listenPort, listenHost, callback);
    },
    close(callback) {
      return server.close(callback);
    },
    address() {
      return server.address();
    },
  };
}

const isEntrypoint = process.argv[1]
  && import.meta.url === pathToFileURL(process.argv[1]).href;

if (isEntrypoint) {
  const targetHost = process.env.OSV_TARGET_HOST ?? "api.osv.dev";
  const targetPort = Number.parseInt(process.env.OSV_TARGET_PORT ?? "443", 10);
  const listenPort = Number.parseInt(process.env.PROXY_PORT ?? "3128", 10);
  const proxy = createOsvProxy({ targetHost, targetPort, listenPort });
  proxy.listen(() => {
    process.stdout.write("OSV allowlist proxy ready\n");
  });
}
