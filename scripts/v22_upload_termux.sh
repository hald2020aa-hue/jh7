#!/data/data/com.termux/files/usr/bin/bash
# Safely stages the reviewed V22 source over the V21 GitHub branch in a NEW branch.
# Requires git, python3 and prior git authentication on the phone. Never force-pushes.
set -euo pipefail
HERE="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SRC="$(dirname -- "$HERE")"
REPO="https://github.com/hald2020aa-hue/jh7.git"
BASE="feature/jh-v21-ten-screen-redesign"
NEW="feature/jh-v22-visual-match"
DEST="${HOME}/jh7-v22-visual-match"
if ! command -v git >/dev/null || ! command -v python3 >/dev/null; then
  printf '%s\n' 'Install git and python first: pkg install git python -y' >&2
  exit 1
fi
if [[ -e "$DEST" ]]; then
  printf '%s\n' "A directory already exists: $DEST. Refusing to overwrite it." >&2
  exit 1
fi
python3 "$SRC/qa/v22/validate_v22.py" >/dev/null
git clone --branch "$BASE" --single-branch "$REPO" "$DEST"
cd "$DEST"
git switch -c "$NEW"
python3 - "$SRC" "$DEST" <<'PY'
import pathlib, shutil, sys
src=pathlib.Path(sys.argv[1]);dest=pathlib.Path(sys.argv[2])
for path in src.rglob('*'):
    if not path.is_file() or any(part in ('.git','__pycache__') for part in path.relative_to(src).parts): continue
    relative=path.relative_to(src)
    target=dest/relative
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(path,target)
PY
chmod +x gradlew
git add -A
git diff --cached --check
if git diff --cached --quiet; then
  echo 'No differences to upload.'
  exit 0
fi
git commit -m 'V22: redesign ten real manga screens with protected source behavior'
git push -u origin "$NEW"
printf '%s\n' 'V22 branch pushed; check https://github.com/hald2020aa-hue/jh7/actions'
