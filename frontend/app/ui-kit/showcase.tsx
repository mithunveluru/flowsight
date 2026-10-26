"use client";

import * as React from "react";
import { Droplets, Plus, Receipt, Repeat, Sparkles } from "lucide-react";
import { AnimatedNumber, Collapse, DrawLine, FadeIn, Reveal, Stagger, StaggerItem } from "@/components/motion/primitives";
import { Band, HeroBand } from "@/components/ui/band";
import { Menu, MenuContent, MenuItem, MenuLabel, MenuSeparator, MenuTrigger } from "@/components/ui/menu";
import { TabPanel, Tabs } from "@/components/ui/tabs";
import { TextLink } from "@/components/ui/text-link";
import { toast } from "@/components/ui/toast";
import { useAnnounce, LiveRegion } from "@/components/ui/announcer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardFooter, CardHeader, CardSection } from "@/components/ui/card";
import { ChartFrame, ChartLegend, type ChartState } from "@/components/ui/chart-frame";
import { ConfidenceChip } from "@/components/ui/confidence-chip";
import {
  SelectionCheckbox,
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useRowSelection,
  useTableSort,
} from "@/components/ui/data-table";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/dialog";
import { EvidenceDrawer, EvidenceTrigger } from "@/components/ui/evidence-drawer";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  ConfidenceBadge,
  EvidenceChips,
  ForecastCell,
  ForecastPanel,
  ImpactFigure,
  InsightCallout,
  SignalCard,
  SignalSectionHeader,
  SourceTag,
  StatusMarker,
  TrendDelta,
} from "@/components/ui/signals";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { Textarea } from "@/components/ui/textarea";
import { ThemeToggle } from "@/components/ui/theme";
import { SimpleTooltip } from "@/components/ui/tooltip";

const THEME_INDEPENDENT = ["--radius", "--duration-", "--ease-", "--font-", "--sp-", "--ty-", "--bar-height", "--elev-1", "--elev-2", "--elev-3", "--elev-card"];

function useTokenParity() {
  const [result, setResult] = React.useState<{ checked: number; missing: string[] } | null>(null);
  React.useEffect(() => {
    const light = new Set<string>();
    const dark = new Set<string>();
    for (const sheet of Array.from(document.styleSheets)) {
      let rules: CSSRuleList;
      try {
        rules = sheet.cssRules;
      } catch {
        continue;
      }
      for (const rule of Array.from(rules)) {
        if (!(rule instanceof CSSStyleRule)) continue;
        const target = rule.selectorText === ":root" ? light : rule.selectorText === ".dark" ? dark : null;
        if (!target) continue;
        for (const prop of Array.from(rule.style)) {
          if (!prop.startsWith("--")) continue;
          if (THEME_INDEPENDENT.some((p) => prop.startsWith(p))) continue;
          if (target === light && rule.style.getPropertyValue(prop).trim().startsWith("var(")) continue;
          target.add(prop);
        }
      }
    }
    setResult({ checked: light.size, missing: [...light].filter((t) => !dark.has(t)).sort() });
  }, []);
  return result;
}

const SWATCHES = [
  "background", "foreground", "card", "muted", "muted-foreground", "primary", "border", "input",
  "brand", "brand-soft", "positive", "positive-soft", "caution", "caution-soft", "warning", "warning-soft",
  "chart-spend", "chart-income", "chart-1", "chart-2", "chart-3", "chart-4", "chart-5", "chart-6",
];

interface Row {
  id: string;
  date: string;
  merchant: string;
  category: string;
  amount: number;
}

const ROWS: Row[] = [
  { id: "t1", date: "2026-06-03", merchant: "Zomato", category: "Food & Dining", amount: 380 },
  { id: "t2", date: "2026-06-01", merchant: "Salary credit", category: "Income", amount: -100000 },
  { id: "t3", date: "2026-05-31", merchant: "Amazon", category: "Shopping", amount: 3402 },
  { id: "t4", date: "2026-05-29", merchant: "Swiggy", category: "Food & Dining", amount: 375 },
  { id: "t5", date: "2026-05-28", merchant: "Cult Fit", category: "Healthcare", amount: 2494 },
];

