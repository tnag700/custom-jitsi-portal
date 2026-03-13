/**
 * This is the base config for vite.
 * When building, the adapter config is used which loads this file and extends it.
 */
import { qwikVite } from "@qwik.dev/core/optimizer";
import { qwikRouter } from "@qwik.dev/router/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type UserConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import pkg from "./package.json";

type PkgDep = Record<string, string>;
const { dependencies = {}, devDependencies = {} } = pkg as any as {
  dependencies: PkgDep;
  devDependencies: PkgDep;
  [key: string]: unknown;
};
errorOnDuplicatesPkgDeps(devDependencies, dependencies);

/**
 * Note that Vite normally starts from `index.html` but the qwikRouter plugin makes start at `src/entry.ssr.tsx` instead.
 */
export default defineConfig(({ command, mode }): UserConfig => {
  return {
    plugins: [qwikRouter(), qwikVite(), tsconfigPaths({ root: "." }), tailwindcss()],
    // This tells Vite which dependencies to pre-build in dev mode.
    optimizeDeps: {
      // Put problematic deps that break bundling here, mostly those with binaries.
      // For example ['better-sqlite3'] if you use that in server functions.
      exclude: ["@qwik-ui/headless"],
    },

    /**
     * This is an advanced setting. It improves the bundling of your server code. To use it, make sure you understand when your consumed packages are dependencies or dev dependencies. (otherwise things will break in production)
     */
    ssr:
      command === "build" && mode === "production"
        ? {
            noExternal: ["@qwik-ui/headless"],
          }
        : undefined,

    server: {
      headers: {
        // Don't cache the server response in dev mode
        "Cache-Control": "public, max-age=0",
      },
    },
    preview: {
      headers: {
        // Do cache the server response in preview (non-adapter production build)
        "Cache-Control": "public, max-age=600",
      },
    },
  };
});

// *** utils ***

/**
 * Function to identify duplicate dependencies and throw an error
 * @param {Object} devDependencies - List of development dependencies
 * @param {Object} dependencies - List of production dependencies
 */
function errorOnDuplicatesPkgDeps(
  devDependencies: PkgDep,
  dependencies: PkgDep,
) {
  let msg = "";
  // Create an array 'duplicateDeps' by filtering devDependencies.
  // If a dependency also exists in dependencies, it is considered a duplicate.
  const duplicateDeps = Object.keys(devDependencies).filter(
    (dep) => dependencies[dep],
  );

  // Build-time Qwik framework packages should stay in devDependencies.
  // Runtime UI libraries such as @qwik-ui/headless are valid production deps.
  const buildTimeQwikPkgs = Object.keys(dependencies).filter((value) =>
    /^@qwik\.dev\//i.test(value),
  );

  // any errors for missing "qwik-router-config"
  // [PLUGIN_ERROR]: Invalid module "@qwik-router-config" is not a valid package
  msg = `Move build-time Qwik packages ${buildTimeQwikPkgs.join(", ")} to devDependencies`;

  if (buildTimeQwikPkgs.length > 0) {
    throw new Error(msg);
  }

  // Format the error message with the duplicates list.
  // The `join` function is used to represent the elements of the 'duplicateDeps' array as a comma-separated string.
  msg = `
    Warning: The dependency "${duplicateDeps.join(", ")}" is listed in both "devDependencies" and "dependencies".
    Please move the duplicated dependencies to "devDependencies" only and remove it from "dependencies"
  `;

  // Throw an error with the constructed message.
  if (duplicateDeps.length > 0) {
    throw new Error(msg);
  }
}
