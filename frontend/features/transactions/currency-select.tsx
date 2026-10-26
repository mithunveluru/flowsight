"use client";

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { currencyName, currencyOptions } from "./currency";

/** Currency picker; put it inside a <Field> so the label and errors are wired. */
export function CurrencySelect({
  value,
  onChange,
  detected,
  placeholder = "Choose currency",
  className,
}: {
  value: string;
  onChange: (code: string) => void;
  // a code read from a receipt, listed first when it is not a common one
  detected?: string | null;
  placeholder?: string;
  className?: string;
}) {
  const options = currencyOptions(detected, value);
  return (
    <Select value={value || undefined} onValueChange={onChange}>
      <SelectTrigger className={className}>
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((code) => (
          <SelectItem key={code} value={code}>
            <span className="font-medium">{code}</span>
            <span className="ml-2 text-muted-foreground">{currencyName(code)}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
