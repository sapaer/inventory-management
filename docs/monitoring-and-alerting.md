# Monitoring &amp; alerting

Two independent layers, both **inert until their env vars are set** (local dev and `mvn test`
are unaffected):

1. **App-side spike alerting** — the app counts 4xx / 5xx responses and OTP delivery failures in
   memory and, once a threshold is crossed, logs a `WARN` and emails the on-call address.
2. **Log shipping** — structured JSON logs are pushed to Better Stack for search / retention and
   as a backup place to build alert rules.

---

## 1. App-side spike alerting → Gmail

### What is measured

| Signal | Source | Default threshold |
| --- | --- | --- |
| `HTTP 5xx errors` | final response status ≥ 500 (`RequestLoggingFilter`) | ≥ 10 in 5 min |
| `HTTP 4xx responses` | final response status 400–499 | ≥ 50 in 5 min |
| `OTP delivery failures` | `OTP_DELIVERY_FAILED` in `GlobalExceptionHandler` | ≥ 1 in 5 min |

`MonitoringScheduler` runs every 60s. On a breach it logs
`WARN  MONITORING ALERT: <signal> (count=… threshold=… window=…m)` and calls `AlertEmailSender`.
After an alert, that signal is muted for `alert-cooldown-minutes` (default 30) so one incident is
one email. Counts are in-memory and reset on restart — fine for short-window spikes.

### Enable Gmail delivery

1. On the Gmail account, turn on 2-Step Verification, then create an **App Password**
   (Google Account → Security → App passwords). You get a 16-character string.
2. Set these env vars on Render (Environment tab):

   ```
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=alerts.yourshop@gmail.com
   SMTP_PASSWORD=<16-char app password>          # no spaces
   MONITORING_ALERT_TO=you@gmail.com,teammate@gmail.com
   ```

   Without `SMTP_HOST` there is no `JavaMailSender` bean and alerts only log at `WARN`
   (still shipped to Better Stack). Without `MONITORING_ALERT_TO` the same.

### Tunables (all optional, env)

| Env | Default | Meaning |
| --- | --- | --- |
| `MONITORING_ENABLED` | `true` | master switch for evaluation + email |
| `MONITORING_WINDOW_MINUTES` | `5` | rolling window for every signal |
| `MONITORING_5XX_THRESHOLD` | `10` | 0 disables this signal |
| `MONITORING_4XX_THRESHOLD` | `50` | 0 disables this signal |
| `MONITORING_OTP_FAILURE_THRESHOLD` | `1` | 0 disables this signal |
| `MONITORING_ALERT_COOLDOWN_MINUTES` | `30` | min gap between emails per signal |
| `MONITORING_ALERT_FROM` | `SMTP_USERNAME` | From address |

---

## 2. Log shipping → Better Stack

### Set up

1. Better Stack → **Telemetry → Sources → Connect source → HTTP**. Copy the **Source token**
   and the **Ingesting host** (e.g. `https://s1234567.eu-nbg-2.betterstackdata.com`).
2. Set on Render:

   ```
   LOG_SHIP_TOKEN=<source token>
   LOG_SHIP_URL=https://s1234567.eu-nbg-2.betterstackdata.com   # optional; defaults to https://in.logs.betterstack.com
   ```

`LogShippingBootstrap` (called first thing in `main`) promotes these to system properties before
Log4j2 initialises; the `<Http>` appender in `log4j2-spring.xml` is attached only when
`LOG_SHIP_TOKEN` is present. Logs are sent as ECS-format JSON, asynchronously (a slow/down
endpoint never blocks a request).

### Backup alert rules in Better Stack (optional)

If you also want portal-side alerting, create these on the source:

- `level: ERROR` — count > 10 over 5 minutes → email
- `message CONTAINS "otp delivery failed"` — count ≥ 1 over 5 minutes → email
- `message CONTAINS "MONITORING ALERT"` — any occurrence → email (mirrors the app-side alert)

### Alternative: Render native log stream

Instead of the appender, Render can forward all stdout to Better Stack / Papertrail / Grafana
(Render dashboard → Logs → Log Streams). Then leave `LOG_SHIP_TOKEN` unset and do all alerting
in the portal. The app-side alerting in section 1 still works either way.

---

## Local testing

```bash
# force an OTP failure alert path: no delivery channel configured + bypass off
MONITORING_OTP_FAILURE_THRESHOLD=1 SMTP_HOST= ./mvnw spring-boot:run
# hit POST /api/v1/auth/otp/request with a valid phone -> OTP_DELIVERY_FAILED
# within 60s the log shows: WARN MONITORING ALERT: OTP delivery failures ...
```

To see a real email locally, set the `SMTP_*` + `MONITORING_ALERT_TO` vars in `.env`.
