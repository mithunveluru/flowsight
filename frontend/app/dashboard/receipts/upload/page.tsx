"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  FileImage,
  Loader2,
  ScanLine,
  Upload,
  XCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { receiptApi } from "@/features/receipts/api";
import { cn } from "@/lib/utils";

type UploadState = "idle" | "selected" | "uploading" | "error";

const ACCEPTED = "image/jpeg,image/png,image/webp,image/tiff";
const MAX_MB = 5;

export default function ReceiptUploadPage() {
  const router = useRouter();
  const [uploadState, setUploadState] = useState<UploadState>("idle");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  const handleFile = useCallback((file: File) => {
    if (!file.type.startsWith("image/")) {
      setErrorMsg("Only image files are supported (JPEG, PNG, WebP, TIFF).");
      return;
    }
    if (file.size > MAX_MB * 1024 * 1024) {
      setErrorMsg(`File is too large. Maximum size is ${MAX_MB} MB.`);
      return;
    }
    setErrorMsg(null);
    setSelectedFile(file);
    setPreview(URL.createObjectURL(file));
    setUploadState("selected");
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      const file = e.dataTransfer.files?.[0];
      if (file) handleFile(file);
    },
    [handleFile]
  );

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
    e.target.value = "";
  };

  const processReceipt = async () => {
    if (!selectedFile) return;
    setUploadState("uploading");
    setErrorMsg(null);

    try {
      const data = await receiptApi.upload(selectedFile);
      // Always navigate to the review screen — user must confirm before a transaction is saved
      router.push(`/dashboard/receipts/${data.id}/review`);
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Upload failed");
      setUploadState("error");
    }
  };

  const reset = () => {
    setUploadState("idle");
    setSelectedFile(null);
    setPreview(null);
    setErrorMsg(null);
  };

  return (
    <div className="max-w-2xl animate-fade-in">
      <Link
        href="/dashboard/receipts"
        className="mb-6 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to receipts
      </Link>

      <div className="mb-6">
        <h1 className="text-xl font-semibold text-slate-900">Scan receipt</h1>
        <p className="mt-1 text-sm text-slate-500">
          Upload a photo of your receipt. FlowSight will extract the merchant,
          amount, and date automatically using OCR.
        </p>
      </div>

      {/* Upload zone */}
      {uploadState === "idle" || uploadState === "error" ? (
        <DropZone
          isDragging={isDragging}
          onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onFileChange={handleInputChange}
          accepted={ACCEPTED}
          error={errorMsg}
        />
      ) : null}

      {/* File selected — preview + process */}
      {(uploadState === "selected" || uploadState === "uploading") && selectedFile && preview && (
        <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
          <div className="relative aspect-[4/3] max-h-72 bg-slate-50 overflow-hidden">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={preview} alt="Receipt preview" className="w-full h-full object-contain" />
          </div>
          <div className="p-4 flex items-center justify-between border-t border-slate-200">
            <div>
              <p className="text-sm font-medium text-slate-900">{selectedFile.name}</p>
              <p className="text-xs text-slate-400 mt-0.5">
                {(selectedFile.size / 1024).toFixed(0)} KB
              </p>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={reset} disabled={uploadState === "uploading"}>
                Change
              </Button>
              <Button size="sm" onClick={processReceipt} disabled={uploadState === "uploading"}>
                {uploadState === "uploading" ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Analyzing...
                  </>
                ) : (
                  <>
                    <ScanLine className="h-4 w-4" />
                    Process receipt
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Upload guidance */}
      {uploadState === "idle" && (
        <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-4">
          <p className="text-xs font-medium text-slate-700 mb-2">For best results:</p>
          <ul className="space-y-1 text-xs text-slate-500">
            <li>• Ensure the receipt is flat and well-lit</li>
            <li>• Capture the entire receipt including the total amount</li>
            <li>• Avoid shadows and glare on the text</li>
            <li>• Supported formats: JPEG, PNG, WebP, TIFF — max {MAX_MB} MB</li>
          </ul>
        </div>
      )}
    </div>
  );
}

function DropZone({
  isDragging,
  onDragOver,
  onDragLeave,
  onDrop,
  onFileChange,
  accepted,
  error,
}: {
  isDragging: boolean;
  onDragOver: (e: React.DragEvent) => void;
  onDragLeave: () => void;
  onDrop: (e: React.DragEvent) => void;
  onFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  accepted: string;
  error: string | null;
}) {
  return (
    <div>
      <label
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed p-12 transition-colors",
          isDragging
            ? "border-blue-400 bg-blue-50"
            : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50"
        )}
      >
        <Upload className={cn("h-8 w-8 mb-3", isDragging ? "text-blue-500" : "text-slate-300")} />
        <p className="text-sm font-medium text-slate-700">
          Drag &amp; drop receipt image
        </p>
        <p className="mt-1 text-xs text-slate-400">or click to browse your files</p>
        <input
          type="file"
          accept={accepted}
          className="sr-only"
          onChange={onFileChange}
        />
      </label>
      {error && (
        <p className="mt-2 flex items-center gap-1.5 text-sm text-red-600">
          <XCircle className="h-4 w-4 shrink-0" />
          {error}
        </p>
      )}
    </div>
  );
}

