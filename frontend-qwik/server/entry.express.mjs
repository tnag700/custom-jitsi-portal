import express from "express";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const __dirname = fileURLToPath(new URL(".", import.meta.url));

const PORT = parseInt(process.env["PORT"] ?? "3000", 10);

async function startServer() {
  const app = express();

  // Serve static assets from client build
  app.use(
    "/build",
    express.static(join(__dirname, "..", "dist", "build"), {
      immutable: true,
      maxAge: "1y",
    }),
  );

  app.use(
    express.static(join(__dirname, "..", "dist"), {
      redirect: false,
    }),
  );

  // Qwik SSR middleware
  const serverEntryUrl = pathToFileURL(
    join(__dirname, "..", "dist", "server", "entry.express.js"),
  ).href;

  const { default: qwikRouter } = await import(serverEntryUrl);

  const routerMiddleware =
    typeof qwikRouter === "function" ? qwikRouter : qwikRouter?.router;
  const notFoundMiddleware =
    typeof qwikRouter === "object" ? qwikRouter?.notFound : undefined;

  if (typeof routerMiddleware !== "function") {
    throw new TypeError("Invalid Qwik router middleware export");
  }

  app.use(routerMiddleware);

  if (typeof notFoundMiddleware === "function") {
    app.use(notFoundMiddleware);
  }

  app.listen(PORT, () => {
    console.log(`Server started: http://localhost:${PORT}/`);
  });
}

startServer();
