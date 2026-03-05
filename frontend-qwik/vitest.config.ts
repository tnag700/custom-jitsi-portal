import { defineConfig } from "vitest/config";
import { qwikVite } from "@qwik.dev/core/optimizer";
import { qwikRouter } from "@qwik.dev/router/vite";
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig({
  plugins: [qwikRouter(), qwikVite(), tsconfigPaths()],
  test: {
    include: ["src/**/*.test.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "json-summary"],
      reportsDirectory: "coverage",
      include: [
        "src/lib/**/*.ts",
        "src/lib/**/*.tsx",
        "src/routes/**/*.ts",
        "src/routes/**/*.tsx",
      ],
      exclude: [
        "src/**/*.test.ts",
        "src/__tests__/**",
      ],
    },
  },
});
