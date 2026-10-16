"""Render the synthetic receipt golden set: JPEG images plus one ground-truth JSON line each.

Output is a pure function of the seed and the pinned image (fonts, Pillow, libjpeg), so
truth.jsonl, including every image's sha256, can be committed and checked on each run.
"""
import argparse
import hashlib
import json
import math
import random
from dataclasses import asdict, dataclass, field
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SEED = 20261015
CENT = Decimal("0.01")
FONT_DIR = Path("/usr/share/fonts/truetype")
MONO_FONTS = {
    "dejavu": ("dejavu/DejaVuSansMono.ttf", "dejavu/DejaVuSansMono-Bold.ttf"),
    "liberation": ("liberation/LiberationMono-Regular.ttf", "liberation/LiberationMono-Bold.ttf"),
    "freemono": ("freefont/FreeMono.ttf", "freefont/FreeMonoBold.ttf"),
}
# Liberation Mono has no Arabic-Indic digits.
ARABIC_DIGIT_FONTS = ("dejavu", "freemono")
ARABIC_TEXT_FONT = "dejavu/DejaVuSans.ttf"
PERTURBATIONS = ("clean", "background", "rotation", "perspective", "blur", "glare", "crumple", "low_contrast")
# Photographed on a table: the paper sits on a darker background. The rest are cropped scans.
PHOTO_KINDS = {"background", "rotation", "perspective"}

# (currency, number_format, count); 200 receipts in total.
PLAN = (
    ("INR", "indian", 50),
    ("USD", "us", 50),
    ("SAR", "us", 20), ("SAR", "eu", 15), ("SAR", "arabic_indic", 15),
    ("AED", "us", 25), ("AED", "eu", 10), ("AED", "arabic_indic", 15),
)

# printed name -> canonical name, item pool, receipt kind
MERCHANTS = {
    "INR": [
        ("HALDIRAM'S SNACKS PVT LTD", "Haldiram's", "sweets", "restaurant"),
        ("CAFE COFFEE DAY", "Cafe Coffee Day", "cafe", "restaurant"),
        ("AVENUE SUPERMARTS LTD (DMART)", "DMart", "grocery_in", "retail"),
        ("RELIANCE FRESH", "Reliance Fresh", "grocery_in", "retail"),
        ("BARBEQUE NATION", "Barbeque Nation", "restaurant_in", "restaurant"),
        ("APOLLO PHARMACY", "Apollo Pharmacy", "pharmacy_in", "retail"),
        ("MORE SUPERMARKET", "More", "grocery_in", "retail"),
        ("DOMINO'S PIZZA", "Domino's", "pizza_in", "restaurant"),
        ("SARAVANA BHAVAN", "Saravana Bhavan", "restaurant_in", "restaurant"),
        ("CROMA", "Croma", "electronics_in", "retail"),
        ("RELIANCE DIGITAL", "Reliance Digital", "electronics_in", "retail"),
        ("CHAI POINT", "Chai Point", "cafe", "restaurant"),
    ],
    "USD": [
        ("STARBUCKS COFFEE #12345", "Starbucks", "cafe_us", "restaurant"),
        ("TOTAL WINE & MORE", "Total Wine & More", "wine_us", "retail"),
        ("BILL'S DINER", "Bill's Diner", "diner_us", "restaurant"),
        ("WAL-MART SUPERCENTER", "Walmart", "grocery_us", "retail"),
        ("WALGREENS", "Walgreens", "pharmacy_us", "retail"),
        ("TARGET", "Target", "grocery_us", "retail"),
        ("CHIPOTLE MEXICAN GRILL", "Chipotle", "mexican_us", "restaurant"),
        ("CVS PHARMACY", "CVS", "pharmacy_us", "retail"),
        ("BEST BUY", "Best Buy", "electronics_us", "retail"),
        ("TRADER JOE'S", "Trader Joe's", "grocery_us", "retail"),
    ],
    "SAR": [
        ("PANDA RETAIL CO.", "Panda", "grocery_gulf", "retail", "بنده"),
        ("DANUBE", "Danube", "grocery_gulf", "retail", "الدانوب"),
        ("AL BAIK", "Al Baik", "fastfood_gulf", "restaurant", "البيك"),
        ("JARIR BOOKSTORE", "Jarir Bookstore", "electronics_gulf", "retail", "مكتبة جرير"),
        ("NAHDI PHARMACY", "Nahdi Pharmacy", "pharmacy_gulf", "retail", "صيدلية النهدي"),
        ("TAMIMI MARKETS", "Tamimi Markets", "grocery_gulf", "retail", "أسواق التميمي"),
        ("KUDU", "Kudu", "fastfood_gulf", "restaurant", "كودو"),
    ],
    "AED": [
        ("LULU HYPERMARKET", "Lulu Hypermarket", "grocery_gulf", "retail", "لولو هايبرماركت"),
        ("CARREFOUR", "Carrefour", "grocery_gulf", "retail", "كارفور"),
        ("SPINNEYS", "Spinneys", "grocery_gulf", "retail", "سبينس"),
        ("UNION COOP", "Union Coop", "grocery_gulf", "retail", "الاتحاد التعاونية"),
        ("ASTER PHARMACY", "Aster Pharmacy", "pharmacy_gulf", "retail", "صيدلية أستر"),
        ("SHARAF DG", "Sharaf DG", "electronics_gulf", "retail", "شرف دي جي"),
        ("ENOC", "ENOC", "fuel_gulf", "retail", "اينوك"),
    ],
}

