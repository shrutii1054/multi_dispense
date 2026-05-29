# Document 2: Van Sales — Technical Approach

**Per-requirement pattern recommendations** based on existing RetailONE MPOS codebase and spec v2.1.

---

## 1. Config-driven UI (VanSalesPolicy)

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | Singleton `VanSalesPolicy` backed by immutable `VanSalesConfig` data class, loaded from API and cached in DataStore + `VanSalesConfigEntity` (Room) |
| **UI binding** | View-level `View.GONE` / `isVisible` in Activities (match `MPOSDashboardActivity.applyFeatureVisibility()`); optional lightweight `PolicyAware` interface for fragments |
| **Why (codebase fit)** | `FeatureManager` already gates dashboard cards from org modules; Van Sales needs richer typed flags than string module names |
| **Libraries** | No new UI framework — keep ViewBinding + Material |
| **Risks** | Mid-day config change vs in-flight sale; mitigate by snapshotting policy at transaction start. Two sources of truth if both `FeatureManager` and policy used — **document single rule:** module `"van sales"` = entry; `VanSalesPolicy` = behaviour |

---

## 2. Van POS extension

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | **Composition over duplication:** `VanPosCoordinator` wraps existing `PointOfSaleActivity` / `PointofSaleDetailsActivity` with `Intent` extras: `EXTRA_VAN_MODE=true`, `EXTRA_VAN_STORE_ID`, `EXTRA_JOURNEY_ID` |
| **Repository** | `VanPosSaleRepository` extends or delegates to `PosSaleRepository`; adds van/journey/GPS fields to `JsonObject` patch before `POST sale` |
| **Customer flow** | Reuse customer bottom sheet pattern from `MPOSDashboardActivity.customerBottomSheet()`; branch to hide anonymous path when `requireIdentifiedCustomer()` |
| **Why** | POS logic (tax, batch, barcode, printing) is mature; rewriting risks regression |
| **Libraries** | Existing ZXing, Gson, printer utils |
| **Risks** | Walk-in path leakage from store mode; **enforce** van entry only via `VanHomeActivity`. Stock scope must use van `store_id` not depot. Manual invoice entry and tax-inclusive rules must match store behaviour per spec §5.6 |

---

## 3. GPS capture (customer + invoice)

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | `GpsCaptureHelper` — Play Services **Fused Location Provider** one-shot `getCurrentLocation()` with timeout; fallback last-known if fresh enough (&lt; 5 min) |
| **Permissions** | `ACCESS_FINE_LOCATION` + runtime rationale; degrade gracefully when denied (block regular customer create if policy requires GPS) |
| **Customer master** | Extend `AddNewCustReq` / `NewCustomerRegisterActivity` with `latitude`, `longitude` when `enable_gps_capture` |
| **Per-sale stamp** | Capture at payment confirm in `PointofSaleDetailsActivity` van branch; attach to sale JSON |
| **Why** | No location code exists today (manifest has no location permissions) |
| **Libraries** | `com.google.android.gms:play-services-location` (add to `app/build.gradle.kts`) |
| **Risks** | Battery / indoor accuracy; UX latency on checkout — show non-blocking “acquiring location…” with 10s timeout. Promotion validation (spec §5.6) needs **server-side** geo rules; app only captures coordinates. Privacy compliance for driver location data |

---

## 4. Offline sync

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | Clone proven **`PendingSaleEntity` + `PosSaleRepository` + `SyncWorker`** pipeline for van entity types |
| **Sync status** | Reuse `PENDING | SYNCING | SYNCED | FAILED` enum pattern |
| **Trigger** | Keep WorkManager 15-min + network constraint; add van repos to `SyncWorker.doWork()` |
| **Invoice IDs** | Reuse `generateNextOfflineInvoiceId()` logic with `OFF_` prefix |
| **Why** | Strongest pattern in codebase; spec flags Mozambique offline as high-risk |
| **Libraries** | Room 2.6.1, WorkManager 2.8.1 (already present) |
| **Risks** | `fallbackToDestructiveMigration()` **must be replaced** before production van offline. Journey + offline compound complexity — journey state must be cached at start day. Stock conflicts on replay — align with server idempotency keys (`client_txn_id` UUID per pending row) |

---

