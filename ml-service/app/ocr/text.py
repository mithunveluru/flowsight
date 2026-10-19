"""Reading one OCR pass of a receipt: amounts, token-classified labels, items, merchant,
date and currency. Mirrors the backend's Java parser so both paths agree on the rules."""

import re
from dataclasses import dataclass, field
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal

CENT = Decimal("0.01")

_DIGITS = {ord(c): str(i) for i, c in enumerate("٠١٢٣٤٥٦٧٨٩")}
_DIGITS.update({ord(c): str(i) for i, c in enumerate("۰۱۲۳۴۵۶۷۸۹")})
_DIGITS.update({ord("٫"): ".", ord("٬"): ",", 0x00A0: " ", 0x202F: " "})

_NUMBER = re.compile(r"(?<![\d])(?<![\d][.,])(\d+(?:[.,']\d+)*)(?!\s*%)(?![.,]?\d)")
_SPLIT_GROUP = re.compile(r"(?<=\d[.,]) (?=\d{3}(?:[.,]\d))")
_SPLIT_DECIMAL = re.compile(r"(?<=\d) (?=[.,]\d{2}(?!\d))")
_NEGATIVE_PREFIX = re.compile(r"(?:^|[\s:])[-\u2212(]\s*(?:Rs\.?|INR|SAR|AED|USD|EUR|GBP|SR|[$₹€£])?\s*$", re.I)
_CURRENCY_WORDS = {"RS", "INR", "SAR", "SR", "AED", "DH", "DHS", "USD", "US", "EUR", "GBP"}
MAX_INTEGER_DIGITS = 9


def normalize(text: str) -> str:
    """ASCII digits and separators, plus repair of spaces OCR puts inside amounts."""
    s = (text or "").translate(_DIGITS)
    s = _SPLIT_GROUP.sub("", s)
    return _SPLIT_DECIMAL.sub("", s)


@dataclass(frozen=True)
class Amount:
    value: Decimal
    start: int
    end: int
    two_decimals: bool


def _glued(text: str, start: int, end: int) -> bool:
    i = start
    while i > 1 and text[i - 1] == "." and text[i - 2].isalpha():
        i -= 1
    if i > 0 and text[i - 1].isalpha():
        j = i
        while j > 0 and text[j - 1].isalpha():
            j -= 1
        if text[j:i].upper() not in _CURRENCY_WORDS:
            return True
    k = end
    if k < len(text) and text[k] in ".,":
        k += 1
    if k < len(text) and text[k].isalpha():
        m = k
        while m < len(text) and text[m].isalpha():
            m += 1
        return text[k:m].upper() not in _CURRENCY_WORDS
    return False


def _interpret(token: str):
    seps = [i for i, c in enumerate(token) if c in ".,'"]
    last = seps[-1] if seps else -1
    fraction = ""
    if last >= 0 and token[last] != "'" and len(token) - last - 1 == 2:
        int_part, fraction = token[:last], token[last + 1:]
    elif last >= 0 and len(token) - last - 1 != 3:
        if sum(c in ".," for c in token) != 1:
            return None
        int_part, fraction = token[:last], token[last + 1:]
        if len(fraction) > 3:
            return None
    else:
        int_part = token
    digits = re.sub(r"[.,']", "", int_part)
    if not digits or len(digits) > MAX_INTEGER_DIGITS:
        return None
    value = Decimal(digits + ("." + fraction if fraction else "")).quantize(CENT, ROUND_HALF_UP)
    return value, len(fraction) == 2


def find_amounts(line: str) -> list[Amount]:
    text = normalize(line)
    out = []
    for m in _NUMBER.finditer(text):
        if _glued(text, m.start(1), m.end(1)):
            continue
        parsed = _interpret(m.group(1))
        if parsed is None:
            continue
        value, two = parsed
        neg = bool(_NEGATIVE_PREFIX.search(text[max(0, m.start(1) - 10):m.start(1)])) or (
            m.end(1) < len(text) and text[m.end(1)] in "-)" and (m.end(1) + 1 >= len(text) or not text[m.end(1) + 1].isdigit()))
        out.append(Amount(-value if neg else value, m.start(1), m.end(1), two))
    return out


def parse_money(raw) -> Decimal | None:
    """One amount from a string or number, e.g. "1.234,50", "SAR 45.00" or 12.5."""
    if raw is None or isinstance(raw, bool):
        return None
    if isinstance(raw, (int, float, Decimal)):
        return Decimal(str(raw)).quantize(CENT, ROUND_HALF_UP)
    found = find_amounts(str(raw))
    return found[0].value if len(found) == 1 else None