ITEMS = {
    "sweets": [("Kaju Katli 500g", 475), ("Soan Papdi", 120), ("Aloo Bhujia 400g", 95), ("Rasgulla Tin", 210), ("Raj Kachori", 160)],
    "cafe": [("Cappuccino", 180), ("Cafe Latte", 190), ("Masala Chai", 60), ("Veg Puff", 75), ("Chocolate Muffin", 110)],
    "grocery_in": [("Aashirvaad Atta 5kg", 265), ("Amul Butter 500g", 285), ("Toor Dal 1kg", 165), ("Basmati Rice 5kg", 690),
                   ("Tata Salt 1kg", 28), ("Fortune Oil 1L", 155), ("Maggi 12 Pack", 168), ("Surf Excel 1kg", 140)],
    "restaurant_in": [("Paneer Tikka", 280), ("Butter Naan", 60), ("Dal Makhani", 240), ("Veg Biryani", 260),
                      ("Masala Dosa", 120), ("Filter Coffee", 45), ("Gulab Jamun", 90)],
    "pharmacy_in": [("Dolo 650 Strip", 32), ("Vicks Vaporub", 145), ("ORS Sachet", 22), ("Dettol 500ml", 189), ("Crocin Advance", 30)],
    "pizza_in": [("Farmhouse Pizza M", 459), ("Garlic Breadsticks", 129), ("Choco Lava Cake", 109), ("Pepsi 500ml", 60)],
    "electronics_in": [("Samsung 55in TV", 54990), ("Boat Earbuds", 1499), ("HP Laptop 15s", 48990), ("Philips Trimmer", 1795),
                       ("LG Microwave", 12490), ("USB-C Cable", 399)],
    "cafe_us": [("Grande Latte", 5.45), ("Cold Brew", 4.95), ("Butter Croissant", 3.75), ("Egg Bites", 5.25), ("Chai Tea Latte", 5.15)],
    "wine_us": [("Cabernet 750ML", 19.99), ("Pinot Noir 750ML", 24.99), ("IPA 6PK", 11.49), ("Prosecco 750ML", 14.99), ("Bourbon 750ML", 34.99)],
    "diner_us": [("Burger Deluxe", 12.00), ("Club Sandwich", 11.50), ("Pancake Stack", 9.25), ("Coffee", 2.50), ("Milkshake", 6.75)],
    "grocery_us": [("Milk 1 Gal", 3.49), ("Bananas", 1.29), ("Bread Whole Wheat", 2.99), ("Eggs Large 12", 4.19),
                   ("Cheddar Cheese", 5.49), ("Orange Juice", 4.79), ("Paper Towels", 12.99)],
    "pharmacy_us": [("Advil 200mg", 10.99), ("Vitamin D3", 8.49), ("Toothpaste", 4.29), ("Band-Aid", 5.79), ("Nyquil", 13.99)],
    "mexican_us": [("Burrito Bowl", 10.75), ("Chips Guac", 5.15), ("Steak Tacos", 11.25), ("Fountain Drink", 2.95)],
    "electronics_us": [("HDMI Cable 6ft", 19.99), ("Wireless Mouse", 29.99), ("65in 4K TV", 1199.99), ("Laptop 14in", 899.00), ("USB Hub", 24.99)],
    "grocery_gulf": [("Almarai Milk 2L", 11.50), ("Basmati Rice 5kg", 42.95), ("Arabic Bread", 3.00), ("Dates 1kg", 28.00),
                     ("Labneh 500g", 13.25), ("Chicken 1kg", 19.75), ("Tea 100 Bags", 16.50)],
    "fastfood_gulf": [("Chicken Meal", 22.00), ("Shrimp Meal", 25.00), ("Garlic Sauce", 1.00), ("Pepsi", 3.00), ("Big Burger", 18.00)],
    "electronics_gulf": [("iPhone 15 128GB", 3699.00), ("Laptop 15in", 2499.00), ("Notebook A4", 9.50), ("Headphones", 349.00),
                         ("Printer Ink", 129.00)],
    "pharmacy_gulf": [("Panadol 24s", 9.75), ("Vitamin C", 32.00), ("Sunscreen SPF50", 79.00), ("Face Wash", 45.50)],
    "fuel_gulf": [("Special 95 Litres", 120.40), ("Car Wash", 35.00), ("Water 1.5L", 2.50), ("Coffee", 12.00)],
}

