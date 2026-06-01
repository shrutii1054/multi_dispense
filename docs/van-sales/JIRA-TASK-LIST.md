# Van Sales MPOS — Jira-Style Task List

**Epic:** RETAILONE-VAN — Van Sales Android MPOS  
**Project:** RetailONE Multi-tenant  
**Spec:** Kookwell Van Sales v2.1  

Use **Fix Version:** `VanSales-Phase1` | `VanSales-Phase2` | `VanSales-Phase3`

---

## Epic 1: Foundation & config (Phase 1)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-101 | Story | Freeze van baseline API contracts with backend | 5 | Phase1 | — |
| VAN-102 | Story | Implement VanSalesPolicy + config DTO + DataStore/Room cache | 5 | Phase1 | VAN-101 |
| VAN-103 | Story | Add Retrofit models and ApiService van baseline endpoints | 5 | Phase1 | VAN-101 |
| VAN-104 | Story | Login routing: org module "van sales" + RoleRouter to Van Home | 3 | Phase1 | VAN-102 |
| VAN-105 | Story | VanSessionManager: driver→vehicle→van-store binding after login | 5 | Phase1 | VAN-103, VAN-104 |
| VAN-106 | Task | Unit tests for VanSalesPolicy flag resolution | 2 | Phase1 | VAN-102 |

---

## Epic 2: Van Home & Start of Day (Phase 1)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-201 | Story | VanHomeActivity dashboard with van action cards | 5 | Phase1 | VAN-105 |
| VAN-202 | Story | StartDayActivity: vehicle, depot, start-day API | 8 | Phase1 | VAN-105, VAN-103 |
| VAN-203 | Story | Gate POS and van actions until Start Day completed | 3 | Phase1 | VAN-202 |
| VAN-204 | Task | Van Home layout + navigation drawer parity with store | 2 | Phase1 | VAN-201 |
| VAN-205 | Task | i18n strings for van home and start day (EN, fr, sw, pt) | 2 | Phase1 | VAN-201, VAN-202 |

---

## Epic 3: Van POS — Uganda baseline (Phase 1)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-301 | Story | Mandatory customer selection; hide walk-in for van | 5 | Phase1 | VAN-201, VAN-102 |
| VAN-302 | Story | Van POS mode in PointOfSaleActivity via Intent extras | 8 | Phase1 | VAN-301, VAN-105 |
| VAN-303 | Story | VanPosSaleRepository: van store_id on sale API | 5 | Phase1 | VAN-302, VAN-103 |
| VAN-304 | Story | Van-scoped product inventory screen | 3 | Phase1 | VAN-105 |
| VAN-305 | Story | Van sales history and daily summary (new customer count) | 5 | Phase1 | VAN-105 |
| VAN-306 | Story | Customer master: area field + first/last order display | 3 | Phase1 | VAN-103 |
| VAN-307 | Bug | Regression test: store POS unchanged when van module off | 2 | Phase1 | VAN-302 |

---

## Epic 4: Van Cash-up / End of Day (Phase 1)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-401 | Story | VanCashUpActivity + reuse CashUpDetails OTP flow | 8 | Phase1 | VAN-202, VAN-103 |
| VAN-402 | Story | Van-scoped cashupdetails and submitcashup payloads | 5 | Phase1 | VAN-401 |
| VAN-403 | Story | Auto-logout after van cash-up completion | 2 | Phase1 | VAN-401 |
| VAN-404 | Story | Incentive-on-track hint when show_incentive_hint_mpos=true | 3 | Phase1 | VAN-102, VAN-305 |

---

## Epic 5: QA & release — Uganda (Phase 1)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-501 | Test | E2E: VSE login → start day → 3 sales → cash-up → logout | 5 | Phase1 | VAN-203, VAN-303, VAN-401 |
| VAN-502 | Test | Verify store manager still uses MPOSDashboardActivity | 2 | Phase1 | VAN-104 |
| VAN-503 | Task | Staging tenant Uganda config (all Mozambique flags off) | 1 | Phase1 | VAN-102 |

---

## Epic 6: Journey module (Phase 2)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-601 | Story | JourneyRepository + JourneyCacheEntity + APIs | 8 | Phase2 | VAN-101 |
| VAN-602 | Story | JourneyGatekeeper: block/allow start day, POS, cash-up | 8 | Phase2 | VAN-601 |
| VAN-603 | Story | Start Day: block open journey + require approved requisition | 5 | Phase2 | VAN-602, VAN-202 |
| VAN-604 | Story | JourneyStatusActivity: VSE journey timeline UI | 5 | Phase2 | VAN-601 |
| VAN-605 | Story | Stamp journey_id on all van sales (online + pending) | 5 | Phase2 | VAN-303, VAN-602 |
| VAN-606 | Story | Mid-journey top-up requisition UI | 5 | Phase2 | VAN-602 |
| VAN-607 | Story | Sales return actor gate (warehouse_mgr vs vse) | 3 | Phase2 | VAN-102 |
| VAN-608 | Story | EOD: journey remains open when journey_closer_role=accounts | 3 | Phase2 | VAN-401, VAN-602 |

---

