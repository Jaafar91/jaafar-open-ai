#!/usr/bin/env python3
"""Prints the next safe Android versionCode for this app.

Queries the Android Publisher API for the highest versionCode across every
track this app has ever been released to (production, internal, closed
testing, drafts -- everything), then prints max + INCREMENT.

This is the source of truth CI should use for a real Play upload: asking
Play itself avoids any risk of two different workflows (a PR build and a
manually-dispatched signed release, say) computing out-of-order or
colliding versionCodes from independent local counters or clocks. Play
requires every new upload's versionCode to be strictly greater than every
versionCode already published for this applicationId, so "max seen so far"
is the only value that matters.

Reads the service account key JSON from the GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
environment variable (the same secret already used for publishing). Package
name and increment come from command-line arguments.

Exits non-zero on any failure (auth, network, malformed response) rather
than guessing -- callers should treat this step as best-effort (e.g. run it
with continue-on-error in CI) and fall back to another safely-monotonic
scheme (like build.gradle.kts's minutes-since-anchor fallback) when it
fails, rather than block the whole pipeline on Play's API being reachable.
"""
import argparse
import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def highest_version_code(tracks: list) -> int:
    """Highest versionCode across every release in every track, or 0 if none exist yet."""
    version_codes = [
        int(code)
        for track in tracks
        for release in track.get("releases", [])
        for code in release.get("versionCodes", [])
    ]
    return max(version_codes, default=0)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--increment", type=int, default=1)
    args = parser.parse_args()

    key_json = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    if not key_json:
        print("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not set", file=sys.stderr)
        return 1

    credentials = service_account.Credentials.from_service_account_info(
        json.loads(key_json), scopes=SCOPES
    )
    session = AuthorizedSession(credentials)
    app_base = f"{API_BASE}/{args.package_name}"

    edit_response = session.post(f"{app_base}/edits")
    edit_response.raise_for_status()
    edit_id = edit_response.json()["id"]

    try:
        tracks_response = session.get(f"{app_base}/edits/{edit_id}/tracks")
        tracks_response.raise_for_status()
        tracks = tracks_response.json().get("track", [])
        highest_published = highest_version_code(tracks)
    finally:
        # Edits are drafts; discard it rather than leaving it open (an open
        # edit can block creating a new one on the next run).
        session.delete(f"{app_base}/edits/{edit_id}")

    next_version_code = highest_published + args.increment

    print(f"Highest published versionCode: {highest_published}", file=sys.stderr)
    print(f"Next versionCode ({args.increment:+d}): {next_version_code}", file=sys.stderr)
    print(next_version_code)
    return 0


if __name__ == "__main__":
    sys.exit(main())
