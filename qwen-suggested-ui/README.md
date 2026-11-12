# Qwen Suggested UI: "The Architect" Theme

This folder contains an alternative design system for Trinity. It strictly adheres to the original **Paper & Ink** restrictions (no gradients, no hue, strictly achromatic, 8-color palette), but proposes a completely different aesthetic language.

## The Philosophy
The original theme is "utilitarian UI kit" — small rounded pills, tight spacing, and soft hairlines.
**"The Architect"** treats Trinity like an editorial broadsheet or an architectural blueprint.

### Key Changes
1. **Typography Hierarchy**:
   - **Display / Headlines**: `FontFamily.Serif` (e.g., Georgia). Gives a timeless, authoritative weight.
   - **Body**: `FontFamily.SansSerif`. Clean and legible.
   - **UI Chrome / Metadata**: `FontFamily.Monospace` with wide letter-spacing. Technical and precise.
2. **Shapes**: Strictly **0.dp rounded corners** everywhere. Sharp, intentional rectangles. No pills, no soft curves.
3. **Dividers**: Replaces ubiquitous 1px hairlines with architectural rules.
   - **Heavy Rule (3.dp Ink)**: Separates major sections (e.g., under the header).
   - **Indented Rule (1.dp LineSoft)**: Separates list items, starting only after the avatar column.
4. **Buttons**: Large, sharp rectangular blocks instead of small outlined pills.
5. **Avatars**: Sharp architectural squares instead of circles.

## How to use
To test this theme, replace the imports in your screens:
```kotlin
// Change from:
import com.sarim.trinity.ui.theme.Palette
import com.sarim.trinity.ui.components.*
import com.sarim.trinity.ui.screens.*

// To:
import com.sarim.trinity.ui.qwen_theme.QwenPalette
import com.sarim.trinity.ui.qwen_components.*
import com.sarim.trinity.ui.qwen_screens.*
```

And update `MainActivity.kt` to use `QwenTheme` instead of `TrinityTheme`.

## Files
- `Theme.kt` → `QwenPalette` and `QwenTheme`
- `Components.kt` → `Rule`, `ArchitectAvatar`, `EditorialHeader`, `ArchitectButton`, `ChromeLabel`
- `QwenMessagesScreen.kt` → Reimagined Messages screen
- `QwenDialpadScreen.kt` → Reimagined Dialpad screen
