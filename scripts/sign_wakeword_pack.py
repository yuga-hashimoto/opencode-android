#!/usr/bin/env python3
"""Create a signed OpenCode Android wake-word pack manifest.

The private key is read from OPENCODE_ANDROID_WAKEWORD_SIGNING_KEY and is never
written into the repository. The generated JSON matches WakeWordPackManager's
SHA256withRSA signing payload exactly.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import urlparse


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("--id", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--required-file", action="append", default=["pack.json"])
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def validate(args: argparse.Namespace) -> None:
    if not args.archive.is_file():
        raise SystemExit(f"Archive not found: {args.archive}")
    if not args.id or len(args.id) > 64 or not all(
        c.islower() or c.isdigit() or c in "._-" for c in args.id
    ):
        raise SystemExit("Pack id must contain only lowercase letters, digits, '.', '_', or '-'")
    parsed = urlparse(args.url)
    if parsed.scheme.lower() != "https" or not parsed.hostname or parsed.username or parsed.password:
        raise SystemExit("Pack URL must be an HTTPS URL without credentials")
    required = sorted({item.strip() for item in args.required_file if item.strip()})
    if "pack.json" not in required:
        raise SystemExit("pack.json must be listed as a required file")
    if any(item.startswith("/") or "\\" in item or ".." in Path(item).parts for item in required):
        raise SystemExit("Required file path is unsafe")
    args.required_file = required


def signing_payload(manifest: dict[str, object]) -> bytes:
    lines = [
        str(manifest["schemaVersion"]),
        str(manifest["id"]).strip(),
        str(manifest["name"]).strip(),
        str(manifest["version"]).strip(),
        str(manifest["url"]),
        str(manifest["sha256"]).lower(),
        str(manifest["sizeBytes"]),
        "\0".join(sorted(str(item).strip() for item in manifest["requiredFiles"])),
    ]
    return "\n".join(lines).encode("utf-8")


def sign(payload: bytes, private_key: Path) -> str:
    with tempfile.TemporaryDirectory(prefix="wakeword-sign-") as directory:
        payload_path = Path(directory, "payload.bin")
        signature_path = Path(directory, "signature.bin")
        payload_path.write_bytes(payload)
        subprocess.run(
            [
                "openssl",
                "dgst",
                "-sha256",
                "-sign",
                str(private_key),
                "-out",
                str(signature_path),
                str(payload_path),
            ],
            check=True,
        )
        return base64.b64encode(signature_path.read_bytes()).decode("ascii")


def main() -> None:
    args = parse_args()
    validate(args)
    key_value = os.environ.get("OPENCODE_ANDROID_WAKEWORD_SIGNING_KEY", "").strip()
    if not key_value:
        raise SystemExit("OPENCODE_ANDROID_WAKEWORD_SIGNING_KEY is required")
    private_key = Path(key_value).expanduser()
    if not private_key.is_file():
        raise SystemExit(f"Signing key not found: {private_key}")

    archive = args.archive.read_bytes()
    manifest: dict[str, object] = {
        "schemaVersion": 1,
        "id": args.id,
        "name": args.name,
        "version": args.version,
        "url": args.url,
        "sha256": hashlib.sha256(archive).hexdigest(),
        "sizeBytes": len(archive),
        "requiredFiles": args.required_file,
        "signature": "",
    }
    manifest["signature"] = sign(signing_payload(manifest), private_key)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(args.output)


if __name__ == "__main__":
    main()