INR_HEADERS = ("TAX INVOICE", "ORIGINAL FOR RECIPIENT", "DUPLICATE COPY", "RETAIL INVOICE")
CITIES = {
    "INR": [("MG Road, Bengaluru 560001", "29"), ("Linking Road, Mumbai 400050", "27"), ("Connaught Place, New Delhi 110001", "07"),
            ("T Nagar, Chennai 600017", "33")],
    "USD": [("2101 Somerville Rd, Annapolis MD 21401", "6.00"), ("350 5th Ave, New York NY 10118", "8.875"),
            ("233 S Wacker Dr, Chicago IL 60606", "10.25"), ("1 Market St, San Francisco CA 94105", "8.625")],
    "SAR": [("King Fahd Rd, Riyadh", None), ("Tahlia St, Jeddah", None), ("Prince Turki St, Al Khobar", None)],
    "AED": [("Al Wasl Rd, Dubai", None), ("Corniche Rd, Abu Dhabi", None), ("Al Qasba, Sharjah", None)],
}
ARABIC_INDIC = str.maketrans("0123456789,.", "٠١٢٣٤٥٦٧٨٩٬٫")


def money(x) -> Decimal:
    return Decimal(str(x)).quantize(CENT, rounding=ROUND_HALF_UP)


def group_western(intpart: str) -> str:
    out = []
    while len(intpart) > 3:
        out.insert(0, intpart[-3:])
        intpart = intpart[:-3]
    out.insert(0, intpart)
    return ",".join(out)


def group_indian(intpart: str) -> str:
    if len(intpart) <= 3:
        return intpart
    head, tail = intpart[:-3], intpart[-3:]
    groups = []
    while len(head) > 2:
        groups.insert(0, head[-2:])
        head = head[:-2]
    if head:
        groups.insert(0, head)
    return ",".join(groups + [tail])


def format_amount(value: Decimal, number_format: str) -> str:
    """1,234.50 (us) / 1.234,50 (eu) / 1,23,456.00 (indian) / ١٬٢٣٤٫٥٠ (arabic_indic)."""
    sign = "-" if value < 0 else ""
    intpart, frac = f"{abs(value):.2f}".split(".")
    if number_format == "indian":
        return f"{sign}{group_indian(intpart)}.{frac}"
    if number_format == "eu":
        return f"{sign}{group_western(intpart).replace(',', '.')},{frac}"
    us = f"{sign}{group_western(intpart)}.{frac}"
    return us.translate(ARABIC_INDIC) if number_format == "arabic_indic" else us


@dataclass
class Receipt:
    id: str
    currency: str
    number_format: str
    perturbation: str
    merchant_raw: str
    merchant_canonical: str
    date: str
    date_text: str
    items: list
    subtotal: str
    discounts: list
    taxes: list
    tip: str | None
    round_off: str | None
    total: str
    traps: list = field(default_factory=list)
    lines: list = field(default_factory=list)


