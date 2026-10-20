"""Write a merchant-embeddings artifact from a CSV export of merchant_aliases.

  psql "$DATABASE_URL" -c "\\copy (SELECT alias, canonical_name FROM merchant_aliases ORDER BY priority) TO 'aliases.csv' CSV HEADER"
  python -m app.merchant_embeddings.build --aliases aliases.csv --out "$ML_MODEL_DIR/merchant-embeddings/<version>"
  python -m app.cli register merchant-embeddings <version> --training-data '{"aliases": <n>}' --status shadow

The baseline bundled in the image comes from the seed rows of the alias migration:

  python -m app.merchant_embeddings.build --from-sql ../backend/src/main/resources/db/migration/V16__merchant_aliases.sql \\
      --out app/merchant_embeddings/artifact
"""

import argparse
import csv
import json
import re
import sys
from pathlib import Path

from app.merchant_embeddings import ARTIFACT


# ('alias', 'Canonical Name', ...) rows of the seed INSERT; SQL doubles single quotes
_SEED_ROW = re.compile(r"^\s*\('((?:[^']|'')*)',\s*'((?:[^']|'')*)'", re.MULTILINE)


def rows_from_sql(sql_file: Path) -> list[dict]:
    text = sql_file.read_text(encoding="utf-8")
    return [{"alias": a.replace("''", "'"), "canonical": c.replace("''", "'")} for a, c in _SEED_ROW.findall(text)]


def rows_from_csv(aliases_csv: Path) -> list[dict]:
    with open(aliases_csv, encoding="utf-8", newline="") as f:
        return [{"alias": r["alias"], "canonical": r["canonical_name"]}
                for r in csv.DictReader(f) if r.get("alias") and r.get("canonical_name")]


def build(aliases_csv: Path, out_dir: Path) -> int:
    return write(rows_from_csv(aliases_csv), out_dir, str(aliases_csv))


def write(rows: list[dict], out_dir: Path, source: str) -> int:
    if not rows:
        raise ValueError(f"no aliases in {source}")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / ARTIFACT).write_text(json.dumps(rows, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    return len(rows)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--aliases", type=Path)
    source.add_argument("--from-sql", type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args(argv)
    rows = rows_from_sql(args.from_sql) if args.from_sql else rows_from_csv(args.aliases)
    print(f"wrote {write(rows, args.out, str(args.from_sql or args.aliases))} aliases to {args.out / ARTIFACT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
