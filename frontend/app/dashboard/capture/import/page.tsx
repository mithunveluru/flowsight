import { CsvImport } from "./csv-import";

export default function ImportStatementPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-headline sm:text-display">Import statement</h1>
        <p className="mt-1.5 text-supporting">
          Upload the CSV export from your bank. You see the first rows before anything is saved.
        </p>
      </div>
      <CsvImport />
    </div>
  );
}
