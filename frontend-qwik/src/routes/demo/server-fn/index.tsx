import { component$, useSignal } from "@qwik.dev/core";
import { server$ } from "@qwik.dev/router";

/**
 * Server function invoked by a user event (click).
 * Runs on the server, returns result to the client via RPC.
 */
const getServerTime = server$(() => {
  return new Date().toISOString();
});

/**
 * Task 5 baseline: server$ — RPC call triggered by user interaction.
 */
export default component$(() => {
  const serverTime = useSignal("");

  return (
    <section>
      <h2>server$ demo</h2>
      <button
        onClick$={async () => {
          serverTime.value = await getServerTime();
        }}
      >
        Получить серверное время
      </button>
      {serverTime.value && <p>Серверное время: {serverTime.value}</p>}
    </section>
  );
});
