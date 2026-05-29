# Document 3: Van Sales — Execution Plan

**Timeline reference:** Spec §7 — 10 calendar weeks (common core + Mozambique delta + config plumbing).  
**Complexity:** S = ≤2 days | M = 3–5 days | L = 6+ days (1 dev)

---

## Phase 1: Core (Uganda baseline)

**Goal:** Single APK with van module enabled for Uganda tenant flags (journey/GPS/offline **off**). VSE can login → start day → sell → cash-up.

| ID | Task | What to build | Screens / components | Dependencies | Size |
|----|------|---------------|------------------------|--------------|------|
| P1-01 | Backend contract freeze (van baseline) | API OpenAPI / sample payloads for login config, binding, start day, van sale, van cash-up | — | Product sign-off | M |
| P1-02 | `VanSalesPolicy` + config cache | Policy service, DTO, DataStore + Room cache, init on login | `MPOSLoginActivity`, `MyApplication` | P1-01 | M |
| P1-03 | Org module + login routing | `"van sales"` module; `RoleRouter` → van vs store landing | `MPOSLoginActivity`, `SplashScreenActivity` | P1-02 | M |
| P1-04 | Driver → Van-Store binding | Load van store id, vehicle, price book context after login | `VanSessionManager`, login flow | P1-01, P1-03 | M |
| P1-05 | `VanHomeActivity` | Van dashboard cards: POS, inventory, sales, stock req, sync, end day | New activity + layout | P1-04 | M |
| P1-06 | Start of Day | Vehicle pre-fill, depot selection, GPS start hook (no capture), API submit | `StartDayActivity` | P1-04, P1-01 | L |
| P1-07 | Start Day gate | Block POS until day started; replace store `isCashupOutdated` for VSE | `VanHomeActivity` | P1-06 | S |
| P1-08 | Van POS — customer gate | Mandatory customer; hide walk-in; reuse bottom sheet | `MPOSDashboardActivity` pattern → van | P1-05, P1-02 | M |
| P1-09 | Van POS — sale path | Intent extras van mode; `VanPosSaleRepository`; van `store_id` on APIs | `PointOfSaleActivity`, `PointofSaleDetailsActivity` | P1-08, P1-04 | L |
| P1-10 | Van product inventory | Filter `ProductInventoryActivity` to van store | `ProductInventoryActivity` | P1-04 | S |
| P1-11 | Van sales history | Sales list/detail scoped to van day/store | `SalesAndPaymentActivity`, ViewModels | P1-04 | M |
| P1-12 | Van Cash-up / End Day | Reuse cash-up UI; van-scoped `cashupdetails` / `submitcashup`; OTP | `VanCashUpActivity`, `CashUpDetailsActivity` delegate | P1-06, P1-01 | L |
| P1-13 | Auto-logout after EOD | Post cash-up logout (match store behaviour) | Cash-up completion | P1-12 | S |
| P1-14 | Customer master fields | Area, first-capture date display on register/search | `NewCustomerRegisterActivity` | P1-01 | S |
| P1-15 | Incentive hint (Uganda) | Show on-track hint on payment when `show_incentive_hint_mpos` | `SalesPaymentDetailsActivity` or van summary | P1-02 | S |
| P1-16 | API models + Retrofit | `models/VanSalesModel/*`, `ApiService` van endpoints | Network layer | P1-01 | M |
| P1-17 | QA — Uganda happy path | Login → start day → 3 sales → cash-up | — | P1-06–P1-13 | M |
| P1-18 | Strings / i18n | EN + existing locales for van screens | `res/values*` | P1-05–P1-12 | S |

**Phase 1 exit criteria:** Uganda tenant config (journey/GPS/offline off); VSE complete day without portal journey; store POS unaffected.

---

## Phase 2: Flag-gated extensions (Mozambique)

**Goal:** Enable Mozambique tenant flags without APK fork.

