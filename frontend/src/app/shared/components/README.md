# Shared frontend components

Read [frontend/AGENTS.md](../../../../AGENTS.md) and [frontend/README.md](../../../../README.md) before implementing UI. Inspect the existing component API and reuse it; feature code owns business rules and adapts domain data to these controls.

| Need                               | Component and contract                                                                                                                                                                                                                                                                                                                                                                                                                         |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Button awaiting a result           | `app-button [loading]="true"`: disables clicks, exposes `aria-busy`, and shows a spinner that respects reduced motion. Supply a localized action/status label and any live status explanation in the feature.                                                                                                                                                                                                                                  |
| Status and error banners           | `app-alert`: select `info`, `warning`, `error`, or `success`. It owns the semantic status role and the shared `ui-banner` visual language; keep banners typographic and do not add decorative emoji or feature-local alert shells.                                                                                                                                                                                                             |
| Form fields                        | `app-input`, `app-select`, `app-textarea`, `app-checkbox`; use Angular form bindings, semantic tokens, and existing validation. `app-checkbox` supports projected label content, including links.                                                                                                                                                                                                                                              |
| Color selection                    | `app-color-selector`: calculator/checkout pass `variants` and consume `colorSelected`; shop maps its variants into `groups` and consumes `variantSelected`. Preserve opaque product IDs and numeric filament IDs. `showLabel` and `subtitle` enable a descriptive trigger without a separate popup implementation.                                                                                                                             |
| Modal                              | `app-dialog`: create conditionally with `@if`/`*ngIf`, provide `title` and `closeLabel`, and handle `dismissed` by clearing the owning feature's open state. Project the body; use `subtitle`, `size`, and `fullscreenOnMobile` when needed. The native modal owns focus containment, background isolation, Escape, backdrop dismissal, and closing. Opening runs only in the browser. Keep destructive actions explicit in projected content. |
| Image enlargement                  | `app-image-preview [src]="url" [alt]="filename"`: a keyboard accessible thumbnail opens `app-dialog`, fullscreen on mobile, with fit/original-size viewing. The owner supplies and releases the preview URL. Set `--image-preview-height` on the host to adjust the thumbnail height. Reuse this for invoice and instruction attachments.                                                                                                      |
| Asynchronous model preview         | `app-model-preview-dialog [filename]="name" [source]="blobObservable"`: create conditionally for an STL response. It manages loading/error signals and cancels its subscription when dismissed. Feature services own file access and any conversion. Admin requests reuse calculator preview conversion; sessions serve their stored STL through the authenticated admin endpoint.                                                             |
| Legal consent                      | `app-legal-consent [control]="..."`: pass the existing `FormControl` with `Validators.requiredTrue`. It owns localized terms/privacy links and the touched/invalid error; the parent retains submission and validation policy.                                                                                                                                                                                                                 |
| File selection/drop                | `app-dropzone`: set `accept`, `multiple`, `disabled`, `label`, and `subtext`; handle `filesDropped` in the feature. Use `showFileNames=false` when the feature already owns the preview list. File validation, attachment persistence, preview URLs, and removal belong to the feature. The native `accept` attribute is only a picker hint.                                                                                                   |
| Quantity and color for print items | `app-print-item-controls`, already shared by calculator and checkout.                                                                                                                                                                                                                                                                                                                                                                          |

Checkout uses `app-print-item-controls` with `showColorLabel=true` and
`quantityLabelKey="CALC.QUANTITY"` and `stacked=true` in both summary views. Quantity
and color each occupy a full row. The normal summary additionally uses
`compact=true`: a short 32 px quantity field and a borderless color trigger sized
to its content. The expanded summary retains its larger full-width fields. Keep material and print
metrics outside that panel. `app-color-selector field=true` renders a bordered
trigger and uses the CDK inline popover so options remain inside native dialogs.
The existing @angular/cdk 22.2.3 input `cdkConnectedOverlayUsePopover="inline"` was
verified on 2026-10-22 against installed types/source and the
[official overlay API](https://material.angular.dev/cdk/overlay/api) and
[directive source](https://github.com/angular/components/blob/main/src/cdk/overlay/overlay-directives.ts).
No dependency versions changed. `node scripts/check-external-packages.mjs` passed
all 45 metadata checks; focused component and responsive browser checks passed.

`app-select size="small"` provides the 34 px inline control used alongside print
quantity/color controls. Use `[compact]="true"` to remove the usual field bottom
margin. Keep the localized label and Angular form binding; feature SCSS owns
positioning (for example a per-file unit selector aligned to the card's right).

Calculator-only settings live in [`features/calculator/components/print-settings`](../../features/calculator/components/print-settings/), not in generic shared UI. Both global and selected-file settings pass the existing `FormGroup` and current option lists into that panel. Preserve the per-file nozzle/layer constraints in the owning form.

Extend a component for reusable behavior rather than copying its markup/styles into a feature. Keep HTML, SCSS, and TypeScript separate. Run `npm run check:ui-reuse`, `npm run check:i18n`, compilation, and relevant browser tests from `frontend/`; compare responsive rendering and long translated content. The reuse check blocks known clones, but is not a general duplication detector.

ControlValueAccessor implementations must notify Angular from `writeValue()` when
storing values in plain fields. Reactive-form patches (including `emitEvent: false`)
must update the rendered control under OnPush without emitting a user change.
The internal ngModel in `app-select` is standalone: the outer formControlName owns
registration with the reactive form. Verify both model values and rendered controls
when switching between records that reuse the same FormGroup.

For icon-only actions, pass a localized `ariaLabel` to `app-button` so the native
button has an accessible name. Keep the icon hidden from assistive technology.

`app-stl-viewer` accepts `aspectRatio` for responsive thumbnails (for example `1`
for a square), `initialZoom` to bring the initial camera closer (default `1`), and
`showDimensions=false` for small thumbnails. Enlarged viewers keep dimensions and
interactive zoom. These inputs do not change model geometry or stored previews.

When a thumbnail already supplies a border and clips its content, set
`--stl-viewer-border: 0` and `borderRadius="0"` on its embedded viewer to avoid
nested rounded borders. Standalone viewers retain their shared default border.

For a viewer filling a grid cell with a height defined by adjacent content, omit
`aspectRatio`, give its host a height of `100%`, and set `--stl-viewer-height: 100%`
on the containing preview. The existing ResizeObserver adapts its canvas/camera.
Checkout uses this in the normal summary so the preview matches the controls;
the expanded summary retains square thumbnails.

`app-dialog` explicitly restores focus to the connected element that opened it
when destroyed, after closing the native dialog. Preserve this behavior for
keyboard users; the sample catalogue browser tests exercise Enter, Tab and Escape.
Image thumbnails use native lazy loading; `app-image-preview.loadFailed` lets the
owning feature replace unavailable sources with its localized fallback.
