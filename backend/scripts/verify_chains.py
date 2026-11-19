#!/usr/bin/env python3
"""D1: independent check of history chains exported by the Java tests.

Uses hashlib, cryptography (Ed25519) and rfc8785. Exits 0 only when every chain's
verdict matches the expected status and, when invalid, the seq of the first failure.
"""
import base64
import hashlib
import json
import sys

import rfc8785
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.serialization import load_der_public_key


def canonical(document):
    dumped = rfc8785.dumps(document)
    return dumped.decode("utf-8") if isinstance(dumped, bytes) else dumped


def verify(chain):
    key = load_der_public_key(base64.b64decode(chain["publicKey"]))
    previous = bytes(32)
    for index, entry in enumerate(chain["entries"], start=1):
        seq = entry["seq"]
        if seq != index:
            return "INVALID", seq
        if base64.b64decode(entry["previousHash"]) != previous:
            return "INVALID", seq
        text = entry["canonical"]
        if canonical(entry["document"]) != text:
            return "INVALID", seq
        digest = hashlib.sha256(previous + text.encode("utf-8")).digest()
        if digest != base64.b64decode(entry["hash"]):
            return "INVALID", seq
        try:
            key.verify(base64.b64decode(entry["signature"]), digest)
        except (InvalidSignature, Exception):
            return "INVALID", seq
        if entry["document"].get("seq") != seq:
            return "INVALID", seq
        previous = digest
    return "VALID", None


def main():
    with open(sys.argv[1], encoding="utf-8") as fh:
        chains = json.load(fh)["chains"]
    for chain in chains:
        status, seq = verify(chain)
        expected = chain["expectedStatus"]
        expected_seq = chain.get("expectedSeq")
        if status != expected or seq != expected_seq:
            print(f"{chain['name']}: got {status} {seq}, expected {expected} {expected_seq}", file=sys.stderr)
            return 1
        print(f"{chain['name']}: {status}" + (f" seq {seq}" if seq else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
