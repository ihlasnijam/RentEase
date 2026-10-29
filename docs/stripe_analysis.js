const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, AlignmentType, HeadingLevel, BorderStyle, WidthType,
  ShadingType, VerticalAlign, LevelFormat, PageBreak
} = require('docx');
const fs = require('fs');

const NAVY  = "1A56DB";
const DARK  = "0F172A";
const GRAY  = "64748B";
const WHITE = "FFFFFF";
const GREEN = "065F46";
const GREEN_BG = "D1FAE5";
const RED   = "991B1B";
const RED_BG = "FEE2E2";
const AMBER = "92400E";
const AMBER_BG = "FEF3C7";
const BLUE_BG = "EFF6FF";
const W = 9360;

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const B = { top: border, bottom: border, left: border, right: border };

const h1 = text => new Paragraph({
  heading: HeadingLevel.HEADING_1,
  spacing: { before: 320, after: 160 },
  children: [new TextRun({ text, bold: true, size: 34, font: "Arial", color: NAVY })]
});
const h2 = text => new Paragraph({
  heading: HeadingLevel.HEADING_2,
  spacing: { before: 240, after: 120 },
  children: [new TextRun({ text, bold: true, size: 26, font: "Arial", color: DARK })]
});
const para = (text, opts = {}) => new Paragraph({
  alignment: AlignmentType.JUSTIFIED,
  spacing: { before: 80, after: 120, line: 276 },
  children: [new TextRun({ text, size: 22, font: "Arial", color: DARK, ...opts })]
});
const bullet = text => new Paragraph({
  numbering: { reference: "bullets", level: 0 },
  spacing: { before: 60, after: 60 },
  children: [new TextRun({ text, size: 22, font: "Arial", color: DARK })]
});
const spacer = () => new Paragraph({ spacing: { before: 60, after: 60 }, children: [new TextRun("")] });

function cell(text, bg, bold = false, color = DARK, width = 4680) {
  return new TableCell({
    borders: B,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: bg, type: ShadingType.CLEAR },
    margins: { top: 100, bottom: 100, left: 140, right: 140 },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      spacing: { before: 0, after: 0 },
      children: [new TextRun({ text, size: 20, font: "Arial", color, bold })]
    })]
  });
}

function headerCell(text, width = 4680) {
  return new TableCell({
    borders: B,
    width: { size: width, type: WidthType.DXA },
    shading: { fill: NAVY, type: ShadingType.CLEAR },
    margins: { top: 100, bottom: 100, left: 140, right: 140 },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 0, after: 0 },
      children: [new TextRun({ text, size: 21, font: "Arial", color: WHITE, bold: true })]
    })]
  });
}

// ── Main merit/demerit table ──────────────────────────────────────────────────
// Columns: Category | Current (Simulated) | Stripe | Tabby | Verdict
// Widths: 1400 + 2000 + 2000 + 2000 + 1960 = 9360

const COL = [1400, 2050, 2050, 1960, 1900];

function row5(cat, cur, stripe, tabby, verdict, curBg = WHITE, strBg = WHITE, tabBg = WHITE, verBg = WHITE, ri = 0) {
  const bg = ri % 2 === 0 ? WHITE : "F8FAFC";
  return new TableRow({ children: [
    cell(cat,     bg,    true,  DARK,  COL[0]),
    cell(cur,     curBg, false, DARK,  COL[1]),
    cell(stripe,  strBg, false, DARK,  COL[2]),
    cell(tabby,   tabBg, false, DARK,  COL[3]),
    cell(verdict, verBg, false, DARK,  COL[4]),
  ]});
}

