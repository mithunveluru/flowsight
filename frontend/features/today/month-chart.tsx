"use client";

import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatRupees } from "./logic";

export interface MonthBar {
  label: string;
  spend: number;
  // the current, unfinished month
  partial?: boolean;
}

const compact = (v: number) => (v >= 1000 ? `₹${Math.round(v / 1000)}k` : `₹${v}`);

// Loaded with next/dynamic so recharts stays out of the Today bundle.
export default function MonthChart({ data, typical }: { data: MonthBar[]; typical?: number }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--chart-grid))" vertical={false} />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }} axisLine={false} tickLine={false} />
        <YAxis
          width={44}
          tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }}
          axisLine={false}
          tickLine={false}
          tickFormatter={compact}
        />
        <Tooltip
          formatter={(v: unknown) => [formatRupees(Number(v)), "Spent"]}
          contentStyle={{
            fontSize: 12,
            borderRadius: 8,
            border: "1px solid hsl(var(--border))",
            background: "hsl(var(--popover))",
            color: "hsl(var(--popover-foreground))",
          }}
          cursor={{ fill: "hsl(var(--muted) / 0.5)" }}
        />
        {typical != null && (
          <ReferenceLine y={typical} stroke="hsl(var(--muted-foreground))" strokeDasharray="4 4" />
        )}
        <Bar dataKey="spend" radius={[4, 4, 0, 0]} maxBarSize={36}>
          {data.map((d) => (
            <Cell
              key={d.label}
              fill="hsl(var(--chart-spend))"
              fillOpacity={d.partial ? 0.55 : 1}
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
