#!/usr/bin/env python3
"""V23 incremental source safeguards. This is NOT an Android screenshot test."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
import hashlib
import json
root=Path(__file__).resolve().parents[2]
dev=root/'app/src/dev/res'
main=root/'app/src/main/res'
errors=[]
checks=0

def check(ok,why):
    global checks
    checks+=1
    if not ok: errors.append(why)

for path in (dev/'drawable/v23_hero_banner.xml',dev/'drawable/v23_brand_header.xml'):
    check(path.exists(),f'visual drawable missing: {path.name}')
    if path.exists(): ET.parse(path)
art=main/'drawable-nodpi/v23_header_art.webp'
strip=dev/'drawable-nodpi/v23_brand_strip.webp'
for f in (art,strip):
    check(f.exists(),f'art asset missing: {f}')
    if f.exists():
        payload=f.read_bytes()
        check(payload[:4]==b'RIFF' and payload[8:12]==b'WEBP',f'asset is not a valid WebP container: {f.name}')
        check(len(payload) > 3000,f'art asset too small to provide a banner: {f.name}')
check(not (dev/'drawable-nodpi/v23_header_art.webp').exists(),'dev and main duplicate resource')
checks_on={
    'main_activity.xml':'@drawable/v23_brand_header',
    'reader_activity.xml':'@drawable/v23_brand_header',
    'library_controller.xml':'@drawable/v23_hero_banner',
    'recents_controller.xml':'@drawable/v23_hero_banner',
    'browse_controller.xml':'@drawable/v23_hero_banner',
    'source_global_search_controller.xml':'@drawable/v23_hero_banner',
}
for filename,required in checks_on.items():
    p=dev/'layout'/filename
    xml=p.read_text()
    ET.fromstring(xml)
    check(required in xml,f'{filename} has no cinematic header')
    ids=[e.get('{http://schemas.android.com/apk/res/android}id') for e in ET.fromstring(xml).iter() if e.get('{http://schemas.android.com/apk/res/android}id')]
    check(len(ids)==len(set(ids)),f'duplicate ids {filename}')
for filename in ['browse_controller.xml','source_global_search_controller.xml']:
    xml=(dev/'layout'/filename).read_text()
    check('RecyclerView' in xml and 'android:paddingTop=' in xml,f'{filename} does not reserve header space')
    check('android:clickable="false"' in xml,f'{filename} art could swallow clicks')
for filename in ['values/strings.xml','values-ar/strings.xml']:
    t=ET.parse(dev/filename).getroot()
    names=[e.attrib['name'] for e in t if e.tag=='string']
    check(len(names)==len(set(names)),f'duplicate strings in {filename}')
    for token in ['v23_browse_title','v23_browse_caption','v23_search_title','v23_search_caption']:
        check(token in names,f'{filename} lacks {token}')
home=(root/'app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeController.kt').read_text()
for token in ['featuredPool = remember(library, recent)', 'onPrevious =', 'onNext =', 'onSelect =', 'HomeFeaturedCard(', 'MangaCover(manga', 'getFavoriteMangas()']:
    check(token in home,f'home actual-data carousel missing {token}')
for forbidden in ['Solo Leveling','Fake manga','mockManga']:
    check(forbidden not in home,f'home contains fictional catalogue data: {forbidden}')
dl=(root/'app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt').read_text()
for token in ['painterResource(id = R.drawable.v23_header_art)','loadManga(row.download.manga)', 'transferStats.collectAsState()', 'onDelete', 'onPause','onResume','onPrioritize']:
    check(token in dl,f'download art or live data missing {token}')
manifest=json.loads((root/'qa/v22/protected_v21_manifest.json').read_text())
for path,expected in manifest.items():
    f=root/path
    check(f.is_file() and hashlib.sha256(f.read_bytes()).hexdigest()==expected,f'protected file changed: {path}')
check(len(manifest)>=1000,'protected manifest too small')
settings=(root/'app/src/main/java/eu/kanade/tachiyomi/ui/setting/NeonSettingsStyler.kt').read_text()
for token in ['settingsHero', 'findViewHolderForAdapterPosition(0)', 'drawHeroText(', 'PreferenceGroupAdapter', 'list.addItemDecoration(settingsHero)']:
    check(token in settings,f'settings must retain real preferences with decorated header: {token}')
for p in (main/'values/v23_settings_strings.xml',main/'values-ar/v23_settings_strings.xml'):
    check(p.exists(),f'missing settings header strings: {p.name}')
    if p.exists(): ET.parse(p)
for p in dev.rglob('*.xml'):ET.parse(p)
if errors:
    print(f'FAIL: {len(errors)} of {checks} checks')
    for problem in errors:print(' -',problem)
    raise SystemExit(1)
print(f'V23 STATIC VISUAL INTEGRATION CHECKS: {checks}/{checks} PASS (does NOT establish screenshot similarity, runtime, or APK build)')
