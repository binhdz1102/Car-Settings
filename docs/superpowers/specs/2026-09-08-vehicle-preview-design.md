# Vehicle Preview and Guide Animation Design

## Product contract

The second column is a read-only context and confirmed-state preview. It never replaces the
control row, never presents live sensor detections that the VHAL does not expose, and never offers
the illustrated-guide action. Detailed explanatory artwork belongs to the control Info dialog.

The Info dialog opens on a static poster. A guide runs once only after the user presses Play. Stop,
Replay, Back, connection loss, lifecycle disposal, or a newly restrictive UX policy cancels the
playback. Restricted, distraction-optimization-required, and unavailable UX policy states expose a
short reason and do not expose the full guide.

## Data contract

The editor value remains optimistic so a switch or slider responds immediately. The preview uses
the last value acknowledged by CarService/VHAL (`confirmedValue`) and its timestamp. A newer
confirmed event may update the preview while an older optimistic request remains pending. Missing,
unreadable, unavailable, malformed, or errored values render an unknown state instead of a guessed
off, closed, centered, or mid-range state.

## Visual contract

Runtime artwork is built from Android vector layers. Each scene declares a stable scene ID, layer
IDs, normalized anchors, and optional pivots. Feature registries map definitions to scenes; the
core renderer applies transforms but does not infer vehicle meaning from an image or title.

The design brief for each scene is English so a tool such as Stitch can produce the same layer
names and storyboard. The brief is illustrative: it must not claim measured sensor coverage,
automatic braking, hardware illumination, or a physical result that is not represented by a VHAL
property.

## UX and rotary contract

The preview is decorative and has no FocusArea or guide button. Zone selection and control rows
remain actionable. Info actions remain separate row actions only while the visual policy allows the
guide. Closing Info restores the opener when it remains actionable; otherwise focus moves to the
nearest valid editor or the screen's fallback action.

## Validation contract

Definition coverage, scene/resource references, layer IDs, anchors, policy transitions, optimistic
versus confirmed values, guide playback, touch, rotary, Back, fallback, localization, and wide/narrow
layouts all require automated tests. Screenshot review supplements semantic and geometry tests; it
is not the sole correctness gate.
