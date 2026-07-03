import type { Metadata } from "next";
import { LegalPage, type LegalSection } from "@/components/legal/LegalPage";
import { LEGAL } from "@/components/legal/legal-config";

export const metadata: Metadata = {
  title: "Terms of Service | FlowSight",
  description: "The terms that govern your use of FlowSight.",
};

const sections: LegalSection[] = [
  {
    id: "acceptance",
    title: "Acceptance of Terms",
    body: (
      <p>
        By creating an account or using {LEGAL.product} (the &quot;Service&quot;), you agree to these
        Terms of Service. If you do not agree, please do not use the Service.
      </p>
    ),
  },
  {
    id: "eligibility",
    title: "Eligibility",
    body: (
      <p>
        You must be at least 18 years old and able to enter into a binding agreement to use the
        Service. By using it, you confirm that you meet these requirements.
      </p>
    ),
  },
  {
    id: "what-flowsight-is",
    title: "What FlowSight Is",
    body: (
      <>
        <p>
          {LEGAL.product} is a tool for understanding your own finances. It analyzes transactions and
          receipts <strong>that you provide</strong> and surfaces spending patterns, recurring
          charges, recoverable spending, budgets, goals, and projections.
        </p>
        <p>
          It is <strong>not</strong> a bank, payment processor, accounting system, tax preparer, or
          investment advisor, and it does not connect to your financial accounts.
        </p>
      </>
    ),
  },
  {
    id: "user-responsibilities",
    title: "User Responsibilities",
    body: (
      <>
        <p>You are responsible for:</p>
        <ul>
          <li>The accuracy of the data you enter or import.</li>
          <li>Keeping your login credentials confidential.</li>
          <li>Any activity that occurs under your account.</li>
          <li>Your own financial decisions and their outcomes.</li>
        </ul>
      </>
    ),
  },
  {
    id: "acceptable-use",
    title: "Acceptable Use",
    body: (
      <>
        <p>You agree not to:</p>
        <ul>
          <li>Access or attempt to access data that is not yours.</li>
          <li>Interfere with, overload, or disrupt the Service or its infrastructure.</li>
          <li>Reverse engineer or misuse the Service beyond its intended purpose.</li>
          <li>Upload malware or content you do not have the right to submit.</li>
          <li>Use the Service to violate any applicable law.</li>
        </ul>
      </>
    ),
  },
  {
    id: "user-content",
    title: "User Content",
    body: (
      <p>
        You retain ownership of the transactions, receipts, and other content you submit. You grant us
        a limited permission to store and process that content solely to operate the Service for you,
        as described in our{" "}
        <a href="/privacy">Privacy Policy</a>. We do not claim ownership of your data.
      </p>
    ),
  },
  {
    id: "account-security",
    title: "Account Security",
    body: (
      <p>
        Protect your password and notify us of any unauthorized use of your account. We hash passwords
        and use signed session tokens, but you are responsible for activity performed with your
        credentials.
      </p>
    ),
  },
  {
    id: "data-accuracy",
    title: "Data Accuracy",
    body: (
      <p>
        Automated features such as categorization, receipt extraction (OCR), and CSV parsing can make
        mistakes. You are expected to review extracted or imported data before relying on it. Insights
        are only as accurate as the data you provide.
      </p>
    ),
  },
  {
    id: "not-financial-advice",
    title: "Not Financial Advice",
    body: (
      <p>
        {LEGAL.product} provides financial <strong>insights</strong>, not advice. Nothing in the
        Service constitutes financial, legal, tax, or investment advice, and no
        professional-client relationship is created. You are solely responsible for your financial
        decisions and should consult a qualified professional where appropriate.
      </p>
    ),
  },
  {
    id: "service-availability",
    title: "Service Availability",
    body: (
      <p>
        We aim to keep the Service available but do not guarantee uninterrupted access. We may modify,
        suspend, or discontinue features at any time, and maintenance or outages may occur without
        notice.
      </p>
    ),
  },
  {
    id: "intellectual-property",
    title: "Intellectual Property",
    body: (
      <p>
        The Service, including its software, design, and branding, belongs to {LEGAL.product} and its
        licensors. These Terms do not transfer any ownership of the Service to you. Your own data
        remains yours.
      </p>
    ),
  },
  {
    id: "feedback",
    title: "Feedback",
    body: (
      <p>
        If you send us suggestions or feedback, you allow us to use them to improve the Service without
        any obligation to you.
      </p>
    ),
  },
  {
    id: "third-parties",
    title: "Third-Party Services",
    body: (
      <p>
        The Service relies on third-party providers for hosting, email delivery, and receipt
        extraction. We are not responsible for the availability or actions of those providers beyond
        our reasonable control.
      </p>
    ),
  },
  {
    id: "disclaimer",
    title: "Disclaimer",
    body: (
      <p>
        The Service is provided &quot;as is&quot; and &quot;as available,&quot; without warranties of
        any kind, whether express or implied, including fitness for a particular purpose and accuracy
        of results, to the extent permitted by law.
      </p>
    ),
  },
  {
    id: "limitation-of-liability",
    title: "Limitation of Liability",
    body: (
      <p>
        To the maximum extent permitted by law, {LEGAL.product} will not be liable for any indirect,
        incidental, or consequential damages, or for financial losses arising from decisions you make
        based on the Service. The Service is a free tool provided to help you understand your own
        finances.
      </p>
    ),
  },
  {
    id: "termination",
    title: "Termination",
    body: (
      <p>
        You may stop using the Service and request account deletion at any time. We may suspend or
        terminate accounts that violate these Terms or misuse the Service. On termination, your right
        to use the Service ends.
      </p>
    ),
  },
  {
    id: "changes",
    title: "Changes to Terms",
    body: (
      <p>
        We may update these Terms as the Service evolves. We will update the &quot;Last updated&quot;
        date above, and continued use after changes means you accept the revised Terms.
      </p>
    ),
  },
  {
    id: "governing-law",
    title: "Governing Law",
    body: (
      <p>
        These Terms are governed by the laws of {LEGAL.governingLaw}, without regard to conflict-of-law
        rules. Any disputes will be handled in the courts of that jurisdiction.
      </p>
    ),
  },
  {
    id: "contact",
    title: "Contact Information",
    body: (
      <p>
        Questions about these Terms? Email us at{" "}
        <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>.
      </p>
    ),
  },
];

export default function TermsOfServicePage() {
  return (
    <LegalPage
      title="Terms of Service"
      intro={
        <p>
          These Terms govern your use of {LEGAL.product}. Please read them; they explain what you can
          expect from the Service and what we expect from you.
        </p>
      }
      sections={sections}
    />
  );
}