def fake_digits(rng: random.Random, n: int) -> str:
    return "".join(str(rng.randint(0, 9)) for _ in range(n))


def pick_items(rng, pool, max_items):
    chosen = rng.sample(ITEMS[pool], k=rng.randint(1, min(max_items, len(ITEMS[pool]))))
    items = []
    for name, unit in chosen:
        qty = rng.choice((1, 1, 1, 2, 3)) if money(unit) < 1000 else 1
        unit = money(unit)
        items.append({"name": name, "qty": qty, "unit_price": str(unit), "amount": str(money(unit * qty))})
    return items


def pick_date(rng) -> date:
    return date(2023, 1, 1) + timedelta(days=rng.randint(0, 3 * 365 - 1))


def build_receipt(idx: int, currency: str, number_format: str, perturbation: str, seed: int) -> Receipt:
    rng = random.Random(f"{seed}-{idx}")
    entry = rng.choice(MERCHANTS[currency])
    raw, canonical, pool, kind = entry[:4]
    arabic_name = entry[4] if len(entry) > 4 else None
    fmt = lambda v: format_amount(money(v), number_format)
    d = pick_date(rng)
    items = pick_items(rng, pool, 6)
    subtotal = sum(Decimal(i["amount"]) for i in items)
    traps, discounts, taxes = [], [], []
    tip = round_off = None
    width = 40
    L = []  # (text, style) where style in {"title", "center", "text", "arabic"}

    def row(label, value, sym=""):
        right = f"{sym}{value}"
        L.append((label + " " * max(1, width - len(label) - len(right)) + right, "text"))

    def rule():
        L.append(("-" * width, "text"))

    if currency == "INR":
        if rng.random() < 0.45:
            L.append((rng.choice(INR_HEADERS), "center"))
            traps.append("header_above_merchant")
        L.append((raw, "title"))
        addr, state = rng.choice(CITIES["INR"])
        L.append((addr, "center"))
        L.append((f"Ph: +91 {fake_digits(rng, 10)}", "center"))
        L.append((f"GSTIN: {state}AAAC{chr(65 + rng.randint(0, 25))}{fake_digits(rng, 4)}F1Z{rng.randint(1, 9)}", "center"))
        date_text = rng.choice((d.strftime("%d/%m/%Y"), d.strftime("%d-%m-%Y"), d.strftime("%d %b %Y")))
        L.append((f"Bill No: {rng.randint(1000, 99999)}  Date: {date_text}", "text"))
        L.append((f"Cashier: {rng.choice(['Ravi', 'Priya', 'Anil', 'Meena'])}  Table: {rng.randint(1, 30)}", "text"))
        rule()
        L.append(("Item              Qty    Rate    Amount", "text"))
        for it in items:
            L.append((f"{it['name'][:17]:<17} {it['qty']:>3} {fmt(it['unit_price']):>8} {fmt(it['amount']):>9}", "text"))
        rule()
        row("Sub Total", fmt(subtotal))
        taxable = subtotal
        if rng.random() < 0.3:
            disc = money(subtotal * Decimal(rng.choice(("0.05", "0.10"))))
            discounts.append({"label": "Discount", "amount": str(disc)})
            row("Discount", fmt(-disc))
            taxable -= disc
        rate = Decimal("2.5") if kind == "restaurant" else rng.choice((Decimal("6"), Decimal("9")))
        half = money(taxable * rate / 100)
        for label in ("CGST", "SGST"):
            taxes.append({"label": label, "rate": str(rate), "amount": str(half)})
            row(f"{label} @{rate}%", fmt(half))
        exact = taxable + 2 * half
        total = exact.quantize(Decimal("1"), rounding=ROUND_HALF_UP)
        if total != exact:
            round_off = str(money(total - exact))
            row("Round Off", ("+" if total > exact else "") + fmt(total - exact))
        label = rng.choice(("Grand Total", "Net Payable", "Total"))
        row(label, fmt(total), rng.choice(("", "Rs.")))
        rule()
        pay = rng.choice(("UPI", "Card", "Cash"))
        if pay == "Cash":
            tendered = money(math.ceil(float(total) / 500) * 500)
            row("Cash", fmt(tendered))
            row("Change", fmt(tendered - total))
            traps.append("cash_change_lines")
        else:
            row(f"Paid by {pay}", fmt(total))
        L.append(("Thank you! Visit again", "center"))

    elif currency == "USD":
        L.append((raw, "title"))
        if raw.startswith("TOTAL WINE"):
            traps.append("merchant_contains_label_word")
        if raw.startswith("BILL'S"):
            traps.append("merchant_contains_label_word")
        L.append((f"Store #{rng.randint(10, 9999):04d}", "center"))
        addr, rate = rng.choice(CITIES["USD"])
        L.append((addr, "center"))
        L.append((f"Tel: ({rng.randint(200, 989)}) {rng.randint(200, 999)}-{fake_digits(rng, 4)}", "center"))
        date_text = rng.choice((d.strftime("%m/%d/%Y"), d.strftime("%m/%d/%y")))
        L.append((f"{date_text}  {rng.randint(7, 22):02d}:{rng.randint(0, 59):02d}  Reg {rng.randint(1, 9)}", "text"))
        if kind == "restaurant":
            L.append((f"Server: {rng.choice(['Mike', 'Sarah', 'Jose', 'Kim'])}", "text"))
        rule()
        for it in items:
            label = f"{it['qty']} {it['name']}" if it["qty"] > 1 else it["name"]
            row(label[:28], fmt(it["amount"]))
        if rng.random() < 0.5:
            n_items = sum(i["qty"] for i in items)
            L.append((f"Total Items: {n_items}", "text"))
            traps.append("total_items_line")
        row("SUBTOTAL", fmt(subtotal))
        tax = money(subtotal * Decimal(rate) / 100)
        taxes.append({"label": "Sales Tax", "rate": rate, "amount": str(tax)})
        row(f"SALES TAX {rate}%", fmt(tax))
        total = subtotal + tax
        if kind == "restaurant" and rng.random() < 0.6:
            tip_amt = money(subtotal * Decimal(rng.choice(("0.15", "0.18", "0.20"))))
            row("TOTAL", fmt(total), "$")
            row("TIP", fmt(tip_amt))
            tip = str(tip_amt)
            total += tip_amt
            row("GRAND TOTAL", fmt(total), "$")
            traps.append("tip_after_total")
        else:
            row("TOTAL", fmt(total), "$")
        if rng.random() < 0.5:
            tendered = money(math.ceil(float(total) / 20) * 20)
            row("Total Tendered", fmt(tendered))
            row("CASH", fmt(tendered))
            row("CHANGE", fmt(tendered - total))
            traps.append("tendered_cash_change_lines")
        else:
            row(f"VISA ****{fake_digits(rng, 4)}", fmt(total))
        L.append(("THANK YOU FOR SHOPPING", "center"))

    else:
        vat_rate = Decimal("15") if currency == "SAR" else Decimal("5")
        header = "SIMPLIFIED TAX INVOICE" if currency == "SAR" else "TAX INVOICE"
        if rng.random() < 0.45:
            L.append((header, "center"))
            traps.append("header_above_merchant")
        L.append((raw, "title"))
        L.append((arabic_name, "arabic"))
        addr, _ = rng.choice(CITIES[currency])
        L.append((addr, "center"))
        reg = f"3{fake_digits(rng, 13)}3" if currency == "SAR" else f"100{fake_digits(rng, 12)}"
        L.append((("VAT No: " if currency == "SAR" else "TRN: ") + reg, "center"))
        if not traps:
            L.append((header, "center"))
        date_text = rng.choice((d.isoformat(), d.strftime("%d/%m/%Y")))
        L.append((f"Date: {date_text}  Inv: {rng.randint(10000, 999999)}", "text"))
        rule()
        for it in items:
            label = f"{it['name'][:22]} x{it['qty']}" if it["qty"] > 1 else it["name"][:26]
            row(label, fmt(it["amount"]))
        rule()
        inclusive = rng.random() < 0.5
        if inclusive:
            # Gulf shelf prices usually include VAT; the receipt backs it out.
            total = subtotal
            vat = money(total * vat_rate / (100 + vat_rate))
            subtotal = total - vat
            row("Total Excl. VAT", fmt(subtotal))
        else:
            vat = money(subtotal * vat_rate / 100)
            total = subtotal + vat
            row("Subtotal", fmt(subtotal))
        taxes.append({"label": "VAT", "rate": str(vat_rate), "amount": str(vat)})
        row(f"VAT {vat_rate}%", fmt(vat))
        row("Total Incl. VAT" if inclusive else "Total", fmt(total), f"{currency} ")
        if rng.random() < 0.5:
            tendered = money(math.ceil(float(total) / 50) * 50)
            row("Cash", fmt(tendered), f"{currency} ")
            row("Change", fmt(tendered - total), f"{currency} ")
            traps.append("cash_change_lines")
        else:
            row(f"mada ****{fake_digits(rng, 4)}" if currency == "SAR" else f"Card ****{fake_digits(rng, 4)}", fmt(total))
        L.append(("Thank you for your visit", "center"))

    return Receipt(
        id=f"syn-{idx:04d}", currency=currency, number_format=number_format, perturbation=perturbation,
        merchant_raw=raw, merchant_canonical=canonical, date=d.isoformat(), date_text=date_text,
        items=items, subtotal=str(money(subtotal)), discounts=discounts, taxes=taxes, tip=tip,
        round_off=round_off, total=str(money(total)), traps=traps, lines=L,
    )