const ROW_ACCESSORS = {
  date: (r: Row) => new Date(r.date),
  merchant: (r: Row) => r.merchant,
  amount: (r: Row) => r.amount,
};

const rupees = (v: number) => `₹${Math.abs(v).toLocaleString("en-IN")}`;

function Block({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-4">
      <h2 className="text-overline">{title}</h2>
      {children}
    </section>
  );
}

const BAND_TONES = ["canvas", "chrome", "band", "blue", "lavender", "sky", "deep", "navy"] as const;
const BUTTON_VARIANTS = ["default", "secondary", "outline", "ghost", "destructive", "link"] as const;
const TAB_ITEMS = [
  { value: "month", label: "This month" },
  { value: "quarter", label: "Quarter" },
  { value: "year", label: "Year" },
];

function FoundationBlock() {
  const [tab, setTab] = React.useState("month");
  return (
    <Block title="Bands, tabs, links, buttons, menus, toasts">
      <div className="overflow-hidden rounded-lg border border-border">
        {BAND_TONES.map((tone) => (
          <Band key={tone} tone={tone} space="sm" curved={tone === "lavender"}>
            <p className="text-ty-subhead">Band tone: {tone}</p>
          </Band>
        ))}
      </div>
      <div className="overflow-hidden rounded-lg">
        <HeroBand
          title="Hero band"
          lead="Title, lead and actions on a solid deep-blue band."
          actions={<Button variant="secondary">Primary action</Button>}
        />
      </div>

      <Card className="space-y-4">
        <Tabs items={TAB_ITEMS} value={tab} onValueChange={setTab} label="Range" idPrefix="kit" />
        {TAB_ITEMS.map((i) => (
          <TabPanel key={i.value} value={i.value} active={tab === i.value} idPrefix="kit">
            <p className="text-body">Panel for {i.label.toString().toLowerCase()}.</p>
          </TabPanel>
        ))}
      </Card>

      <Card className="space-y-4">
        <div className="space-y-3">
          {BUTTON_VARIANTS.map((v) => (
            <div key={v} className="flex flex-wrap items-center gap-3">
              <span className="w-24 text-caption">{v}</span>
              <Button variant={v}>Rest</Button>
              <Button variant={v} disabled>Disabled</Button>
              <Button variant={v} size="sm">Small</Button>
              <Button variant={v} size="lg">Large</Button>
            </div>
          ))}
        </div>
        <p className="text-body">
          Inline <TextLink href="#kit">text link</TextLink> and <TextLink href="#kit" chevron>link with chevron</TextLink>.
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <Menu>
            <MenuTrigger asChild>
              <Button variant="outline">Menu</Button>
            </MenuTrigger>
            <MenuContent>
              <MenuLabel>Account</MenuLabel>
              <MenuItem>Settings</MenuItem>
              <MenuSeparator />
              <MenuItem>Sign out</MenuItem>
            </MenuContent>
          </Menu>
          <Button variant="outline" onClick={() => toast({ title: "Saved", description: "Budget updated", tone: "success" })}>
            Success toast
          </Button>
          <Button variant="outline" onClick={() => toast({ title: "Import failed", tone: "error" })}>
            Error toast
          </Button>
          <Button variant="outline" onClick={() => toast({ title: "Syncing" })}>
            Info toast
          </Button>
        </div>
      </Card>

      <Card className="space-y-4">
        <Reveal>
          <p className="text-body">Reveal fades and rises on scroll.</p>
        </Reveal>
        <Stagger className="grid gap-2 sm:grid-cols-3">
          {["One", "Two", "Three"].map((t) => (
            <StaggerItem key={t}>
              <div className="rounded-md bg-band-blue p-sp-wide text-body">{t}</div>
            </StaggerItem>
          ))}
        </Stagger>
        <svg viewBox="0 0 200 40" className="h-10 w-full max-w-sm text-primary" role="img" aria-label="Line drawn on load">
          <DrawLine d="M2 34 C 40 34, 50 8, 90 14 S 150 36, 198 6" />
        </svg>
      </Card>
    </Block>
  );
}

