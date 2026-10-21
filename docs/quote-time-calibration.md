# A1 / P2S — offline timing comparison

[Open the interactive matrix](calibration/a1-p2s-matrix.html).

## Boundary: the website calculator is not part of this experiment

The calculator uses native slicer duration. No A1/P2S multiplier, experimental
curve or preparation adjustment is called by `QuoteSessionItemService`.
The previously added normalization injection, pricing metadata and configuration
flag have been removed. `GCodeParser`, `PrintStats` and `OrcaProfileResolver` have
been restored to their prior implementations. Conversion and unit handling are
preserved. Saved quotes are not rewritten.

All experimental classes live in `com.printcalculator.tools`. They have no Spring
service annotations. `CalibrationSlicer` passes its own `CalibrationGCodeParser`
to a separately instantiated slicer; `CalibrationPrintStats` contains the extra
model/layer metadata. The website parser and statistics do not acquire this
behavior. Integrating research into the website requires a separate explicit
user request.

## Revised comparison

The new macOS ARM64 measurements use OrcaSlicer 2.3.2 and cover **79 resolved
profile pairs**, representing **159 material/nozzle/layer/quality choices**.
The materials are PLA, PLA TOUGH, PETG and TPU. PC has been excluded from the A1
reference; TPU with a 0.2 mm nozzle is also excluded. Successful slicing is not
proof of physical compatibility. The manufacturer lists PC among the materials
not recommended for A1 and marks TPU unsupported with the 0.2 mm hotend:
[A1 specifications](https://bambulab.com/en/a1/tech-specs),
[A1 hotend compatibility](https://us.store.bambulab.com/products/bambu-hotend-a1-series).
Sources checked 2026-10-20.

Six new fixtures replace the tiny rods as the basis of the experiment:

| Role | Geometry |
| --- | --- |
| Curve fitting | Cube 80 mm; block 120 × 80 × 20 mm; square tube 80 × 80 × 80 mm, wall 3 mm |
| Independent check | Cube 65 mm; cylinder Ø60 × 70 mm; plate 150 × 100 × 5 mm |

All fixtures are procedural closed meshes centered on the bed. Each is sliced
with 15% grid infill and supports enabled. Only training samples with at least
30 minutes of model printing time on both machines enter the curve. The three
check geometries never affect fitting. There are **474 paired comparisons /
948 successful native slices** in this revised set. No physical prints were timed.

The report defaults to the independent 65 mm cube and **model printing time**.
The selector can display G-code total estimates instead. Separate columns show
each machine's `total − model` estimate. CSV export includes the selected time
basis, both preparation estimates, and experimental curve errors.

The configured filament profiles are not always equivalent: for example PETG
uses Bambu PETG Basic on A1 and Generic PETG on P2S at the standard nozzle.
Consequently these ratios compare configured profiles, including flow, cooling
and process speeds; they do not isolate the hardware's speed advantage. Names
of both filament and process presets appear on the quality tooltip. Missing
mappings follow a snapshot of the existing runtime fallbacks in the offline
collector; changes to those fallbacks require a new inventory/review.

## Preparation estimates

The P2S machine presets contain `machine_prepare_compensation_time = 370` seconds.
OrcaSlicer 2.3.2 does not use that setting in its G-code processor; it contains a
hard-coded G29 timing estimate instead. Current Bambu Studio reads the setting.
See the versioned [Orca processor](https://github.com/OrcaSlicer/OrcaSlicer/blob/v2.3.2/src/libslic3r/GCode/GCodeProcessor.cpp)
and [Bambu Studio processor](https://github.com/bambulab/BambuStudio/blob/master/src/libslic3r/GCode/GCodeProcessor.cpp).
This is why the P2S G-code's roughly 14 seconds must not be presented as a
physically verified startup time or compared uncritically with A1's minutes.
The 370-second profile value is not a stopwatch measurement either.

No guessed correction has been added to the calculator or the generated G-code.
Real preparation duration still needs physical timing with controlled initial
temperatures, bed leveling, calibration and material loading settings.

## Experimental curves are not ready for general use

The monotone per-layer curve fits model time separately and adds an A1 preparation
estimate once. Its median absolute error on the three independent geometries is
**3.30%**, and its maximum error is **41.76%**. These errors reproduce A1 slicer
estimates; they do not measure physical accuracy. The worst error shows that this
curve cannot be treated as a universal A1/P2S conversion, even with larger parts.
The rule's experimental validation flag is false when maximum check error exceeds
10%. This flag has no connection to the website calculator.

These six new geometries have **not** been compared on Linux. Earlier Linux
checks used small fixtures and are retained as historical evidence, not as
validation of the revised comparison.

Evidence:

- [New measurements](../backend/src/test/resources/quote-time/a1-p2s-v3-measurements.json).
- [Experimental curves and errors](calibration/a1-p2s-v3-experiment.json).
- [Technical inventory snapshot](../backend/src/test/resources/quote-time/a1-p2s-v2-inventory.json).
- [Historical small-fixture measurements](../backend/src/test/resources/quote-time/a1-p2s-v2-measurements.json).
- [Historical Linux checks](../backend/src/test/resources/quote-time/a1-p2s-v2-linux-checks.json).

## Reproduction

Use the read-only technical export in
`backend/scripts/export-quote-calibration-inventory.sql`. From `backend/`:

```bash
bash scripts/calibrate-quote-times.sh --matrix /tmp/inventory.json /tmp/comparison --representative --plan
bash scripts/calibrate-quote-times.sh --matrix /tmp/inventory.json /tmp/comparison --representative
bash scripts/calibrate-quote-times.sh --fit /tmp/comparison/results.json /tmp/experiment.json
# An empty {"measurements":[]} Linux file explicitly means no Linux verification.
bash scripts/calibrate-quote-times.sh --report /tmp/comparison/results.json \
  /tmp/experiment.json /tmp/linux-results.json /tmp/a1-p2s-matrix.html
```

The collector checkpoints each native slice and rejects changed inventory,
profiles or runtime when resuming. The report's authoring sources are under
`backend/src/main/resources/quote-time/report/`; the generator embeds them in one
standalone HTML file. Publishing this report or changing experimental curves
must not affect quote duration, prices or printer selection.

The historical v1/v2 rule files remain evidence for offline replay only. Their
small-fixture errors and PC comparisons must not be used as production evidence.
