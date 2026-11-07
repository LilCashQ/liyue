#!/bin/bash
# 把当前源码提交推到 GitHub，并把指定 APK 传成 Release 资源。
# 用法: tools/sync-github.sh 0.28.0 [提交说明]
set -euo pipefail

VERSION="${1:?用法: tools/sync-github.sh <版本号> [提交说明]}"
MESSAGE="${2:-里悦 v$VERSION}"
REPO_DIR="/storage/emulated/0/Ai 工作区/里悦源码"
APK="/sdcard/Download/DSH-Go/里悦-$VERSION-arm64.apk"
TOKEN_FILE="/root/.config/liyue/github_token"
REPO="LilCashQ/liyue"

[ -f "$TOKEN_FILE" ] || { echo "缺少 $TOKEN_FILE，请重新走一次 GitHub 设备授权" >&2; exit 1; }
[ -f "$APK" ] || { echo "缺少安装包 $APK" >&2; exit 1; }

python3 - "$PWD" "$REPO_DIR" <<'PY'
import filecmp, os, shutil, sys
src, dst = sys.argv[1], sys.argv[2]
skip = {'.git', 'build', '.gradle', '.kotlin', '.idea', '.cxx', 'captures', '__pycache__', 'keystore'}
for root, dirs, files in os.walk(src):
    dirs[:] = [d for d in dirs if d not in skip]
    rel = os.path.relpath(root, src)
    for name in files:
        if name.endswith(('.jks', '.keystore')):
            continue
        s = os.path.join(root, name)
        d = os.path.join(dst, os.path.normpath(os.path.join(rel, name)))
        os.makedirs(os.path.dirname(d), exist_ok=True)
        if not os.path.exists(d) or not filecmp.cmp(s, d, shallow=False):
            shutil.copy2(s, d)
PY

cd "$REPO_DIR"
git add -A
git -c user.name=LilCashQ -c user.email=306065663+LilCashQ@users.noreply.github.com \
    commit -q -m "$MESSAGE" || echo "没有新的改动需要提交"

cat > /tmp/askpass.sh <<EOF
#!/bin/sh
case "\$1" in
  Username*) echo x-access-token ;;
  *) cat "$TOKEN_FILE" ;;
esac
EOF
chmod 700 /tmp/askpass.sh
GIT_ASKPASS=/tmp/askpass.sh GIT_TERMINAL_PROMPT=0 git push -q origin main
rm -f /tmp/askpass.sh

TOKEN="$(cat "$TOKEN_FILE")" VERSION="$VERSION" APK="$APK" REPO="$REPO" python3 - <<'PY'
import glob, json, os, urllib.error, urllib.request
token, version, apk, repo = (os.environ[k] for k in ("TOKEN", "VERSION", "APK", "REPO"))

def api(url, method="GET", payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "LiYue-Android",
        "Content-Type": "application/json",
    })
    return json.loads(urllib.request.urlopen(req, timeout=60).read().decode())

tag = f"v{version}"
try:
    release = api(f"https://api.github.com/repos/{repo}/releases/tags/{tag}")
except urllib.error.HTTPError:
    release = api(f"https://api.github.com/repos/{repo}/releases", "POST", {
        "tag_name": tag, "name": f"里悦 {tag}", "body": f"里悦 {tag}", "draft": False, "prerelease": False,
    })
upload = release["upload_url"].split("{")[0]
name = f"LiYue-{version}-arm64.apk"
with open(apk, "rb") as handle:
    data = handle.read()
req = urllib.request.Request(
    f"{upload}?name={name}", data=data, method="POST",
    headers={"Authorization": f"token {token}", "Content-Type": "application/vnd.android.package-archive",
             "User-Agent": "LiYue-Android", "Accept": "application/vnd.github+json"},
)
print("UPLOAD", name, urllib.request.urlopen(req, timeout=600).status)
print("RELEASE", release["html_url"])
PY
