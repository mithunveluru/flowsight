import { useEffect, useRef, useState } from "react";
import { readImage } from "@tauri-apps/plugin-clipboard-manager";
import {
  PlusCircle,
  Receipt as ReceiptIcon,
  UploadCloud,
  Clipboard,
  Loader2,
  CheckCircle2,
} from "lucide-react";
import { AnimatedSwitch } from "@/components/motion/primitives";
import { ConfidenceBadge } from "@/components/ui/signals";
import {
  CATEGORY_OPTIONS,
  type TransactionCategory,
} from "@/features/transactions/types";
import type { Receipt } from "@/features/receipts/types";
import { cn } from "~/lib/utils";
import { useDensity } from "~/lib/density";
import { ApiError } from "~/lib/api";
import { transactionApi } from "~/api/transactions";
import { receiptApi } from "~/api/receipts";
import { bumpRefresh } from "~/lib/refresh";
import { toast } from "~/components/Toaster";
import { Button, TextField } from "~/components/ui";
import { formatINR, todayISO } from "~/lib/format";

export type CaptureTab = "transaction" | "receipt";

export function CaptureView({ initialTab = "transaction" }: { initialTab?: CaptureTab }) {
  const density = useDensity();
  const [tab, setTab] = useState<CaptureTab>(initialTab);
  return (
    <div
      className={cn(
        "flex h-full flex-col px-4 py-4",
        density === "large" && "mx-auto w-full max-w-xl"
      )}
    >
      <div className="mb-4 flex gap-2">
        <SubTab active={tab === "transaction"} onClick={() => setTab("transaction")} icon={<PlusCircle className="h-3.5 w-3.5" />}>
          Transaction
        </SubTab>
        <SubTab active={tab === "receipt"} onClick={() => setTab("receipt")} icon={<ReceiptIcon className="h-3.5 w-3.5" />}>
          Receipt
        </SubTab>
      </div>
      <AnimatedSwitch viewKey={tab} className="flex-1">
        {tab === "transaction" ? <QuickTxnForm /> : <ReceiptCapture />}
      </AnimatedSwitch>
    </div>
  );
}

function SubTab({
  active,
  onClick,
  icon,
  children,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-1 items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors",
        active
          ? "border-transparent bg-muted text-foreground"
          : "border-border text-muted-foreground hover:text-foreground"
      )}
    >
      {icon}
      {children}
    </button>
  );
}

// -----------------------------------------------------------------------------
// Quick transaction
// -----------------------------------------------------------------------------

