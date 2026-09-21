# Support line (one number, rings every co-founder)

One published Twilio number rings all co-founders simultaneously — first to pick up gets
the call. No one answers → the caller leaves a voicemail, and every founder gets an SMS
(or WhatsApp) with the caller's number and a link to the recording.

Reuses the Twilio account already configured for OTP/WhatsApp in this app (`TWILIO_ACCOUNT_SID`
/ `TWILIO_AUTH_TOKEN`). Inert until `SUPPORT_LINE_ENABLED=true` and `SUPPORT_LINE_NUMBERS` are set.

## Call flow

```
Customer dials the support number
        │
        ▼
POST /api/v1/voice/incoming ──► <Dial> all SUPPORT_LINE_NUMBERS at once (rings ~20s)
        │
        ├─ someone answers ──► connected, then action fires with DialCallStatus=completed
        │                       └─► POST /api/v1/voice/no-answer ──► <Hangup/>
        │
        └─ no answer / busy ──► POST /api/v1/voice/no-answer ──► <Record> a voicemail
                                        │
                                        ▼
                                POST /api/v1/voice/voicemail-complete
                                        │
                                        ▼
                                SMS (or WhatsApp) every founder: caller + recording link
```

Every webhook request is verified against Twilio's `X-Twilio-Signature` header
(`TwilioSignatureValidator`, using `TWILIO_AUTH_TOKEN`) — anything not genuinely from Twilio
gets `403`, so the founders' numbers in the `/incoming` TwiML can't be read by a random request.

## Setup

1. **Buy a Twilio voice-capable number** — Twilio Console → Phone Numbers → Buy a number
   (pick India local or an India toll-free number). SMS capability too if you want the
   voicemail notification to go over SMS (recommended — see note below).

2. **Set env vars** (Render → Environment, or `.env` locally):

   ```
   SUPPORT_LINE_ENABLED=true
   SUPPORT_LINE_NUMBERS=+91XXXXXXXXX1,+91XXXXXXXXX2,+91XXXXXXXXX3,+91XXXXXXXXX4,+91XXXXXXXXX5
   ```

   (`TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` should already be set from the OTP integration.)

3. **Point the number's webhook at this app** — Twilio Console → the number → Voice Configuration
   → "A call comes in" → Webhook → `https://<your-render-app>.onrender.com/api/v1/voice/incoming`
   → HTTP POST. The other two URLs (`/no-answer`, `/voicemail-complete`) are set automatically by
   the TwiML this app returns — nothing to configure for them.

4. Call the number to test.

## Tuning (all optional, env)

| Env | Default | Meaning |
| --- | --- | --- |
| `SUPPORT_LINE_RING_TIMEOUT_SECONDS` | `20` | How long all 5 phones ring before falling to voicemail |
| `SUPPORT_LINE_VOICEMAIL_ENABLED` | `true` | `false` → caller just hears "no one available", no recording |
| `SUPPORT_LINE_VOICEMAIL_MAX_LENGTH_SECONDS` | `120` | Max voicemail length |

## Notes

- **Voicemail notification channel**: SMS is used when Twilio SMS is configured
  (`TWILIO_FROM_NUMBER`), falling back to WhatsApp otherwise. Prefer keeping SMS configured —
  WhatsApp business-initiated freeform messages only deliver within 24h of the recipient last
  messaging your WhatsApp number, so a founder who's never messaged it won't reliably get it.
- **Caller ID on the founders' phones**: not overridden, so Twilio's default applies — the
  founders see the *customer's* number, not the shared support number, which is what you
  generally want for calling back.
- **Cost**: standard Twilio voice minutes (inbound + one leg per number until answered) and
  a small monthly number rental. Check current Twilio India voice pricing before enabling.
- To change the 5 numbers later, just update `SUPPORT_LINE_NUMBERS` and redeploy — no Twilio
  console changes needed.
