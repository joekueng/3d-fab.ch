# Workshop filament recipes

`Polymaker PC-ABS @BBL P2S` is an initial recipe, not a factory-calibrated profile.
It inherits the installed P2S Generic PC machine-specific filament settings and
replaces material-specific values from Polymaker's published PC-ABS guidance:
260 °C nozzle (published range 250–270), 100 °C bed (90–105), cooling off,
1 mm direct-drive retraction at 40 mm/s, and density 1.1 g/cm³.
The P2S has no active chamber heater, so this recipe does not request one.

The 3.2 mm³/s flow limit is an explicit initial workshop estimate derived from
40 mm/s × 0.4 mm line width × 0.2 mm layer height. It is not a measured melt-flow
limit or a manufacturer profile. Refine it using actual workshop prints before
claiming calibrated duration/weight. Flow ratio 1 is likewise an initial value.
This file is used for quote statistics, never as a downloadable printer job.

Sources verified 2026-11-13:
[Polymaker product guidance](https://polymaker.com/product/polymaker-pc-abs),
[Polymaker TDS V5.1](https://polymaker.com.cn/wp-content/uploads/lana-downloads/Polymaker_PC_ABS_TDS_V5.1.pdf).
The product page's direct-drive 40 mm/s retraction is used; the older TDS lists
20 mm/s. Neither source supplies a Bambu-specific calibrated recipe.

`StockMaterialProfilesTest` verifies the merged settings. Its optional native
case uses a synthetic 10 mm cube with `STOCK_MATERIAL_NATIVE=true` and
`SLICER_PATH` pointing to the binary under test. Local OrcaSlicer 2.5.0 and pinned
2.4.2 checks returned positive print time and weight for both PC-ABS and TPU 85A.
The 2.4.2 cube results were 521 s / 0.58 g for PC-ABS (0.4 mm nozzle, 0.20 mm layer)
and 819 s / 0.77 g for TPU 85A (0.6 mm nozzle, 0.24 mm layer). These integration
checks do not replace physical calibration of the initial PC-ABS flow assumption.
