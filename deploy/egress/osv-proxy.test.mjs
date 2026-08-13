import assert from "node:assert/strict";
import net from "node:net";
import { after, before, test } from "node:test";
import { createOsvProxy } from "./osv-proxy.mjs";

let target;
let proxy;
let targetPort;
let proxyPort;

before(async () => {
  target = net.createServer((socket) => socket.pipe(socket));
  await new Promise((resolve) => target.listen(0, "127.0.0.1", resolve));
  targetPort = target.address().port;
  proxy = createOsvProxy({
    targetHost: "127.0.0.1",
    targetPort,
    listenHost: "127.0.0.1",
    listenPort: 0,
  });
  await new Promise((resolve) => proxy.listen(resolve));
  proxyPort = proxy.address().port;
});

after(async () => {
  await new Promise((resolve) => proxy.close(resolve));
  await new Promise((resolve) => target.close(resolve));
});

function connect(authority) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(proxyPort, "127.0.0.1");
    socket.once("error", reject);
    socket.once("connect", () => {
      socket.write(`CONNECT ${authority} HTTP/1.1\r\nHost: ${authority}\r\n\r\n`);
    });
    socket.once("data", (data) => resolve({ socket, data: data.toString("utf8") }));
  });
}

test("allows only the configured OSV authority", async () => {
  const allowed = await connect(`127.0.0.1:${targetPort}`);
  assert.match(allowed.data, /^HTTP\/1\.1 200 Connection Established/);
  allowed.socket.end();

  const denied = await connect("example.org:443");
  assert.match(denied.data, /^HTTP\/1\.1 403 Forbidden/);
  denied.socket.destroy();
});
