"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Camera, FileImage, Infinity as InfinityIcon, Loader2, RotateCw, Upload, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAnnounce } from "@/components/ui/announcer";
import { accountApi } from "@/features/account/api";
import type { ReceiptQuotaInfo } from "@/features/account/types";
import { receiptApi, UploadTimeoutError } from "@/features/receipts/api";
import {
  createObjectUrlSlot,
  progressPercent,
  RECEIPT_ACCEPT,
  UPLOAD_TIMEOUT_MS,
  validateReceiptFile,
} from "@/features/receipts/capture-logic";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";
import { TitleRow } from "@/features/shell-pages/title-row";


const ScanLine = dynamic(() => import("@/features/receipts/scan-line").then((m) => m.ScanLine), { ssr: false });

type Phase =
  | { kind: "idle" }
  | { kind: "sending"; percent: number | null }
  | { kind: "reading"; since: number }
  | { kind: "failed"; title: string; message: string; checkInbox: boolean }
  | { kind: "cancelled"; afterSend: boolean };

export default function ScanReceiptPage() {
  const router = useRouter();
  const announce = useAnnounce();
  const queryClient = useQueryClient();
  const [phase, setPhase] = useState<Phase>({ kind: "idle" });
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [quota, setQuota] = useState<ReceiptQuotaInfo | null>(null);
  const controller = useRef<AbortController | null>(null);
  const sent = useRef(false);
  const slot = useRef(createObjectUrlSlot());
  const pickRef = useRef<HTMLInputElement>(null);
  const cameraRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    accountApi.get().then((a) => setQuota(a.receiptQuota)).catch(() => {});
    const s = slot.current;
    return () => {
      controller.current?.abort();
      s.dispose();
    };
  }, []);

  const start = useCallback(
    async (f: File) => {
      controller.current?.abort();
      const ac = new AbortController();
      controller.current = ac;
      sent.current = false;
      setPhase({ kind: "sending", percent: 0 });
      try {
        const receipt = await receiptApi.uploadWithProgress(f, {
          signal: ac.signal,
          timeoutMs: UPLOAD_TIMEOUT_MS,
          onProgress: (loaded, total) => setPhase({ kind: "sending", percent: progressPercent(loaded, total) }),
          onUploaded: () => {
            sent.current = true;
            setPhase({ kind: "reading", since: Date.now() });
            announce("Uploaded. Reading your receipt.");
          },
        });
        void queryClient.invalidateQueries({ queryKey: ["receipts"] });
        announce("Receipt read. Opening the review.");
        router.push(`/dashboard/receipts/${receipt.id}/review`);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          setPhase({ kind: "cancelled", afterSend: sent.current });
          announce("Upload cancelled.");
          return;
        }
        const next: Phase =
          err instanceof UploadTimeoutError
            ? {
                kind: "failed",
                title: "This is taking longer than expected",
                message: "The server may still finish reading it. Check your Receipt inbox in a minute, or try again.",
                checkInbox: true,
              }
            : err instanceof ApiError
              ? {
                  kind: "failed",
                  title: err.status === 402 || err.status === 429 ? "Receipt scans are paused" : "We could not read this upload",
                  message: err.message,
                  checkInbox: false,
                }
              : {
                  kind: "failed",
                  title: "The upload did not go through",
                  message: "Check your connection and try again. Nothing was saved.",
                  checkInbox: sent.current,
                };
        setPhase(next);
        announce(`${next.title}. ${next.message}`, "assertive");
      }
    },
    [announce, queryClient, router]
  );

  const choose = (f: File | undefined) => {
    if (!f) return;
    const problem = validateReceiptFile(f);
    if (problem) {
      setFileError(problem);
      announce(problem, "assertive");
      return;
    }
    setFileError(null);
    setFile(f);
    setPreview(slot.current.set(f));
    void start(f);
  };

  const reset = () => {
    controller.current?.abort();
    setFile(null);
    setPreview(slot.current.set(null));
    setPhase({ kind: "idle" });
  };

  const busy = phase.kind === "sending" || phase.kind === "reading";
  const blocked = quota != null && !quota.canProcess;

  return (
    <div className="max-w-2xl space-y-sp-giant">
      <TitleRow
        title="Scan receipt"
        description="We read the merchant, total and date into a draft. Nothing is saved until you confirm it."
        actions={quota ? <QuotaPill quota={quota} /> : undefined}
      />

      {blocked && (
        <div role="alert" className="rounded-md border border-warning/25 bg-warning-soft px-4 py-3 text-sm text-warning">
          <p className="font-medium">Receipt scan limit reached</p>
          <p className="mt-0.5 text-xs">
            You have used all {quota!.limit} receipt scans. Your receipts and transactions stay available, and you can{" "}
            <Link href="/dashboard/capture/manual" className="underline underline-offset-2">add a transaction manually</Link>.
          </p>
        </div>
      )}

      <input
        ref={pickRef}
        type="file"
        accept={RECEIPT_ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          choose(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="sr-only"
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => {
          choose(e.target.files?.[0]);
          e.target.value = "";
        }}
      />

      {phase.kind === "idle" ? (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            if (!blocked) choose(e.dataTransfer.files?.[0]);
          }}
          className={cn(
            "flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-10 text-center transition-colors sm:p-12",
            dragging ? "border-primary bg-band-blue" : "border-input bg-canvas"
          )}
        >
          <Upload className={cn("mb-3 h-8 w-8", dragging ? "text-primary" : "text-muted-foreground/60")} aria-hidden="true" />
          <p className="text-sm font-medium text-foreground">Drop a receipt image here</p>
          <p className="mt-1 text-xs text-muted-foreground">JPEG, PNG, WebP, TIFF or BMP, up to 5 MB</p>
          <div className="mt-5 flex flex-wrap justify-center gap-2">
            <Button onClick={() => cameraRef.current?.click()} disabled={blocked} className="md:hidden">
              <Camera aria-hidden="true" />
              Take a photo
            </Button>
            <Button variant="outline" onClick={() => pickRef.current?.click()} disabled={blocked}>
              <FileImage aria-hidden="true" />
              Choose an image
            </Button>
          </div>
          {fileError && (
            <p role="alert" className="mt-4 flex items-center gap-1.5 text-sm font-medium text-warning">
              <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
              {fileError}
            </p>
          )}
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg bg-chrome">
          {preview && (
            <div className="relative flex max-h-72 justify-center overflow-hidden bg-chrome">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={preview} alt="The receipt you chose" className="max-h-72 w-auto object-contain" />
              {phase.kind === "reading" && <ScanLine />}
            </div>
          )}
          <div className="border-t border-border p-4">
            {phase.kind === "sending" && <SendingStatus percent={phase.percent} name={file?.name} />}
            {phase.kind === "reading" && <ReadingStatus since={phase.since} />}
            {phase.kind === "failed" && (
              <div role="alert">
                <p className="flex items-center gap-2 text-sm font-medium text-warning">
                  <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
                  {phase.title}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">{phase.message}</p>
              </div>
            )}
            {phase.kind === "cancelled" && (
              <div>
                <p className="text-sm font-medium text-foreground">Upload cancelled</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {phase.afterSend
                    ? "The image had already reached us, so a draft may still appear in your Receipt inbox. Delete it there if you do not want it."
                    : "Nothing was sent."}
                </p>
              </div>
            )}

            <div className="mt-4 flex flex-wrap gap-2">
              {busy ? (
                <Button variant="outline" onClick={() => controller.current?.abort()}>
                  <X aria-hidden="true" />
                  Cancel
                </Button>
              ) : (
                <>
                  {file && phase.kind !== "cancelled" && (
                    <Button onClick={() => start(file)}>
                      <RotateCw aria-hidden="true" />
                      Try again
                    </Button>
                  )}
                  <Button variant="outline" onClick={reset}>
                    Choose another image
                  </Button>
                  {(phase.kind === "cancelled" ? phase.afterSend : phase.kind === "failed" && phase.checkInbox) && (
                    <Button variant="ghost" asChild>
                      <Link href="/dashboard/receipts">Open Receipt inbox</Link>
                    </Button>
                  )}
                  <Button variant="ghost" asChild>
                    <Link href="/dashboard/capture/manual">Enter it manually</Link>
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {phase.kind === "idle" && (
        <div className="rounded-md bg-chrome p-4">
          <p className="mb-2 text-xs font-medium text-foreground/80">For the best read</p>
          <ul className="list-disc space-y-1 pl-4 text-xs text-muted-foreground">
            <li>Lay the receipt flat in good light, without shadows or glare.</li>
            <li>Keep the whole receipt in the frame, including the total.</li>
            <li>We keep a smaller copy of the image for up to 7 days so you can check the draft against it.</li>
          </ul>
        </div>
      )}
    </div>
  );
}

function SendingStatus({ percent, name }: { percent: number | null; name?: string }) {
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-sm font-medium text-foreground">Uploading</p>
        <span className="text-xs tabular-nums text-muted-foreground">{percent != null ? `${percent}%` : ""}</span>
      </div>
      <div
        role="progressbar"
        aria-label="Upload progress"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
        className="mt-2 h-1.5 overflow-hidden rounded-sm bg-band"
      >
        <div className="h-full origin-left bg-primary transition-transform duration-fast" style={{ width: "100%", transform: `scaleX(${(percent ?? 5) / 100})` }} />
      </div>
      {name && <p className="mt-2 truncate text-xs text-muted-foreground">{name}</p>}
    </div>
  );
}

function ReadingStatus({ since }: { since: number }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  const seconds = Math.max(0, Math.round((now - since) / 1000));
  return (
    <div className="flex items-start gap-2.5">
      <Loader2 className="mt-0.5 h-4 w-4 shrink-0 animate-spin text-muted-foreground" aria-hidden="true" />
      <div>
        <p className="text-sm font-medium text-foreground">Reading your receipt</p>
        <p className="mt-0.5 text-xs text-muted-foreground">
          We read it a few times and check that the total adds up. This usually takes under a minute.
          <span className="ml-1 tabular-nums" aria-hidden="true">{seconds} s</span>
        </p>
      </div>
    </div>
  );
}

function QuotaPill({ quota }: { quota: ReceiptQuotaInfo }) {
  if (quota.unlimited) {
    return (
      <Link
        href="/dashboard/settings"
        className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-md bg-band-blue px-2.5 py-1 text-xs font-medium text-primary"
      >
        <InfinityIcon className="h-3 w-3" aria-hidden="true" />
        Unlimited receipt scans
      </Link>
    );
  }
  const pct = (quota.used / Math.max(1, quota.limit)) * 100;
  const tone =
    pct >= 100 ? "border-warning/25 bg-warning-soft text-warning"
    : pct >= 80 ? "border-caution/25 bg-caution-soft text-caution"
    : "border-border bg-chrome text-muted-foreground";
  return (
    <Link
      href="/dashboard/settings"
      className={cn("inline-flex items-center whitespace-nowrap rounded-md border px-2.5 py-1 text-xs font-medium tabular-nums", tone)}
    >
      Receipt scans: {quota.used} of {quota.limit} used
    </Link>
  );
}