# ---------------------------------------------------------------- labels

TAX_WORDS = {"tax", "vat", "gst", "cgst", "sgst", "igst", "utgst", "cess", "hst", "pst", "qst", "taxes", "ضريبة", "الضريبة"}
PAYMENT_WORDS = {"visa", "mastercard", "master", "amex", "rupay", "mada", "card", "upi", "debit", "credit", "paid",
                 "gpay", "paytm", "phonepe", "wallet", "applepay", "knet", "netbanking", "eftpos"}
GRAND = {"grand total", "net payable", "amount payable", "total payable", "total due", "amount due", "balance due",
         "final total", "total amount due", "bill amount", "total incl vat", "total including vat", "total inc vat",
         "total incl tax", "amount to pay", "to pay", "net amount payable"}
SUBTOTAL = {"subtotal", "sub total", "sub tot", "subtot", "total excl vat", "total excluding vat", "total excl tax",
            "total before tax", "taxable amount", "taxable value", "amount excl vat", "total ex vat", "net sales",
            "المجموع الفرعي"}
ARABIC_TOTAL = {"الإجمالي", "الاجمالي", "المجموع"}
TOTAL_KINDS = {"GRAND_TOTAL", "TOTAL"}
SUMMARY_KINDS = {"GRAND_TOTAL", "TOTAL", "SUBTOTAL", "TAX", "TAX_TOTAL", "DISCOUNT", "SERVICE_CHARGE", "TIP",
                 "ROUND_OFF", "ITEM_COUNT", "SAVINGS"}


def tokens(label: str) -> list[str]:
    s = (label or "").lower().replace("sub-total", "subtotal").replace("e-wallet", "wallet")
    return [t for t in re.split(r"[^\w]+|[\d_]+", s) if t]


def _phrase(joined: str, phrases) -> bool:
    padded = f" {joined} "
    return any(f" {p} " in padded for p in phrases)


def classify(label: str) -> str:
    t = tokens(label)
    if not t:
        return "OTHER"
    j = " ".join(t)
    has = lambda *w: any(x in t for x in w)  # noqa: E731
    if has("items", "item", "qty", "quantity", "pcs", "articles") and not has("amount", "price", "value") and (
            has("total", "no", "count", "number", "of") or len(t) == 1):
        return "ITEM_COUNT"
    if has("tendered", "tender", "received"):
        return "TENDERED"
    if has("change") and not has("exchange"):
        return "CHANGE"
    if has("saved", "savings", "saving"):
        return "SAVINGS"
    if _phrase(j, {"round off", "roundoff", "rounding", "round", "rnd off", "rounded"}):
        return "ROUND_OFF"
    if has("tip", "tips", "gratuity"):
        return "TIP"
    if _phrase(j, SUBTOTAL):
        return "SUBTOTAL"
    if _phrase(j, GRAND):
        return "GRAND_TOTAL"
    if has("total", "tot") and any(x in TAX_WORDS for x in t):
        return "TAX_TOTAL"
    if has("service") or _phrase(j, {"svc chg", "svc charge"}):
        return "SERVICE_CHARGE"
    if has("discount", "disc", "coupon", "promo", "offer", "less", "voucher"):
        return "DISCOUNT"
    if any(x in TAX_WORDS for x in t):
        return "TAX"
    if has("total", "tot", "payable") or _phrase(j, {"net amount"}) or any(x in ARABIC_TOTAL for x in t):
        return "TOTAL"
    if has("cash"):
        return "TENDERED"
    if any(x in PAYMENT_WORDS for x in t):
        return "PAYMENT"
    return "OTHER"


# ---------------------------------------------------------------- currency

