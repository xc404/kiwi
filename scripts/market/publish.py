#!/usr/bin/env python3
"""Publish template pack or plugin JAR to Nexus kiwi-market-raw and update market/index.json."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
INDEX_LOCAL = REPO_ROOT / ".market-index.local.json"


def env(name: str, default: str | None = None) -> str:
    val = os.environ.get(name, default)
    if val is None:
        raise SystemExit(f"Missing env {name}")
    return val


def auth_header(user: str, password: str) -> dict[str, str]:
    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    return {"Authorization": f"Basic {token}"}


def upload(base: str, dest: str, local: Path, headers: dict[str, str]) -> None:
    data = local.read_bytes()
    req = urllib.request.Request(f"{base}/{dest}", data=data, method="PUT", headers=headers)
    with urllib.request.urlopen(req) as resp:
        resp.read()
    print(f"Uploaded {dest}")


def load_index(base: str, headers: dict[str, str]) -> dict:
    try:
        req = urllib.request.Request(f"{base}/market/index.json", headers=headers)
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError:
        return {"schemaVersion": 1, "generatedAt": "", "items": []}


def save_index(base: str, data: dict, headers: dict[str, str]) -> None:
    data["schemaVersion"] = 1
    data["generatedAt"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    INDEX_LOCAL.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    upload(base, "market/index.json", INDEX_LOCAL, headers)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def upsert_item(data: dict, item: dict) -> None:
    items = [i for i in data.get("items", []) if not (i.get("slug") == item["slug"] and i.get("version") == item["version"] and i.get("type") == item["type"])]
    items.append(item)
    data["items"] = items


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("upload-index")

    t = sub.add_parser("template")
    t.add_argument("slug")
    t.add_argument("version")
    t.add_argument("file")
    t.add_argument("--name")
    t.add_argument("--summary", default="")

    p = sub.add_parser("plugin")
    p.add_argument("artifact")
    p.add_argument("version")
    p.add_argument("file")
    p.add_argument("--group-id", default="com.kiwi")
    p.add_argument("--component-keys", default="")

    args = parser.parse_args()
    nexus = env("NEXUS_URL", "http://localhost:8081")
    user = env("NEXUS_USER", "admin")
    password = env("NEXUS_PASSWORD")
    raw_repo = env("RAW_REPO", "kiwi-market-raw")
    base = f"{nexus}/repository/{raw_repo}"
    headers = auth_header(user, password)

    data = load_index(base, headers)

    if args.cmd == "upload-index":
        save_index(base, data, headers)
        return

    file_path = Path(args.file)
    if not file_path.is_file():
        raise SystemExit(f"File not found: {file_path}")

    digest = sha256_file(file_path)

    if args.cmd == "template":
        slug, ver = args.slug, args.version
        name = args.name or slug
        dest = f"templates/{slug}/{ver}/{slug}-{ver}.kiwi-template-pack"
        manifest_dest = f"templates/{slug}/{ver}/manifest.json"
        upload(base, dest, file_path, headers)
        manifest_tmp = REPO_ROOT / ".manifest.tmp.json"
        manifest_tmp.write_text(json.dumps({"slug": slug, "version": ver, "name": name}), encoding="utf-8")
        upload(base, manifest_dest, manifest_tmp, headers)
        manifest_tmp.unlink(missing_ok=True)
        item = {
            "type": "template",
            "slug": slug,
            "name": name,
            "version": ver,
            "summary": args.summary,
            "downloadUrl": dest,
            "sha256": digest,
            "manifestUrl": manifest_dest,
        }
        upsert_item(data, item)
        save_index(base, data, headers)
        print(f"Published template {slug}@{ver} sha256={digest}")
        return

    artifact, ver = args.artifact, args.version
    group = args.group_id
    group_path = group.replace(".", "/")
    dest = f"plugins/{group_path}/{artifact}/{ver}/{artifact}-{ver}.jar"
    manifest_dest = f"plugins/{group_path}/{artifact}/{ver}/manifest.json"
    upload(base, dest, file_path, headers)
    manifest_tmp = REPO_ROOT / ".manifest.tmp.json"
    manifest_tmp.write_text(json.dumps({"groupId": group, "artifactId": artifact, "version": ver}), encoding="utf-8")
    upload(base, manifest_dest, manifest_tmp, headers)
    manifest_tmp.unlink(missing_ok=True)
    keys = [k.strip() for k in args.component_keys.split(",") if k.strip()]
    item = {
        "type": "plugin",
        "slug": artifact,
        "name": artifact,
        "version": ver,
        "downloadUrl": dest,
        "sha256": digest,
        "manifestUrl": manifest_dest,
        "componentKeys": keys,
        "mavenCoordinate": {"groupId": group, "artifactId": artifact, "version": ver},
    }
    upsert_item(data, item)
    save_index(base, data, headers)
    print(f"Published plugin {artifact}@{ver} sha256={digest}")


if __name__ == "__main__":
    main()
