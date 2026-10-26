"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, FileSpreadsheet, Loader2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/data-table";
import { useAnnounce } from "@/components/ui/announcer";
import { useImportCsv } from "@/features/transactions/hooks";
import type { BulkImportResult } from "@/features/transactions/types";
import { ApiError } from "@/lib/api";
import { cn } from "@/lib/utils";
import { toast } from "@/components/ui/toast";
import { PREVIEW_ROWS, ROLE_LABEL, previewCsv, validateCsvFile, type ColumnRole, type CsvPreview } from "./csv-preview";

const BOM = "﻿";
const ERRORS_SHOWN = 8;

const formatSize = (bytes: number) => (bytes < 1024 ? `${bytes} B` : `${Math.round(bytes / 1024)} KB`);

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });

interface Selected {
  file: File;
  preview: CsvPreview;
}

/** Pick a statement, check the first rows, import, then show what happened. */
export function CsvImport() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [selected, setSelected] = useState<Selected | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const importCsv = useImportCsv();
  const announce = useAnnounce();

  const choose = async (file: File | undefined) => {
    if (!file) return;
    importCsv.reset();
    const invalid = validateCsvFile(file);
    if (invalid) {
      setSelected(null);
      setFileError(invalid);
      return;
    }
    const text = await file.text();
    // the server reads the first header byte literally, so send the file without a BOM
    const upload = text.startsWith(BOM) ? new File([text.slice(1)], file.name, { type: file.type || "text/csv" }) : file;
    setFileError(null);
    setSelected({ file: upload, preview: previewCsv(text) });
  };

  const runImport = () => {
    if (!selected) return;
    importCsv.mutate(selected.file, {
      onSuccess: (result) => {
        announce(`Import finished. ${result.imported} imported, ${result.skipped} skipped.`);
        toast({
          title: `${result.imported.toLocaleString("en-IN")} transactions added`,
          description: result.skipped ? `${result.skipped} rows were skipped.` : undefined,
          tone: result.imported > 0 ? "success" : "error",
        });
      },
      onError: () => announce("Import failed.", "assertive"),
    });
  };

  const startOver = () => {
    importCsv.reset();
    setSelected(null);
    setFileError(null);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  if (importCsv.data) return <ImportSummary result={importCsv.data} onAnother={startOver} />;

  return (
    <div className="space-y-6">
      <label
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          void choose(e.dataTransfer.files?.[0]);
        }}
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-6 py-10 text-center transition-colors focus-within:ring-2 focus-within:ring-ring",
          dragging ? "border-primary bg-band-blue" : "border-input hover:bg-chrome"
        )}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          aria-describedby="csv-hint"
          onChange={(e) => {
            void choose(e.target.files?.[0]);
            e.target.value = "";
          }}
        />
        <Upload className="h-6 w-6 text-muted-foreground" strokeWidth={1.75} aria-hidden="true" />
        <span className="text-sm font-medium text-foreground">
          {selected ? "Choose a different file" : "Choose a CSV file or drop it here"}
        </span>
        <span id="csv-hint" className="text-caption">
          Bank statement export, up to 5 MB. Nothing is saved until you press Import.
        </span>
      </label>

      {fileError && (
        <p role="alert" className="flex items-start gap-2 rounded-md bg-warning-soft px-4 py-3 text-sm text-warning">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {fileError}
        </p>
      )}

      {selected && (
        <Card padding="none" className="overflow-hidden">
          <div className="p-5 sm:p-6">
            <CardHeader
              as="h2"
              icon={<FileSpreadsheet />}
              title={selected.file.name}
              description={`${formatSize(selected.file.size)} · first ${PREVIEW_ROWS} rows shown`}
            />
            <DetectedColumns preview={selected.preview} />
          </div>

          {selected.preview.rows.length > 0 && (
            <Table containerClassName="border-t border-border" className="text-sm">
              <TableCaption srOnly>First {PREVIEW_ROWS} rows of {selected.file.name}</TableCaption>
              <TableHeader>
                <TableRow>
                  {selected.preview.headers.map((h, i) => (
                    <TableHead key={i} className="whitespace-nowrap">
                      {h || `Column ${i + 1}`}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {selected.preview.rows.map((row, r) => (
                  <TableRow key={r}>
                    {selected.preview.headers.map((_, c) => (
                      <TableCell key={c} className="max-w-[16rem] truncate whitespace-nowrap">
                        {row[c] ?? ""}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          <div className="flex flex-col gap-3 border-t border-border p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
            {selected.preview.problem ? (
              <p className="flex items-start gap-2 text-sm text-warning">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                {selected.preview.problem}
              </p>
            ) : (
              <p className="text-supporting">Looks readable. Rows we can&apos;t read are skipped and listed after the import.</p>
            )}
            <Button onClick={runImport} disabled={!!selected.preview.problem || importCsv.isPending} className="shrink-0">
              {importCsv.isPending ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Upload aria-hidden="true" />}
              {importCsv.isPending ? "Importing" : "Import"}
            </Button>
          </div>

          {importCsv.isError && (
            <p role="alert" className="border-t border-border bg-warning-soft px-5 py-3 text-sm text-warning sm:px-6">
              {importCsv.error instanceof ApiError && importCsv.error.status === 429
                ? "You've imported a lot in the last hour. Try again later."
                : "The import didn't go through. Your file wasn't saved; try again."}
            </p>
          )}
        </Card>
      )}
    </div>
  );
}

function DetectedColumns({ preview }: { preview: CsvPreview }) {
  const entries = (Object.entries(preview.columns) as [ColumnRole, number][]).filter(([role]) => role !== "type");
  if (entries.length === 0) return null;
  return (
    <div className="mt-4">
      <h3 className="text-overline mb-2">Columns we recognised</h3>
      <ul className="flex flex-wrap gap-2">
        {entries.map(([role, index]) => (
          <li key={role} className="rounded-md bg-chrome px-2.5 py-1 text-xs text-muted-foreground">
            <span className="font-medium text-foreground">{ROLE_LABEL[role]}</span> · {preview.headers[index]}
          </li>
        ))}
      </ul>
    </div>
  );
}

function ImportSummary({ result, onAnother }: { result: BulkImportResult; onAnother: () => void }) {
  const ok = result.imported > 0;
  return (
    <Card as="section" aria-labelledby="import-result-title">
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className={cn(
            "flex h-9 w-9 shrink-0 items-center justify-center rounded-md",
            ok ? "bg-positive-soft text-positive" : "bg-warning-soft text-warning"
          )}
        >
          {ok ? <CheckCircle2 className="h-5 w-5" /> : <AlertTriangle className="h-5 w-5" />}
        </span>
        <div className="min-w-0">
          <h2 id="import-result-title" className="text-headline">
            {ok ? "Import finished" : "Nothing was imported"}
          </h2>
          {result.firstTransactionDate && result.lastTransactionDate && (
            <p className="mt-1 text-supporting">
              Transactions from {formatDate(result.firstTransactionDate)} to {formatDate(result.lastTransactionDate)}.
            </p>
          )}
        </div>
      </div>

      <dl className="mt-6 grid grid-cols-3 gap-4">
        <Stat label="Imported" value={result.imported} />
        <Stat label="Skipped" value={result.skipped} />
        <Stat label="Rows read" value={result.totalRows} />
      </dl>

      {result.errors.length > 0 && (
        <div className="mt-6">
          <h3 className="text-title">Why rows were skipped</h3>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-supporting">
            {result.errors.slice(0, ERRORS_SHOWN).map((e, i) => (
              <li key={i}>{e}</li>
            ))}
          </ul>
          {result.errors.length > ERRORS_SHOWN && (
            <p className="mt-1 text-caption">and {result.errors.length - ERRORS_SHOWN} more</p>
          )}
        </div>
      )}

      <div className="mt-6 flex flex-wrap gap-2">
        <Button asChild>
          <Link href="/dashboard">Go to Today</Link>
        </Button>
        {ok && (
          <Button variant="outline" asChild>
            <Link href="/dashboard/transactions">View transactions</Link>
          </Button>
        )}
        <Button variant="ghost" onClick={onAnother}>
          Import another file
        </Button>
      </div>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="stat-label">{label}</dt>
      <dd className="stat-value-sm mt-1">{value.toLocaleString("en-IN")}</dd>
    </div>
  );
}
