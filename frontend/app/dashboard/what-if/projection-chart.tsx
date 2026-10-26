"use client";

import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { rupees, rupeesCompact } from "@/features/insights/format";

export interface ProjectionRow {
  month: string;
  before: number;
  after: number;
}

export default function ProjectionChart({ data, height }: { data: ProjectionRow[]; height: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="grad-before" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="hsl(var(--chart-axis))" stopOpacity={0.12} />
            <stop offset="95%" stopColor="hsl(var(--chart-axis))" stopOpacity={0} />
          </linearGradient>
          <linearGradient id="grad-after" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="hsl(var(--chart-spend))" stopOpacity={0.18} />
            <stop offset="95%" stopColor="hsl(var(--chart-spend))" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--chart-grid))" vertical={false} />
        <XAxis dataKey="month" tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }} axisLine={false} tickLine={false} />
        <YAxis
          tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }}
          axisLine={false}
          tickLine={false}
          tickFormatter={(v: number) => rupeesCompact(v)}
          width={56}
        />
        <Tooltip
          formatter={(v: unknown, name: unknown) => {
            const lbl = name === "before" ? "Current trajectory" : "With this decision";
            return `${lbl}: ${rupees(Number(v))}`;
          }}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid hsl(var(--chart-grid))" }}
          labelStyle={{ color: "hsl(var(--foreground))", fontWeight: 600 }}
        />
        <Legend
          iconType="circle"
          iconSize={8}
          formatter={(v) => (v === "before" ? "Current trajectory" : "With this decision")}
          wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
        />
        <Area type="monotone" dataKey="before" stroke="hsl(var(--chart-axis))" strokeWidth={2} fill="url(#grad-before)" dot={false} />
        <Area type="monotone" dataKey="after" stroke="hsl(var(--chart-spend))" strokeWidth={2} fill="url(#grad-after)" dot={false} />
      </AreaChart>
    </ResponsiveContainer>
  );
}