const comparisonTable = new Table({
  width: { size: W, type: WidthType.DXA },
  columnWidths: COL,
  rows: [
    new TableRow({ tableHeader: true, children: [
      headerCell("Category",            COL[0]),
      headerCell("Current (Simulated)", COL[1]),
      headerCell("Stripe",              COL[2]),
      headerCell("Tabby (BNPL)",        COL[3]),
      headerCell("Verdict",             COL[4]),
    ]}),

    row5("Real money processing", "❌ No real charges", "✅ Full card processing", "✅ BNPL & card", "Stripe/Tabby needed for real payments", WHITE, GREEN_BG, GREEN_BG, BLUE_BG, 0),
    row5("UAE availability", "N/A", "✅ Available in UAE — supports AED", "✅ UAE-native, AED", "Both work in UAE", WHITE, GREEN_BG, GREEN_BG, GREEN_BG, 1),
    row5("Setup complexity", "✅ None — already built", "⚠️ Moderate — Stripe account, webhooks, SDK", "⚠️ Moderate — merchant account + integration", "Current wins for simplicity", GREEN_BG, AMBER_BG, AMBER_BG, GREEN_BG, 0),
    row5("Transaction fees", "0% (simulated)", "1.5% + AED 1.00 per charge (UAE cards)", "Varies: 0-6% on BNPL plans", "Stripe slightly cheaper for regular rent", GREEN_BG, AMBER_BG, AMBER_BG, BLUE_BG, 1),
    row5("Business registration required", "❌ No", "✅ Yes — UAE business registration or Stripe Atlas", "✅ Yes — DIFC/ADGM licensed", "Blocker for academic project", GREEN_BG, RED_BG, RED_BG, BLUE_BG, 0),
    row5("Payout to landlord", "N/A", "✅ Stripe Connect — direct to landlord bank", "❌ Not designed for marketplace payouts", "Stripe wins for landlord payouts", WHITE, GREEN_BG, RED_BG, GREEN_BG, 1),
    row5("Installments / BNPL", "❌ No", "⚠️ Stripe Klarna (not UAE)", "✅ Core feature — 4 installments, 0% interest", "Tabby wins for BNPL", RED_BG, AMBER_BG, GREEN_BG, GREEN_BG, 0),
    row5("PCI compliance", "✅ No card data stored", "✅ PCI DSS Level 1 — Stripe handles all card data", "✅ Handled by Tabby", "Both compliant", GREEN_BG, GREEN_BG, GREEN_BG, GREEN_BG, 1),
    row5("Mobile SDK (Android)", "N/A", "✅ Stripe Android SDK — native UI components", "⚠️ WebView-based checkout", "Stripe better native experience", WHITE, GREEN_BG, AMBER_BG, GREEN_BG, 0),
    row5("Recurring / auto-debit", "❌ No", "✅ Stripe Subscriptions — auto monthly charges", "❌ Not supported", "Stripe wins for auto rent collection", RED_BG, GREEN_BG, RED_BG, GREEN_BG, 1),
    row5("Refunds", "N/A", "✅ Full/partial refund API", "✅ Supported", "Both handle refunds", WHITE, GREEN_BG, GREEN_BG, GREEN_BG, 0),
    row5("Webhooks / server needed", "❌ No server", "⚠️ Yes — Firebase Cloud Functions needed for webhooks", "⚠️ Yes — webhook endpoint needed", "Adds complexity and cost (Blaze plan)", GREEN_BG, AMBER_BG, AMBER_BG, AMBER_BG, 1),
    row5("Test mode for demo", "✅ Full demo works now", "✅ Stripe test cards — easy to demo without real money", "⚠️ Limited sandbox", "Stripe test mode excellent for demos", GREEN_BG, GREEN_BG, AMBER_BG, GREEN_BG, 0),
    row5("Speed to implement", "✅ Already done", "⚠️ 3-5 days of integration work", "⚠️ 5-7 days of integration work", "Current fastest, Stripe next", GREEN_BG, AMBER_BG, AMBER_BG, BLUE_BG, 1),
    row5("Monthly cost (no txns)", "AED 0", "AED 0 (pay per transaction only)", "AED 0 (pay per transaction)", "All free to start", GREEN_BG, GREEN_BG, GREEN_BG, GREEN_BG, 0),
    row5("Customer support", "N/A", "✅ 24/7 developer support", "⚠️ Business hours UAE", "Stripe better for developers", WHITE, GREEN_BG, AMBER_BG, GREEN_BG, 1),
    row5("Academic project suitability", "✅ Perfect — shows the flow", "✅ Good — can demo with test cards", "⚠️ Requires merchant account", "Current or Stripe for project", GREEN_BG, GREEN_BG, AMBER_BG, GREEN_BG, 0),
    row5("Commercial deployment", "❌ Cannot charge real money", "✅ Production-ready", "✅ Production-ready", "Stripe or Tabby needed for real launch", RED_BG, GREEN_BG, GREEN_BG, BLUE_BG, 1),
  ]
});