_CUES = [
    ("INR", r"₹|\bRs\.?\s?\d|\bINR\b", 6, 0), ("INR", r"\bGSTIN\b|\b[CSIU]GST\b|\bHSN\b|\bFSSAI\b", 4, re.I),
    ("INR", r"\+91[\s-]?\d|\b(?:Bengaluru|Bangalore|Mumbai|Delhi|Chennai|Kolkata|Hyderabad|Pune|Gurgaon|Gurugram|Noida)\b", 2, re.I),
    ("INR", r"\bUPI\b|\bPaytm\b|\bRuPay\b", 1, re.I),
    ("SAR", r"\bSAR\b|\bSR\s?\d|ر\.س|ريال", 6, re.I),
    ("SAR", r"\bVAT\s*(?:No|Number|Reg)\b[^\n]*\b3\d{13}3\b|\bSimplified\s+Tax\s+Invoice\b", 4, re.I),
    ("SAR", r"\+966[\s-]?\d|\b(?:Riyadh|Jeddah|Khobar|Dammam|Makkah|Mecca|Madinah|Medina)\b|\bmada\b", 2, re.I),
    ("AED", r"\bAED\b|\bDhs?\.?\s?\d|د\.إ|درهم", 6, re.I), ("AED", r"\bTRN\b", 4, re.I),
    ("AED", r"\+971[\s-]?\d|\b(?:Dubai|Abu\s+Dhabi|Sharjah|Ajman|Fujairah|UAE)\b", 2, re.I),
    ("USD", r"\bUSD\b|US\$", 6, re.I), ("USD", r"\$\s?\d", 4, 0), ("USD", r"\bSales\s+Tax\b", 4, re.I),
    ("USD", r"\b[A-Z]{2}\s+\d{5}(?:-\d{4})?\b|\(\d{3}\)\s?\d{3}-\d{4}", 2, 0),
    ("EUR", r"€|\bEUR\b", 6, re.I), ("GBP", r"£|\bGBP\b", 6, re.I),
]
_CUES = [(c, re.compile(p, f), w) for c, p, w, f in _CUES]


def detect_currency(text: str) -> tuple[str | None, float]:
    scores: dict[str, int] = {}
    for code, pattern, weight in _CUES:
        if pattern.search(text or ""):
            scores[code] = scores.get(code, 0) + weight
    if not scores:
        return None, 0.0
    ranked = sorted(scores.values(), reverse=True)
    top, second = ranked[0], (ranked[1] if len(ranked) > 1 else 0)
    if top == second:
        return None, 0.0
    code = max(scores, key=scores.get)
    margin = top - second
    conf = 0.97 if top >= 10 and margin >= 6 else 0.93 if margin >= 6 else 0.85 if margin >= 4 else 0.70 if margin >= 2 else 0.50
    return code, conf


# ---------------------------------------------------------------- dates

_MONTHS = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
           "november", "december"]
_ISO = re.compile(r"(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)")
_NUMERIC = re.compile(r"(?<![\d.])(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})(?!\d)")
_DMY = re.compile(r"(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*([A-Za-z]{3,9})[\s\-/.,']*(\d{4}|\d{2})(?!\d)")
_MDY = re.compile(r"\b([A-Za-z]{3,9})[\s.\-]+(\d{1,2})(?:st|nd|rd|th)?,?[\s\-]+(\d{4})(?!\d)")
_DATE_LABEL = re.compile(r"\b(?:date|dated|dt|bill\s+date|inv(?:oice)?\s+date)\b", re.I)


def _month(word: str):
    w = word.lower()
    for i, m in enumerate(_MONTHS):
        if len(w) >= 3 and m.startswith(w):
            return i + 1
    return None


def _mk(y, m, d):
    try:
        return date(y, m, d)
    except ValueError:
        return None


@dataclass(frozen=True)
class DateGuess:
    value: date | None
    ambiguous: bool = False
    labelled: bool = False


def find_date(lines: list[str], currency: str | None, today: date | None = None) -> DateGuess:
    latest = (today or date.today()) + timedelta(days=1)
    month_first = currency == "USD"
    found = []
    for raw in lines:
        line = normalize(raw)
        labelled = bool(_DATE_LABEL.search(line))
        cands = []
        for m in _ISO.finditer(line):
            cands.append((_mk(int(m[1]), int(m[2]), int(m[3])), False))
        for m in _NUMERIC.finditer(line):
            a, b, y = int(m[1]), int(m[2]), int(m[3])
            y = 2000 + y if len(m[3]) == 2 else y
            ambiguous = a <= 12 and b <= 12 and a != b
            day_first = a > 12 or (b <= 12 and not month_first)
            cands.append((_mk(y, b, a) if day_first else _mk(y, a, b), ambiguous))
        for m in _DMY.finditer(line):
            mo = _month(m[2])
            if mo:
                y = int(m[3]) + (2000 if len(m[3]) == 2 else 0)
                cands.append((_mk(y, mo, int(m[1])), False))
        for m in _MDY.finditer(line):
            mo = _month(m[1])
            if mo:
                cands.append((_mk(int(m[3]), mo, int(m[2])), False))
        for d, amb in cands:
            if d and 2000 <= d.year and d <= latest:
                found.append(DateGuess(d, amb, labelled))
    if not found:
        return DateGuess(None)
    return max(found, key=lambda g: (2 if g.labelled else 0) + (0 if g.ambiguous else 1))


# ---------------------------------------------------------------- merchant

