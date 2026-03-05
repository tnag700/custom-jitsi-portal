import { component$ } from "@qwik.dev/core";
import { routeAction$, Form, zod$, z } from "@qwik.dev/router";

/**
 * Task 5 baseline: routeAction$ + Form + zod$ — progressive enhancement.
 * Form works without JS; zod$ validates on the server.
 */
export const useAddItem = routeAction$(
  async (data) => {
    // Server-side handler: in a real app this would call a domain service.
    return { success: true, item: data.name };
  },
  zod$({
    name: z.string().min(1, "Имя обязательно"),
  }),
);

export default component$(() => {
  const addAction = useAddItem();

  return (
    <section>
      <h2>routeAction$ + Form + zod$ demo</h2>

      <Form action={addAction}>
        <label>
          Название:
          <input type="text" name="name" />
        </label>
        <button type="submit">Добавить</button>
      </Form>

      {addAction.value?.success && <p>Добавлено: {addAction.value.item}</p>}

      {addAction.value?.failed && (
        <p style="color:red">
          {(addAction.value.fieldErrors?.name as string[] | undefined)?.join(
            ", ",
          )}
        </p>
      )}
    </section>
  );
});
