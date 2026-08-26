# Project: Time-Limited AI Kiosk

## Overview
A "plug and play" AI access system for walk-in customers, similar to how public
libraries/cyber cafes grant timed computer access. A customer gets a
username/password or voucher, uses an AI chat session for a fixed time window,
and the session auto-terminates (logout + data deletion) after the limit.

## Core session rules
- **Session length:** 30 minutes wall-clock, hard cutoff
- **Token cap:** 50,000 tokens per session (input + output combined), hard cutoff
  - Whichever limit (time or tokens) is hit first ends the session
  - Warn the user at ~5 min remaining OR ~80% of token budget used
- **Data retention:** chat logs kept for 24 hours after session end, then
  hard-deleted (not soft-deleted) via scheduled job
- **Concurrency target:** 10 simultaneous active sessions
- **Auth model:** simple generated username/password or voucher code per
  customer — no self-serve signup, staff/admin issues credentials
- **Manual logout:** chat UI has a "Logout" button gated by a confirmation
  dialog showing remaining minutes/tokens, so a misclick can't end the
  session early
- **Grace period on logout:** a session isn't hard-deleted the instant it
  ends (manual or accidental logout) — it's paused and kept resumable for a
  5-minute grace period (configurable); the 30-min wall-clock timer keeps
  counting down during this window (no time refunded); returning with the
  same voucher/session within the window resumes the same tokens-remaining
  and conversation history
- **Voucher linking:** a customer can apply a second, unused voucher to an
  already-active session to add its token cap and time to the running
  session, without starting over or losing history
- **Email export before shredding:** a customer may have their chat
  transcript (PDF or text) emailed to them before the archive is shredded;
  export is email-only — nothing is downloaded to the shared kiosk terminal
  — and the backend's own copy is still hard-deleted after the 24h
  retention window regardless of whether it was exported

## Stack
| Layer | Choice | Notes |
|---|---|---|
| Frontend | React | Login screen, chat UI, live countdown timer, token usage bar |
| Backend | Spring Boot (Java) | Auth, session enforcement, LLM proxy |
| Session state | Redis | TTL-based auto-expiry (30 min) handles logout + short-term cleanup natively |
| Persistent storage | PostgreSQL | Voucher/user records, session logs, chat archive (until shredding job runs) |
| AI layer | Claude API (start with Haiku 4.5 for cost, Sonnet 5 if quality needs bump) | Proxied through backend — API key never touches frontend. Swappable later for self-hosted vLLM/Ollama if volume justifies local GPU hardware |
| Deployment | Docker Compose locally first | Can move to Cloud Run/GKE later, or stay fully on-prem/offline for data privacy |
| Admin | Small dashboard (React + Spring Boot) | View active sessions, time/tokens remaining per user, manual kill switch |
| Email service | AWS SES (or SendGrid/Postmark) | Sends the on-demand chat transcript export; the only outbound channel for chat content leaving the kiosk — nothing is saved to local disk |
| Scheduler | Spring `@Scheduled` job | Hard-deletes Postgres chat archive after the 24h retention window (belt-and-suspenders alongside Redis TTL) |

## Cost reference (as of Aug 2026, subject to change)
- Claude Haiku 4.5: $1 / $5 per million input/output tokens → ~$0.17 per 50K-token session
- Claude Sonnet 5: $2 / $10 per million input/output tokens → ~$0.34 per 50K-token session
- Self-hosted open-source (Llama 3.1 / Qwen2.5 via Docker + vLLM/Ollama): ~₹2.5–4 lakh
  upfront GPU hardware (e.g. RTX 4090 24GB), near-zero marginal cost per session after that.
  Worth revisiting once real usage volume is known.

