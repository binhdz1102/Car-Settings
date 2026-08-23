# Car-Settings - Design Specification

> **Audience**: Google Stitch design engineers and external OEM design partners.
> **Source of truth**: Generated from production codebase; UI parity with
> `My-System-App` (the shell, component set, CCP/rotary contract and tokens are
> the shared `core:ui` layer, ported verbatim).
> **Last updated**: 2026-08-23

---

## 1. Design Tokens

### 1.1 SettingsTokens (static constants)
| Token | Value | Usage |
|-------|-------|-------|
| CardShape | RoundedCornerShape(16.dp) | All cards, rail items, vehicle rows |
| CardHorizontalPadding | 24.dp | Inner padding for list rows |
| CardSingleLineVerticalPadding | 20.dp | Single-line row vertical padding |
| CardSummaryVerticalPadding | 16.dp | Two-line row vertical padding |
| CardGap | 4.dp | Gap between adjacent cards |
| RailItemMinHeight | 92.dp | Minimum height of a category rail item |
| RailWidth | 340.dp | Fixed width of the left navigation rail |
| DetailPaneHorizontalPadding | 24.dp | Detail pane horizontal inset |
| DetailPaneVerticalPadding | 16.dp | Detail pane vertical inset |
| FormControlHeight | 72.dp | Height of text fields and sliders |
| FormItemHeight | 80.dp | FocusItem height for form controls |
| LeadingIconSize | 28.dp | Leading icon size in list rows |
| SectionTopGap | 16.dp | Gap above section headers |

### 1.2 Color roles (BTheme automotive preset, light + dark)
| Role | Light | Dark | Usage |
|------|-------|------|-------|
| primary | #00668A | #8BC9FF | Focus borders, selected icons |
| onPrimary | #FFFFFF | #00344F | Text on primary fill |
| surface | #FAFCFD | #0F171D | Card backgrounds |
| onSurface | #151D22 | #E6F1F8 | Primary text, value text |
| surfaceVariant | #DCE7EC | #24313A | Alternate surface |
| onSurfaceVariant | #3F4B52 | #BECBD4 | Secondary text, muted icons |
| outline | #6E7C84 | #7D909C | Card borders (unfocused) |
| warning | #8A4F00 | #FFB95C | Warning/attention |
| info | #006780 | #65D9FF | Informational |
| success | #006D3D | #7DDBA7 | Success state |

`MySystemTheme` wraps all content in `BTheme(preset = AutomotivePreset)` and
applies the automotive typography scale (≈1.08–1.20× on each semantic role).
B-Material components require these `BTheme` locals; never render them outside
the theme boundary.

### 1.3 SettingsToken functions (per-theme mapped)
- settingsSurfaceColor() - card surface
- settingsSelectedColor() - selected/pressed bg
- settingsBackgroundColor() - page background
- settingsPrimaryColor() - focus border + icon tint
- settingsOutlineColor() - unfocused border
- settingsMutedTextColor() - muted icon tint

> **Stitch constraint**: Never use raw SettingsToken colors without a paired contentColor.
> Always propagate MaterialTheme.colorScheme.onSurface as contentColor on Surface composables.

---

## 2. Typography Scale

| Style | Size | Weight | Usage |
|-------|------|--------|-------|
| titleLarge | 22sp | Medium | Row titles, rail category labels |
| titleMedium | 16sp | Medium | Section headers |
| bodyLarge | 16sp | Normal | Value text (KeyValueRow) |
| bodyMedium | 14sp | Normal | Summaries, annotations, zone labels |
| bodySmall | 12sp | Normal | Error messages below text fields |
| labelMedium | 12sp | Medium | Helper labels |

---

## 3. Navigation Model

### 3.1 Shell structure
`
MainActivity (setSafeRotaryContent host "main-window")
+-- MySystemTheme (BTheme automotive preset, theme follows DisplayRepository ThemeMode)
    +-- SettingsAppShell (two-pane: rail 340dp | detail)
        +-- Rail pane: 14 SettingsCategoryItems (92dp min-height each)
        +-- Detail pane: NavHost (68 registered destinations)
`

