/**
 * Example domain function demonstrating the "domains — толстые" pattern.
 * Business logic lives here, not in routes.
 */
export function greetUser(name: string): string {
  return `Привет, ${name}! Добро пожаловать в Jitsi.`;
}
