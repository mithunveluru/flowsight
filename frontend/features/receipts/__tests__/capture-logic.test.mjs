import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createObjectUrlSlot,
  MAX_RECEIPT_BYTES,
  progressPercent,
  receiptMimeType,
  validateReceiptFile,
} from "../capture-logic.ts";

test("file checks mirror the backend's types and 5 MB limit", () => {
  assert.equal(validateReceiptFile({ type: "image/jpeg", name: "r.jpg", size: 1000 }), null);
  assert.match(validateReceiptFile({ type: "application/pdf", name: "r.pdf", size: 1000 }), /JPEG, PNG/);
  assert.match(validateReceiptFile({ type: "image/heic", name: "IMG_1.HEIC", size: 1000 }), /HEIC/);
  assert.match(validateReceiptFile({ type: "image/png", name: "r.png", size: MAX_RECEIPT_BYTES + 1 }), /limit is 5 MB/);
  assert.match(validateReceiptFile({ type: "image/png", name: "r.png", size: 0 }), /empty/);
});

test("a file with no reported type is typed from its extension", () => {
  assert.equal(receiptMimeType({ type: "", name: "scan.JPG" }), "image/jpeg");
  assert.equal(receiptMimeType({ type: "", name: "scan.tif" }), "image/tiff");
  assert.equal(receiptMimeType({ type: "", name: "notes.txt" }), null);
  assert.equal(validateReceiptFile({ type: "", name: "scan.png", size: 10 }), null);
});

test("progress is a whole percent, or null while the size is unknown", () => {
  assert.equal(progressPercent(512, 1024), 50);
  assert.equal(progressPercent(2000, 1024), 100);
  assert.equal(progressPercent(10, null), null);
  assert.equal(progressPercent(10, 0), null);
});

test("the object URL slot revokes the previous URL and the last one on dispose", () => {
  const created = [];
  const revoked = [];
  let n = 0;
  const slot = createObjectUrlSlot(
    () => { const u = `blob:${++n}`; created.push(u); return u; },
    (u) => revoked.push(u)
  );
  assert.equal(slot.set({}), "blob:1");
  assert.equal(slot.set({}), "blob:2");
  assert.deepEqual(revoked, ["blob:1"]);
  assert.equal(slot.set(null), null);
  assert.deepEqual(revoked, ["blob:1", "blob:2"]);
  slot.set({});
  slot.dispose();
  slot.dispose();
  assert.deepEqual(revoked, ["blob:1", "blob:2", "blob:3"]);
  assert.equal(slot.get(), null);
  assert.equal(created.length, revoked.length);
});
