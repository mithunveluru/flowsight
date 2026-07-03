import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "@/components/legal/LegalPage";
import { LEGAL } from "@/components/legal/legal-config";

export const metadata: Metadata = {
  title: "Privacy Policy | FlowSight",
  description: "How FlowSight collects, uses, and protects the financial data you provide.",
};

const sections: LegalSection[] = [
  {
    id: "introduction",
    title: "Introduction",
    body: (
      <>
        <p>
          {LEGAL.product} is a behavioral financial intelligence tool. It helps you understand your
          own spending by analyzing transactions and receipts that <strong>you provide</strong>. It
          is not a bank, payment processor, accounting service, or investment advisor, and it does
          not connect to your bank accounts or card networks.
        </p>
        <p>
          This policy explains what data we handle, why, and the choices you have. We only describe
          things the product actually does.
        </p>
      </>
    ),
  },
  {
    id: "information-we-collect",
    title: "Information We Collect",
    body: (
      <>
        <p>We collect only what you give us or what is needed to run the service:</p>
        <ul>
          <li>
            <strong>Account information</strong> — your name, email address, and a password. Your
            password is never stored in readable form (see Authentication below).
          </li>
          <li>
            <strong>Financial data you enter or import</strong> — transactions with their date,
            amount, merchant, description, category, notes, and currency, whether added manually,
            imported from a CSV file, or extracted from a receipt.
          </li>
          <li>
            <strong>Receipt images</strong> you choose to upload for extraction.
          </li>
          <li>
            <strong>Data derived from the above</strong> — budgets, goals, recurring-payment
            detections, and the insights we compute for you.
          </li>
          <li>
            <strong>Limited technical data</strong> — your IP address and browser user-agent are
            recorded in security audit logs for a small set of sensitive actions (such as sign-in
            and password reset), and standard server request logs.
          </li>
        </ul>
        <p>
          We do <strong>not</strong> link to your bank, and we never receive your card numbers, bank
          login credentials, or account balances from any financial institution.
        </p>
      </>
    ),
  },
  {
    id: "how-we-use",
    title: "How Information Is Used",
    body: (
      <>
        <p>We use your information to:</p>
        <ul>
          <li>Provide the core features: recording, categorizing, and analyzing your transactions.</li>
          <li>Generate spending insights, recurring-payment detection, budgets, goals, and reports.</li>
          <li>Extract details from receipts and CSV files you submit.</li>
          <li>Secure your account and detect abuse.</li>
          <li>Send you essential service messages, such as password-reset emails.</li>
        </ul>
        <p>
          We do not sell your data, and we do not use it for advertising.
        </p>
      </>
    ),
  },
  {
    id: "transaction-data",
    title: "Transaction Data",
    body: (
      <p>
        Your transactions are stored so we can show your history and compute analytics. Each record
        is tied to your account and is visible only to you. You can edit or delete any transaction at
        any time, and deleting it removes it from your analytics.
      </p>
    ),
  },
  {
    id: "receipt-ocr",
    title: "Receipt OCR Processing",
    body: (
      <>
        <p>
          When you upload a receipt image, we process it to extract details like the merchant,
          amount, and date. Extraction may be performed by an automated on-device OCR engine and, if
          configured, by a third-party OCR service to which the receipt image is sent for
          processing.
        </p>
        <p>
          Automated extraction can be <strong>inaccurate</strong>. Results are presented to you as a
          draft: nothing is saved as a transaction until you review the extracted values, correct
          anything that is wrong, and confirm. Uploaded images are stored to support that review and
          your records; you can delete a receipt at any time.
        </p>
      </>
    ),
  },
  {
    id: "csv-import",
    title: "CSV Import Processing",
    body: (
      <p>
        When you import a CSV file, it is parsed on our server to create transactions. The file is
        validated (type and size) before processing; rows that cannot be understood are skipped and
        reported back to you rather than saved incorrectly. We keep the resulting transactions, not
        the uploaded file itself.
      </p>
    ),
  },
  {
    id: "sms-import",
    title: "SMS Import",
    body: (
      <p>
        {LEGAL.product} does not currently import or read SMS messages. If we add this in the future,
        it will be optional and this policy will be updated to describe exactly what is processed.
      </p>
    ),
  },
  {
    id: "ai-insights",
    title: "AI & Insight Generation",
    body: (
      <>
        <p>
          Most insights (spending patterns, leaks, budgets, projections) are computed by deterministic
          logic running on your own data. Some receipt extraction uses AI-assisted processing.
        </p>
        <p>
          All automated analysis is <strong>informational only</strong>. It may contain errors and is
          not financial, tax, legal, or investment advice. You remain responsible for your financial
          decisions.
        </p>
      </>
    ),
  },
  {
    id: "authentication",
    title: "Authentication & Account Information",
    body: (
      <p>
        You sign in with an email and password. Passwords are hashed with BCrypt before storage, so we
        never keep the plaintext. Sessions use signed JSON Web Tokens (JWT). Password-reset links are
        single-use and time-limited.
      </p>
    ),
  },
  {
    id: "cookies",
    title: "Cookies & Local Storage",
    body: (
      <p>
        We do not use advertising or cross-site tracking cookies. To keep you signed in, the app
        stores your session token in your browser&apos;s local storage. Clearing your browser storage or
        signing out removes it.
      </p>
    ),
  },
  {
    id: "analytics",
    title: "Analytics",
    body: (
      <p>
        We do not use third-party analytics, advertising, or behavioral-tracking services in the app.
      </p>
    ),
  },
  {
    id: "data-retention",
    title: "Data Retention",
    body: (
      <p>
        We keep your data while your account is active so the service works. You can delete individual
        transactions, receipts, budgets, and goals at any time. If you want your entire account and
        data deleted, contact us and we will remove it. Security audit logs may be retained for a
        limited period for abuse prevention.
      </p>
    ),
  },
  {
    id: "user-rights",
    title: "Your Rights",
    body: (
      <>
        <p>You can, at any time:</p>
        <ul>
          <li>Access and review the data in your account.</li>
          <li>Correct or update your transactions and profile.</li>
          <li>Export your transactions as a CSV file.</li>
          <li>Delete individual records, or request deletion of your whole account.</li>
        </ul>
      </>
    ),
  },
  {
    id: "security",
    title: "Security Practices",
    body: (
      <>
        <p>Measures we actually use include:</p>
        <ul>
          <li>Password hashing with BCrypt and JWT-based authentication.</li>
          <li>Transport encryption (HTTPS) in production.</li>
          <li>Security response headers (including Content-Security-Policy and HSTS).</li>
          <li>Input validation and file-type checks on uploads.</li>
          <li>Rate limiting on sensitive endpoints.</li>
          <li>Per-user data isolation, so each account only sees its own data.</li>
        </ul>
        <p>
          No method of storage or transmission is completely secure, so we cannot guarantee absolute
          security.
        </p>
      </>
    ),
  },
  {
    id: "third-parties",
    title: "Third-Party Services",
    body: (
      <>
        <p>We rely on a small number of providers to run the service:</p>
        <ul>
          <li>Cloud hosting for the application and database.</li>
          <li>A transactional email provider to deliver messages such as password resets.</li>
          <li>An OCR service for receipt extraction, when configured (receipt images are sent to it).</li>
        </ul>
        <p>
          These providers process data only to perform their function for us. We do not sell or share
          your data for their own marketing.
        </p>
      </>
    ),
  },
  {
    id: "children",
    title: "Children's Privacy",
    body: (
      <p>
        {LEGAL.product} is not directed to children under 18, and we do not knowingly collect data from
        them. If you believe a child has provided us information, contact us and we will delete it.
      </p>
    ),
  },
  {
    id: "international",
    title: "International Users",
    body: (
      <p>
        Our servers and service providers may be located in countries other than yours. By using
        {" "}
        {LEGAL.product}, you understand that your data may be processed and stored in those locations.
      </p>
    ),
  },
  {
    id: "changes",
    title: "Changes to This Policy",
    body: (
      <p>
        We may update this policy as the product evolves. When we do, we will revise the &quot;Last
        updated&quot; date above, and for significant changes we will provide a more prominent notice.
      </p>
    ),
  },
  {
    id: "contact",
    title: "Contact Information",
    body: (
      <p>
        Questions about privacy or your data? Email us at{" "}
        <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>.
      </p>
    ),
  },
];

export default function PrivacyPolicyPage() {
  return (
    <LegalPage
      title="Privacy Policy"
      intro={
        <p>
          This Privacy Policy describes how {LEGAL.product} handles the information you provide when
          you use the service. We aim to keep it short and honest.
        </p>
      }
      sections={sections}
    />
  );
}
