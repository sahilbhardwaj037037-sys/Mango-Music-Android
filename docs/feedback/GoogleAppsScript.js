/**
 * =========================================================================
 * Mango Music Android — Feedback Backend Script
 * Recipient Email: bysahilworks@gmail.com
 * =========================================================================
 * 
 * Instructions:
 * 1. Open Google Sheets (https://sheets.new) in your Google account.
 * 2. Rename the spreadsheet to: "Mango Music - User Feedback"
 * 3. In the top menu, go to: Extensions > Apps Script.
 * 4. Replace any existing code in Code.gs with this entire script.
 * 5. Click "Save project" (Ctrl+S or Cmd+S).
 * 6. Click "Deploy" > "New deployment".
 * 7. In the dialog:
 *    - Click the gear icon next to "Select type" and choose "Web app".
 *    - Description: "Mango Music Feedback Service"
 *    - Execute as: "Me (your account)"
 *    - Who has access: "Anyone" (allows the Android app to POST feedback without exposing tokens)
 * 8. Click "Deploy", review/grant permissions when prompted.
 * 9. Copy the generated "Web app URL" (ending in /exec).
 * 10. Update `FeedbackConfig.DEFAULT_WEB_APP_URL` in Android app or enter it in app settings.
 */

const NOTIFICATION_EMAIL = "bysahilworks@gmail.com";

function doPost(e) {
  const lock = LockService.getScriptLock();
  // Prevent concurrent write collisions
  if (!lock.tryLock(10000)) {
    return createJsonResponse("error", "Server is busy. Please try again shortly.");
  }

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return createJsonResponse("error", "Empty payload received.");
    }

    const payload = JSON.parse(e.postData.contents);
    const category = (payload.category || "Other").toString().trim();
    const message = (payload.message || "").toString().trim();
    const email = (payload.email || "").toString().trim();
    const appName = (payload.appName || "Mango Music").toString();
    const appVersion = (payload.versionName ? payload.versionName + " (" + payload.versionCode + ")" : payload.appVersion || "1.0 (1)").toString();
    const device = (payload.deviceManufacturer ? payload.deviceManufacturer + " " + payload.deviceModel : payload.device || "Unknown").toString();
    const androidOS = (payload.androidRelease ? "Android " + payload.androidRelease + " (API " + payload.sdkInt + ")" : payload.androidOS || "Unknown").toString();
    const timestamp = (payload.timestamp || new Date().toISOString()).toString();

    if (!message) {
      return createJsonResponse("error", "Feedback message is required.");
    }

    const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();

    // Auto-create header row if sheet is newly initialized
    if (sheet.getLastRow() === 0) {
      sheet.appendRow([
        "Timestamp",
        "Category",
        "Message",
        "Reply Email",
        "App Name",
        "App Version",
        "Device",
        "Android OS"
      ]);
      // Format headers: Bold, charcoal background, frozen row
      const headerRange = sheet.getRange(1, 1, 1, 8);
      headerRange.setFontWeight("bold");
      headerRange.setBackground("#F3F3F5");
      headerRange.setFontColor("#18181B");
      sheet.setFrozenRows(1);
      sheet.setRowHeight(1, 36);
    }

    // Append the feedback row
    sheet.appendRow([
      timestamp,
      category,
      message,
      email,
      appName,
      appVersion,
      device,
      androidOS
    ]);

    // Send instant notification email to bysahilworks@gmail.com
    try {
      const subjectPreview = message.length > 40 ? message.substring(0, 40) + "..." : message;
      const subject = `[Mango Music Feedback] ${category}: ${subjectPreview}`;
      const body = `New feedback received from Mango Music Android:

Feedback Category: ${category}
Timestamp: ${timestamp}

User Message:
--------------------------------------------------
${message}
--------------------------------------------------

User Reply Email: ${email ? email : "None provided"}

Device & System Information:
- App Version: ${appVersion}
- Device: ${device}
- Android: ${androidOS}

View all submissions in your Google Sheet:
${SpreadsheetApp.getActiveSpreadsheet().getUrl()}
`;

      const mailOptions = {
        to: NOTIFICATION_EMAIL,
        subject: subject,
        body: body
      };
      if (email) {
        mailOptions.replyTo = email;
      }

      MailApp.sendEmail(mailOptions);
    } catch (mailError) {
      Logger.log("Email notification error: " + mailError.toString());
      // Keep feedback successful even if email dispatch fails
    }

    return createJsonResponse("success", "Feedback recorded successfully");

  } catch (err) {
    Logger.log("doPost error: " + err.toString());
    return createJsonResponse("error", "Failed to process feedback: " + err.toString());
  } finally {
    lock.releaseLock();
  }
}

function createJsonResponse(status, message) {
  return ContentService.createTextOutput(JSON.stringify({
    status: status,
    message: message
  })).setMimeType(ContentService.MimeType.JSON);
}

// Simple healthcheck endpoint
function doGet(e) {
  return createJsonResponse("ok", "Mango Music Feedback Service is active");
}
