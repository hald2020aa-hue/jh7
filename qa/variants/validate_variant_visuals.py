from pathlib import Path
import json, re
ROOT=Path(__file__).resolve().parents[2]
m=json.loads((ROOT/'MANGA_VIRUS_VARIANT.json').read_text())
assert m['score'] is None
assert len(m['screens'])==10
assert len(m['protected_behaviors'])>=8
for rel, ids in {
'app/src/dev/res/layout/library_controller.xml':['@+id/library_layout','@+id/library_grid_recycler','@+id/library_top_actions'],
'app/src/dev/res/layout/browse_controller.xml':['@+id/browse_layout','@+id/source_recycler'],
'app/src/dev/res/layout/source_item.xml':['@+id/source_card','@+id/source_image','@+id/source_pin','@+id/source_latest'],
'app/src/dev/res/layout/manga_header_item.xml':['@+id/manga_cover','@+id/favorite_button','@+id/start_reading_button'],
'app/src/dev/res/layout/chapters_item.xml':['@+id/chapter_title','@+id/download_button'],
'app/src/dev/res/layout/download_item.xml':['@+id/download_progress','@+id/download_menu'],
'app/src/dev/res/layout/reader_activity.xml':['@+id/viewer_container','@+id/toolbar','@+id/chapters_sheet'],
}.items():
 s=(ROOT/rel).read_text()
 for i in ids: assert i in s,(rel,i)
assert '@+id/layer_full_width' in (ROOT/'app/src/dev/res/drawable/v23_brand_header.xml').read_text()
assert '@+id/layer_one_item' in (ROOT/'app/src/dev/res/drawable/v23_brand_header.xml').read_text()
for rel in ['app/src/dev/res/values/neon_v19_colors.xml','app/src/dev/res/values/v22_colors.xml']:
 s=(ROOT/rel).read_text(); assert '<resources>' in s and '</resources>' in s
print(f"{m['version']} {m['variant']}: variant visual/resource checks PASS")
