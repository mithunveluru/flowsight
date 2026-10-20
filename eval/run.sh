#!/usr/bin/env bash
# Usage: eval/run.sh [--mode tesseract|llm|ml-service] [--update-baseline]
#
# tesseract (default): offline; scorer tests, receipt OCR via Tesseract, categorization, merchants,
#             recurring payments and leaks, the insights grounding validator and the assistant's
#             intent matcher,
#             cash-flow forecasts and budget pace.
# llm:        receipt OCR through the receipt-ocr service exactly as the backend calls it
#             (needs RECEIPT_OCR_URL, default http://127.0.0.1:8001).
# ml-service: receipt OCR through ml-service's ocr capability (needs ML_SERVICE_URL and
#             ML_SERVICE_TOKEN, ML_OCR_ENABLED=true on the service).
# Non-default modes score synthetic receipts only.
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
  ml-service) SUITE=ocr-ml-service ;;
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

if [ "$MODE" = tesseract ]; then
  # Synthetic transaction histories for recurring and leak detection; only their hash is committed.
  in_image "$IMAGE" python3 /work/eval/recurring/generate.py --out /work/eval/out/recurring >/dev/null
  HASH=$(sha256sum "$OUT/recurring/cases.jsonl" | cut -d' ' -f1)
  if [ "$HASH" != "$(cat "$ROOT/eval/recurring/cases.sha256")" ]; then
    if [ -n "$UPDATE" ]; then
      echo "$HASH" > "$ROOT/eval/recurring/cases.sha256"
      echo "recurring cases updated: eval/recurring/cases.sha256"
    else
      echo "FAIL: generated recurring cases differ from eval/recurring/cases.sha256." >&2
      echo "      Run with --update-baseline if the change is intended." >&2
      exit 2
    fi
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
  in_image "$IMAGE" python3 /work/eval/forecast/generate.py --out /work/eval/out/forecast/histories.jsonl
  TESTS="ReceiptEvalTest,TextEvalTest,RecurringEvalTest,InsightsEvalTest,ForecastEvalTest"
  MVN_PROPS+=(-Deval.insights.validator=/work/eval/insights/validator_cases.jsonl
              -Deval.insights.validatorOutput=/work/eval/out/pred-insights-validator.jsonl
              -Deval.insights.intents=/work/eval/insights/intents.csv
              -Deval.insights.intentsOutput=/work/eval/out/pred-assistant-intents.jsonl)
  MVN_PROPS+=(-Deval.categorization.input=/work/eval/categorization/cases.csv
              -Deval.categorization.output=/work/eval/out/pred-categorization.jsonl
              -Deval.categorization.modelInput=/work/eval/out/categorization-model-input.jsonl
              -Deval.merchants.input=/work/eval/merchants/cases.csv
              -Deval.merchants.output=/work/eval/out/pred-merchants.jsonl
              -Deval.recurring.input=/work/eval/out/recurring/cases.jsonl
              -Deval.recurring.rulesOutput=/work/eval/out/pred-recurring-rules.jsonl
              -Deval.recurring.modelInput=/work/eval/out/recurring-model-input.jsonl
              -Deval.forecast.input=/work/eval/out/forecast/histories.jsonl
              -Deval.forecast.outputDir=/work/eval/out)
elif [ "$MODE" = ml-service ]; then
  MVN_PROPS+=(-Deval.mlServiceUrl="${ML_SERVICE_URL:?ML_SERVICE_URL must be set}")
  DOCKER_ENV=(-e GROQ_API_KEY= -e ML_SERVICE_TOKEN)
else
  MVN_PROPS+=(-Deval.receiptOcrUrl="${RECEIPT_OCR_URL:-http://127.0.0.1:8001}" -Deval.delayMs="${EVAL_DELAY_MS:-2000}")
  # Like the compose stack, the Tesseract fallback may ask Groq about ambiguous merchants.
  DOCKER_ENV=(-e GROQ_API_KEY)
fi

in_image "${DOCKER_ENV[@]}" -w /work/backend "$IMAGE" \
  mvn -q -B -Dmaven.repo.local=/m2/repository test -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false \
  "${MVN_PROPS[@]}"

