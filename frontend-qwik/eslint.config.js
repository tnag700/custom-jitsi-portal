import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import { globalIgnores } from "eslint/config";
import { qwikEslint9Plugin } from "eslint-plugin-qwik";

const ignores = [
  "**/*.log",
  "**/.DS_Store",
  "**/*.",
  ".vscode/settings.json",
  "**/.history",
  "**/.yarn",
  "**/bazel-*",
  "**/bazel-bin",
  "**/bazel-out",
  "**/bazel-qwik",
  "**/bazel-testlogs",
  "**/dist",
  "**/dist-dev",
  "lib",
  "**/lib-types",
  "**/etc",
  "**/external",
  "**/node_modules",
  "**/temp",
  "**/tsc-out",
  "**/tsdoc-metadata.json",
  "**/target",
  "**/output",
  "**/rollup.config.js",
  "**/build",
  "**/.cache",
  "**/.vscode",
  "**/.rollup.cache",
  "**/dist",
  "**/tsconfig.tsbuildinfo",
  "**/vite.config.ts",
  "**/*.spec.tsx",
  "**/*.spec.ts",
  "**/.netlify",
  "**/pnpm-lock.yaml",
  "**/package-lock.json",
  "**/yarn.lock",
  "**/server",
  "eslint.config.js",
];

export default tseslint.config(
  globalIgnores(ignores),
  js.configs.recommended,
  tseslint.configs.recommended,
  qwikEslint9Plugin.configs.recommended,
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021,
        ...globals.serviceworker,
      },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/consistent-type-imports": [
        "error",
        {
          prefer: "type-imports",
          fixStyle: "separate-type-imports",
        },
      ],
      "@typescript-eslint/no-floating-promises": "error",
      "@typescript-eslint/no-misused-promises": [
        "error",
        {
          checksVoidReturn: {
            attributes: false,
          },
        },
      ],
      "@typescript-eslint/await-thenable": "error",
    },
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "axios",
              message:
                "Use server-context APIs via routeLoader$/routeAction$ and domain services instead of client HTTP libraries.",
            },
            {
              name: "ky",
              message:
                "Use server-context APIs via routeLoader$/routeAction$ and domain services instead of client HTTP libraries.",
            },
            {
              name: "@tanstack/query-core",
              message:
                "Do not introduce client query libraries in Qwik routes/domains for this project.",
            },
            {
              name: "@tanstack/react-query",
              message:
                "Do not introduce client query libraries in Qwik routes/domains for this project.",
            },
            {
              name: "swr",
              message:
                "Do not introduce SWR-style client fetching in this project architecture.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/routes/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^~\\/lib\\/domains\\/[^/]+\\/.+",
              message:
                "Routes must import domains only via public API barrel: ~/lib/domains/<domain>",
            },
          ],
        },
      ],
      "no-restricted-syntax": [
        "error",
        {
          selector: "CallExpression[callee.name='fetch']",
          message:
            "Do not call fetch directly in routes. Use domain services via routeLoader$/routeAction$.",
        },
      ],
    },
  },
  {
    files: ["src/lib/domains/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "@qwik.dev/router",
              importNames: ["routeLoader$", "routeAction$", "server$"],
              message:
                "Domain layer must not declare route/server APIs. Keep routeLoader$/routeAction$/server$ inside src/routes.",
            },
          ],
          patterns: [
            {
              regex: "^~\\/routes\\/.+",
              message:
                "Domain layer must not import route files",
            },
            {
              regex: "^~\\/lib\\/shared\\/.+",
              message:
                "Domain layer must import shared via public API barrel: ~/lib/shared",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/lib/shared/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "^~\\/lib\\/domains\\/.+",
              message:
                "Shared layer must not depend on domain layer",
            },
            {
              regex: "^~\\/routes\\/.+",
              message:
                "Shared layer must not depend on route layer",
            },
          ],
        },
      ],
    },
  },
);
