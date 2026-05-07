package com.cargotracker.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends transactional emails via SMTP.
 *
 * <p>Configuration is read from system properties (set as JVM options in
 * GlassFish) or falls back to environment variables. No MicroProfile or
 * extra dependency required — Jakarta Mail ships with GlassFish 7.
 *
 * <p>Set these as JVM options in GlassFish Admin Console under
 * Configurations → server-config → JVM Options, or in domain.xml:
 * <pre>
 *   -Dmail.smtp.host=smtp.mailtrap.io
 *   -Dmail.smtp.port=587
 *   -Dmail.smtp.username=YOUR_USERNAME
 *   -Dmail.smtp.password=YOUR_PASSWORD
 *   -Dmail.from.address=noreply@cargotracker.example.com
 *   -Dmail.from.name=Cargo Tracker
 *   -Dapp.base-url=http://localhost:8080/Cargo_Tracker_System
 * </pre>
 *
 * <p>For local dev with Mailtrap, grab your SMTP credentials from
 * https://mailtrap.io — emails are caught without hitting real inboxes.
 */
@ApplicationScoped
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class.getName());

    private String smtpHost;
    private int    smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private String fromAddress;
    private String fromName;
    private String baseUrl;

    @PostConstruct
    void init() {
        smtpHost     = prop("mail.smtp.host",     "smtp.mailtrap.io");
        smtpPort     = Integer.parseInt(prop("mail.smtp.port", "587"));
        smtpUsername = prop("mail.smtp.username", "");
        smtpPassword = prop("mail.smtp.password", "");
        fromAddress  = prop("mail.from.address",  "noreply@cargotracker.example.com");
        fromName     = prop("mail.from.name",     "Cargo Tracker");
        baseUrl      = prop("app.base-url",       "http://localhost:8080/Cargo_Tracker_System");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a password-reset email with a single-use link valid for 15 minutes.
     *
     * @param toEmail  recipient address
     * @param toName   recipient display name used in the greeting
     * @param rawToken URL-safe Base64 token to embed in the reset link
     */
    public void sendPasswordResetEmail(String toEmail, String toName, String rawToken) {
        String resetLink = baseUrl + "/pages/reset-password.html?token=" + rawToken;
        send(toEmail,
             "Reset your Cargo Tracker password",
             buildResetEmailBody(toName, resetLink));
    }

    /**
     * Sends an email-verification link mailed at registration.
     *
     * <p>The link points directly at {@code GET /api/auth/verify?token=...}
     * so a one-click confirmation works without depending on a separate
     * frontend page being deployed. The endpoint returns plain HTML.
     *
     * @param toEmail  recipient address
     * @param toName   recipient display name used in the greeting
     * @param rawToken URL-safe Base64 token; remains valid for 24 hours
     */
    public void sendVerificationEmail(String toEmail, String toName, String rawToken) {
        String verifyLink = baseUrl + "/api/auth/verify?token=" + rawToken;
        send(toEmail,
             "Confirm your Cargo Tracker email",
             buildVerificationEmailBody(toName, verifyLink));
    }

    /**
     * Sends a booking confirmation email after a new cargo is created.
     *
     * <p>Call this from your cargo booking endpoint (e.g. CargoResource.java)
     * right after the new cargo is persisted. Example:
     * <pre>
     *   // Inside your @POST endpoint, after saving the cargo:
     *   String userEmail = // get from User entity or JWT claims
     *   String userName  = // get from User entity
     *   mailService.sendBookingConfirmationEmail(
     *       userEmail, userName,
     *       cargo.getTrackingNumber(),
     *       originName,          // full name e.g. "New York, US"
     *       originUnlocode,      // e.g. "USNYC"
     *       destinationName,     // full name e.g. "Rotterdam, NL"
     *       destinationUnlocode, // e.g. "NLRTM"
     *       cargo.getDescription(),
     *       cargo.getWeightKg(),
     *       cargo.getExpectedArrival() != null ? cargo.getExpectedArrival().toString() : "Not specified"
     *   );
     * </pre>
     *
     * @param toEmail            recipient address (the logged-in user's email)
     * @param toName             recipient display name for the greeting
     * @param trackingNumber     the generated tracking number
     * @param originName         full location name resolved from the UNLOCODE (e.g. "New York, US")
     * @param originCode         UNLOCODE of origin (e.g. "USNYC")
     * @param destinationName    full location name resolved from the UNLOCODE (e.g. "Rotterdam, NL")
     * @param destinationCode    UNLOCODE of destination (e.g. "NLRTM")
     * @param description        cargo description
     * @param weightKg           cargo weight in kilograms
     * @param expectedArrival    expected arrival date string, or "Not specified"
     */
    public void sendBookingConfirmationEmail(
            String toEmail,
            String toName,
            String trackingNumber,
            String originName,
            String originCode,
            String destinationName,
            String destinationCode,
            String description,
            double weightKg,
            String expectedArrival
    ) {
        String trackUrl = baseUrl + "/pages/track.html?tracking=" + trackingNumber;
        send(toEmail,
             "Booking Confirmed — " + trackingNumber,
             buildBookingEmailBody(
                 toName, trackingNumber, trackUrl,
                 originName, originCode,
                 destinationName, destinationCode,
                 description, weightKg, expectedArrival
             ));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlBody) {
        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust",       smtpHost);
        props.put("mail.smtp.host",            smtpHost);
        props.put("mail.smtp.port",            String.valueOf(smtpPort));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromAddress, fromName));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            msg.setSubject(subject);
            msg.setContent(htmlBody, "text/html; charset=utf-8");
            Transport.send(msg);
        } catch (Exception e) {
            // Never propagate — the forgot-password flow MUST return 200 whether or
            // not the email exists; surfacing a delivery failure to the caller would
            // confirm the address is valid (account-enumeration leak). We log at
            // WARNING for ops visibility but the HTTP response stays uniform.
            LOG.log(Level.WARNING, e, () -> "Failed to send mail to " + toEmail);
        }
    }

    /** Reads a system property, falling back to an environment variable, then to defaultValue. */
    private static String prop(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) return value;
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        value = System.getenv(envKey);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private String buildVerificationEmailBody(String name, String verifyLink) {
        return """
            <html>
            <body style="font-family: sans-serif; color: #333;">
              <h2>Confirm your email</h2>
              <p>Hi %s,</p>
              <p>Thanks for registering with Cargo Tracker. To finish setting
                 up your account, please confirm this email address by clicking
                 the button below.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;padding:10px 20px;background:#1a73e8;
                          color:#fff;text-decoration:none;border-radius:4px;">
                  Confirm Email
                </a>
              </p>
              <p>This link expires in <strong>24 hours</strong>.<br>
                 If you did not create this account, you can safely ignore
                 this email — no further action is needed.</p>
              <hr>
              <small>Cargo Tracker — automated message, please do not reply.</small>
            </body>
            </html>
            """.formatted(name, verifyLink);
    }

    private String buildResetEmailBody(String name, String resetLink) {
        return """
            <html>
            <body style="font-family: sans-serif; color: #333;">
              <h2>Password Reset Request</h2>
              <p>Hi %s,</p>
              <p>We received a request to reset your Cargo Tracker password.
                 Click the button below to choose a new password.</p>
              <p>
                <a href="%s"
                   style="display:inline-block;padding:10px 20px;background:#1a73e8;
                          color:#fff;text-decoration:none;border-radius:4px;">
                  Reset Password
                </a>
              </p>
              <p>This link expires in <strong>15 minutes</strong>.<br>
                 If you did not request a reset, you can safely ignore this email.</p>
              <hr>
              <small>Cargo Tracker — automated message, please do not reply.</small>
            </body>
            </html>
            """.formatted(name, resetLink);
    }

    private String buildBookingEmailBody(
            String name,
            String trackingNumber,
            String trackUrl,
            String originName,
            String originCode,
            String destinationName,
            String destinationCode,
            String description,
            double weightKg,
            String expectedArrival
    ) {
        return """
            <html>
            <body style="font-family: 'Courier New', monospace; background:#0d0f10; color:#e8eaec;
                         margin:0; padding:0;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="max-width:560px; margin:32px auto; background:#141618;
                            border:1px solid #2a2e32; border-radius:10px; overflow:hidden;">

                <!-- Header -->
                <tr>
                  <td style="background:#1c1f21; padding:24px 28px; border-bottom:1px solid #2a2e32;">
                    <span style="font-size:1rem; font-weight:700; letter-spacing:0.14em;
                                 text-transform:uppercase; color:#f59e0b;">
                      &#x2B22; CargoTrack
                    </span>
                  </td>
                </tr>

                <!-- Body -->
                <tr>
                  <td style="padding:28px;">
                    <h2 style="margin:0 0 6px; font-size:1.1rem; color:#e8eaec;
                               letter-spacing:0.08em; text-transform:uppercase;">
                      Booking Confirmed
                    </h2>
                    <p style="margin:0 0 20px; font-size:0.75rem; color:#555d66;">
                      Hi %s, your cargo has been successfully booked.
                    </p>

                    <!-- Tracking number callout -->
                    <div style="background:#1c1f21; border:1px solid #3a4048;
                                border-left:3px solid #f59e0b; border-radius:6px;
                                padding:14px 16px; margin-bottom:20px;">
                      <div style="font-size:0.62rem; letter-spacing:0.12em;
                                  text-transform:uppercase; color:#555d66; margin-bottom:4px;">
                        Tracking Number
                      </div>
                      <div style="font-size:1.1rem; font-weight:700; color:#f59e0b;
                                  letter-spacing:0.06em;">
                        %s
                      </div>
                    </div>

                    <!-- Route -->
                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="margin-bottom:20px;">
                      <tr>
                        <td style="background:#1c1f21; border:1px solid #2a2e32;
                                   border-radius:6px; padding:12px 14px; width:44%%;">
                          <div style="font-size:0.6rem; letter-spacing:0.1em;
                                      text-transform:uppercase; color:#555d66; margin-bottom:3px;">
                            Origin
                          </div>
                          <div style="font-weight:700; color:#f59e0b; font-size:0.85rem;">%s</div>
                          <div style="font-size:0.7rem; color:#8a9099; margin-top:2px;">%s</div>
                        </td>
                        <td style="text-align:center; color:#f59e0b; font-size:1.2rem;
                                   padding:0 8px;" width="12%%">
                          &#x2192;
                        </td>
                        <td style="background:#1c1f21; border:1px solid #2a2e32;
                                   border-radius:6px; padding:12px 14px; width:44%%;">
                          <div style="font-size:0.6rem; letter-spacing:0.1em;
                                      text-transform:uppercase; color:#555d66; margin-bottom:3px;">
                            Destination
                          </div>
                          <div style="font-weight:700; color:#f59e0b; font-size:0.85rem;">%s</div>
                          <div style="font-size:0.7rem; color:#8a9099; margin-top:2px;">%s</div>
                        </td>
                      </tr>
                    </table>

                    <!-- Details table -->
                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="background:#1c1f21; border:1px solid #2a2e32;
                                  border-radius:6px; overflow:hidden; margin-bottom:24px;
                                  font-size:0.75rem;">
                      <tr style="border-bottom:1px solid #2a2e32;">
                        <td style="padding:9px 14px; color:#555d66; width:40%%;
                                   text-transform:uppercase; letter-spacing:0.08em;
                                   font-size:0.62rem;">Description</td>
                        <td style="padding:9px 14px; color:#e8eaec;">%s</td>
                      </tr>
                      <tr style="border-bottom:1px solid #2a2e32;">
                        <td style="padding:9px 14px; color:#555d66;
                                   text-transform:uppercase; letter-spacing:0.08em;
                                   font-size:0.62rem;">Weight</td>
                        <td style="padding:9px 14px; color:#e8eaec;">%.1f kg</td>
                      </tr>
                      <tr>
                        <td style="padding:9px 14px; color:#555d66;
                                   text-transform:uppercase; letter-spacing:0.08em;
                                   font-size:0.62rem;">Expected Arrival</td>
                        <td style="padding:9px 14px; color:#e8eaec;">%s</td>
                      </tr>
                    </table>

                    <!-- CTA button -->
                    <p style="text-align:center; margin:0 0 8px;">
                      <a href="%s"
                         style="display:inline-block; padding:11px 28px;
                                background:#f59e0b; color:#0d0f10;
                                text-decoration:none; border-radius:4px;
                                font-weight:700; font-size:0.78rem;
                                letter-spacing:0.1em; text-transform:uppercase;">
                        Track Your Shipment
                      </a>
                    </p>
                  </td>
                </tr>

                <!-- Footer -->
                <tr>
                  <td style="background:#1c1f21; border-top:1px solid #2a2e32;
                             padding:14px 28px; font-size:0.65rem; color:#555d66;
                             text-align:center;">
                    Cargo Tracker &mdash; automated message, please do not reply.
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                name,
                trackingNumber,
                originCode,    originName,
                destinationCode, destinationName,
                description,
                weightKg,
                expectedArrival,
                trackUrl
        );
    }
}