## Epic 7: GPS & route (Phase 2)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-701 | Story | Location permissions + GpsCaptureHelper (Fused Location) | 5 | Phase2 | — |
| VAN-702 | Story | GPS on regular customer first capture (addcustomer) | 5 | Phase2 | VAN-701, VAN-306 |
| VAN-703 | Story | Per-invoice GPS stamp on sale submit | 5 | Phase2 | VAN-701, VAN-303 |
| VAN-704 | Story | Odometer + fuel fields on Start Day (policy-gated) | 3 | Phase2 | VAN-202 |
| VAN-705 | Story | Closing odometer on van cash-up (policy-gated) | 3 | Phase2 | VAN-401 |
| VAN-706 | Story | Approved route picker on Start Day | 5 | Phase2 | VAN-202 |
| VAN-707 | Story | EOD notes field (road conditions, incidents) | 2 | Phase2 | VAN-401 |
| VAN-708 | Story | Display server promotion geo validation errors | 3 | Phase2 | VAN-703 |

---

## Epic 8: QA — Mozambique flags (Phase 2)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-801 | Test | E2E journey: approved → start → sell → return states | 8 | Phase2 | VAN-602–VAN-608 |
| VAN-802 | Test | GPS capture on customer + invoice with location on | 5 | Phase2 | VAN-702, VAN-703 |
| VAN-803 | Task | Staging tenant Mozambique config (all flags on) | 1 | Phase2 | VAN-102 |

---

## Epic 9: Offline sync (Phase 3)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-901 | Story | Room migration v27+: van tables; remove destructive migration | 5 | Phase3 | — |
| VAN-902 | Story | PendingVanSaleEntity + DAO + offline sale path | 8 | Phase3 | VAN-303, VAN-901 |
| VAN-903 | Story | PendingVanDayEntity + PendingVanCashupEntity | 5 | Phase3 | VAN-202, VAN-401 |
| VAN-904 | Story | PendingCustomerEntity with GPS fields | 5 | Phase3 | VAN-702 |
| VAN-905 | Story | Offline bootstrap: cache products, customers, journey | 8 | Phase3 | VAN-105, VAN-901 |
| VAN-906 | Story | Extend SyncWorker: ordered van queue replay | 5 | Phase3 | VAN-902–VAN-904 |
| VAN-907 | Story | Invoice ID remap after successful sale sync | 5 | Phase3 | VAN-902 |
| VAN-908 | Story | Failed sync UI: retry and error display on Van Home | 5 | Phase3 | VAN-906 |
| VAN-909 | Story | client_txn_id UUID on all pending writes (with backend) | 3 | Phase3 | VAN-101 |
| VAN-910 | Story | Offline mode indicator + manual sync on Van Home | 3 | Phase3 | VAN-906 |

---

## Epic 10: QA — Offline (Phase 3)

| Key | Type | Summary | Story Points | Fix Version | Depends On |
|-----|------|---------|--------------|-------------|------------|
| VAN-1001 | Test | Airplane mode: start day, sales, cash-up, reconnect sync | 8 | Phase3 | VAN-902–VAN-910 |
| VAN-1002 | Test | Conflict handling: duplicate invoice, journey state mismatch | 5 | Phase3 | VAN-906 |
| VAN-1003 | Test | 8-hour offline soak test on device | 5 | Phase3 | VAN-1001 |

---

## Backend tickets (mirror for API team)

| Key | Type | Summary | Fix Version |
|-----|------|---------|-------------|
| VAN-B01 | Story | Extend login/userprofile with van_sales_config + binding | Phase1 |
| VAN-B02 | Story | vansales/start-day, day-status APIs | Phase1 |
| VAN-B03 | Story | Extend sale/addcustomer for van context | Phase1 |
| VAN-B04 | Story | vansales cash-up + extend CIT OTP | Phase1 |
| VAN-B05 | Story | Journey lifecycle APIs + block-reason | Phase2 |
| VAN-B06 | Story | Routes approved list API | Phase2 |
| VAN-B07 | Story | Sync bootstrap + idempotent sale/start-day | Phase3 |
| VAN-B08 | Story | Portal: Van Sales tenant config keys §4.1 | Phase1 |

---

## Sprint mapping (suggested)

| Sprint | Goal | Keys |
|--------|------|------|
| Sprint 1 | Contracts + policy + routing | VAN-101–106, VAN-B01, VAN-B08 |
| Sprint 2 | Van Home + Start Day | VAN-201–205, VAN-B02 |
| Sprint 3 | Van POS | VAN-301–307, VAN-B03 |
| Sprint 4 | Cash-up + Phase 1 QA | VAN-401–404, VAN-501–503, VAN-B04 |
| Sprint 5 | Journey core | VAN-601–608, VAN-B05 |
| Sprint 6 | GPS + route + Phase 2 QA | VAN-701–708, VAN-801–803, VAN-B06 |
| Sprint 7 | Offline schema + pending sales | VAN-901–902, VAN-909 |
| Sprint 8 | Offline bootstrap + SyncWorker | VAN-903–910, VAN-B07 |
| Sprint 9 | Offline QA + hardening | VAN-1001–1003 |

---

## Definition of Done (all stories)

- [ ] Code reviewed; matches existing MVVM + Repository patterns  
- [ ] VanSalesPolicy used for branching (no country name checks)  
- [ ] Strings in `values/strings.xml` (+ locales where applicable)  
- [ ] Manual test steps in ticket comment  
- [ ] No regression on store POS path  
- [ ] Backend contract linked in ticket  
