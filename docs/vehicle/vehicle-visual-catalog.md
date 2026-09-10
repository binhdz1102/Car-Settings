# Vehicle visual catalog

Each entry is a stable definition-to-scene contract. `context-only` means the preview may show
where the setting applies but must not animate a physical outcome. `null` guide IDs use the static
Info poster only.

| Feature | Definitions | Preview scene | Guide policy |
|---|---|---|---|
| HVAC | POWER, TEMPERATURE_SET, TEMPERATURE_CURRENT, FAN_SPEED, FAN_DIRECTION, AC, MAX_AC, AUTO, RECIRCULATION, AUTO_RECIRCULATION, DUAL, FRONT_DEFROSTER, REAR_DEFROSTER, MAX_DEFROST, MIRROR_HEAT, SEAT_TEMPERATURE, SEAT_VENTILATION, STEERING_WHEEL_HEAT, TEMPERATURE_DISPLAY_UNITS | `climate_cabin_top_view` | State/context; guides only for airflow, recirculation, and defrost where a scene exists |
| Driver Assistance | All `DriverAssistanceId` values | `adas_context_top_view` or a named function scene | Blind spot, cross traffic, collision, lane, and parking scenes are distinct; profile/volume/status are context-only |
| Doors | DOOR_LOCK, CHILD_LOCK, DOOR_POSITION, DOOR_MOVEMENT | `vehicle_doors_top_view` | Position guides only when position is observed; movement commands are instructional only |
| Windows | WINDOW_LOCK, WINDOW_POSITION, WINDOW_MOVEMENT | `vehicle_windows_top_view` | Position guides only when position is observed |
| Mirrors | MIRROR_LOCK, MIRROR_FOLD, MIRROR_AUTO_FOLD, MIRROR_AUTO_TILT, MIRROR_VERTICAL, MIRROR_HORIZONTAL | `vehicle_mirrors_top_view` | Fold/axis guides use distinct pivots; auto settings are context-only |
| Seats | All `SeatControlId` values | `vehicle_seat_side_view` | Position/support guides use only the matching observed property |
| Lighting | HEADLIGHTS, HIGH_BEAM, FOG_LIGHTS, HAZARD_LIGHTS, CABIN_LIGHTS, READING_LIGHTS, STEERING_WHEEL_LIGHTS, FOOTWELL_LIGHTS | `vehicle_lighting_top_view` | Preview is confirmed setting; hazard/beam motion is Info-only illustration |

The feature registries are the executable source of truth. Every registry entry must reference a
scene and meaning from this catalog, and coverage tests must fail when a new definition is missing.
