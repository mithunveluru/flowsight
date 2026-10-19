"""Write a merchant-embeddings artifact from a CSV export of merchant_aliases.

  psql "$DATABASE_URL" -c "\\copy (SELECT alias, canonical_name FROM merchant_aliases ORDER BY priority) TO 'aliases.csv' CSV HEADER"
  python -m app.merchant_embeddings.build --aliases aliases.csv --out "$ML_MODEL_DIR/merchant-embeddings/<version>"
  python -m app.cli register merchant-embeddings <version> --training-data '{"aliases": <n>}' --status shadow
"""

import argparse
import csv
import json
import sys
from pathlib import Path

from app.merchant_embeddings import ARTIFACT


def build(aliases_csv: Path, out_dir: Path) -> int:
    with open(aliases_csv, encoding="utf-8", newline="") as f:
        rows = [{"alias": r["alias"], "canonical": r["canonical_name"]}
                for r in csv.DictReader(f) if r.get("alias") and r.get("canonical_name")]
    if not rows:
        raise ValueError(f"no aliases in {aliases_csv}")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / ARTIFACT).write_text(json.dumps(rows, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    return len(rows)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--aliases", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args(argv)
    print(f"wrote {build(args.aliases, args.out)} aliases to {args.out / ARTIFACT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
