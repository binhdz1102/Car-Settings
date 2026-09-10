# Vehicle visual scene contract

Each scene has a stable ID, a 1600x900 viewBox, normalized anchors, and named layers. The current
registry uses the existing artwork as a safe base layer. New vector assets must preserve the scene
ID and split every moving/highlighted part into a separate Android Vector Drawable resource before
the layer registry is changed.

Stitch briefs are English so layer names remain stable across design and implementation. Text,
sensor readings, warning claims, and vehicle-control state never live inside the artwork.