def render(receipt: Receipt, rng: random.Random) -> Image.Image:
    font_choices = ARABIC_DIGIT_FONTS if receipt.number_format == "arabic_indic" else tuple(MONO_FONTS)
    regular, bold = MONO_FONTS[rng.choice(font_choices)]
    size = rng.choice((26, 28, 30))
    body = ImageFont.truetype(str(FONT_DIR / regular), size)
    title = ImageFont.truetype(str(FONT_DIR / bold), int(size * 1.35))
    arabic = ImageFont.truetype(str(FONT_DIR / ARABIC_TEXT_FONT), size)
    char_w = body.getlength("M")
    line_h = int(size * 1.45)
    margin = 36
    width = int(char_w * 40) + 2 * margin
    height = margin * 2 + sum(int(line_h * 1.4) if s == "title" else line_h for _, s in receipt.lines)
    paper_tone = rng.randint(240, 252)
    ink = rng.randint(15, 55)
    img = Image.new("L", (width, height), paper_tone)
    draw = ImageDraw.Draw(img)
    y = margin
    for text, style in receipt.lines:
        if style == "title":
            w = title.getlength(text)
            draw.text(((width - w) / 2, y), text, font=title, fill=ink)
            y += int(line_h * 1.4)
            continue
        if style == "arabic":
            w = arabic.getlength(text, direction="rtl")
            draw.text(((width - w) / 2, y), text, font=arabic, fill=ink, direction="rtl")
        elif style == "center":
            w = body.getlength(text)
            draw.text(((width - w) / 2, y), text, font=body, fill=ink)
        else:
            draw.text((margin, y), text, font=body, fill=ink)
        y += line_h
    return img


