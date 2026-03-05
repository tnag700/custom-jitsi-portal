import { component$ } from "@qwik.dev/core";
import { routeLoader$ } from "@qwik.dev/router";
import { greetUser } from "~/lib/domains/example";

/**
 * Task 5 baseline: routeLoader$ for reading data.
 * Demonstrates SSR data loading — executed on the server.
 */
export const useGreeting = routeLoader$(() => {
  return { message: greetUser("Qwik") };
});

export default component$(() => {
  const greeting = useGreeting();

  return (
    <section>
      <h2>routeLoader$ demo</h2>
      <p>{greeting.value.message}</p>
    </section>
  );
});
