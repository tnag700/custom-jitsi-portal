/*
 * WHAT IS THIS FILE?
 *
 * It's the entry point for the Express HTTP server when building for production.
 *
 * Learn more about Node.js server integrations here:
 * - https://qwik.dev/docs/deployments/node/
 */
import { createQwikRouter } from "@qwik.dev/router/middleware/node";
import render from "./entry.ssr";

/**
 * The default export is the QwikRouter middleware for Express.
 */
export default createQwikRouter({ render });