| ID | Task | What to build | Screens / components | Dependencies | Size |
|----|------|---------------|------------------------|--------------|------|
| P2-01 | Journey API + cache | Journey CRUD/read APIs; `JourneyCacheEntity`; refresh on login/resume | `JourneyRepository` | P1-01 journey APIs | L |
| P2-02 | Journey state machine client | `JourneyGatekeeper`; sealed states; error messages | All van transactional screens | P2-01 | L |
| P2-03 | Start Day — journey gate | Block if open journey; require approved requisition | `StartDayActivity` | P2-02, P1-06 | M |
| P2-04 | Journey status UI | Timeline / current step for VSE | `JourneyStatusActivity` | P2-01 | M |
| P2-05 | Journey# on sales | Stamp `journey_id` on pending + live sales | `VanPosSaleRepository` | P2-02, P1-09 | M |
| P2-06 | Top-up requisition | Mid-journey requisition UI when flag on | New activity or extend `StockRequisitionActivity` | P2-02, P2-01 | M |
| P2-07 | Sales return actor gate | Hide/block VSE return if `warehouse_mgr` | `ReturnSaleActivity`, goods return | P1-02 | S |
| P2-08 | GPS — permissions + helper | Manifest, runtime permissions, `GpsCaptureHelper` | Application + base activity | — | M |
| P2-09 | GPS — customer master | Lat/long on `addcustomer` for regular customers | `NewCustomerRegisterActivity` | P2-08, P1-14 | M |
| P2-10 | GPS — per-invoice stamp | Capture at checkout; sale payload | `PointofSaleDetailsActivity` | P2-08, P1-09 | M |
| P2-11 | Odometer + fuel (Start Day) | Policy-gated fields + validation | `StartDayActivity` | P1-06, P1-02 | S |
| P2-12 | Odometer (End Day) | Closing reading on van cash-up | `VanCashUpActivity` | P1-12, P1-02 | S |
| P2-13 | Route planning picker | Approved routes on Start Day when flag on | `StartDayActivity` | P1-01 routes API | M |
| P2-14 | EOD journey messaging | Cash-up does not close journey when `journey_closer_role=accounts` | `VanCashUpActivity` | P2-02, P1-12 | S |
| P2-15 | EOD notes field | Road conditions / incidents notes | Van cash-up layout | P1-12 | S |
| P2-16 | Promotion geo validation | Pass coords; display server validation errors | POS checkout | P2-10, backend | S |
| P2-17 | QA — Mozambique flags on | Full journey + GPS + odometer on staging tenant | — | P2-* | L |

**Phase 2 exit criteria:** Mozambique config enabled; journey enforced; GPS on customer + invoice; odometer/fuel/route; EOD leaves journey open for Accounts.

---

## Phase 3: Offline sync

**Goal:** `enable_offline_sync=true` — operate disconnected; replay on reconnect.

| ID | Task | What to build | Screens / components | Dependencies | Size |
|----|------|---------------|------------------------|--------------|------|
| P3-01 | Room migrations (production-safe) | Remove destructive migration; v27+ van tables | `PosDatabase` | P1-02 | M |
| P3-02 | Pending van sale queue | `PendingVanSaleEntity`, DAO, repository | `VanPosSaleRepository` | P1-09, P3-01 | L |
| P3-03 | Pending start day / cash-up | `PendingVanDayEntity`, `PendingVanCashupEntity` | Start/cash-up repos | P1-06, P1-12 | M |
| P3-04 | Pending customer + GPS | `PendingCustomerEntity` | Customer register | P2-09 | M |
| P3-05 | Offline catalog bootstrap | Download van products/customers/journey on login | Login + `VanHomeActivity` | P1-04, P3-01 | L |
| P3-06 | Extend `SyncWorker` | Ordered replay; register all van repos | `SyncWorker`, `MyApplication` | P3-02–P3-04 | M |
| P3-07 | Offline invoice ID remap | Server ID replacement post-sync (sales) | `VanPosSaleRepository` | P3-02 | M |
| P3-08 | Conflict UI | Failed sync screen; retry / discard policies | `VanHomeActivity` sync status | P3-06 | M |
| P3-09 | Offline indicator + manual sync | Reuse dashboard sync button pattern | `VanHomeActivity` | P3-06 | S |
| P3-10 | `client_txn_id` idempotency | UUID on each pending row; server contract | All pending entities | Backend | S |
| P3-11 | QA — offline scenarios | Airplane mode: start day, 5 sales, cash-up, reconnect sync | — | P3-* | L |

**Phase 3 exit criteria:** 8-hour offline operation; no data loss; journey state consistent after sync.

---

## Cross-phase: Config plumbing (run parallel to Phase 1)

| ID | Task | Size |
|----|------|------|
| X-01 | Portal tenant Van Sales keys (backend) — out of Android scope but blocking P1-02 | M |
| X-02 | `VanSalesPolicy` unit tests | S |
| X-03 | Feature flag documentation for QA tenants | S |

---

## Backend contracts needed

Checklist for backend team — **all required before Phase 1 code complete** unless marked [P2]/[P3].

### Authentication & session

| # | Method | Endpoint (proposed) | Request | Response | Phase |
|---|--------|---------------------|---------|----------|-------|
| B-01 | POST | `login` (extend) | existing + return `van_sales_config`, `landing_mode`, `van_store_id?` | `VanSalesConfig`, binding | P1 |
| B-02 | GET | `userprofile` (extend) | — | `organization.van_sales_config`, modules | P1 |
| B-03 | GET | `vansales/driver-binding` | `user_id` | `van_store_id`, `vehicle`, `distributor_id`, price book id | P1 |

