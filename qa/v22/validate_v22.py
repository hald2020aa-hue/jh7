#!/usr/bin/env python3
"""V22 static safeguards: ten-screen layouts, adapters, assets, and protected original code.

This is not an Android APK build or a device screenshot comparison.
"""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEV = ROOT / 'app/src/dev/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
checks = 0


def check(ok, message):
    global checks
    if not ok:
        raise AssertionError(message)
    checks += 1
    print('PASS:', message)


def read(file):
    return (ROOT / file).read_text(encoding='utf-8')


# Pixel concept references are only design guidance: no mock manga entities belong here.
manifest = json.loads((Path(__file__).parent / 'protected_v21_manifest.json').read_text())
check(len(manifest) >= 1000, 'at least 1,000 original engine/data/resource files are locked')
for path, expected in manifest.items():
    file = ROOT / path
    check(file.is_file() and hashlib.sha256(file.read_bytes()).hexdigest() == expected,
          f'unchanged V21 functional source: {path}')

assets = [
    'v22_screen_background', 'v22_hero_banner', 'v22_surface_card',
    'v22_surface_selected', 'v22_neon_chip', 'v22_brand_header',
    'v22_bottom_navigation', 'v22_reader_panel', 'v22_reader_controls',
    'v22_poster_frame', 'v22_poster_gradient', 'v22_input_field',
    'v22_progress_track', 'v22_status_dot',
]
for name in assets:
    ET.parse(DEV / 'drawable' / (name + '.xml'))
    check(True, f'V22 visual resource parses: {name}')

palette = ET.parse(DEV / 'values/v22_colors.xml').getroot()
color_names = {el.get('name') for el in palette.findall('color')}
check({'v22_void', 'v22_cyan', 'v22_magenta', 'v22_raised', 'v22_muted', 'v22_text'} <= color_names,
      'V22 typography and neon color tokens exist')

screen_areas = {
    'primary_shell': ['main_activity.xml'],
    'library': ['library_controller.xml','manga_grid_item.xml','manga_list_item.xml','library_display_layout.xml'],
    'updates': ['recents_controller.xml','recent_manga_item.xml','recents_updates_view.xml'],
    'source_browse': ['browse_controller.xml','source_item.xml','browse_source_controller.xml','source_header_item.xml'],
    'manga_details': ['manga_details_controller.xml','manga_header_item.xml'],
    'chapters': ['chapters_item.xml','chapter_header_item.xml'],
    'reader': ['reader_activity.xml','reader_nav.xml','reader_chapters_sheet.xml'],
    'search': ['source_global_search_controller.xml','source_global_search_controller_card_item.xml','source_filter_sheet.xml'],
    'settings': ['settings_search_controller.xml','settings_search_controller_card.xml'],
    'downloads': ['download_item.xml','download_bottom_sheet.xml'],
}
for area, layouts in screen_areas.items():
    for name in layouts:
        file = DEV / 'layout' / name
        root = ET.parse(file).getroot()
        xml = file.read_text()
        check('v22_' in xml, f'{area} uses the V22 design system: {name}')
        ids = [el.get(ANDROID + 'id') for el in root.iter() if el.get(ANDROID + 'id')]
        # Includes can reuse IDs only across separate files, never within a layout.
        check(len(ids) == len(set(ids)), f'{name} retains unique view IDs')

