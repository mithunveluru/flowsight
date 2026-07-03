package com.flowsight.reports;

import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.analytics.MerchantSummary;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Builds the report PDF with OpenPDF; the visual language mirrors the web UI.
@Service
@Slf4j
public class PdfGenerationService {

    // design tokens (web UI palette)
    private static final Color INK         = new Color(0x1B, 0x1F, 0x26); // near-black foreground
    private static final Color INK_SOFT    = new Color(0x6B, 0x72, 0x80); // muted secondary
    private static final Color INK_FAINT   = new Color(0x9C, 0xA3, 0xAF); // captions
    private static final Color DIVIDER     = new Color(0xE5, 0xE7, 0xEB);
    private static final Color BRAND       = new Color(0x2D, 0x6C, 0xDF); // refined blue
    private static final Color SEVERITY_HI = new Color(0xDC, 0x26, 0x26);
    private static final Color SEVERITY_MD = new Color(0xD9, 0x77, 0x06);
    private static final Color SEVERITY_LO = new Color(0x16, 0xA3, 0x4A);

    // Helvetica is built into OpenPDF (no embedding needed)
    private static final Font  TITLE_FONT     = new Font(Font.HELVETICA, 32, Font.BOLD,   INK);
    private static final Font  COVER_LABEL    = new Font(Font.HELVETICA, 10, Font.BOLD,   INK_FAINT);
    private static final Font  COVER_META     = new Font(Font.HELVETICA, 11, Font.NORMAL, INK_SOFT);
    private static final Font  SECTION_LABEL  = new Font(Font.HELVETICA,  9, Font.BOLD,   INK_FAINT);
    private static final Font  HEADING_FONT   = new Font(Font.HELVETICA, 18, Font.BOLD,   INK);
    private static final Font  SUBHEAD_FONT   = new Font(Font.HELVETICA, 12, Font.BOLD,   INK);
    private static final Font  BODY_FONT      = new Font(Font.HELVETICA, 10.5f, Font.NORMAL, INK);
    private static final Font  CAPTION_FONT   = new Font(Font.HELVETICA,  9, Font.NORMAL, INK_FAINT);
    private static final Font  STAT_LABEL     = new Font(Font.HELVETICA,  8, Font.BOLD,   INK_FAINT);
    private static final Font  STAT_VALUE     = new Font(Font.HELVETICA, 20, Font.BOLD,   INK);
    private static final Font  TABLE_HEADER   = new Font(Font.HELVETICA,  8, Font.BOLD,   INK_FAINT);
    private static final Font  TABLE_BODY     = new Font(Font.HELVETICA, 10, Font.NORMAL, INK);

    private static final float MARGIN     = 56f;   // ~2cm
    private static final float SECTION_GAP = 18f;

    private static final DateTimeFormatter GEN_DATE_FMT =
        DateTimeFormatter.ofPattern("d MMMM yyyy");

    public byte[] generate(ReportData data, ReportInsightGenerator.ReportNarrative narrative) throws IOException {
        Document doc = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageDecorator());
            doc.open();

            renderCover(doc, data);

            doc.newPage();
            renderSectionHeader(doc, "01", "Executive Summary");
            renderHeroStats(doc, data);
            renderParagraphs(doc, narrative.getExecutiveSummary());
            addSpace(doc, SECTION_GAP);

            renderSectionHeader(doc, "02", "Spending Behavior");
            renderParagraphs(doc, narrative.getBehaviorAnalysis());
            addSpace(doc, SECTION_GAP);

            renderSectionHeader(doc, "03", "Recurring Commitments");
            renderParagraphs(doc, narrative.getRecurringSummary());
            if (data.getRecurringPatterns() != null && !data.getRecurringPatterns().isEmpty()) {
                renderRecurringTable(doc, data.getRecurringPatterns());
            }
            addSpace(doc, SECTION_GAP);

            doc.newPage(); // new page for visual separation
            renderSectionHeader(doc, "04", "Financial Leak Analysis");
            renderLeaks(doc, narrative.getLeakLines());
            addSpace(doc, SECTION_GAP);