## 5. Journey Module state machine

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | **Server-authoritative FSM**; client holds `JourneyCacheEntity` mirror. `JourneyRepository` polls/refreshes on screen entry. `JourneyGatekeeper` centralizes “can start day / can sell / can cash-up” |
| **States (client view)** | `DRAFT → APPROVED → LOADED → IN_PROGRESS → AWAITING_RETURN → RETURNED → RECONCILED → SETTLED → CLOSED` (map to server enums 1:1) |
| **UI** | `JourneyStatusActivity` (read-only timeline); block dialogs from `JourneyGatekeeper` |
| **Stamping** | `journey_id` on all `PendingVanSaleEntity` and sale API payloads |
| **Why** | 13-step flow with segregation of duties cannot be client-only |
| **Libraries** | None — Kotlin sealed class for states |
| **Risks** | Largest Mozambique risk (spec §7.2). Stale cache selling in wrong state — **hard block** POS unless `IN_PROGRESS`. Top-up requisition needs new API. WH Manager return step is portal-side but MPOS must reflect state transitions |

---

## 6. Start-of-Day / End-of-Day

| Aspect | Recommendation |
|--------|----------------|
| **Start pattern** | New `StartDayActivity` workflow activity (like `CashUpActivity` gate + form). `VanDayRepository.startDay()` → API + local `VanDayEntity` |
| **Gate** | `VanHomeActivity` checks `VanDayRepository.isDayActive()` before POS — analogous to `isCashupOutdated()` on store dashboard |
| **End pattern** | `VanCashUpActivity` → reuse `CashUpDetailsActivity` layout/logic via shared `CashUpFlowDelegate` or copy with van ViewModel |
| **Odometer / fuel** | Policy-gated `TextInput` fields; validate numeric ranges |
| **GPS tracking start** | On successful start day: optional periodic `PendingGpsEventEntity` if product requires continuous tracking (spec says “GPS tracking begins” — clarify interval with backend) |
| **Why** | Store uses cash-up date not explicit start; van needs explicit Start Day card per spec §5.4 |
| **Libraries** | Reuse cash-up OTP (`sendotptocit`, `verifycitotp`) |
| **Risks** | Two day models (store `cashup_date_time` vs van `van_day`) — VSE login must not use store cash-up gate. Auto-logout after EOD must clear van session without wiping offline queue |

---

## 7. Role-based navigation

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | `RoleRouter` after `userprofile`: map `role_id` / `user_type` → `LandingDestination { STORE, VAN, HYBRID }` |
| **VSE** | `VanHomeActivity` when `VanSalesPolicy.isModuleEnabled()` |
| **Store manager** | Existing `MPOSDashboardActivity` |
| **Implementation** | Extend `MPOSLoginActivity` success handler; avoid duplicating offline login paths |
| **Why** | `role_id` exists on `UserDetails` but is unused for navigation today |
| **Libraries** | None |
| **Risks** | Role IDs may differ per tenant — prefer server `landing_mode` field in profile API over hardcoded IDs. Users with both store and van hats need product decision |

---

## 8. OTP-verified bank deposit

| Aspect | Recommendation |
|--------|----------------|
| **Pattern** | **Reuse existing CIT cash-up OTP flow** verbatim: `CashupDetailsViewmodel` → `sendotptocit` → `verifycitotp` → `submitcashup` |
| **Van extension** | Pass `van_day_id` / `journey_id` in `CashupSubmitReq` extension fields |
| **Deposit management** | Portal-only (`enable_deposit_management`); MPOS only hands over cash-up |
| **Why** | `CashUpDetailsActivity` already implements OTP UI and totalizer hooks |
| **Libraries** | None new |
| **Risks** | conflate Forgot PIN OTP (`sendotp`) with CIT OTP — keep separate ViewModels. M-Money vs cash split validation is server-side |

---

## Summary risk register

| # | Area | Severity | Mitigation |
|---|------|----------|------------|
| R1 | Offline + Journey | High | Server idempotency; strict journey gatekeeper; ordered sync |
| R2 | GPS accuracy / permission denial | Medium | Timeout + policy-driven block/allow |
| R3 | POS regression | Medium | Van mode via Intent extras; integration tests on tax/print |
| R4 | Room destructive migration | High | Proper migrations before Mozambique |
| R5 | Dual FeatureManager / Policy | Medium | Document precedence; single init on login |
| R6 | Backend API lag | High | Phase 1 mocks / contract-first; stub journey states for Uganda |

---

## Technology stack (unchanged unless noted)

| Concern | Current | Van Sales addition |
|---------|---------|-------------------|
| UI | XML + ViewBinding | New van activities |
| DI | Manual | Stay manual (or phased Hilt — out of scope) |
| Async | Coroutines | Same |
| Network | Retrofit 2.9 | New van endpoints |
| DB | Room 26 | +van entities, migrations |
| Background | WorkManager | Extended worker |
| Location | — | Play Services Location |
| Navigation | Intents | Same pattern |