### Configuration

| # | Method | Endpoint | Notes | Phase |
|---|--------|----------|-------|-------|
| B-04 | GET | `vansales/config` | All §4.1 keys + version | P1 |
| B-05 | — | Config versioning | `config_version` for audit | P1 |

### Day lifecycle

| # | Method | Endpoint | Request | Response | Phase |
|---|--------|----------|---------|----------|-------|
| B-06 | GET | `vansales/day-status` | `van_store_id` | `active`, `van_day_id`, started_at | P1 |
| B-07 | POST | `vansales/start-day` | depot_id, vehicle_id, odometer?, fuel?, route_id?, gps | `van_day_id` | P1 |
| B-08 | GET | `vansales/cashup-details` | `van_day_id` | expected cash/M-Money (mirror `cashupdetails`) | P1 |
| B-09 | POST | `vansales/submit-cashup` | amounts, OTP ref, odometer?, notes, journey_id? | success | P1 |
| B-10 | POST | `sendotptocit` / `verifycitotp` | extend with `van_day_id` | existing | P1 |

### Sales & customer

| # | Method | Endpoint | Notes | Phase |
|---|--------|----------|-------|-------|
| B-11 | POST | `sale` (extend) | `van_store_id`, `van_day_id`, `journey_id?`, `sale_lat`, `sale_lng`, `client_txn_id` | P1/P2 |
| B-12 | POST | `addcustomer` (extend) | `latitude`, `longitude`, `customer_type` (regular/retail) | P2 |
| B-13 | POST | `getcustomer` | van-scoped customer list / search | P1 |
| B-14 | POST | `searchstoreproduct` | van `store_id` | P1 |

### Journey [P2]

| # | Method | Endpoint | Notes | Phase |
|---|--------|----------|-------|-------|
| B-15 | GET | `vansales/journey/active` | Current journey for VSE | P2 |
| B-16 | GET | `vansales/journey/{id}` | State, lines, allowed actions | P2 |
| B-17 | POST | `vansales/journey/top-up` | Linked requisition | P2 |
| B-18 | GET | `vansales/journey/block-reason` | Message for Start Day block | P2 |

### Routes [P2]

| # | Method | Endpoint | Phase |
|---|--------|----------|-------|
| B-19 | GET | `vansales/routes/approved` | P2 |

### GPS [P2]

| # | Method | Endpoint | Phase |
|---|--------|----------|-------|
| B-20 | POST | `vansales/gps-events` | Optional heartbeat batch | P2 |

### Offline / sync [P3]

| # | Method | Endpoint | Notes | Phase |
|---|--------|----------|-------|-------|
| B-21 | POST | `vansales/sync/bootstrap` | Products, customers, journey snapshot | P3 |
| B-22 | POST | `sale` | Idempotent on `client_txn_id` | P3 |
| B-23 | POST | `vansales/start-day` | Idempotent per van+date | P3 |
| B-24 | — | Conflict responses | 409 + `error_code`, `server_invoice_id` | P3 |

### Incentive [P1 Uganda]

| # | Method | Endpoint | Phase |
|---|--------|----------|-------|
| B-25 | GET | `vansales/incentive-hint` | `van_day_id` → on-track % | P1 |

### Deposit management (portal — reference)

| # | Method | Endpoint | Phase |
|---|--------|----------|-------|
| B-26 | CRUD | `vansales/deposits` | Accounts portal only | P2 |

---

## Dependency graph (simplified)

```
P1-01 (APIs) ─► P1-02 (Policy) ─► P1-03 (Routing) ─► P1-04 (Binding)
                                      │
                    P1-05 (Van Home) ◄┘
                         │
         P1-06 (Start Day) ─► P1-07 (Gate) ─► P1-08–09 (POS)
                         │
                    P1-12 (Cash-up)
                         │
         P2-01 (Journey) ─► P2-02 (FSM) ─► P2-03–06
         P2-08 (GPS) ─► P2-09–10
                         │
         P3-01 (Migrations) ─► P3-02–06 (Queues + Worker)
```

---

## Team allocation suggestion (10 weeks)

| Weeks | Android focus | Backend focus |
|-------|---------------|-----------------|
| 1–2 | P1-02–P1-05, P1-16 | B-01–B-07 |
| 3–4 | P1-06–P1-09, P1-12 | B-08–B-14 |
| 5–6 | P1-10–P1-18, P2-01–P2-05 | B-15–B-18 |
| 7–8 | P2-06–P2-17 | B-19–B-20, portal journey |
| 9–10 | P3-* | B-21–B-24, hardening |
