# Vehicle Preview and Guide Animation Implementation Plan

> **For agentic workers:** use the executing-plans workflow and complete each task with its tests before moving to the next task.

**Goal:** Make Vehicle previews represent confirmed state/context and move explanatory animation into an explicit, UX-policy-gated Info dialog.

**Architecture:** Core UI owns the visual model, policy, selection, playback state, and layer renderer. Feature modules own definition-to-scene registries and frame resolvers. The vehicle controller keeps optimistic editor state and confirmed observation state separately.

**Tech Stack:** Kotlin, Jetpack Compose, B-Material, Android Vector Drawable, JUnit, Compose UI Test, AAOS rotary focus.

**Spec:** `docs/superpowers/specs/2026-09-08-vehicle-preview-design.md`

## Global Constraints

- No new animation/video/WebView dependency.
- `confirmedValue` is the only preview state source.
- Preview is decorative; Info is the only guide entry point.
- Restricted or unavailable UX policy disables long Info content and playback.
- No live-detection claim without a matching VHAL event/property.
- No new Compose test dependency; the convention plugin already supplies it.

## Execution order

1. Add the spec/catalog and visual model.
2. Map confirmed snapshots and fix UX-policy failure handling.
3. Fix Climate metadata and generic selection/layout.
4. Add Info playback and scene contract.
5. Replace ADAS substring mapping and add feature registries.
6. Add scene layers for Climate, ADAS, Door, Seat, and Lighting.
7. Run unit/instrumentation/build verification and remove obsolete CTA code.
