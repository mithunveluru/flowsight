import importlib

from app.capability import Capability

# One line per capability package, e.g. "app.categorizer".
INSTALLED: tuple[str, ...] = ("app.merchant_embeddings", "app.ocr")


def load_installed(modules: tuple[str, ...] = INSTALLED) -> list[Capability]:
    return [importlib.import_module(path).CAPABILITY for path in modules]
