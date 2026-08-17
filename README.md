# Inventory Management

Phase 1 backend for auto-parts inventory: **Auth**, **Inventory**, and **Notification**.

- Java 17+
- Spring Boot 3.3
- PostgreSQL (data + OTP/session store)
- JWT access tokens + Postgres refresh sessions

## Run locally

1. Install [JDK 17+](https://adoptium.net/) and [Maven 3.9+](https://maven.apache.org/).

2. Start Postgres (Docker):

```bash
docker compose up -d
```

3. Copy env values:

```bash
cp .env.example .env
```

4. Start the API:

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Health check: `GET /actuator/health`.

On Windows PowerShell:

```powershell
cd C:\Users\aakas\desktop\inventory-management
mvn spring-boot:run
```

## Auth flow

| Method | Path | Auth |
| --- | --- | --- |
| POST | `/api/v1/auth/otp/request` | public |
| POST | `/api/v1/auth/otp/verify` | public |
| POST | `/api/v1/auth/token/refresh` | public |
| DELETE | `/api/v1/auth/logout` | Bearer |
| GET | `/api/v1/auth/profile` | Bearer |
| PUT | `/api/v1/auth/profile` | Bearer |

OTP and refresh sessions are stored in Postgres (`app_kv_store`) after WhatsApp/SMS send succeeds. Failed send does not create a user.

Delivery order: **Twilio WhatsApp**, then **Twilio SMS**. Meta Cloud API and MSG91 remain fallbacks if Twilio is not configured.

## Twilio (SMS + WhatsApp)

Set these in `.env`:

```
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=ACxxxxxxxx
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_FROM_NUMBER=+1xxxxxxxxxx
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
TWILIO_OTP_CONTENT_SID=
TWILIO_LOW_STOCK_CONTENT_SID=
```

- `TWILIO_FROM_NUMBER` is the SMS sender (E.164).
- `TWILIO_WHATSAPP_FROM` is the WhatsApp sender. Sandbox default is `whatsapp:+14155238886`. Recipients must join the sandbox first.
- Content SIDs are optional. Leave them empty for sandbox freeform text. For production WhatsApp, create approved templates in Twilio Content Template Builder and put the `HX...` SIDs here. OTP uses variable `1`; low-stock uses `1` (part name) and `2` (quantity).

Request body:

```json
{ "phone": "9876543210" }
```

## Inventory

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/inventory` | `q`, `vehicle`, `status` query params |
| POST | `/api/v1/inventory` | add part |
| GET | `/api/v1/inventory/{id}` | |
| PUT | `/api/v1/inventory/{id}` | |
| DELETE | `/api/v1/inventory/{id}` | soft delete |
| PATCH | `/api/v1/inventory/{id}/quantity` | `{ "change": -1, "changeType": "SOLD" }` |
| GET | `/api/v1/inventory/low-stock` | |
| GET | `/api/v1/inventory/history/{id}` | |

`costPrice` is stored but never returned in API responses.

Frontend curls (success + error cases): [`docs/frontend-api-curls.md`](docs/frontend-api-curls.md)

## Project layout (Java)

```
src/main/java/com/autoparts/inventory/
  controller/     Auth, Inventory, Notification, Upload, Health
  service/        Auth, Inventory, Notification, Upload, OTP dispatcher
  repository/     JPA repositories
  entity/         JPA entities
  dto/            request bodies
  enums/          BusinessType, VehicleCategory, ChangeType, Notification*, OnboardingStatus
  client/         Twilio, WhatsApp, SMS, FCM
  scheduler/      low-stock cron
  security/       JWT filter
  config/         app properties
```

## Tests

```bash
mvn test
```