NON_MERCHANT = set("""tax invoice simplified retail original recipient duplicate triplicate copy customer merchant memo
supply receipt sales estimate kot order token proforma quotation challan statement abbreviated note bill cash credit debit
total grand sub subtotal net amount payable due balance vat gst cgst sgst igst hst pst change tendered tender paid payment
discount savings service charge round off tip visa mastercard amex card upi mada approved contactless chip pin date time
cashier server table cover guest guests terminal register reg transaction approval auth code reference ref no number ticket
sequence item items qty quantity price rate description unit each phone tel fax email mobile ph thank thanks you welcome
visit visiting again come please shopping have nice day for to the of by at our your and a with us""".split())
_NOISE = ("thank you", "thanks for", "visit again", "come again", "www.", "http", "e-mail", "email:", "tel:", "ph:",
          "fax:", "phone:", "contact us")
_STREET = re.compile(r"\b(st|ave|rd|blvd|dr|ln|ct|pl|way|hwy|pkwy|suite|ste|fl|floor|unit|apt|po\s+box|bldg)\b", re.I)
_STREET_WORD = re.compile(r"(?i)\b(road|street|avenue|lane|nagar|marg|chowk|bazaar)\b.*[,\d]|[,\d].*\b(road|street|avenue|lane|nagar|marg)\b")
_PLACE = re.compile(r"(?i:\b(?:dubai|abu\s+dhabi|sharjah|ajman|fujairah|riyadh|jeddah|khobar|dammam|makkah|madinah|mumbai|"
                    r"bengaluru|bangalore|delhi|chennai|kolkata|hyderabad|pune|gurgaon|gurugram|noida|new\s+york|chicago|"
                    r"san\s+francisco|los\s+angeles)\b)|\b\d{6}\b|\b[A-Z]{2}\s+\d{5}(?:-\d{4})?\b")
_ID_NOISE = re.compile(r"GSTIN|GTIN|\bGST\b|\bCIN\b|\bPAN\b|\bTIN\b|\bEIN\b|\bTRN\b|VAT\s+(?:REG|NO)|Tel\s*:|Ph\s*:|"
                       r"Fax\s*:|@|www\.|http|[0-9]{10,}", re.I)
_KEY_VALUE = re.compile(r"^\s*[A-Za-z\u0600-\u06FF][A-Za-z\u0600-\u06FF .#]{0,18}[:!]\s*\S")
_STORE_LINE = re.compile(r"^\W*(?:#|(?:store|str|branch|outlet|shop|location)\b\s*(?:#|no\.?|number)?\s*\d)", re.I)
_MONEY = re.compile(r"\d[.,]\d{2}(?!\d)")
_STORE_SUFFIX = re.compile(r"\s*(?:\b(?:store|str|branch)\s*)?(?:#|\bno\.?\s*)\s*\d+\s*$", re.I)
_LEGAL = re.compile(r"(?:[\s,]+(?:pvt\.?|private|ltd\.?|limited|llc|l\.l\.c\.?|inc\.?|co\.|corp\.?|llp|fze|fzco|plc|est\.?))+\s*$", re.I)


def is_heading(text: str) -> bool:
    words = [w for w in tokens(text) if len(w) > 1]
    return not words or all(w in NON_MERCHANT for w in words)


def excluded(text: str) -> bool:
    low = text.lower()
    if any(p in low for p in _NOISE) or text[0].isdigit():
        return True
    if sum(c.isdigit() for c in text) / len(text) > 0.5:
        return True
    for p in (_MONEY, _KEY_VALUE, _STORE_LINE, _STREET, _STREET_WORD, _PLACE, _ID_NOISE):
        if p.search(text):
            return True
    return is_heading(text)


def clean_merchant(raw: str) -> str:
    s = _STORE_SUFFIX.sub("", raw.strip()).strip()
    s2 = _LEGAL.sub("", s).strip()
    s = s2 or s
    s = re.sub(r"^[^\w]+|[^\w)]+$", "", s).strip()
    return re.sub(r"\s{2,}", " ", s)


@dataclass
class Line:
    text: str
    confidence: float = 0.8
    top: int = 0
    height: int = 0
    words: list = field(default_factory=list)  # (text, left, top, width, height)


