#!/usr/bin/env python3
"""
Load artists from a JSON file and upsert them into Firestore.

Expected JSON shape:
{
  "artists": [
    { "id": "...", "name": "..." },
    ...
  ]
}

Each document will be written to the given collection with fields:
  id          : YouTube artist/channel id (also used as the doc id)
  name        : Display name
  isFemale    : bool (default False)
  isChasid    : bool (default False)
  isGenZ      : bool (default False)
  updatedAt   : server timestamp
"""

import argparse
import json
import sys
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Upload artists to Firestore.")
    parser.add_argument("--json", required=True, help="Path to artists.json")
    parser.add_argument(
        "--service-account",
        required=True,
        help="Path to Firebase service account JSON",
    )
    parser.add_argument(
        "--project",
        required=True,
        help="Firebase project id",
    )
    parser.add_argument(
        "--collection",
        default="artistsWhitelist",
        help="Firestore collection name (default: artistsWhitelist)",
    )
    parser.add_argument(
        "--is-female-default",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Default value for isFemale (default: False)",
    )
    parser.add_argument(
        "--is-chasid-default",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Default value for isChasid (default: False)",
    )
    parser.add_argument(
        "--is-genz-default",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Default value for isGenZ (default: False)",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=400,
        help="Writes per batch commit (default: 400, max 500)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse input and show summary without writing to Firestore",
    )
    return parser.parse_args()


def load_artists(json_path: Path) -> list[dict]:
    with json_path.open() as f:
        data = json.load(f)
    artists = data.get("artists", [])
    if not isinstance(artists, list):
        raise ValueError("Expected 'artists' to be a list in the JSON.")
    return artists


def main() -> int:
    args = parse_args()
    json_path = Path(args.json)
    if not json_path.is_file():
        print(f"JSON not found: {json_path}", file=sys.stderr)
        return 1

    artists = load_artists(json_path)
    print(f"Loaded {len(artists)} artists from {json_path}")

    # Initialize Firebase app
    cred = credentials.Certificate(args.service_account)
    firebase_admin.initialize_app(cred, {"projectId": args.project})
    db = firestore.client()

    if args.dry_run:
        print("Dry run: no writes performed.")
        return 0

    batch_size = max(1, min(args.batch_size, 500))
    batch = db.batch()
    count = 0
    for artist in artists:
        artist_id = artist.get("id")
        name = artist.get("name")
        if not artist_id or not name:
            print(f"Skipping entry missing id or name: {artist}", file=sys.stderr)
            continue

        doc_ref = db.collection(args.collection).document(artist_id)
        batch.set(
            doc_ref,
            {
                "id": artist_id,
                "name": name,
                "isFemale": args.is_female_default,
                "isChasid": args.is_chasid_default,
                "isGenZ": args.is_genz_default,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            },
            merge=True,
        )
        count += 1

        if count % batch_size == 0:
            batch.commit()
            batch = db.batch()
            print(f"Committed {count} documents...")

    if count % batch_size != 0:
        batch.commit()
    print(f"Finished. Upserted {count} documents into '{args.collection}'.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
