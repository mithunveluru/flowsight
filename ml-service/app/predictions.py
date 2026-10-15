import hashlib
import hmac
import json
import logging
import sys
from datetime import datetime, timezone
from typing import Any, Callable

from pydantic import BaseModel

Sink = Callable[[dict[str, Any]], None]

_logger = logging.getLogger("flowsight.ml.predictions")


def stdout_sink(entry: dict[str, Any]) -> None:
    if not _logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(logging.Formatter("%(message)s"))
        _logger.addHandler(handler)
        _logger.setLevel(logging.INFO)
        _logger.propagate = False
    _logger.info(json.dumps(entry, separators=(",", ":"), default=str))


class PredictionLog:
    """Structured prediction records. Inputs are stored only as a keyed hash so identical
    inputs can be correlated without the log ever holding user text."""

    def __init__(self, hash_key: str, sink: Sink = stdout_sink):
        self._key = hash_key.encode()
        self._sink = sink

    def input_hash(self, payload: BaseModel) -> str:
        canonical = json.dumps(payload.model_dump(mode="json"), sort_keys=True, separators=(",", ":"))
        return hmac.new(self._key, canonical.encode(), hashlib.sha256).hexdigest()

    def record(
        self,
        *,
        request_id: str,
        capability: str,
        model_version: str,
        role: str,
        latency_ms: float,
        input_hash: str,
        input_summary: dict[str, Any],
        output_summary: dict[str, Any] | None = None,
        error: str | None = None,
    ) -> None:
        entry = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "request_id": request_id,
            "capability": capability,
            "model_version": model_version,
            "role": role,
            "latency_ms": round(latency_ms, 2),
            "input_hash": input_hash,
            "input_summary": input_summary,
            "output_summary": output_summary,
            "error": error,
        }
        try:
            self._sink(entry)
        except Exception:
            logging.getLogger(__name__).exception("prediction log sink failed")
