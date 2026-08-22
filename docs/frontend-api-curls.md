# Frontend API curls

Base URL: `http://localhost:8080`

On Windows PowerShell use `curl.exe` (not `curl`). Replace placeholders before running.

```
PHONE=9876543210
OTP=123456
ACCESS_TOKEN=eyJ...
REFRESH_TOKEN=<userId>:<uuid>
ITEM_ID=<uuid>
NOTIFICATION_ID=<uuid>
```

Auth header for protected routes:

```
Authorization: Bearer ACCESS_TOKEN
```

## Response envelope

Success:

```json
{ "success": true, "data": { } }
```

Error:

```json
{
  "success": false,
  "error": { "code": "VALIDATION_ERROR", "message": "...", "field": "phone" }
}
```

`field` is present only for bean-validation errors.

`costPrice` is accepted on create/update but **never returned**.

---

## Health (public)

```bash
curl.exe -sS http://localhost:8080/actuator/health
curl.exe -sS http://localhost:8080/health
```

Expected `200`:

```json
{ "success": true, "data": { "status": "UP", "postgres": "UP", "redis": "UP" } }
```

---

## Auth

### Request OTP (public)

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/request ^
  -H "Content-Type: application/json" ^
  -d "{\"phone\":\"9876543210\"}"
```

Success `200`:

```json
{ "success": true, "data": { "message": "OTP sent", "expires_in": "300" } }
```

OTP is 6 digits, expires in 5 minutes. User is created only after successful verify.

Negatives:

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/request -H "Content-Type: application/json" -d "{\"phone\":\"12345\"}"
# 400 VALIDATION_ERROR  Enter a valid 10-digit Indian mobile number

curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/request -H "Content-Type: application/json" -d "{}"
# 400 VALIDATION_ERROR  field=phone  must not be blank
```

Phone must match `^[6-9]\\d{9}$`.

Other errors: `OTP_DELIVERY_FAILED` (400), `OTP_MAX_ATTEMPTS` (429, 5 requests / 10 minutes).

### Verify OTP (public)

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/verify ^
  -H "Content-Type: application/json" ^
  -d "{\"phone\":\"9876543210\",\"otp\":\"123456\"}"
```

Success `200`:

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "uuid:uuid",
    "isNewUser": true,
    "user": {
      "id": "uuid",
      "phone": "9876543210",
      "name": null,
      "shopName": null,
      "onboardingStatus": "REGISTERED"
    }
  }
}
```

Negatives:

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/verify -H "Content-Type: application/json" -d "{\"phone\":\"9876543210\",\"otp\":\"000000\"}"
# 400 OTP_EXPIRED  (no OTP in Redis)  or  OTP_INVALID  (wrong code)

curl.exe -sS -X POST http://localhost:8080/api/v1/auth/otp/verify -H "Content-Type: application/json" -d "{\"phone\":\"9876543210\",\"otp\":\"12\"}"
# 400 VALIDATION_ERROR  field=otp  size must be between 6 and 6
```

### Refresh token (public)

JSON field is `refresh_token` (underscore).

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/token/refresh ^
  -H "Content-Type: application/json" ^
  -d "{\"refresh_token\":\"REFRESH_TOKEN\"}"
```

Success `200`: `{ "accessToken", "refreshToken" }` (new refresh token; old one is invalid).

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/token/refresh -H "Content-Type: application/json" -d "{\"refresh_token\":\"not-a-token\"}"
# 401 UNAUTHORIZED  Invalid refresh token
```

### Profile (Bearer)

```bash
curl.exe -sS http://localhost:8080/api/v1/auth/profile -H "Authorization: Bearer ACCESS_TOKEN"
```

```bash
curl.exe -sS -X PUT http://localhost:8080/api/v1/auth/profile ^
  -H "Authorization: Bearer ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Aakash\",\"shopName\":\"Test Auto Parts\",\"email\":\"shop@example.com\",\"businessType\":\"SHOP\",\"address\":\"Shop 1\",\"area\":\"Sector 14\",\"city\":\"Gurgaon\",\"state\":\"Haryana\",\"pincode\":\"122001\",\"vehicleCategories\":[\"FOUR_WHEELER\"]}"
```

`businessType`: `SHOP` | `SERVICE_CENTER` | `BOTH`  
`vehicleCategories`: `TWO_WHEELER` | `FOUR_WHEELER` | `THREE_WHEELER` | `COMMERCIAL` | `EV`  
`onboardingStatus`: `REGISTERED` → `PROFILED` after name + shopName.

```bash
curl.exe -sS http://localhost:8080/api/v1/auth/profile
# 401 UNAUTHORIZED  Missing or invalid token
```

### Logout (Bearer)

```bash
curl.exe -sS -X DELETE http://localhost:8080/api/v1/auth/logout -H "Authorization: Bearer ACCESS_TOKEN"
```

Clears the refresh session. The access JWT stays valid until it expires (~24h).

### Multiple accounts on one phone

A phone number can own several shop accounts. If `otp/verify` finds more than one account for the
phone, it returns `needsAccountSelection` instead of tokens:

```json
{ "success": true, "data": { "needsAccountSelection": true, "phoneToken": "...", "accounts": [ { "id": "...", "shopName": "...", "status": "ACTIVE" } ] } }
```

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/accounts/select -H "Content-Type: application/json" -d "{\"phoneToken\":\"PHONE_TOKEN\",\"accountId\":\"ACCOUNT_ID\"}"
```