def merchant_score(line: Line, rel_top: float, median_height: float) -> float:
    text = line.text.strip()
    if len(text) < 4 or excluded(text):
        return 0.0
    s = 0.35 * (1 - rel_top / 0.25) if rel_top <= 0.25 else 0.0
    s += min(1.0, line.confidence) * 0.30
    if median_height and line.height >= 1.2 * median_height:
        s += 0.15
    letters = [c for c in text if c.isalpha()]
    if len(letters) >= 2 and sum(c.isupper() for c in letters) / len(letters) >= 0.85:
        s += 0.20
    elif sum(w[:1].isupper() for w in text.split()) / max(1, len(text.split())) >= 0.6:
        s += 0.10
    s += 0.10 if len(text) <= 30 else 0.04 if len(text) <= 60 else 0
    if len(letters) / len(text) >= 0.75:
        s += 0.05
    return s


# ---------------------------------------------------------------- one pass

INTEGER_OK = {"GRAND_TOTAL", "TOTAL", "SUBTOTAL", "TENDERED", "CHANGE"}
_LEAD_QTY = re.compile(r"^(\d{1,2})\s+(?=[^\W\d_])")
_X_QTY = re.compile(r"(?i)(?:^|\s)(?:x\s?(\d{1,3})|(\d{1,3})\s?x)(?=\s|$)")


@dataclass
class AmountLine:
    kind: str
    text: str
    value: Decimal
    index: int


@dataclass
class Item:
    name: str
    quantity: int | None
    amount: Decimal


@dataclass
class Parsed:
    lines: list[Line]
    amounts: list[AmountLine]
    items: list[Item]
    merchant: tuple[str, str, float] | None  # cleaned, raw, score
    currency: tuple[str | None, float]
    mean_confidence: float

    def text(self) -> str:
        return "\n".join(l.text for l in self.lines)

    def of(self, *kinds) -> list[AmountLine]:
        return [a for a in self.amounts if a.kind in kinds]


def amount_line(text: str, index: int) -> AmountLine | None:
    found = find_amounts(text)
    if not found:
        return None
    kind = classify(text)
    chosen = next((a for a in reversed(found) if a.two_decimals), None)
    if chosen is None and kind in INTEGER_OK:
        last = found[-1]
        norm = normalize(text)
        masked = last.start > 0 and norm[last.start - 1] in "*#xX"
        if not masked and last.value > 0 and len(str(int(last.value))) <= 6:
            chosen = last
    return AmountLine(kind, text, chosen.value, index) if chosen else None


def item_from(line: AmountLine) -> Item | None:
    text = normalize(line.text)
    amounts = find_amounts(text)
    end = len(text)
    for a in reversed(amounts):
        between = text[a.end:end]
        if between.strip() and not re.fullmatch(r"[xX@*]?", between.strip()):
            break
        end = a.start
    qty = None
    x = _X_QTY.search(text)
    if x and x.start() < end + 4:
        qty = int(x[1] or x[2])
        end = min(end, x.start())
    name = re.sub(r"[\s:.\-@*]+$", "", text[:end]).strip()
    lead = _LEAD_QTY.match(name)
    if lead:
        qty = int(lead[1])
        name = name[lead.end():].strip()
    if sum(c.isalpha() for c in name) < 2:
        return None
    decimals = [a for a in amounts if a.two_decimals]
    if len(decimals) >= 2:
        rate = decimals[-2].value
        if rate > 0 and line.value % rate == 0 and 0 < line.value / rate <= 99:
            qty = int(line.value / rate)
    return Item(name, qty, line.value)


def read_pass(lines: list[Line]) -> Parsed:
    lines = [l for l in lines if l.text and l.text.strip()]
    text = "\n".join(l.text for l in lines)
    amounts = [a for i, l in enumerate(lines) if (a := amount_line(l.text.strip(), i))]
    end = min((a.index for a in amounts if a.kind in SUMMARY_KINDS), default=len(lines))
    items = []
    for a in amounts:
        if a.index < end and a.kind == "OTHER" and a.value > 0 and not _KEY_VALUE.search(a.text):
            it = item_from(a)
            if it:
                items.append(it)

    heights = sorted(l.height for l in lines if l.height)
    median = heights[len(heights) // 2] if heights else 0
    doc_h = max((l.top for l in lines), default=0) + 150 if any(l.top for l in lines) else len(lines) * 25
    first_amount = next((i for i, l in enumerate(lines) if _MONEY.search(l.text)), len(lines))
    best = None
    for i, l in enumerate(lines):
        rel = (l.top if any(x.top for x in lines) else i * 25) / max(1, doc_h)
        s = merchant_score(l, rel, median) * (0.5 if i > first_amount else 1.0)
        if s > 0 and (best is None or s > best[2]):
            best = (clean_merchant(l.text), l.text.strip(), s)
    mean = sum(l.confidence for l in lines) / len(lines) if lines else 0.0
    return Parsed(lines, amounts, items, best, detect_currency(normalize(text)), mean)
