# V24 regression checks for the reader toolbar crash and browse spacing.
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEV = ROOT / "app/src/dev/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
checks = 0

def check(ok, message):
    global checks
    checks += 1
    if not ok:
        raise AssertionError(message)
    print("PASS:", message)

header = ET.parse(DEV / "drawable/v23_brand_header.xml").getroot()
layers = {
    element.get(ANDROID + "id"): element
    for element in header.iter()
    if element.get(ANDROID + "id")
}
for layer_id in ("@+id/layer_full_width", "@+id/layer_one_item"):
    check(layer_id in layers, f"reader toolbar layer ID exists: {layer_id}")
    check(
        layers[layer_id].get(ANDROID + "drawable") == "@android:color/transparent",
        f"{layer_id} remains visually transparent",
    )

controller = (
    ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/source/BrowseController.kt"
).read_text(encoding="utf-8")
for token in (
    "private var baseSourceTopPadding = 0",
    "val appBarHeight = activityBinding?.appBar?.height ?: 0",
    "appBarHeight + 16.dpToPx",
    "activityBinding?.appBar?.doOnNextLayout { updateSourceTopPadding() }",
    "recycler.updatePaddingRelative(top = targetTop)",
):
    check(token in controller, f"adaptive browse spacing remains wired: {token}")

source_item = (DEV / "layout/source_item.xml").read_text(encoding="utf-8")
for edge in ("Start", "Top", "End", "Bottom"):
    check(
        f'android:layout_margin{edge}="7dp"' in source_item,
        f"source card {edge.lower()} spacing is 7dp",
    )

print(f"V24 READER CRASH + BROWSE SPACING CHECKS: {checks}/{checks} PASS")
