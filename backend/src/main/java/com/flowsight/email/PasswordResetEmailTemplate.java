package com.flowsight.email;

/**
 * Renders the password-reset email body. Provider-agnostic: Gmail SMTP, Resend,
 * or any future {@link EmailService} implementation share this markup so the
 * branding stays identical regardless of transport.
 */
final class PasswordResetEmailTemplate {

    static final String SUBJECT = "Reset your FlowSight password";

    private PasswordResetEmailTemplate() {
    }

    static String html(String name, String resetUrl) {
        String safeName = htmlEscape(name);
        String safeUrl  = htmlEscape(resetUrl);
        return """
            <!DOCTYPE html>
            <html>
              <body style="margin:0;padding:0;background:#f7f6f1;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#0f172a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f7f6f1;padding:48px 16px;">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="100%%" style="max-width:520px;background:#ffffff;border-radius:14px;border:1px solid rgba(15,23,42,0.06);box-shadow:0 1px 1px rgba(15,23,42,0.025),0 2px 6px -2px rgba(15,23,42,0.04);" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="padding:40px 40px 8px;">
                            <p style="margin:0;font-size:13px;font-weight:600;letter-spacing:0.04em;color:#475569;text-transform:uppercase;">FlowSight</p>
                            <h1 style="margin:18px 0 0;font-size:22px;font-weight:600;letter-spacing:-0.015em;color:#0f172a;">Reset your password</h1>
                            <p style="margin:14px 0 0;font-size:15px;line-height:1.55;color:#475569;">Hi %s, we received a request to reset your password. Use the button below to set a new one.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 40px 8px;">
                            <a href="%s" style="display:inline-block;background:#0f172a;color:#ffffff;text-decoration:none;font-size:14px;font-weight:500;padding:12px 22px;border-radius:10px;">Reset password</a>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 40px 36px;">
                            <p style="margin:0;font-size:12px;line-height:1.6;color:#64748b;">If the button does not work, paste this link into your browser:</p>
                            <p style="margin:6px 0 0;font-size:12px;line-height:1.6;color:#475569;word-break:break-all;"><a href="%s" style="color:#475569;text-decoration:underline;">%s</a></p>
                            <p style="margin:20px 0 0;font-size:12px;line-height:1.6;color:#94a3b8;">This link is valid for 30 minutes and can be used once. If you did not request a password reset, you can ignore this message.</p>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:24px 0 0;font-size:11px;color:#94a3b8;">FlowSight will never ask you to share your password.</p>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(safeName, safeUrl, safeUrl, safeUrl);
    }

    static String text(String name, String resetUrl) {
        return """
            Hi %s,

            We received a request to reset your FlowSight password.
            Open this link to set a new one:

              %s

            This link is valid for 30 minutes and can be used once.
            If you did not request a password reset, you can ignore this email.

            — FlowSight
            """.formatted(name, resetUrl);
    }

    private static String htmlEscape(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
