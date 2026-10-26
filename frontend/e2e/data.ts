const DAY = 86_400_000;

function iso(daysAgo: number): string {
  return new Date(Date.now() - daysAgo * DAY).toISOString().slice(0, 10);
}

const SPEND = [
  ["SHELL PETROL PUMP", 1500],
  ["AMAZON PAY INDIA", 2400],
  ["POS BIGBASKET BLR", 1800],
  ["APOLLO PHARMACY", 650],
  ["UBER INDIA TRIP", 320],
] as const;

/**
 * About four months of statement lines ending yesterday. The only food rows are the two
 * dated 2 and 3 days ago, so "food in the last 30 days" is always 800.
 */
export function statementCsv(): string {
  const rows = ["date,description,amount"];
  for (let d = 1; d <= 120; d++) {
    const [name, base] = SPEND[d % SPEND.length];
    rows.push(`${iso(d)},${name},-${base + (d % 7) * 40}.00`);
    if (d % 30 === 5) rows.push(`${iso(d)},SALARY CREDIT ACME LTD,85000.00`);
    if (d % 30 === 8) rows.push(`${iso(d)},HOUSE RENT TRANSFER,-22000.00`);
    if (d % 30 === 12) rows.push(`${iso(d)},SPOTIFY P0B4,-119.00`);
  }
  rows.push(`${iso(2)},UPI/ZOMATO/food,-500.00`);
  rows.push(`${iso(3)},UPI/SWIGGY/order,-300.00`);
  return rows.join("\n") + "\n";
}

export const FOOD_LAST_30_DAYS = 800;
