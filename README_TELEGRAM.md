# Telegram Direct Download Resolver

## One-time setup
1. **TDLib**: put a TDLib Android AAR (1.8.30+) in `app/libs/` (build it from https://github.com/tdlib/td/tree/master/example/android or use a prebuilt one you trust).
2. **API id/hash**: create them at https://my.telegram.org/apps. Either add to `local.properties`
   (`TELEGRAM_API_ID=123456`, `TELEGRAM_API_HASH=abcdef...`) or type them into the Login screen (stored encrypted).
3. Build. The new **Telegram** tab sits between *My Files* and *Settings*.

## How the "temporary download URL" works
Telegram has no public HTTP URL for a user-session file (Telegram Web streams parts over MTProto in a service worker).
So the app does the closest client-side equivalent: it starts a loopback server on `127.0.0.1:<random port>` and gives
you `http://127.0.0.1:PORT/dl/<random token>/<name>`. Opening it makes the app pull the bytes from Telegram with your
own session (TDLib `downloadFile` ranges) and stream them to the requester. Range requests/resume work.
- Works only on this device, while the app runs (a foreground notification keeps it alive), until the link expires (15 min–4 h, Settings).
- Nothing passes through any server of yours; the token is 144-bit random, the server only binds to loopback and checks the Host header.
- "Download file" bypasses HTTP entirely and writes to Downloads/Telegram.
- Chats with "Restrict saving content" are refused.

## Layout
- `domain/telegram` – models, errors, link parser, use cases, repository contracts
- `data/telegram` – TdSession (TDLib), vault (encrypted prefs), resolver, file reader, local server, link service
- `ui/telegram` – screens, ViewModels, inner nav (`TelegramRoot`)
- `di/TelegramModule.kt` – Hilt bindings