function TokensBlock() {
  const parity = useTokenParity();
  return (
    <Block title="Tokens">
      <div className="grid grid-cols-3 gap-3 sm:grid-cols-6 lg:grid-cols-8">
        {SWATCHES.map((name) => (
          <div key={name} className="space-y-1.5">
            <div className="h-10 rounded-lg border border-border" style={{ background: `hsl(var(--${name}))` }} />
            <p className="truncate font-mono text-[11px] text-muted-foreground">{name}</p>
          </div>
        ))}
      </div>
      <p className="text-caption" data-parity={parity?.missing.length === 0 ? "ok" : "missing"}>
        {parity == null
          ? "Checking light/dark parity"
          : parity.missing.length === 0
            ? `Dark parity: all ${parity.checked} theme tokens have a dark value.`
            : `Missing dark values: ${parity.missing.join(", ")}`}
      </p>
      <div className="grid gap-4 sm:grid-cols-3">
        {(["elev-1", "elev-2", "elev-3"] as const).map((e) => (
          <div key={e} className={`rounded-xl bg-card p-5 text-caption ${e}`}>
            .{e}
          </div>
        ))}
      </div>
    </Block>
  );
}

function TypeBlock() {
  return (
    <Block title="Type scale">
      <Card className="space-y-3">
        <p className="text-display">Display 30</p>
        <p className="text-headline">Headline 20</p>
        <p className="text-title">Title 14 semibold</p>
        <p className="text-body">Body 14. Your spending this month is tracking close to your usual pattern.</p>
        <p className="text-supporting">Supporting 14 muted, for descriptions under titles.</p>
        <p className="text-caption">Caption 12 muted, for metadata and hints.</p>
        <p className="text-overline">Overline 10</p>
        <div className="flex items-end gap-8">
          <div>
            <p className="stat-label">Net cashflow</p>
            <p className="stat-value">₹99,645</p>
          </div>
          <div>
            <p className="stat-label">Spent</p>
            <p className="stat-value-sm">₹355</p>
          </div>
        </div>
      </Card>
    </Block>
  );
}

function SignalsBlock() {
  return (
    <Block title="Signals">
      <SignalSectionHeader icon={<Sparkles className="h-4 w-4" />} title="Patterns we noticed" subtitle="Week by week" />
      <div className="grid gap-4 md:grid-cols-2">
        <SignalCard
          severity="HIGH"
          icon={<Droplets className="h-4 w-4" />}
          title="Silent drains: 3 high-frequency merchants"
          metric={<ImpactFigure monthly={10734} />}
        >
          <p className="text-supporting">Small, frequent purchases that add up.</p>
          <EvidenceChips className="mt-2" items={["94 visits", "3 merchants", "last 90 days"]} />
        </SignalCard>
        <SignalCard severity="MEDIUM" icon={<Repeat className="h-4 w-4" />} title="Two subscriptions rose in price">
          <InsightCallout tone="signal">Compare current prices with your past payments.</InsightCallout>
        </SignalCard>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <ConfidenceBadge severity="HIGH" />
        <ConfidenceBadge severity="MEDIUM" />
        <ConfidenceBadge severity="LOW" />
        <StatusMarker label="Overdue" color="hsl(var(--caution))" />
        <TrendDelta percent={12.4} intent="lower-is-better" suffix="vs last month" />
        <TrendDelta percent={-8} intent="lower-is-better" />
        <TrendDelta percent={0} intent="higher-is-better" />
        <SourceTag source="rules" />
        <SourceTag source="model" version="v3" />
        <SourceTag source="user" />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <ConfidenceChip value={0.92} showValue />
        <ConfidenceChip value={0.64} showValue />
        <ConfidenceChip value={0.31} showValue />
        <ConfidenceChip
          value={0.55}
          thresholds={{ high: 0.65, medium: 0.4 }}
          labels={{ medium: "Review recommended" }}
        />
      </div>
      <Card padding="none" className="overflow-hidden">
        <div className="p-5">
          <p className="text-title">Landlord transfer</p>
        </div>
        <ForecastPanel>
          <ForecastCell label="1 year" value="₹1,94,749" />
          <ForecastCell label="5 years" value="₹9,73,744" />
          <ForecastCell label="10 years" value="₹19,47,487" />
          <ForecastCell label="10 yr opportunity cost" value="₹29.7L" hint="if invested at 8%/yr" accent />
        </ForecastPanel>
      </Card>
    </Block>
  );
}