`phoneToken` is single-use and expires in 5 minutes (same window as the OTP). Selecting a deactivated
account reactivates it, since it's proof of phone ownership.

List / add / switch accounts while logged in (Bearer):

```bash
curl.exe -sS http://localhost:8080/api/v1/auth/accounts -H "Authorization: Bearer ACCESS_TOKEN"
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/accounts -H "Authorization: Bearer ACCESS_TOKEN"
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/accounts/switch -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"accountId\":\"ACCOUNT_ID\"}"
```

`switch` only works between accounts sharing the same phone number, and refuses a deactivated target
(`409 ACCOUNT_DEACTIVATED`) — reactivate it via OTP verify/select instead.

### Deactivate / delete account (Bearer)

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/auth/deactivate -H "Authorization: Bearer ACCESS_TOKEN"
curl.exe -sS -X DELETE http://localhost:8080/api/v1/auth/account -H "Authorization: Bearer ACCESS_TOKEN"
```

`deactivate` is reversible — re-verifying OTP (or selecting/switching to the account) reactivates it.
`DELETE /account` is a hard delete: it permanently removes the account and everything it owns
(inventory, location, notifications). No undo.

---

## Inventory (Bearer)

### List

Query: `q` (search), `vehicle` (`FOUR_WHEELER` …), `status` (`IN_STOCK` | `LOW_STOCK` | `OUT_OF_STOCK`).

```bash
curl.exe -sS "http://localhost:8080/api/v1/inventory" -H "Authorization: Bearer ACCESS_TOKEN"
curl.exe -sS "http://localhost:8080/api/v1/inventory?q=Brake&vehicle=FOUR_WHEELER&status=IN_STOCK" -H "Authorization: Bearer ACCESS_TOKEN"
```

### Add part

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/inventory ^
  -H "Authorization: Bearer ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"partName\":\"Brake Pad\",\"localName\":\"Brake Shoe\",\"specification\":\"Front\",\"description\":\"Ceramic\",\"vehicleCategory\":\"FOUR_WHEELER\",\"brand\":\"Bosch\",\"model\":\"Swift\",\"quantity\":5,\"minQuantity\":2,\"sellingPrice\":450.50,\"costPrice\":300,\"images\":[]}"
```

Success `201`. Required: `partName`, `vehicleCategory`, `quantity`. Max 3 `images`.

Negatives:

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/inventory -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"quantity\":1}"
# 400 VALIDATION_ERROR

curl.exe -sS -X POST http://localhost:8080/api/v1/inventory -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"partName\":\"X\",\"vehicleCategory\":\"SPACESHIP\",\"quantity\":1}"
# 400 VALIDATION_ERROR  Invalid request body

curl.exe -sS -X POST http://localhost:8080/api/v1/inventory -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"partName\":\"X\",\"vehicleCategory\":\"FOUR_WHEELER\",\"quantity\":1,\"images\":[\"a\",\"b\",\"c\",\"d\"]}"
# 400 VALIDATION_ERROR  field=images  size must be between 0 and 3
```

### Get / update / delete

```bash
curl.exe -sS http://localhost:8080/api/v1/inventory/ITEM_ID -H "Authorization: Bearer ACCESS_TOKEN"

curl.exe -sS -X PUT http://localhost:8080/api/v1/inventory/ITEM_ID ^
  -H "Authorization: Bearer ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"partName\":\"Brake Pad Pro\",\"sellingPrice\":499,\"minQuantity\":10}"

curl.exe -sS -X DELETE http://localhost:8080/api/v1/inventory/ITEM_ID -H "Authorization: Bearer ACCESS_TOKEN"
```

Delete is soft (`isActive=false`). Later GET returns 404.

```bash
curl.exe -sS http://localhost:8080/api/v1/inventory/00000000-0000-0000-0000-000000000001 -H "Authorization: Bearer ACCESS_TOKEN"
# 404 NOT_FOUND  Part not found
```

### Quantity

`changeType`: `ADD` | `SOLD` | `RECEIVED` | `ADJUSTMENT` | `RETURNED`

```bash
curl.exe -sS -X PATCH http://localhost:8080/api/v1/inventory/ITEM_ID/quantity ^
  -H "Authorization: Bearer ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"change\":-1,\"changeType\":\"SOLD\",\"note\":\"counter sale\"}"
