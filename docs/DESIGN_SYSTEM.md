# Design System — Fashion Studio AI PRO

## 1. Visual Direction
Minimalist, editorial studio aesthetic built on **Material 3** with Progressive Disclosure (primary controls immediately accessible; advanced prompt editor, masks, and verification heatmaps one tap away).

## 2. Color Tokens (`StudioColors`)
| Token | Dark Theme (Default) | Light Theme | Usage |
| :--- | :--- | :--- | :--- |
| Background | `#101114` | `#F7F7F5` | Root canvas & screen backdrop |
| Surface | `#191B20` | `#FFFFFF` | Cards, top/bottom bars, preview frame |
| Secondary Surface | `#24262C` | `#ECECE7` | Chips, quick action cards, audit panels |
| Primary Text | `#F5F5F5` | `#141519` | Headlines, titles, primary labels |
| Secondary Text | `#9CA3AF` | `#525660` | Metadata, hints, SKU/model captions |
| Accent | `#C9A66B` | `#9E7B3E` | Primary CTAs, Product DNA Protected badge, split divider |
| Success | `#22C55E` | `#22C55E` | `DNA Verified`, passing heatmap regions |
| Warning | `#F59E0B` | `#F59E0B` | `Review Required`, missing-angle warnings |
| Error | `#EF4444` | `#EF4444` | `Fidelity Failed`, blocked export, tampered heatmap regions |

## 3. Product DNA Lock Status Indicators
- `DNA Protected` (Gold Lock): Original SHA-256 locked & masks initialized.
- `DNA Verified` (Green Shield): Deterministic metrics passed Mode A thresholds.
- `Review Required` (Amber Eye): Borderline metrics; inspect at 200–400% zoom.
- `Fidelity Failed` (Red Shield): Tampering or missing required angle detected; commercial export blocked.
- `Virtual Reconstruction` (Blue Sparkle): Mode B Virtual Try-On contains reconstructed fabric pixels.