function CardsBlock() {
  return (
    <Block title="Cards">
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader
            icon={<Receipt />}
            title="Receipt inbox"
            description="2 drafts waiting for review"
            action={<Badge variant="amber">2 drafts</Badge>}
          />
          <CardSection divided>
            <p className="text-body">Sections divide related content inside one card.</p>
          </CardSection>
          <CardFooter>
            <Button variant="outline" size="sm">Later</Button>
            <Button size="sm">Review</Button>
          </CardFooter>
        </Card>
        <div className="grid gap-4">
          <Card variant="interactive" as="article" tabIndex={0}>
            <CardHeader title="Interactive card" description="Lifts on hover; give it a link or button inside." />
          </Card>
          <Card variant="muted" padding="md">
            <p className="text-supporting">Muted card for secondary content.</p>
          </Card>
          <Card variant="outline" padding="md">
            <p className="text-supporting">Outline card for nested groups.</p>
          </Card>
        </div>
      </div>
    </Block>
  );
}

function FormBlock() {
  const [amount, setAmount] = React.useState("abc");
  const invalid = amount !== "" && Number.isNaN(Number(amount));
  return (
    <Block title="Fields">
      <Card className="grid gap-5 md:grid-cols-2">
        <Field label="Merchant" hint="As printed on the receipt" required>
          <Input placeholder="Walmart" />
        </Field>
        <Field label="Amount" error={invalid ? "Enter a number, for example 249.50" : undefined}>
          <Input inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Category" hint="Leave on auto-detect to let FlowSight decide">
          <Select defaultValue="auto">
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="auto">Auto-detect</SelectItem>
              <SelectItem value="food">Food & Dining</SelectItem>
              <SelectItem value="groceries">Groceries</SelectItem>
            </SelectContent>
          </Select>
        </Field>
        <Field label="Notes">
          <Textarea placeholder="Add a note about this transaction" />
        </Field>
      </Card>
    </Block>
  );
}