if [ "$MODE" = tesseract ]; then
  # the ml-service categorizer on the items the backend would send it, then the personal layer on top
  in_image -w /work/eval "$IMAGE" python3 categorization/predict_model.py \
    --artifact /work/ml-service/app/categorizer/artifact \
    --input out/categorization-model-input.jsonl --out out/pred-categorization-model.jsonl
  in_image -w /work/backend "$IMAGE" \
    mvn -q -B -Dmaven.repo.local=/m2/repository test -Dtest=PersonalLayerEvalTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Deval.categorization.input=/work/eval/categorization/cases.csv \
    -Deval.categorization.modelInput=/work/eval/out/categorization-model-input.jsonl \
    -Deval.categorization.modelPred=/work/eval/out/pred-categorization-model.jsonl \
    -Deval.categorization.personalOutput=/work/eval/out/pred-categorization-personal.jsonl \
    -Deval.categorization.personalRepeatOutput=/work/eval/out/pred-categorization-personal-repeat.jsonl
  # the recurring-scorer artifact on the backend's requests, then the model-path mapping and leak detectors
  in_image -w /work/eval "$IMAGE" python3 recurring/predict_model.py \
    --artifact /work/ml-service/app/recurring_scorer/artifact \
    --input out/recurring-model-input.jsonl --out out/recurring-model-output.jsonl
  in_image -w /work/backend "$IMAGE" \
    mvn -q -B -Dmaven.repo.local=/m2/repository test -Dtest=RecurringEvalTest#writesModelPredictions \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Deval.recurring.input=/work/eval/out/recurring/cases.jsonl \
    -Deval.recurring.modelPred=/work/eval/out/recurring-model-output.jsonl \
    -Deval.recurring.modelOutput=/work/eval/out/pred-recurring-model.jsonl
fi

STATUS=0
score() { in_image -w /work/eval "$IMAGE" python3 score.py "$@" $UPDATE || STATUS=$(( $? > STATUS ? $? : STATUS )); }
PRIVATE_ARGS=()
[ "$MODE" = tesseract ] && PRIVATE_ARGS=(--private-truth out/private-truth.jsonl)
score receipts --suite "$SUITE" --truth receipts/truth.jsonl --pred "out/pred-$SUITE.jsonl" \
  --baseline "baselines/$SUITE.json" "${PRIVATE_ARGS[@]}"
if [ "$MODE" = tesseract ]; then
  score categorization --suite categorization --cases categorization/cases.csv \
    --pred out/pred-categorization-model.jsonl --baseline baselines/categorization.json
  score categorization --suite categorization-rules --cases categorization/cases.csv \
    --pred out/pred-categorization.jsonl --baseline baselines/categorization-rules.json
  score categorization --suite categorization-personal --cases categorization/cases.csv \
    --pred out/pred-categorization-personal.jsonl --baseline baselines/categorization-personal.json
  score categorization --suite categorization-personal-repeat --cases categorization/cases.csv \
    --pred out/pred-categorization-personal-repeat.jsonl --baseline baselines/categorization-personal-repeat.json
  score merchants --suite merchants --cases merchants/cases.csv \
    --pred out/pred-merchants.jsonl --baseline baselines/merchants.json
  score recurring --suite recurring-rules --cases out/recurring/cases.jsonl \
    --pred out/pred-recurring-rules.jsonl --baseline baselines/recurring-rules.json
  score recurring --suite recurring-model --cases out/recurring/cases.jsonl \
    --pred out/pred-recurring-model.jsonl --baseline baselines/recurring-model.json
  score validator --suite insights-validator --cases insights/validator_cases.jsonl \
    --pred out/pred-insights-validator.jsonl --baseline baselines/insights-validator.json
  score intents --suite assistant-intents --cases insights/intents.csv \
    --pred out/pred-assistant-intents.jsonl --baseline baselines/assistant-intents.json
  # the backend and ml-service must serve the same interval priors
  if ! cmp -s "$ROOT/ml-service/app/forecaster/artifact/priors.json" \
       "$ROOT/backend/src/main/resources/com/flowsight/ml/forecast/priors.json"; then
    echo "FAIL: forecaster priors differ between ml-service and the backend" >&2
    STATUS=1
  fi
  in_image -w /work/eval "$IMAGE" python3 forecast/predict_model.py \
    --model /work/ml-service/app/forecaster/model.py --priors /work/ml-service/app/forecaster/artifact/priors.json \
    --histories out/forecast/histories.jsonl --out out/pred-forecast-ml.jsonl
  for suite in forecast-current budget-pace-current; do
    score forecast --suite "$suite" --histories out/forecast/histories.jsonl \
      --pred "out/pred-$suite.jsonl" --baseline "baselines/$suite.json"
  done
  score forecast --suite forecast --histories out/forecast/histories.jsonl --pred out/pred-forecast.jsonl \
    --baseline baselines/forecast.json --reference baselines/forecast-current.json
  score forecast --suite forecast-ml --histories out/forecast/histories.jsonl --pred out/pred-forecast-ml.jsonl \
    --baseline baselines/forecast-ml.json --reference baselines/forecast.json
  score forecast --suite budget-pace --histories out/forecast/histories.jsonl --pred out/pred-budget-pace.jsonl \
    --baseline baselines/budget-pace.json --reference baselines/budget-pace-current.json
fi

echo "eval ($MODE) finished in $((SECONDS - START))s, exit $STATUS"
exit $STATUS