            renderSectionHeader(doc, "05", "Category Breakdown");
            renderCategoryBars(doc, data.getCurrentPeriod().getCategoryBreakdown(), data.getCurrentPeriod().getTotalSpend().doubleValue());
            addSpace(doc, SECTION_GAP);

            if (data.getCurrentPeriod().getTopMerchants() != null
                && !data.getCurrentPeriod().getTopMerchants().isEmpty()) {
                renderSectionHeader(doc, "06", "Top Merchants");
                renderTopMerchants(doc, data.getCurrentPeriod().getTopMerchants());
                addSpace(doc, SECTION_GAP);
            }

            doc.newPage();
            renderSectionHeader(doc, "07", "Consequence Analysis");
            renderParagraphs(doc, narrative.getConsequenceParagraph());
            addSpace(doc, SECTION_GAP);

            renderSectionHeader(doc, "08", "Recommendations");
            renderRecommendations(doc, narrative.getRecommendations());

        } catch (DocumentException e) {
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }

        return out.toByteArray();
    }

    // header strip + footer page numbers
    private static class PageDecorator extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            int pageNumber = writer.getPageNumber();

            // skip on cover page
            if (pageNumber > 1) {
                cb.saveState();
                cb.setColorStroke(DIVIDER);
                cb.setLineWidth(0.4f);
                cb.moveTo(MARGIN, document.getPageSize().getHeight() - 32);
                cb.lineTo(document.getPageSize().getWidth() - MARGIN, document.getPageSize().getHeight() - 32);
                cb.stroke();

                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("FlowSight", CAPTION_FONT),
                    MARGIN, document.getPageSize().getHeight() - 24, 0);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Financial Intelligence Report", CAPTION_FONT),
                    document.getPageSize().getWidth() - MARGIN, document.getPageSize().getHeight() - 24, 0);

                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase(String.valueOf(pageNumber), CAPTION_FONT),
                    document.getPageSize().getWidth() / 2f, 26, 0);
                cb.restoreState();
            }
        }
    }

    private void renderCover(Document doc, ReportData data) throws DocumentException {
        addSpace(doc, 80);

        PdfPTable logoTable = new PdfPTable(1);
        logoTable.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell(new Phrase("Flowsight", new Font(Font.HELVETICA, 11, Font.BOLD, INK)));
        logoCell.setBorder(0);
        logoCell.setPaddingBottom(80);
        logoTable.addCell(logoCell);
        doc.add(logoTable);

        Paragraph eyebrow = new Paragraph("FINANCIAL INTELLIGENCE REPORT", COVER_LABEL);
        eyebrow.setSpacingAfter(14);
        doc.add(eyebrow);

        Paragraph title = new Paragraph("Your money, decoded.", TITLE_FONT);
        title.setSpacingAfter(8);
        doc.add(title);

        Paragraph period = new Paragraph(data.getPeriodLabel(), COVER_META);
        period.setSpacingAfter(60);
        doc.add(period);

        String coverSummary = String.format(
            "An insights-only summary of your spending behavior, recurring commitments, " +
            "and financial leaks across this window. Numbers come from your actual " +
            "transaction history; narratives are deterministic and grounded in your data."
        );
        Paragraph summary = new Paragraph(coverSummary, BODY_FONT);
        summary.setLeading(16f);
        summary.setSpacingAfter(80);
        doc.add(summary);

        addDivider(doc);
        addSpace(doc, 12);
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.addCell(metaCell("GENERATED", LocalDate.now().format(GEN_DATE_FMT)));
        meta.addCell(metaCell("PERIOD", data.getPeriodLabel()));
        doc.add(meta);
    }

    private PdfPCell metaCell(String label, String value) {
        PdfPTable inner = new PdfPTable(1);
        Paragraph p1 = new Paragraph(label, COVER_LABEL);
        Paragraph p2 = new Paragraph(value, COVER_META);
        p2.setSpacingBefore(4);
        PdfPCell labelCell = new PdfPCell(p1); labelCell.setBorder(0);
        PdfPCell valueCell = new PdfPCell(p2); valueCell.setBorder(0);
        inner.addCell(labelCell);
        inner.addCell(valueCell);
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBorder(0);
        return wrap;
    }

    private void renderSectionHeader(Document doc, String number, String title) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1, 9});
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(12);

        PdfPCell num = new PdfPCell(new Phrase(number, SECTION_LABEL));
        num.setBorder(0);
        num.setVerticalAlignment(Element.ALIGN_TOP);
        num.setPaddingTop(4);
        t.addCell(num);

        PdfPCell head = new PdfPCell(new Phrase(title, HEADING_FONT));
        head.setBorder(0);
        t.addCell(head);

        doc.add(t);
    }

    private void renderHeroStats(Document doc, ReportData data) throws DocumentException {
        var current = data.getCurrentPeriod();

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingAfter(SECTION_GAP);

        t.addCell(statCell("TOTAL SPEND",  formatINR(current.getTotalSpend().doubleValue())));
        t.addCell(statCell("TOTAL INCOME", formatINR(current.getTotalIncome().doubleValue())));
        t.addCell(statCell("NET CASHFLOW", formatINR(current.getNetCashflow().doubleValue())));
        t.addCell(statCell("TRANSACTIONS", String.valueOf(current.getTransactionCount())));

        doc.add(t);
    }

    private PdfPCell statCell(String label, String value) {
        PdfPTable inner = new PdfPTable(1);
        Paragraph l = new Paragraph(label, STAT_LABEL);
        Paragraph v = new Paragraph(value, STAT_VALUE);
        v.setSpacingBefore(6);
        PdfPCell lc = new PdfPCell(l); lc.setBorder(0);
        PdfPCell vc = new PdfPCell(v); vc.setBorder(0);
        inner.addCell(lc);
        inner.addCell(vc);
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setBorder(Rectangle.BOTTOM);
        wrap.setBorderColor(DIVIDER);
        wrap.setBorderWidth(0.4f);
        wrap.setPaddingBottom(14);
        wrap.setPaddingTop(8);
        wrap.setPaddingLeft(8);
        wrap.setPaddingRight(8);
        return wrap;
    }

    private void renderParagraphs(Document doc, List<String> paragraphs) throws DocumentException {
        if (paragraphs == null || paragraphs.isEmpty()) return;
        for (String p : paragraphs) {
            Paragraph para = new Paragraph(p, BODY_FONT);
            para.setLeading(15f);
            para.setSpacingAfter(8);
            doc.add(para);
        }
    }

    private void renderLeaks(Document doc, List<ReportInsightGenerator.LeakLine> lines) throws DocumentException {
        if (lines == null || lines.isEmpty()) {
            renderParagraphs(doc, List.of("No material leaks were detected during this window. Your spending shape is clean."));
            return;
        }

        for (var leak : lines) {
            PdfPTable headerRow = new PdfPTable(new float[]{4, 1});
            headerRow.setWidthPercentage(100);
            Paragraph titleP = new Paragraph(leak.getTitle(), SUBHEAD_FONT);
            PdfPCell titleCell = new PdfPCell(titleP);
            titleCell.setBorder(0);
            titleCell.setPaddingBottom(2);
            headerRow.addCell(titleCell);

            Color sev = switch (leak.getSeverity()) {
                case "HIGH"   -> SEVERITY_HI;
                case "MEDIUM" -> SEVERITY_MD;
                default        -> SEVERITY_LO;
            };
            Font sevFont = new Font(Font.HELVETICA, 8, Font.BOLD, sev);
            Paragraph sevP = new Paragraph(leak.getSeverity() + " IMPACT", sevFont);
            sevP.setAlignment(Element.ALIGN_RIGHT);
            PdfPCell sevCell = new PdfPCell(sevP);
            sevCell.setBorder(0);
            sevCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            headerRow.addCell(sevCell);
            doc.add(headerRow);

            Paragraph desc = new Paragraph(leak.getDescription(), BODY_FONT);
            desc.setLeading(15f);
            desc.setSpacingBefore(4);
            doc.add(desc);

            Paragraph impact = new Paragraph(
                leak.getMonthlyImpact() + "  ·  " + leak.getAnnualImpact(),
                new Font(Font.HELVETICA, 10, Font.BOLD, BRAND));
            impact.setSpacingBefore(6);
            doc.add(impact);

            Paragraph rec = new Paragraph(leak.getRecommendation(), CAPTION_FONT);
            rec.setLeading(13f);
            rec.setSpacingBefore(4);
            rec.setSpacingAfter(16);
            doc.add(rec);

            addThinDivider(doc);
            addSpace(doc, 10);
        }
    }

    private void renderRecurringTable(Document doc, List<RecurringPatternResponse> patterns) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{4, 2, 2, 2});
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(8);

        t.addCell(tableHeader("MERCHANT"));
        t.addCell(tableHeader("PERIOD"));
        t.addCell(tableHeaderRight("PER OCCURRENCE"));
        t.addCell(tableHeaderRight("MONTHLY"));

        int rowLimit = Math.min(patterns.size(), 10);
        for (int i = 0; i < rowLimit; i++) {
            RecurringPatternResponse p = patterns.get(i);
            t.addCell(tableBody(p.getMerchant()));
            t.addCell(tableBody(p.getPeriodLabel()));
            t.addCell(tableBodyRight("₹" + formatINR(p.getEstimatedAmount().doubleValue())));
            t.addCell(tableBodyRight("₹" + formatINR(p.getMonthlyEquivalent().doubleValue())));
        }
        doc.add(t);
    }

    private void renderCategoryBars(Document doc, List<CategoryBreakdownItem> items, double total) throws DocumentException {
        if (items == null || items.isEmpty()) {
            renderParagraphs(doc, List.of("No categorized spend in this period."));
            return;
        }

        int rows = Math.min(items.size(), 8);
        for (int i = 0; i < rows; i++) {
            CategoryBreakdownItem c = items.get(i);

            PdfPTable nameRow = new PdfPTable(new float[]{6, 2});
            nameRow.setWidthPercentage(100);
            PdfPCell name = new PdfPCell(new Phrase(c.getDisplayName(), BODY_FONT));
            name.setBorder(0); name.setPaddingBottom(2);
            nameRow.addCell(name);

            Paragraph amt = new Paragraph(
                "₹" + formatINR(c.getAmount().doubleValue()),
                new Font(Font.HELVETICA, 10, Font.BOLD, INK));
            amt.setAlignment(Element.ALIGN_RIGHT);
            PdfPCell amtCell = new PdfPCell(amt);
            amtCell.setBorder(0); amtCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            nameRow.addCell(amtCell);
            doc.add(nameRow);

            renderProgressBar(doc, c.getPercentage());

            Paragraph caption = new Paragraph(
                String.format("%.0f%% of spend · %d transactions", c.getPercentage(), c.getTransactionCount()),
                CAPTION_FONT);
            caption.setSpacingBefore(2); caption.setSpacingAfter(12);
            doc.add(caption);
        }
    }

    private void renderProgressBar(Document doc, double percent) throws DocumentException {
        // colored cell of proportional width
        double pct = Math.max(1.0, Math.min(100.0, percent));
        PdfPTable bar = new PdfPTable(new float[]{(float) pct, (float)(100 - pct)});
        bar.setWidthPercentage(100);

        PdfPCell filled = new PdfPCell(new Phrase(" ", new Font(Font.HELVETICA, 0.1f)));
        filled.setBackgroundColor(BRAND);
        filled.setBorder(0);
        filled.setFixedHeight(4f);
        bar.addCell(filled);

        PdfPCell empty = new PdfPCell(new Phrase(" ", new Font(Font.HELVETICA, 0.1f)));
        empty.setBackgroundColor(DIVIDER);
        empty.setBorder(0);
        empty.setFixedHeight(4f);
        bar.addCell(empty);

        doc.add(bar);
    }

    private void renderTopMerchants(Document doc, List<MerchantSummary> merchants) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{5, 2, 2});
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(8);

        t.addCell(tableHeader("MERCHANT"));
        t.addCell(tableHeaderRight("VISITS"));
        t.addCell(tableHeaderRight("TOTAL SPEND"));

        int limit = Math.min(merchants.size(), 8);
        for (int i = 0; i < limit; i++) {
            MerchantSummary m = merchants.get(i);
            t.addCell(tableBody(m.getMerchant()));
            t.addCell(tableBodyRight(String.valueOf(m.getTransactionCount())));
            t.addCell(tableBodyRight("₹" + formatINR(m.getTotalAmount().doubleValue())));
        }
        doc.add(t);
    }

    private void renderRecommendations(Document doc, List<ReportInsightGenerator.RecommendationLine> recs) throws DocumentException {
        if (recs == null || recs.isEmpty()) {
            renderParagraphs(doc, List.of(
                "No high-priority recommendations were generated — your habits are well-aligned with your income and goals."));
            return;
        }

        int idx = 1;
        for (var rec : recs) {
            PdfPTable row = new PdfPTable(new float[]{1, 12});
            row.setWidthPercentage(100);
            row.setSpacingBefore(6);

            Paragraph numP = new Paragraph(String.format("%02d", idx),
                new Font(Font.HELVETICA, 10, Font.BOLD, BRAND));
            PdfPCell numCell = new PdfPCell(numP);
            numCell.setBorder(0);
            numCell.setVerticalAlignment(Element.ALIGN_TOP);
            row.addCell(numCell);

            PdfPCell bodyCell = new PdfPCell();
            bodyCell.setBorder(0);
            Paragraph title = new Paragraph(rec.getTitle(), SUBHEAD_FONT);
            bodyCell.addElement(title);
            Paragraph desc = new Paragraph(rec.getDescription(), BODY_FONT);
            desc.setLeading(14f); desc.setSpacingBefore(2);
            bodyCell.addElement(desc);

            if (rec.getAction() != null && !rec.getAction().isBlank()) {
                Paragraph action = new Paragraph("→ " + rec.getAction(),
                    new Font(Font.HELVETICA, 9.5f, Font.NORMAL, INK_SOFT));
                action.setLeading(13f); action.setSpacingBefore(4);
                bodyCell.addElement(action);
            }
            if (rec.getImpactLabel() != null && !rec.getImpactLabel().isBlank()) {
                Paragraph impact = new Paragraph(rec.getImpactLabel(),
                    new Font(Font.HELVETICA, 9.5f, Font.BOLD, BRAND));
                impact.setSpacingBefore(4);
                bodyCell.addElement(impact);
            }
            row.addCell(bodyCell);
            doc.add(row);

            addSpace(doc, 8);
            addThinDivider(doc);
            idx++;
        }
    }

    private PdfPCell tableHeader(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, TABLE_HEADER));
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(DIVIDER); c.setBorderWidth(0.4f);
        c.setPaddingBottom(8); c.setPaddingLeft(0);
        return c;
    }

    private PdfPCell tableHeaderRight(String text) {
        PdfPCell c = tableHeader(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPaddingRight(0);
        return c;
    }

    private PdfPCell tableBody(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, TABLE_BODY));
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(DIVIDER); c.setBorderWidth(0.3f);
        c.setPaddingTop(8); c.setPaddingBottom(8); c.setPaddingLeft(0);
        return c;
    }

    private PdfPCell tableBodyRight(String text) {
        PdfPCell c = tableBody(text);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPaddingRight(0);
        return c;
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" ", new Font(Font.HELVETICA, 0.1f)));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(DIVIDER);
        c.setBorderWidth(0.6f);
        c.setFixedHeight(1f);
        t.addCell(c);
        doc.add(t);
    }

    private void addThinDivider(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(new Phrase(" ", new Font(Font.HELVETICA, 0.1f)));
        c.setBorder(Rectangle.TOP);
        c.setBorderColor(DIVIDER);
        c.setBorderWidth(0.3f);
        c.setFixedHeight(1f);
        t.addCell(c);
        doc.add(t);
    }

    private void addSpace(Document doc, float points) throws DocumentException {
        Paragraph p = new Paragraph(" ", new Font(Font.HELVETICA, 0.1f));
        p.setSpacingAfter(points);
        doc.add(p);
    }

    private static String formatINR(double v) {
        return String.format("%,.0f", v);
    }
}
