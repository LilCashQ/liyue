#!/usr/bin/env python3
"""Publish an already verified launcher APK to a GitHub release."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

REPO = "LilCashQ/liyue"

def request(url: str, token: str, method: str = "GET", data: bytes | None = None,
            content_type: str = "application/vnd.github+json"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "LiYue-Release-Tool",
    }
    if data is not None:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=600) as response:
        return response.status, response.read()

def api(url: str, token: str, method: str = "GET", payload: dict | None = None) -> dict:
    data = json.dumps(payload).encode() if payload is not None else None
    status, body = request(url, token, method, data)
    return json.loads(body) if body else {"status": status}

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag", choices=("launcher-1.6.1",))
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()

    token_path = Path("/root/.config/liyue/github_token")
    token = token_path.read_text().strip()
    if not token:
        raise SystemExit("GitHub token is empty")

    base = f"https://api.github.com/repos/{REPO}"
    try:
        release = api(f"{base}/releases/tags/{args.tag}", token)
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        release = api(f"{base}/releases", token, "POST", {
            "tag_name": args.tag,
            "target_commitish": "launcher",
            "name": "里悦启动器 1.6.1",
            "body": "从 LeviLaunchroidUnlocked v1.6.0 派生的 arm64 构建。保留上游 DEX、原生库和包名；替换显示名称与品牌图标，并由里悦应用内启动。",
            "draft": False,
            "prerelease": False,
        })

    asset_name = "liyue-launcher-1.6.1-arm64.apk"
    for asset in release.get("assets", []):
        if asset["name"] == asset_name:
            _, _ = request(asset["url"], token, "DELETE")

    upload_url = release["upload_url"].split("{")[0] + "?" + urllib.parse.urlencode({"name": asset_name})
    status, _ = request(upload_url, token, "POST", args.apk.read_bytes(),
                        "application/vnd.android.package-archive")
    check = api(f"{base}/releases/tags/{args.tag}", token)
    assets = [a for a in check.get("assets", []) if a["name"] == asset_name]
    if len(assets) != 1 or assets[0]["size"] != args.apk.stat().st_size:
        raise SystemExit("Uploaded launcher asset metadata does not match the local APK")

    digest = __import__("hashlib").sha256(args.apk.read_bytes()).hexdigest()
    _, downloaded = request(assets[0]["browser_download_url"], token, "GET",
                            content_type="application/octet-stream")
    import hashlib
    if hashlib.sha256(downloaded).hexdigest() != digest:
        raise SystemExit("Downloaded release asset SHA-256 differs from local APK")
    print(f"UPLOAD_STATUS {status}")
    print(f"RELEASE {check['html_url']}")
    print(f"ASSET {assets[0]['browser_download_url']}")
    print(f"SHA256 {digest}")

if __name__ == "__main__":
    main()
