import { test } from "node:test";
import assert from "node:assert/strict";
import { detectColumns, parseCsv, previewCsv, validateCsvFile } from "../csv-preview.ts";

test("parses quoted fields, escaped quotes, CRLF and a byte-order mark", () => {
  const text = '\uFEFFDate,Narration,Amount\r\n2026-10-01,"Swiggy, order ""42""",450\r\n\r\n2026-10-02,"multi\nline",99';
  assert.deepEqual(parseCsv(text), [
    ["Date", "Narration", "Amount"],
    ["2026-10-01", 'Swiggy, order "42"', "450"],
    ["2026-10-02", "multi\nline", "99"],
  ]);
  assert.equal(parseCsv(text, 2).length, 2);
});

test("recognises the same header names as the server", () => {
  assert.deepEqual(detectColumns(["Txn Date", "Particulars", "Withdrawal Amt", "Deposit Amt", "Closing Balance"]), {
    date: 0,
    description: 1,
    debit: 2,
    credit: 3,
    balance: 4,
  });
  assert.deepEqual(detectColumns(["When", "What"]), {});
});

test("preview shows five rows and explains files the import cannot read", () => {
  const rows = Array.from({ length: 9 }, (_, i) => `2026-10-0${i + 1},Item ${i},${i * 10}`).join("\n");
  const ok = previewCsv(`Date,Description,Amount\n${rows}`);
  assert.equal(ok.rows.length, 5);
  assert.equal(ok.problem, null);

  assert.match(previewCsv("When,Description,Amount\nx,y,1").problem, /date column/);
  assert.match(previewCsv("Date,Thing,Amount\nx,y,1").problem, /description column/);
  assert.match(previewCsv("Date,Description,Notes\nx,y,z").problem, /amount column/);
  assert.equal(previewCsv("Date,Description,Balance\n2026-10-01,y,100").problem, null);
  assert.match(previewCsv("Date,Description,Amount\n").problem, /no transactions/);
  assert.match(previewCsv("").problem, /empty/);
});

test("rejects files the server would refuse before uploading them", () => {
  assert.equal(validateCsvFile({ name: "statement.CSV", size: 2048 }), null);
  assert.match(validateCsvFile({ name: "statement.xlsx", size: 2048 }), /\.csv/);
  assert.match(validateCsvFile({ name: "big.csv", size: 6 * 1024 * 1024 }), /5 MB/);
  assert.match(validateCsvFile({ name: "empty.csv", size: 0 }), /empty/);
});
