"use client";

import { Area, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { rupees, rupeesCompact } from "@/features/insights/format";
import type { FanRow } from "@/features/simulation/fan";

export default function FanChart({ rows, height }: { rows: FanRow[]; height: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={rows} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--chart-grid))" vertical={false} />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }} axisLine={false} tickLine={false} />
        <YAxis
          tick={{ fontSize: 11, fill: "hsl(var(--chart-axis))" }}
          axisLine={false}
          tickLine={false}
          tickFormatter={(v: number) => rupeesCompact(v)}
          width={60}
        />
        <Tooltip
          content={({ active, payload }) => {
            const row = payload?.[0]?.payload as FanRow | undefined;
            if (!active || !row) return null;
            return (
              <div className="rounded-lg border border-border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-elev-2">
                <p className="font-semibold">Month {row.month}</p>
                <p className="tabular-nums">High: {rupees(row.p90)}</p>
                <p className="tabular-nums">Median: {rupees(row.p50)}</p>
                <p className="tabular-nums">Low: {rupees(row.p10)}</p>
                {row.baseP50 != null && <p className="tabular-nums text-muted-foreground">Without: {rupees(row.baseP50)}</p>}
              </div>
            );
          }}
        />
        <Area type="monotone" dataKey="floor" stackId="fan" stroke="none" fill="transparent" isAnimationActive={false} />
        <Area type="monotone" dataKey="band" stackId="fan" stroke="none" fill="hsl(var(--chart-spend))" fillOpacity={0.18} isAnimationActive={false} />
        <Line type="monotone" dataKey="baseP50" stroke="hsl(var(--chart-axis))" strokeWidth={2} strokeDasharray="5 4" dot={false} isAnimationActive={false} />
        <Line type="monotone" dataKey="p50" stroke="hsl(var(--chart-spend))" strokeWidth={2.5} dot={false} isAnimationActive={false} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
