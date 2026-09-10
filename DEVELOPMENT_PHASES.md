# DraftLock Development Phases

This document is the development roadmap for DraftLock. The phases are sequential: later phases should not be treated as complete until the preceding phase is verified.

## Phase 1 — Foundation & Build Stability
- Keep the Android project compiling reliably.
- Keep Room/database, DataStore, UsageStats, permissions, and OAuth dependencies/build configuration stable.
- Fix compiler, lint, and CI failures before moving forward.
- Maintain a reproducible GitHub Actions build.

**Exit condition:** Phase 1 CI validation passes.

## Phase 2 — Canva One-to-One UI Implementation
- Treat the Canva prototype as the visual and interaction source of truth.
- Reproduce all 20 prototype screens at the Canva 390×844 geometry.
- Match layout, spacing, typography, colors, text, controls, states, navigation, and interactions.
- Use the same Canva assets wherever the prototype uses an asset; do not substitute invented artwork when the original asset is available.
- Validate by screenshot/overlay comparison rather than judging whether the UI merely has a similar aesthetic.

**Exit condition:** all prototype screens and flows are implemented and visually verified against Canva.

## Phase 3 — Core Writing & Accountability
- Implement the actual writing document flow.
- Persist the active document and daily word totals.
- Correctly establish the initial text baseline so existing draft text is not counted as newly written.
- Enforce the daily writing quota.
- Persist daily history and emergency-override records.
- Keep the writing experience functional while requirements are being evaluated.

**Exit condition:** writing, word tracking, daily reset, history, and quota behavior pass functional tests.

## Phase 4 — App Usage & Access Control
- Detect configured apps through Android UsageStats.
- Track requirement time accurately.
- Implement the configured lock/unlock behavior using supported Android device-management capabilities.
- Handle permissions and unavailable capabilities explicitly.
- Keep app requirements synchronized with the rules shown in the UI.

**Exit condition:** configured app requirements and access-control behavior work end-to-end on a test device.

## Phase 5 — Google Account & Google Docs
- Complete AppAuth authorization and redirect handling.
- Maintain authenticated Google account state and refresh tokens safely.
- Read the user's selected Google Docs.
- Apply the configured document filter/destination behavior.
- Read/write the active document and synchronize changes without blocking the writing flow.
- Surface sync state and errors accurately in the UI.

**Exit condition:** a real Google account can authorize, a configured Google Doc can be selected, edited, and synchronized successfully.

## Phase 6 — Integrated Product Flow
- Connect the completed UI to the real data and enforcement layers.
- Remove prototype-only placeholders and hard-coded runtime values where they represent real state.
- Ensure every implemented navigation path reaches the correct functional screen/state.
- Verify the full flow from setup → requirements → writing → app requirements → enforcement → completion → history/analytics → Docs sync.

**Exit condition:** the complete primary user journey works without prototype-only dependencies.

## Phase 7 — QA, Release & Verification
- Run unit/instrumented tests and GitHub Actions.
- Test permissions, OAuth failure/retry, offline behavior, daily rollover, word-count edge cases, app usage tracking, and lock-state recovery.
- Perform visual regression checks against Canva.
- Build the release APK and verify installation/startup on the target Android environment.
- Document known limitations before release.

**Exit condition:** release build passes CI and the agreed functional/visual acceptance checks.

## Current Status

**Phase 1:** In progress — a recent CI run exposed a `Dp`/`Int` compile error in `PrototypeActivity.kt`; the fix has been pushed and must be verified by CI.

**Phase 2:** In progress — the Canva prototype is the required one-to-one specification. Do not mark this phase complete based only on approximate visual similarity.

**Phases 3–7:** Not yet complete; continue sequentially after the relevant exit conditions are met.
