# Questions: mobile-reply-callbacks

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should webhook registration (e.g., Telegram `setWebhook`, Discord webhook URL configuration) be included in this change?
  Context: Webhook registration is a one-time external setup step. Including it would increase scope and risk of accidental overwrite across environments.
  A: Not included. Integrators register webhooks manually. The change focuses on receiving and routing callbacks only.

- [x] Q: Should the Telegram webhook secret be resolved from the vault at `{vaultKey}.webhookSecret`, matching the Discord convention?
  Context: Discord already uses `{vaultKey}.secret`. Each channel has its own vaultKey prefix so suffix collisions are impossible.
  A: Use `{vaultKey}.secret` (same suffix as Discord). Convention: each channel's vault has `.token` and `.secret`.
