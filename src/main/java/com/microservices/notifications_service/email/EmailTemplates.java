package com.microservices.notifications_service.email;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class EmailTemplates {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'");

    private EmailTemplates() {}

    /**
     * Renders a styled order-status notification email.
     *
     * @param customerName display name shown in greeting (falls back to "there")
     * @param orderId      order identifier shown in the summary card
     * @param status       normalised status string (e.g. "PREPARING")
     * @param title        short title shown in the coloured status banner
     * @param baseMessage  body sentence WITHOUT notes appended
     * @param notes        optional restaurant note; rendered in a separate callout block
     */
    public static String orderStatus(String customerName, String orderId,
                                     String status, String title,
                                     String baseMessage, String notes) {

        String safeStatus   = status == null || status.isBlank() ? "UPDATED" : status.toUpperCase();
        String accentColor  = bannerColor(safeStatus);
        String badgeColor   = badgeColor(safeStatus);
        String timestamp    = ZonedDateTime.now(ZoneId.of("UTC")).format(DISPLAY_FMT);
        String notesBlock   = buildNotesBlock(notes);

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1.0">
                  <title>FoodChain — %s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3f4f6;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%%;">

                  <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background-color:#f3f4f6;padding:40px 16px;">
                    <tr><td align="center">

                      <!-- ── Card ──────────────────────────────────────────── -->
                      <table width="600" cellpadding="0" cellspacing="0" border="0"
                             style="max-width:600px;width:100%%;border-radius:12px;
                                    overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.10);">

                        <!-- Brand header -->
                        <tr>
                          <td style="background-color:#e85d04;padding:22px 32px;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="vertical-align:middle;">
                                  <span style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;line-height:1;">Food</span><span style="font-size:22px;font-weight:800;color:#ffd8b4;line-height:1;">Chain</span>
                                </td>
                                <td style="text-align:right;vertical-align:middle;">
                                  <span style="font-size:10px;color:#ffa87d;text-transform:uppercase;letter-spacing:1.5px;">Order Notification</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Status banner -->
                        <tr>
                          <td style="background-color:%s;padding:18px 32px;">
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td style="vertical-align:middle;">
                                  <p style="margin:0;font-size:19px;font-weight:700;color:#ffffff;line-height:1.25;">%s</p>
                                </td>
                                <td style="text-align:right;vertical-align:middle;">
                                  <span style="display:inline-block;background-color:rgba(255,255,255,0.22);
                                               color:#ffffff;font-size:11px;font-weight:700;
                                               padding:4px 13px;border-radius:20px;text-transform:uppercase;
                                               letter-spacing:0.4px;border:1px solid rgba(255,255,255,0.38);">%s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="background-color:#ffffff;padding:28px 32px 4px;">
                            <p style="margin:0 0 6px;font-size:16px;color:#111827;">Hi <strong>%s</strong>,</p>
                            <p style="margin:0 0 24px;font-size:15px;color:#4b5563;line-height:1.7;">%s</p>
                            %s
                          </td>
                        </tr>

                        <!-- Order summary card -->
                        <tr>
                          <td style="background-color:#ffffff;padding:0 32px 28px;">
                            <table width="100%%" cellpadding="0" cellspacing="0"
                                   style="background-color:#f9fafb;border-radius:8px;border:1px solid #e5e7eb;">
                              <tr>
                                <td style="padding:18px 22px;">
                                  <p style="margin:0 0 12px;font-size:10px;font-weight:700;color:#9ca3af;
                                            text-transform:uppercase;letter-spacing:0.8px;">Order Summary</p>
                                  <table width="100%%" cellpadding="0" cellspacing="0">
                                    <tr>
                                      <td style="padding:7px 0;border-bottom:1px solid #e5e7eb;">
                                        <span style="font-size:13px;color:#6b7280;">Order ID</span>
                                      </td>
                                      <td style="padding:7px 0;border-bottom:1px solid #e5e7eb;text-align:right;">
                                        <span style="font-size:12px;font-weight:600;color:#111827;
                                                     font-family:Courier New,Courier,monospace;
                                                     background-color:#f3f4f6;padding:2px 8px;
                                                     border-radius:4px;">%s</span>
                                      </td>
                                    </tr>
                                    <tr>
                                      <td style="padding:7px 0;border-bottom:1px solid #e5e7eb;">
                                        <span style="font-size:13px;color:#6b7280;">Status</span>
                                      </td>
                                      <td style="padding:7px 0;border-bottom:1px solid #e5e7eb;text-align:right;">
                                        <span style="display:inline-block;background-color:%s;
                                                     color:#ffffff;font-size:10px;font-weight:700;
                                                     padding:3px 10px;border-radius:20px;
                                                     text-transform:uppercase;">%s</span>
                                      </td>
                                    </tr>
                                    <tr>
                                      <td style="padding:7px 0;">
                                        <span style="font-size:13px;color:#6b7280;">Updated</span>
                                      </td>
                                      <td style="padding:7px 0;text-align:right;">
                                        <span style="font-size:13px;color:#374151;">%s</span>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Closing note -->
                        <tr>
                          <td style="background-color:#ffffff;padding:0 32px 24px;text-align:center;">
                            <p style="margin:0;font-size:13px;color:#9ca3af;">Thank you for choosing FoodChain. We hope you enjoy your meal!</p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background-color:#f9fafb;padding:16px 32px;text-align:center;border-top:1px solid #e5e7eb;">
                            <p style="margin:0 0 3px;font-size:11px;color:#9ca3af;">You received this because you placed an order with FoodChain.</p>
                            <p style="margin:0;font-size:10px;color:#d1d5db;">&copy; 2025 FoodChain &mdash; All rights reserved.</p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>

                </body>
                </html>
                """.formatted(
                /* 1  <title>          */ title,
                /* 2  banner bg        */ accentColor,
                /* 3  banner title     */ esc(title),
                /* 4  badge pill       */ safeStatus,
                /* 5  greeting name    */ esc(customerName),
                /* 6  body message     */ esc(baseMessage),
                /* 7  notes block      */ notesBlock,
                /* 8  order id         */ esc(orderId),
                /* 9  badge color      */ badgeColor,
                /* 10 badge text       */ safeStatus,
                /* 11 timestamp        */ timestamp
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static String buildNotesBlock(String notes) {
        if (notes == null || notes.isBlank()) return "";
        return """
                <table width="100%%" cellpadding="0" cellspacing="0"
                       style="margin-bottom:20px;background-color:#fffbeb;border-radius:6px;">
                  <tr>
                    <td style="padding:13px 16px;border-left:3px solid #f59e0b;border-radius:0 6px 6px 0;">
                      <p style="margin:0 0 3px;font-size:10px;font-weight:700;color:#92400e;
                                text-transform:uppercase;letter-spacing:0.5px;">Note from the restaurant</p>
                      <p style="margin:0;font-size:13px;color:#78350f;line-height:1.5;">%s</p>
                    </td>
                  </tr>
                </table>
                """.formatted(esc(notes));
    }

    private static String bannerColor(String status) {
        return switch (status) {
            case "CONFIRMED", "READY", "SERVED", "COMPLETED", "PICKED_UP" -> "#059669";
            case "PREPARING"                                               -> "#d97706";
            case "RECEIVED", "ORDER_RECEIVED"                             -> "#2563eb";
            case "CANCELLED"                                               -> "#dc2626";
            default                                                        -> "#374151";
        };
    }

    private static String badgeColor(String status) {
        return switch (status) {
            case "CONFIRMED", "READY", "SERVED", "COMPLETED", "PICKED_UP" -> "#059669";
            case "PREPARING"                                               -> "#d97706";
            case "RECEIVED", "ORDER_RECEIVED"                             -> "#3b82f6";
            case "CANCELLED"                                               -> "#dc2626";
            default                                                        -> "#6b7280";
        };
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