original_bindings = {
    'source_item.xml':['source_card','source_image','title','source_latest','source_pin','source_divider'],
    'source_global_search_controller_card_item.xml':['itemImage','title','favorite_button','duplicate_in_library_button','progress','gradient'],
    'library_controller.xml':['library_layout','library_top_actions','library_stat_total','library_stat_completed','library_stat_reading','library_stat_downloaded','library_grid_recycler'],
    'recents_controller.xml':['recents_layout','recents_summary_chapters','recents_summary_titles','recents_summary_downloaded','recycler'],
    'manga_header_item.xml':['manga_cover','backdrop','favorite_button','start_reading_button','chapters_title','manga_summary'],
    'chapters_item.xml':['chapter_title','chapter_scanlator','download_button','chapter_card'],
    'reader_activity.xml':['viewer_container','page_number','toolbar','touch_view','reader_nav','chapters_sheet'],
    'download_item.xml':['front_view','download_progress','download_progress_text','chapter_title','title'],
}
for filename, ids in original_bindings.items():
    xml = ET.parse(DEV/'layout'/filename).getroot()
    bound = {el.get(ANDROID+'id') for el in xml.iter() if el.get(ANDROID+'id')}
    for ident in ids:
        check('@+id/'+ident in bound, f'{filename} original binding remains: {ident}')

home = read('app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeController.kt')
for token in [
    'db.getFavoriteMangas().executeOnIO()', 'db.getRecentMangaLimit',
    'sourceManager.getCatalogueSources()', 'MangaDetailsController(manga)',
    'loadManga(manga)', 'visibleLibrary', 'selectedGenre', 'HomeGenreChip(',
    'Brush.verticalGradient', 'HomeFeaturedCard(', 'HomeMangaPoster(',
]:
    check(token in home, f'home uses real data or V22 composition: {token}')
for forbidden in ['Solo Leveling','Sample manga','Fake title','mockManga']:
    check(forbidden not in home, f'home does not insert concept manga: {forbidden}')

downloads = read('app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt')
for token in [
    'visibleRows','onToggleAll','onDeleteAll','onPause','onResume','onDelete','onPrioritize',
    'Download.State.DOWNLOADING','Download.State.QUEUE','Download.State.DOWNLOADED',
    'transferStats.collectAsState()', 'loadManga(row.download.manga)',
    'NeonProgressTrack(row.percentage / 100f', 'items(chapters, key',
]:
    check(token in downloads, f'download keeps live queue action/telemetry: {token}')

settings = read('app/src/main/java/eu/kanade/tachiyomi/ui/setting/NeonSettingsStyler.kt')
check('PreferenceGroupAdapter' in settings and 'RippleDrawable' in settings,
      'settings preserves AndroidX preference adapter and click behavior')
check('MaterialSwitch' in settings and 'Slider' in settings,
      'settings keeps original switch/slider state handling')

reader = ET.parse(DEV/'layout'/'reader_activity.xml').getroot()
viewer = next(e for e in reader.iter() if e.get(ANDROID+'id') == '@+id/viewer_container')
check(viewer.get(ANDROID+'background') is None, 'reader page viewing area remains unaltered')

# Ensure all V22 references resolve to an existing drawable or color in dev+main.
for path in (DEV/'layout').glob('*.xml'):
    xml = path.read_text()
    for kind, name in re.findall(r'@(drawable|color)/(v22_[A-Za-z0-9_]+)', xml):
        paths = [DEV/kind/(name+'.xml')] if kind == 'drawable' else []
        if kind == 'color':
            check(name in color_names, f'{path.name} references defined {name}')
        else:
            check(any(f.is_file() for f in paths), f'{path.name} references defined {name}')

gradle = read('app/build.gradle.kts')
check('applicationIdSuffix = ".enhanced"' in gradle and 'applicationIdSuffix = ".debugJ2K"' in gradle,
      'existing development package identity unchanged')
check('versionNameSuffix = "-enhanced-v22-dev"' in gradle and
      'versionCode = AndroidVersions.versionCode + 4' in gradle,
      'V22 distinguishable from V21 without changing package identity')
workflow = read('.github/workflows/quality_gate.yml')
check('python3 qa/v22/validate_v22.py' in workflow, 'CI executes the protected V22 checks')
check('JH_UPDATE_KEYSTORE_BASE64' in workflow and 'Using temporary CI debug signing' in workflow,
      'CI does not silently assert temporary signing is update compatible')
print(f'V22 PROTECTED SOURCE + TEN SCREEN CHECKS: {checks}/{checks} PASS — static/source checks only.')