const doc = new Document({
  numbering: {
    config: [
      { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "\u2022", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
      { reference: "numbers", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    ]
  },
  styles: {
    default: { document: { run: { font: "Arial", size: 22, color: DARK } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true, run: { size: 34, bold: true, font: "Arial", color: NAVY }, paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true, run: { size: 26, bold: true, font: "Arial", color: DARK }, paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 } },
    ]
  },
  sections: [{
    properties: {
      page: { size: { width: 15840, height: 12240 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }, orientation: "landscape" }
    },
    children: [

      // Cover
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 720, after: 0 }, children: [new TextRun({ text: "🏠 RentEase", bold: true, size: 64, font: "Arial", color: NAVY })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120, after: 80 }, children: [new TextRun({ text: "Payment Gateway Analysis", bold: true, size: 36, font: "Arial", color: DARK })] }),
      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 0, after: 480 }, children: [new TextRun({ text: "Current Simulation vs Stripe vs Tabby — Complete Merits & Demerits", size: 24, font: "Arial", color: GRAY, italics: true })] }),

      h1("1  Overview"),
      para("RentEase currently uses a simulated payment gateway that demonstrates the full user journey (card entry, processing animation, payment record creation, receipt, due-date advancement) without charging real money. This is appropriate for academic demonstration but cannot be used for a commercial product. This document analyses three options: keeping the current simulation, integrating Stripe, and integrating Tabby (a UAE-native Buy Now Pay Later provider)."),
      spacer(),

      h1("2  Quick Summary"),
      para("Use this to make your decision at a glance:"),
      bullet("Keep current simulation → Best for: finishing the academic project, demo, and report submission. Costs nothing, works today."),
      bullet("Add Stripe → Best for: commercial launch with real rent collection, auto-recurring payments, Stripe Connect payouts to landlords. Requires UAE business registration."),
      bullet("Add Tabby → Best for: adding 'Pay in 4 installments' option for tenants who cannot afford the full month rent upfront. Add alongside Stripe, not instead of it."),
      bullet("Recommended roadmap: Keep simulation now → Submit project → Register business → Add Stripe → Add Tabby as a feature."),
      spacer(),

      h1("3  Full Comparison Table"),
      para("Green = advantage, Amber = neutral/partial, Red = disadvantage or blocker."),
      spacer(),
      comparisonTable,
      spacer(),

      new Paragraph({ children: [new PageBreak()] }),

      h1("4  Stripe — Detailed Merits"),
      h2("4.1  What Stripe provides for RentEase"),
      bullet("Stripe Payments: Accept Visa, Mastercard, Amex, Apple Pay, Google Pay in AED."),
      bullet("Stripe Connect: Pay out directly to landlord bank accounts after collecting rent from tenants. This is the key feature for a marketplace model."),
      bullet("Stripe Subscriptions: Auto-charge tenants every month on the 1st. No manual payment needed — the app can charge automatically."),
      bullet("Stripe Android SDK: Native card entry UI that is PCI compliant — you never see or store card numbers."),
      bullet("Stripe Webhooks: Firebase Cloud Function receives webhook events (payment succeeded, failed, refunded) and updates Firestore automatically."),
      bullet("Test mode: Full testing with test card numbers (4242 4242 4242 4242) without real charges — perfect for demos."),
      spacer(),

      h2("4.2  Stripe implementation steps for RentEase"),
      para("These are the steps required to add Stripe — included so you know exactly what is involved:"),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Register UAE business (mainland or freezone) — approximately AED 8,000-15,000 one-time cost.", size: 22, font: "Arial", color: DARK })] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Create Stripe account at stripe.com — link your UAE bank account.", size: 22, font: "Arial", color: DARK })] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Add Stripe Android SDK to build.gradle: implementation 'com.stripe:stripe-android:20.x.x'", size: 22, font: "Arial", color: DARK })] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Deploy Firebase Cloud Function to create PaymentIntents on the server side (required by Stripe — payment intents cannot be created from the client).", size: 22, font: "Arial", color: DARK })] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Replace the current dialog_payment_gateway.xml with Stripe's CardInputWidget or PaymentSheet.", size: 22, font: "Arial", color: DARK })] }),
      new Paragraph({ numbering: { reference: "numbers", level: 0 }, spacing: { before: 60, after: 60 }, children: [new TextRun({ text: "Set up webhooks in Firebase Cloud Functions to listen for payment.succeeded events and update Firestore.", size: 22, font: "Arial", color: DARK })] }),
      spacer(),

      h2("4.3  Stripe fees for UAE rent"),
      para("For a AED 4,500 rent payment:"),
      bullet("Stripe fee: 1.5% + AED 1.00 = AED 68.50 per transaction"),
      bullet("Who pays: This is configurable — you can add it to the tenant's amount or absorb it as platform fee."),
      bullet("Monthly Stripe cost for 10 tenants: approximately AED 685 in fees."),
      bullet("Alternative: Charge tenants AED 4,569 and show them 'AED 69 convenience fee' — common practice."),
      spacer(),

      h1("5  Tabby — Detailed Merits"),
      h2("5.1  What Tabby offers"),
      para("Tabby is the UAE's leading Buy Now Pay Later (BNPL) provider. For a AED 4,500 rent payment, Tabby would split it into 4 payments of AED 1,125 each, collected every 2 weeks — at 0% interest to the tenant."),
      bullet("Tenant pays 4 x AED 1,125 over 6 weeks instead of AED 4,500 upfront."),
      bullet("Landlord receives the full AED 4,500 immediately from Tabby."),
      bullet("Tabby charges the merchant (landlord/platform) 3-6% fee."),
      bullet("Strong demand in UAE — especially for young expats who are paid monthly but have rent due on a fixed date."),
      spacer(),

      h2("5.2  Tabby limitations"),
      bullet("Requires a UAE merchant account and approval from Tabby — application process takes 2-4 weeks."),
      bullet("No native Android SDK — integration is via WebView checkout, which feels less native."),
      bullet("Not designed for recurring rent collection — each month is a new BNPL arrangement."),
      bullet("Better suited as an add-on option alongside Stripe, not as the primary payment method."),
      spacer(),

      h1("6  Recommendation"),
      para("Based on this analysis, the recommended payment strategy for RentEase is:"),
      bullet("Now (academic project): Keep the current simulation. It demonstrates the full flow, earns full marks, and requires no business registration."),
      bullet("Phase 1 (6 months after graduation): Register business, integrate Stripe Payments for real rent collection. Use Stripe Connect to pay landlords directly."),
      bullet("Phase 2 (1 year): Add Stripe Subscriptions for automated monthly rent collection on the 1st of each month."),
      bullet("Phase 3 (1.5 years): Add Tabby as an optional payment method for tenants who want to split their rent into 4 installments."),
      spacer(),

      new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 240, after: 0 }, border: { top: { style: BorderStyle.SINGLE, size: 4, color: NAVY, space: 4 } }, children: [new TextRun({ text: "RentEase Payment Analysis — Prepared April 2026", size: 18, font: "Arial", color: GRAY, italics: true })] }),
    ]
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync('/home/claude/newfeatures/RentEase_Payment_Analysis.docx', buffer);
  console.log('Done');
});