function TableBlock() {
  const { sorted, headProps } = useTableSort(ROWS, ROW_ACCESSORS);
  const ids = React.useMemo(() => ROWS.map((r) => r.id), []);
  const selection = useRowSelection(ids);
  return (
    <Block title="Data table">
      <Card padding="none" className="overflow-hidden">
        <Table stickyHeader maxHeight={320}>
          <TableCaption srOnly>Recent transactions, sortable by date, merchant and amount</TableCaption>
          <TableHeader>
            <tr>
              <TableHead className="w-10">
                <SelectionCheckbox aria-label="Select all transactions" {...selection.headerCheckboxProps} />
              </TableHead>
              <TableHead {...headProps("date")}>Date</TableHead>
              <TableHead {...headProps("merchant")}>Merchant</TableHead>
              <TableHead>Category</TableHead>
              <TableHead align="right" {...headProps("amount")}>Amount</TableHead>
            </tr>
          </TableHeader>
          <TableBody>
            {sorted.map((r) => (
              <TableRow key={r.id} selected={selection.isSelected(r.id)}>
                <TableCell>
                  <SelectionCheckbox aria-label={`Select ${r.merchant}`} {...selection.rowCheckboxProps(r.id)} />
                </TableCell>
                <TableCell>{new Date(r.date).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}</TableCell>
                <TableCell className="font-medium">{r.merchant}</TableCell>
                <TableCell className="text-muted-foreground">{r.category}</TableCell>
                <TableCell align="right" className={r.amount < 0 ? "text-positive" : undefined}>
                  <span className="sr-only">{r.amount < 0 ? "credit " : "debit "}</span>
                  {r.amount < 0 ? "+" : "−"}
                  {rupees(r.amount)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
      <LiveRegion visible className="text-caption">
        {selection.count === 0 ? "No rows selected" : `${selection.count} selected`}
      </LiveRegion>
    </Block>
  );
}

function MiniBars({ values }: { values: number[] }) {
  const max = Math.max(...values);
  return (
    <svg viewBox={`0 0 ${values.length * 24} 100`} preserveAspectRatio="none" className="h-full w-full" aria-hidden="true">
      {values.map((v, i) => (
        <rect key={i} x={i * 24 + 4} y={100 - (v / max) * 96} width="16" height={(v / max) * 96} rx="3" fill="hsl(var(--chart-spend))" />
      ))}
    </svg>
  );
}

function ChartBlock() {
  const [state, setState] = React.useState<ChartState>("ready");
  return (
    <Block title="Chart frame">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Chart state">
        {(["ready", "loading", "empty", "error"] as const).map((s) => (
          <Button key={s} size="sm" variant={state === s ? "default" : "outline"} aria-pressed={state === s} onClick={() => setState(s)}>
            {s}
          </Button>
        ))}
      </div>
      <ChartFrame
        title="Monthly spend"
        description="Last 8 months"
        summary="Spend peaked in March at about ₹3.8L and has fallen each month since."
        state={state}
        height={200}
        onRetry={() => new Promise((r) => setTimeout(r, 800)).then(() => setState("ready"))}
        emptyDescription="Import a statement to see how your spend moves month to month."
        legend={
          <ChartLegend
            items={[
              { label: "Spend", color: "hsl(var(--chart-spend))" },
              { label: "Projected spend", color: "hsl(var(--chart-spend))", variant: "dashed" },
              { label: "Income", color: "hsl(var(--chart-income))" },
            ]}
          />
        }
      >
        <MiniBars values={[40, 55, 95, 80, 62, 58, 50, 45]} />
      </ChartFrame>
    </Block>
  );
}

function StatesBlock() {
  return (
    <Block title="States">
      <div className="grid gap-4 md:grid-cols-2">
        <LoadingState variant="card" label="Loading budgets" />
        <LoadingState variant="list" rows={3} label="Loading transactions" />
      </div>
      <LoadingState variant="stats" rows={4} label="Loading summary" />
      <div className="grid gap-4 md:grid-cols-2">
        <EmptyState
          title="No budgets yet"
          description="Set a monthly limit for a category and we'll track it as you spend."
          action={<Button size="sm"><Plus />New budget</Button>}
        />
        <ErrorState
          error={new Error("Request timed out after 30 s")}
          onRetry={() => new Promise((r) => setTimeout(r, 1200))}
        />
      </div>
    </Block>
  );
}

function OverlaysBlock() {
  const announce = useAnnounce();
  return (
    <Block title="Overlays and feedback">
      <div className="flex flex-wrap items-center gap-3">
        <Dialog>
          <DialogTrigger asChild>
            <Button variant="outline">Open dialog</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Delete this budget?</DialogTitle>
              <DialogDescription>Spending history stays; only the limit is removed.</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline">Cancel</Button>
              </DialogClose>
              <DialogClose asChild>
                <Button variant="destructive" onClick={() => announce("Budget deleted")}>Delete</Button>
              </DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <Sheet>
          <SheetTrigger asChild>
            <Button variant="outline">Open sheet</Button>
          </SheetTrigger>
          <SheetContent>
            <SheetHeader className="p-6">
              <SheetTitle>New goal</SheetTitle>
              <SheetDescription>Save toward something that matters.</SheetDescription>
            </SheetHeader>
            <div className="space-y-4 px-6">
              <Field label="Goal name" required>
                <Input placeholder="Emergency fund" />
              </Field>
              <Field label="Target amount" hint="In rupees">
                <Input inputMode="decimal" />
              </Field>
            </div>
          </SheetContent>
        </Sheet>

        <div className="inline-flex items-center gap-1">
          <span className="text-sm font-semibold tabular-nums">₹12,836/mo</span>
          <EvidenceDrawer
            title="Potential monthly savings"
            value="₹12,836/mo"
            trigger={<EvidenceTrigger label="potential monthly savings" />}
            evidence={{
              method: "Sum of the monthly impact of each money drain found in your history.",
              source: "rules",
              confidence: 0.82,
              window: { from: "2026-03-05", to: "2026-06-03" },
              inputs: [
                { label: "Zomato", value: "₹3,797/mo", href: "/dashboard/transactions" },
                { label: "Swiggy", value: "₹3,751/mo" },
                { label: "Zepto", value: "₹3,185/mo" },
              ],
              inputCount: 5,
              caveats: ["Assumes your next three months look like the last three."],
              computedAt: "2026-06-03",
            }}
            footer={<Button size="sm" variant="outline">This is wrong</Button>}
          />
        </div>

        <SimpleTooltip content="Confirm this subscription">
          <Button variant="ghost" size="icon" aria-label="Confirm subscription">
            <Repeat />
          </Button>
        </SimpleTooltip>

        <Button variant="secondary" onClick={() => announce("3 transactions imported")}>
          Announce politely
        </Button>
      </div>
    </Block>
  );
}

function MotionBlock() {
  const [open, setOpen] = React.useState(false);
  const [n, setN] = React.useState(12836);
  return (
    <Block title="Motion">
      <Card className="space-y-4">
        <FadeIn>
          <p className="text-body">FadeIn renders statically when reduced motion is on.</p>
        </FadeIn>
        <div className="flex items-center gap-3">
          <AnimatedNumber value={n} format={(v) => `₹${v.toLocaleString("en-IN")}`} className="stat-value-sm" />
          <Button size="sm" variant="outline" onClick={() => setN((v) => v + 1500)}>Add ₹1,500</Button>
        </div>
        <div>
          <Button size="sm" variant="ghost" aria-expanded={open} aria-controls="collapse-demo" onClick={() => setOpen((o) => !o)}>
            {open ? "Hide" : "Show"} affected items
          </Button>
          <Collapse open={open} id="collapse-demo">
            <ul className="mt-2 space-y-1 text-supporting">
              <li>Zomato, 30 visits</li>
              <li>Swiggy, 30 visits</li>
            </ul>
          </Collapse>
        </div>
      </Card>
    </Block>
  );
}

export function Showcase() {
  return (
    <main className="mx-auto max-w-5xl space-y-12 px-4 py-10 sm:px-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-display">FlowSight UI kit</h1>
          <p className="mt-1 text-supporting">Every shared primitive, in both themes. Development builds only.</p>
        </div>
        <ThemeToggle />
      </header>
      <TokensBlock />
      <FoundationBlock />
      <TypeBlock />
      <CardsBlock />
      <SignalsBlock />
      <FormBlock />
      <TableBlock />
      <ChartBlock />
      <StatesBlock />
      <OverlaysBlock />
      <MotionBlock />
    </main>
  );
}