```

```bash
curl.exe -sS -X PATCH http://localhost:8080/api/v1/inventory/ITEM_ID/quantity -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"change\":-999,\"changeType\":\"SOLD\"}"
# 409 INSUFFICIENT_STOCK  Quantity cannot go below zero
```

When quantity ≤ `minQuantity`, a low-stock notification is created (WhatsApp if Twilio is configured).

### Low stock

```bash
curl.exe -sS http://localhost:8080/api/v1/inventory/low-stock -H "Authorization: Bearer ACCESS_TOKEN"
```

---

## Notifications (Bearer)

```bash
curl.exe -sS "http://localhost:8080/api/v1/notifications?page=1&limit=20" -H "Authorization: Bearer ACCESS_TOKEN"

curl.exe -sS -X PATCH http://localhost:8080/api/v1/notifications/NOTIFICATION_ID/read -H "Authorization: Bearer ACCESS_TOKEN"
```

```bash
curl.exe -sS -X PATCH http://localhost:8080/api/v1/notifications/00000000-0000-0000-0000-000000000001/read -H "Authorization: Bearer ACCESS_TOKEN"
# 404 NOT_FOUND  Notification not found
```

`channel`: `WHATSAPP` | `SMS` | `PUSH` | `IN_APP`  
`type`: includes `LOW_STOCK`

---

## Uploads (Bearer)

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/uploads/presign ^
  -H "Authorization: Bearer ACCESS_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"filename\":\"pad.jpg\",\"contentType\":\"image/jpeg\"}"
```

Allowed `contentType`: `image/jpeg` | `image/png`.

```bash
curl.exe -sS -X POST http://localhost:8080/api/v1/uploads/presign -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"filename\":\"x.gif\",\"contentType\":\"image/gif\"}"
# 400 INVALID_FILE_TYPE

curl.exe -sS -X POST http://localhost:8080/api/v1/uploads/presign -H "Authorization: Bearer ACCESS_TOKEN" -H "Content-Type: application/json" -d "{\"filename\":\"x.jpg\",\"contentType\":\"image/jpeg\"}"
# 400 AWS_NOT_CONFIGURED  when S3 keys are missing
```

Success (when AWS is set): `{ "upload_url", "public_url", "key", "filename" }`. PUT the file to `upload_url`, then store `public_url` on the part `images` array.

---

## Error codes

| HTTP | code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bad phone, missing fields, invalid JSON/enum |
| 400 | `OTP_EXPIRED` | No OTP in Redis |
| 400 | `OTP_INVALID` | Wrong OTP |
| 400 | `OTP_DELIVERY_FAILED` | WhatsApp/SMS send failed |
| 400 | `INVALID_FILE_TYPE` | Presign not jpeg/png |
| 400 | `AWS_NOT_CONFIGURED` | No S3 keys |
| 401 | `UNAUTHORIZED` | Missing/invalid JWT or refresh token |
| 404 | `NOT_FOUND` | Part, notification, or unknown route |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method |
| 409 | `INSUFFICIENT_STOCK` | Quantity would go below 0 |
| 429 | `OTP_MAX_ATTEMPTS` | More than 5 OTP requests in 10 minutes |
| 500 | `SERVER_ERROR` | Unexpected |

---

## Route map

| Method | Path | Auth |
| --- | --- | --- |
| GET | `/actuator/health` or `/health` | public |
| POST | `/api/v1/auth/otp/request` | public |
| POST | `/api/v1/auth/otp/verify` | public |
| POST | `/api/v1/auth/token/refresh` | public |
| DELETE | `/api/v1/auth/logout` | Bearer |
| GET | `/api/v1/auth/profile` | Bearer |
| PUT | `/api/v1/auth/profile` | Bearer |
| POST | `/api/v1/auth/accounts/select` | public (phoneToken) |
| GET | `/api/v1/auth/accounts` | Bearer |
| POST | `/api/v1/auth/accounts` | Bearer |
| POST | `/api/v1/auth/accounts/switch` | Bearer |
| POST | `/api/v1/auth/deactivate` | Bearer |
| DELETE | `/api/v1/auth/account` | Bearer |
| GET | `/api/v1/inventory` | Bearer |
| POST | `/api/v1/inventory` | Bearer |
| GET | `/api/v1/inventory/low-stock` | Bearer |
| GET | `/api/v1/inventory/{id}` | Bearer |
| PUT | `/api/v1/inventory/{id}` | Bearer |
| DELETE | `/api/v1/inventory/{id}` | Bearer |
| PATCH | `/api/v1/inventory/{id}/quantity` | Bearer |
| GET | `/api/v1/notifications` | Bearer |
| PATCH | `/api/v1/notifications/{id}/read` | Bearer |
| POST | `/api/v1/uploads/presign` | Bearer |
