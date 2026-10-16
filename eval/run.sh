#!/usr/bin/env bash
# Usage: eval/run.sh [--mode tesseract|llm|llm-http11] [--update-baseline]
#
# tesseract (default): offline; scorer tests, receipt OCR via Tesseract, categorization, merchants.
# llm:        receipt OCR through the receipt-ocr service exactly as the backend calls it.
# llm-http11: diagnostic; same, but the client is forced to HTTP/1.1 (see CurrentReceiptPipeline).
# LLM modes need RECEIPT_OCR_URL (default http://127.0.0.1:8001) and score synthetic receipts only.
# Exit status: non-zero when any suite regresses against eval/baselines/ or a baseline is stale.
set -euo pipefail

MODE=tesseract
UPDATE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --update-baseline) UPDATE="--update-baseline"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done
case "$MODE" in
  tesseract) SUITE=ocr-tesseract ;;
  llm) SUITE=ocr-llm ;;
  llm-http11) SUITE=ocr-llm-http11-diagnostic ;;
  *) echo "unknown mode: $MODE" >&2; exit 64 ;;
esac

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/eval/out"
M2="${EVAL_M2:-$HOME/.m2}"
IMAGE="flowsight-eval:$(sha256sum "$ROOT/eval/Dockerfile" | cut -c1-12)"
START=$SECONDS
mkdir -p "$OUT" "$M2"

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  docker build --network host -t "$IMAGE" "$ROOT/eval"
fi

# The worktree is mounted at /work; files are written as the calling user.
in_image() {
  docker run --rm --network host -u "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$ROOT":/work -v "$M2":/m2 "$@"
}

if [ "$MODE" = tesseract ]; then
  in_image -w /work/eval "$IMAGE" python3 -m pytest -q -p no:cacheprovider tests
fi

# Regenerate the golden set and check it is byte-identical to the committed truth.
in_image "$IMAGE" python3 /work/eval/receipts/generate.py --out /work/eval/out/receipts >/dev/null
if ! cmp -s "$OUT/receipts/truth.jsonl" "$ROOT/eval/receipts/truth.jsonl"; then
  if [ -n "$UPDATE" ]; then
    cp "$OUT/receipts/truth.jsonl" "$ROOT/eval/receipts/truth.jsonl"
    echo "receipt truth updated: eval/receipts/truth.jsonl"
  else
    echo "FAIL: generated receipts differ from eval/receipts/truth.jsonl (generator, fonts or image changed)." >&2
    echo "      Run with --update-baseline if the change is intended." >&2
    exit 2
  fi
fi

MANIFEST_ARGS=()
[ "$MODE" = tesseract ] || MANIFEST_ARGS=(--synthetic-only)
in_image -w /work/eval "$IMAGE" python3 score.py manifest --truth out/receipts/truth.jsonl \
  --private fixtures/private --out out "${MANIFEST_ARGS[@]}"

MVN_PROPS=(-Deval.input=/work/eval/out/manifest.jsonl -Deval.output=/work/eval/out/pred-$SUITE.jsonl)
TESTS=ReceiptEvalTest
DOCKER_ENV=(-e GROQ_API_KEY=)
if [ "$MODE" = tesseract ]; then
  TESTS="ReceiptEvalTest,TextEvalTest"
  MVN_PROPS+=(-Deval.categorization.input=/work/eval/categorization/cases.csv
              -Deval.categorization.output=/work/eval/out/pred-categorization.jsonl
              -Deval.merchants.input=/work/eval/merchants/cases.csv
              -Deval.merchants.output=/work/eval/out/pred-merchants.jsonl)
else
  MVN_PROPS+=(-Deval.receiptOcrUrl="${RECEIPT_OCR_URL:-http://127.0.0.1:8001}" -Deval.delayMs="${EVAL_DELAY_MS:-2000}")
  [ "$MODE" = llm-http11 ] && MVN_PROPS+=(-Deval.forceHttp11=true)
  # Like the compose stack, the Tesseract fallback may ask Groq about ambiguous merchants.
  DOCKER_ENV=(-e GROQ_API_KEY)
fi

in_image "${DOCKER_ENV[@]}" -w /work/backend "$IMAGE" \
  mvn -q -B -Dmaven.repo.local=/m2/repository test -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false \
  "${MVN_PROPS[@]}"

STATUS=0
score() { in_image -w /work/eval "$IMAGE" python3 score.py "$@" $UPDATE || STATUS=$(( $? > STATUS ? $? : STATUS )); }
PRIVATE_ARGS=()
[ "$MODE" = tesseract ] && PRIVATE_ARGS=(--private-truth out/private-truth.jsonl)
score receipts --suite "$SUITE" --truth receipts/truth.jsonl --pred "out/pred-$SUITE.jsonl" \
  --baseline "baselines/$SUITE.json" "${PRIVATE_ARGS[@]}"
if [ "$MODE" = tesseract ]; then
  score categorization --suite categorization --cases categorization/cases.csv \
    --pred out/pred-categorization.jsonl --baseline baselines/categorization.json
  score merchants --suite merchants --cases merchants/cases.csv \
    --pred out/pred-merchants.jsonl --baseline baselines/merchants.json
fi

echo "eval ($MODE) finished in $((SECONDS - START))s, exit $STATUS"
exit $STATUS
