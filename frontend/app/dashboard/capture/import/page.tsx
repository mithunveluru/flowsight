import { TitleRow } from "@/components/layout/title-row";
import { CsvImport } from "./csv-import";

export default function ImportStatementPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-sp-giant">
      <TitleRow
        title="Import statement"
        description="Upload the CSV export from your bank. You see the first rows before anything is saved."
      />
      <CsvImport />
    </div>
  );
}