function CategorySelect({
  value,
  onChange,
  id,
}: {
  value: string;
  onChange: (v: TransactionCategory | "") => void;
  id?: string;
}) {
  return (
    <select
      id={id}
      className="input-base no-drag appearance-none"
      value={value}
      onChange={(e) => onChange(e.target.value as TransactionCategory | "")}
    >
      <option value="">Auto-detect</option>
      {CATEGORY_OPTIONS.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

function QuickTxnForm() {
  const [merchant, setMerchant] = useState("");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState<TransactionCategory | "">("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);

  const amountNum = Number(amount);
  const valid = merchant.trim().length > 0 && amount.trim().length > 0 && amountNum > 0;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid) return;
    setSaving(true);
    try {
      await transactionApi.create({
        amount: amountNum,
        transactionDate: todayISO(),
        description: merchant.trim(),
        merchant: merchant.trim(),
        type: "DEBIT",
        category: category || undefined,
        notes: notes.trim() || undefined,
      });
      toast.success(`Saved ${formatINR(amountNum)}`);
      setMerchant("");
      setAmount("");
      setCategory("");
      setNotes("");
      bumpRefresh();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not save");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <TextField
        label="Merchant"
        placeholder="e.g. Blue Tokai Coffee"
        value={merchant}
        onChange={(e) => setMerchant(e.target.value)}
        autoFocus
      />
      <TextField
        label="Amount (₹)"
        type="number"
        inputMode="decimal"
        min="0"
        step="0.01"
        placeholder="0"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <div className="space-y-1.5">
        <span className="text-[11px] font-medium text-muted-foreground">Category</span>
        <CategorySelect value={category} onChange={setCategory} />
      </div>
      <TextField
        label="Notes (optional)"
        placeholder="Add a note"
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
      />
      <Button type="submit" loading={saving} disabled={!valid} className="w-full">
        Save transaction
      </Button>
    </form>
  );
}

// -----------------------------------------------------------------------------
// Receipt OCR capture
// -----------------------------------------------------------------------------

type ReceiptStage = "idle" | "uploading" | "processing" | "review" | "blocked";

function ReceiptCapture() {
  const [stage, setStage] = useState<ReceiptStage>("idle");
  const [dragging, setDragging] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  // Review form fields (prefilled from OCR extraction).
  const [merchant, setMerchant] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayISO());
  const [category, setCategory] = useState<TransactionCategory | "">("");
  const [confirming, setConfirming] = useState(false);

  async function pollUntilDone(id: string): Promise<Receipt> {
    for (let i = 0; i < 25; i++) {
      const r = await receiptApi.getById(id);
      if (r.status === "COMPLETED" || r.status === "FAILED") return r;
      await new Promise((res) => setTimeout(res, 1200));
    }
    return receiptApi.getById(id);
  }

  async function handleFile(file: File | Blob, fileName = "receipt.png") {
    setErrorMsg(null);
    setStage("uploading");
    try {
      const uploaded = await receiptApi.upload(file, fileName);
      setStage("processing");
      const done =
        uploaded.status === "COMPLETED" || uploaded.status === "FAILED"
          ? uploaded
          : await pollUntilDone(uploaded.id);
      setReceipt(done);
      if (done.status === "FAILED") {
        setErrorMsg(done.errorMessage || "Could not read this receipt. Enter the details manually.");
      }
      const ex = done.extraction;
      setMerchant(ex?.merchant ?? "");
      setAmount(ex?.amount != null ? String(ex.amount) : "");
      setDate(ex?.date ?? todayISO());
      setCategory("");
      setStage("review");
    } catch (err) {
      if (err instanceof ApiError && err.status === 402) {
        setErrorMsg(err.message || "Receipt quota reached. Increase your limit to scan more.");
        setStage("blocked");
      } else {
        setErrorMsg(err instanceof Error ? err.message : "Upload failed");
        setStage("idle");
      }
    }
  }

  // Paste image from the clipboard via the OS clipboard (screenshots etc.).
  async function pasteFromClipboard() {
    try {
      const img = await readImage();
      const size = await img.size();
      const rgba = await img.rgba();
      const canvas = document.createElement("canvas");
      canvas.width = size.width;
      canvas.height = size.height;
      const ctx = canvas.getContext("2d");
      if (!ctx) throw new Error("Canvas unavailable");
      ctx.putImageData(new ImageData(new Uint8ClampedArray(rgba), size.width, size.height), 0, 0);
      const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, "image/png"));
      if (!blob) throw new Error("Empty clipboard image");
      await handleFile(blob, "clipboard.png");
    } catch {
      toast.error("No image found on the clipboard");
    }
  }

  // Also accept a standard web paste (Ctrl/Cmd+V) of an image.
  useEffect(() => {
    const onPaste = (e: ClipboardEvent) => {
      const item = Array.from(e.clipboardData?.items ?? []).find((i) =>
        i.type.startsWith("image/")
      );
      const file = item?.getAsFile();
      if (file) {
        e.preventDefault();
        void handleFile(file, file.name || "pasted.png");
      }
    };
    window.addEventListener("paste", onPaste);
    return () => window.removeEventListener("paste", onPaste);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function reset() {
    setStage("idle");
    setReceipt(null);
    setErrorMsg(null);
  }

  async function confirm(e: React.FormEvent) {
    e.preventDefault();
    if (!receipt) return;
    const amountNum = Number(amount);
    if (!merchant.trim() || !(amountNum > 0)) {
      toast.error("Merchant and a valid amount are required");
      return;
    }
    setConfirming(true);
    try {
      await receiptApi.confirm(receipt.id, {
        merchant: merchant.trim(),
        amount: amountNum,
        date,
        category: category || undefined,
      });
      toast.success("Receipt saved");
      bumpRefresh();
      reset();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not confirm");
    } finally {
      setConfirming(false);
    }
  }

  if (stage === "uploading" || stage === "processing") {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
        <Loader2 className="h-6 w-6 animate-spin text-signal" />
        <p className="text-sm font-medium text-foreground">
          {stage === "uploading" ? "Uploading receipt…" : "Reading receipt…"}
        </p>
        <p className="text-xs text-muted-foreground">Running the OCR pipeline</p>
      </div>
    );
  }

  if (stage === "blocked") {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <ReceiptIcon className="h-7 w-7 text-warning" />
        <p className="text-sm font-medium text-foreground">Receipt quota reached</p>
        <p className="text-xs text-muted-foreground">{errorMsg}</p>
        <Button variant="secondary" onClick={reset}>
          Back
        </Button>
      </div>
    );
  }

  if (stage === "review" && receipt) {
    const conf = receipt.extraction?.confidence;
    const sev = receipt.requiresConfirmation ? "MEDIUM" : "LOW";
    return (
      <form onSubmit={confirm} className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <CheckCircle2 className="h-4 w-4 text-positive" />
            <p className="text-sm font-medium text-foreground">Review &amp; save</p>
          </div>
          {conf != null && (
            <ConfidenceBadge severity={sev} label={`${Math.round(conf * 100)}% confident`} />
          )}
        </div>
        {errorMsg && (
          <p
            className="rounded-lg px-3 py-2 text-xs"
            style={{ backgroundColor: "hsl(var(--caution-soft))", color: "hsl(var(--caution))" }}
          >
            {errorMsg}
          </p>
        )}
        <TextField label="Merchant" value={merchant} onChange={(e) => setMerchant(e.target.value)} />
        <TextField
          label="Amount (₹)"
          type="number"
          min="0"
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <TextField label="Date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        <div className="space-y-1.5">
          <span className="text-[11px] font-medium text-muted-foreground">Category</span>
          <CategorySelect value={category} onChange={setCategory} />
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="secondary" onClick={reset} className="flex-1">
            Discard
          </Button>
          <Button type="submit" loading={confirming} className="flex-1">
            Save
          </Button>
        </div>
      </form>
    );
  }

  // idle — drop zone
  return (
    <div className="flex h-full flex-col gap-3">
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          const file = e.dataTransfer.files?.[0];
          if (file) void handleFile(file, file.name);
        }}
        onClick={() => fileInput.current?.click()}
        className={cn(
          "flex flex-1 cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-6 text-center transition-colors",
          dragging ? "border-signal bg-signal-soft" : "border-border hover:border-muted-foreground/40"
        )}
      >
        <UploadCloud className={cn("h-7 w-7", dragging ? "text-signal" : "text-muted-foreground")} />
        <p className="text-sm font-medium text-foreground">Drop a receipt</p>
        <p className="text-xs text-muted-foreground">or click to browse · paste with ⌘/Ctrl+V</p>
        {errorMsg && <p className="mt-1 text-xs text-warning">{errorMsg}</p>}
      </div>
      <Button variant="secondary" onClick={pasteFromClipboard} className="w-full">
        <Clipboard className="h-3.5 w-3.5" />
        Paste image from clipboard
      </Button>
      <input
        ref={fileInput}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file, file.name);
          e.target.value = "";
        }}
      />
    </div>
  );
}