def solve_linear(a, b):
    """Gaussian elimination in plain floats so results do not depend on the BLAS build."""
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for col in range(n):
        piv = max(range(col, n), key=lambda r: abs(m[r][col]))
        m[col], m[piv] = m[piv], m[col]
        for r in range(n):
            if r != col:
                f = m[r][col] / m[col][col]
                m[r] = [x - f * y for x, y in zip(m[r], m[col])]
    return [m[i][n] / m[i][i] for i in range(n)]


def perspective_coeffs(dst, src):
    """Coefficients mapping output points dst back to input points src (PIL convention)."""
    a, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    return [round(c, 9) for c in solve_linear(a, b)]


def on_background(paper: Image.Image, rng: random.Random, pad: int) -> Image.Image:
    bg = Image.new("L", (paper.width + 2 * pad, paper.height + 2 * pad), rng.randint(70, 140))
    bg.paste(paper, (pad, pad))
    return bg


def perturb(paper: Image.Image, kind: str, rng: random.Random) -> Image.Image:
    pad = 60
    if kind == "low_contrast":
        lo, hi = rng.randint(95, 125), rng.randint(170, 195)
        paper = paper.point(lambda v: lo + v * (hi - lo) // 255)
    if kind == "crumple":
        w, h = paper.size
        amp, period = rng.uniform(4, 8), rng.uniform(180, 320)
        phase_x, phase_y = rng.uniform(0, 6.28), rng.uniform(0, 6.28)
        step = 40
        mesh = []
        for y0 in range(0, h, step):
            for x0 in range(0, w, step):
                x1, y1 = min(x0 + step, w), min(y0 + step, h)

                def src(x, y):
                    dx = amp * math.sin(y / period * 6.28 + phase_x)
                    dy = amp * math.sin(x / period * 6.28 + phase_y)
                    return round(x + dx), round(y + dy)

                quad = (*src(x0, y0), *src(x0, y1), *src(x1, y1), *src(x1, y0))
                mesh.append(((x0, y0, x1, y1), quad))
        paper = paper.transform((w, h), Image.MESH, mesh, Image.BICUBIC, fill=1, fillcolor=245)
        draw = ImageDraw.Draw(paper)
        for _ in range(rng.randint(2, 4)):
            y = rng.randint(0, h)
            draw.line((0, y, w, y + rng.randint(-60, 60)), fill=rng.randint(170, 215), width=2)
    img = on_background(paper, rng, pad) if kind in PHOTO_KINDS else paper
    w, h = img.size
    if kind == "rotation":
        angle = rng.choice((-1, 1)) * rng.uniform(4, 12)
        img = img.rotate(angle, resample=Image.BICUBIC, expand=True, fillcolor=img.getpixel((0, 0)))
    elif kind == "blur":
        img = img.filter(ImageFilter.GaussianBlur(rng.uniform(1.6, 2.4)))
    elif kind == "glare":
        gw, gh = int(w * rng.uniform(0.5, 0.8)), int(h * rng.uniform(0.25, 0.4))
        spot = Image.radial_gradient("L").resize((gw, gh)).point(lambda v: max(0, 255 - v * 2))
        alpha = Image.new("L", (w, h), 0)
        alpha.paste(spot, (rng.randint(0, w - gw), rng.randint(0, h - gh)))
        strength = rng.uniform(0.75, 0.95)
        alpha = alpha.point(lambda v: int(v * strength))
        img = Image.composite(Image.new("L", (w, h), 255), img, alpha)
    elif kind == "perspective":
        k = rng.uniform(0.06, 0.12)
        dst = [(0, 0), (w, 0), (w, h), (0, h)]
        src = [(-w * k, 0), (w * (1 + k), 0), (w, h), (0, h)]
        img = img.transform((w, h), Image.PERSPECTIVE, perspective_coeffs(dst, src), Image.BICUBIC,
                            fillcolor=img.getpixel((0, 0)))
    noise_rng = np.random.default_rng(rng.randint(0, 2**31))
    arr = np.asarray(img, dtype=np.int16) + noise_rng.integers(-6, 7, size=(img.height, img.width), dtype=np.int16)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "L").convert("RGB")