- The legacy `home` route is kept as a compatibility deep link only; launching
  it redirects to the Connected devices root (identical to My-System-App).
- The AAOS Settings contract (`SettingsIntentRouter`: `android.settings.*`
  actions, `CarSettingActivities$*` legacy aliases, package extras, ringtone
  types) resolves intents to routes *before* the registry gates them.

### 3.2 Categories (14 rail items in order)
1. CONNECTED_DEVICES  -> bluetooth
2. NETWORK_INTERNET   -> network-internet
3. NOTIFICATIONS      -> notifications
4. SOUND              -> sound
5. DISPLAY            -> display
6. PROFILE            -> profile-accounts
7. LOCATION           -> location
8. PRIVACY            -> privacy
9. ACCESSIBILITY      -> accessibility
10. SECURITY          -> security
11. APPS              -> applications
12. ASSISTANCE_VOICE  -> assistant-voice
13. VEHICLE           -> vehicle (landing: hvac, driver assistance, seats, doors, lighting)
14. SYSTEM            -> system

### 3.3 UX Safety Classes
| Class | Allowed while driving | Example |
|-------|-----------------------|---------|
| ALWAYS_SAFE | Yes | home |
| GLANCEABLE | Yes | display, sound, vehicle/* |
| PARKED_ONLY | No | wifi/*, bluetooth/*, security/* |

Blocked destinations show the `ux_restricted` SettingsCardDialog and pop the
back stack when restrictions engage (`VehicleUxPolicy` drives the state).

### 3.4 Registered destinations
74 in the shared registry; 68 composed in this app (Launcher and System UI
panel destinations belong to separate applications and are never offered by
the rail or search).

---

## 4. Component Inventory

### 4.1 Shell components (SettingsAppShell.kt)
| Component | Description |
|-----------|-------------|
| SettingsAppShell | Two-pane shell, rail + detail |
| SettingsCategoryItem | Rail navigation item, 92dp min-height |
| SettingsDetailPane | Detail pane, propagates onBackground contentColor |
| SettingsCardSurface | Base card with focus border (2dp primary) |
| registerSettingsDetailFocusArea() | Side-effect hook for vehicle screens |

**Focus contract for SettingsCategoryItem:**
- Focus (rotary): 3dp primary border; NO background change
- Pressed (touch): settingsSelectedColor() background
- Selected (active category): settingsSelectedColor() bg + primary icon tint

**Focus contract for SettingsCardSurface:**
- Focus: 2dp primary border; surface color unchanged
- Selected (data state): settingsSelectedColor() background
- Disabled: surface at 55% alpha

### 4.2 Core settings rows (SettingsComponents.kt)
| Component | Key behaviour |
|-----------|---------------|
| SettingsActionRow | Navigation row; navigates=true shows chevron |
| SettingsSwitchRow | Full-row toggleable; row tap toggles |
| CapabilityAwareSwitch | SettingsSwitchRow + capability gating |
| SettingsSection | Section header with primary-colour title |
| KeyValueRow | Two-column; value is bodyLarge + onSurface |
| SettingsHeaderBar | 64dp header with back + action slots |
| SettingsAppBarAction | App-bar icon button, 76dp touch target |
| SettingsFormTextField | Text field with isError/errorMessage support |
| SettingsFormSlider | Slider with DirectManipulation rotary |
| SettingsChevronHint | 32dp ChevronRight trailing affordance |
| SettingsCardDialog | Dialog with primary/secondary actions |

### 4.3 Vehicle-specific components
| Component | Description |
|-----------|-------------|
| VehicleSettingsScaffold | Scaffold for vehicle destinations |
| VehicleCategoryScreen | Category screen: overview + preview + controls |
| VehicleControlsPane | Scrollable list of control rows |
| VehicleRotaryControlRow | Row with FocusItem; DM=tertiaryContainer; focus=border |
| VehicleFeatureControlRow | Inner row: switch/slider/enum/status |
| VehicleTopView | Top-down car SVG with tappable zone chips |
| VehicleSwitchRow | Switch row with full-row tap (canonical pattern) |
| VehicleSliderRow | Slider row with DM rotary |
| VehicleEnumRow | Enum selector row |
| VehicleZoneChip | Zone chip (bodyMedium label, Outlined style) |
| RotaryUnavailableState | Greyed-out state for unavailable controls |

### 4.4 Editor contract (VehicleEditorUiKind)
| Editor | Control type | Input |
|--------|-------------|-------|
| SWITCH | Boolean toggle | Tap / CCP Center |
| SLIDER | Float range | DM rotary / drag |
| ENUM | Int from list | Tap to cycle |
| STATUS | Read-only display | None |

### 4.5 ThemeMode
| Mode | Description |
|------|-------------|
| AUTO | Follows system night mode |
| DAY | Force light theme |
| NIGHT | Force dark theme |

---

## 5. Focus / Rotary (CCP) Contract

### 5.1 Window host
`MainActivity` installs `setSafeRotaryContent(hostId = "main-window", controller)`
so every destination shares one CCP focus tree. A bottom-of-stack back callback
exits Direct Manipulation and consumes duplicate popup backs
(`VehicleDialogBackGuard`).

### 5.2 FocusArea hierarchy
`
FocusArea("settings-rail-{routeKey}")       <- rail
FocusArea("settings-header-{routeKey}")     <- app bar
FocusArea("settings-detail-{routeKey}")     <- content
`

Navigation transitions are disabled (`EnterTransition.None`): registering two
destinations during a cross-fade creates competing CCP focus trees.

### 5.3 FocusItem rules
- Every interactive element has a FocusItem with stable FocusItemId
- FocusItemId format: settings-item-{stableHash(destinationKey/focusId)}
- Non-interactive elements must NOT have a FocusItem stop
- Touch mode: FocusItem renders ComposeContent only (no ring)
- Rotary mode: library draws 2dp primary border via rotaryFocusBorder(focused)

### 5.4 Vehicle focus
- Vehicle screens register first FocusArea via registerSettingsDetailFocusArea()
- VehicleRotaryControlRow: touchBehavior=ComposeContent for sliders, View for switches

---

## 6. Automotive Constraints (Must Not Change)

1. **FocusArea geometry must not overlap** - CCP validator rejects overlapping areas
2. **Rail width = 340dp** - OEM chrome assumes fixed rail width
3. **Rail item min-height = 92dp** - required by CCP focus-bounds validator
4. **Header height = 64dp** - shell FocusArea sized at 80dp (64dp + 16dp inset)
5. **FocusItemId must be stable** - IDs use stableRotaryKey() deterministic hash
6. **Vehicle write pipeline** - all writes through VehicleFeatureController.setValue()
7. **AutomotiveTextField** - always use SettingsFormTextField (not raw OutlinedTextField)
8. **No static Color.Black/White** - always use BTheme/Material 3 color roles
9. **BTheme boundary** - B-Material components require the MySystemTheme wrapper

---

## 7. Stitch Integration Checklist

Before exporting a Stitch frame:

- [ ] All cards use SettingsCardSurface with correct focused/selected props
- [ ] All text uses named typography styles (no hardcoded sp values)
- [ ] All colors use theme role tokens (no hardcoded hex)
- [ ] Rail items maintain 340dp x 92dp min constraints
- [ ] Header is exactly 64dp content height
- [ ] Focus ring shown as 2dp primary border (not fill)
- [ ] Selected category = settingsSelectedColor() fill + primary icon
- [ ] Vehicle rows: DM active = tertiaryContainer; focus = border only
- [ ] Dark mode: verify with adb shell cmd uimode night yes