## Compliance / privacy notes
- Confirm commercial reselling of timed API access is within Anthropic's API usage policy
  (this is fine — it's your own app on the API, not shared consumer account access)
- India DPDP Act 2023: need consent notice, minimal data collection, breach process
- Encrypt chat logs at rest and in transit; admin-only access to logs
- Deletion must be verifiable (log "deleted at X"), not just silently trusted TTL
- Email export requires basic consent language at the point an email address
  is collected (login or the session-ending prompt) — it's separate personal
  data collection beyond the anonymous voucher/session identifiers

## Build sequence (do these in order)
1. **Define session rules as config** — session length, token cap, retention window,
   warning thresholds — externalized, not hardcoded
2. **Scaffold backend + frontend, dockerize** — Spring Boot `/chat` stub, React login
   screen, `docker-compose.yml` wiring app + Redis + Postgres. Get it running locally
   end-to-end before adding real logic.
3. **Build voucher/login issuing** — generate username/password or voucher code,
   store in Postgres, create Redis session entry on login with TTL=30min and a
   token counter initialized to the cap
4. **Build the LLM proxy with dual limit checks** — endpoint checks Redis for
   time-remaining and tokens-remaining before every call, forwards to Claude API,
   deducts actual tokens used from the Redis counter after the response, rejects
   the call if either limit is already hit
5. **Build the frontend chat + countdown UI** — chat window, visible countdown
   timer, token/usage bar, polling or websocket for remaining time/tokens
   - **5a. Manual logout button** — a "Logout" button in the chat UI; clicking
     it shows a confirmation dialog ("End session? You have X minutes / Y
     tokens remaining.") and only ends the session on confirm, so a misclick
     can't accidentally end it
6. **Wire up auto-logout and shredding** — Redis TTL expiry handles session
   logout + short-term cleanup automatically; add scheduled job to hard-delete
   longer-retained Postgres chat records after the retention window
   - **6a. `POST /api/session/logout`** — backend endpoint the confirmed
     manual logout calls to end the session on demand
   - **6b. Grace-period pause instead of immediate hard-delete** — on any
     logout (manual or accidental), mark the Redis session paused and keep it
     alive for a 5-minute grace period (configurable) instead of deleting it
     immediately; the 30-min wall-clock timer keeps running during this
     window (no time refunded); returning with the same voucher/session
     within the window resumes the same `tokensRemaining` and
     `session:messages:{id}` history; past the grace period, hard-delete
     proceeds as normal
   - **6c. `POST /api/session/extend`** — accepts a second, unused voucher
     code for an already-active session; adds that voucher's `token_cap` and
     `session_length_minutes` to the current session's remaining time/tokens
     in Redis and marks the second voucher consumed in Postgres, without
     creating a new session or losing conversation history
   - **6d. `POST /api/chat/export`** — formats the session's conversation
     history into a PDF or plain text and emails it to a customer-provided
     address (collected at login or prompted on the session-ending warning
     screen) before the 24h archive is shredded; email-only — nothing is
     written to the local kiosk terminal; the backend's own copy is still
     hard-deleted after the retention window regardless of export
   - **6e. Postgres chat archive + scheduled hard-delete** — every chat
     exchange is upserted into a `chat_archives` table (belt-and-suspenders
     alongside Redis, which only holds the conversation for the session's own
     lifetime + grace period); a `@Scheduled` job sweeps every 15 minutes and
     hard-deletes any archive past `kiosk.session.retention-hours` (default
     24h) — never soft-deleted — logging each deleted session ID and the
     sweep timestamp so deletion is verifiable, not just silently trusted
7. **Build the admin dashboard** — list active sessions with time/tokens
   remaining, manual kill switch. Served at `/admin` on the same frontend
   origin, gated by the same `X-Admin-Key` header as voucher issuance rather
   than a separate staff-account system; `GET /api/admin/sessions` reads a
   live Redis scan, `DELETE /api/admin/sessions/{sessionId}` force-ends one
   immediately (no grace period, unlike a customer's own logout)
   - **7a. Issue New Login panel** — a type toggle (username/password vs
     voucher code) plus a "Generate" button on the dashboard itself, calling
     the existing `POST /api/admin/vouchers` endpoint (built in step 3, until
     now only reachable via curl/PowerShell); displays the result with a
     one-click copy so staff can hand it straight to a walk-in customer
     without a terminal
8. **Load test and deploy** — simulate 10 concurrent sessions locally to confirm
   Redis + Claude API rate limits hold up, then deploy via Docker Compose
   (intranet) or push to Cloud Run/GKE if cloud-hosted

## Current status
- [x] Step 1: Define session rules as config
- [x] Step 2: Scaffold backend + frontend, dockerize
- [x] Step 3: Build voucher/login issuing
- [x] Step 4: Build the LLM proxy with dual limit checks
- [x] Step 5: Build the frontend chat + countdown UI
  - [x] Step 5a: Manual logout button with confirmation dialog
- [x] Step 6: Wire up auto-logout and shredding
  - [x] Step 6a: `POST /api/session/logout` endpoint
  - [x] Step 6b: Grace-period pause/resume instead of immediate hard-delete
  - [x] Step 6c: `POST /api/session/extend` (voucher linking to extend an active session)
  - [x] Step 6d: `POST /api/chat/export` (email transcript before shredding)
  - [x] Step 6e: Postgres chat archive + scheduled hard-delete job
- [x] Step 7: Build the admin dashboard
  - [x] Step 7a: Issue New Login panel (username/password or voucher code)
- [ ] Step 8: Load test and deploy