def build_plan(seed: int):
    plan = []
    for currency, number_format, count in PLAN:
        plan.extend((currency, number_format) for _ in range(count))
    kinds = [PERTURBATIONS[i % len(PERTURBATIONS)] for i in range(len(plan))]
    random.Random(seed).shuffle(kinds)
    return [(i + 1, c, nf, k) for i, ((c, nf), k) in enumerate(zip(plan, kinds))]


def generate(out_dir: Path, seed: int = SEED, limit: int | None = None) -> list[dict]:
    (out_dir / "images").mkdir(parents=True, exist_ok=True)
    records = []
    for idx, currency, number_format, kind in build_plan(seed)[:limit]:
        receipt = build_receipt(idx, currency, number_format, kind, seed)
        rng = random.Random(f"{seed}-{idx}-render")
        img = perturb(render(receipt, rng), kind, rng)
        path = out_dir / "images" / f"{receipt.id}.jpg"
        img.save(path, "JPEG", quality=88)
        record = asdict(receipt)
        record.pop("lines")
        record["image"] = f"images/{receipt.id}.jpg"
        record["image_sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
        record["source"] = "synthetic"
        records.append(record)
    with open(out_dir / "truth.jsonl", "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False, sort_keys=True) + "\n")
    return records


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--limit", type=int)
    args = ap.parse_args()
    print(f"generated {len(generate(args.out, args.seed, args.limit))} receipts in {args.out}")
