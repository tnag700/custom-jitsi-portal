import { component$, useSignal } from "@qwik.dev/core";
import { server$ } from "@qwik.dev/router";

export const getServerTimeExample = server$(() => {
  return new Date().toISOString();
});

export const ServerFunctionExample = component$(() => {
  const serverTime = useSignal("");

  return (
    <section>
      <h2>server$ example</h2>
      <button
        type="button"
        onClick$={async () => {
          serverTime.value = await getServerTimeExample();
        }}
      >
        Получить серверное время
      </button>
      {serverTime.value && <p>Серверное время: {serverTime.value}</p>}
    </section>
  );
});

export default ServerFunctionExample;