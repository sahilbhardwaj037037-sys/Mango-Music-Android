# Mango Music Android — Feedback Backend Setup Guide

This guide describes how to configure the Google Sheets and Google Apps Script backend to receive feedback from Mango Music Android and deliver instant email notifications to `bysahilworks@gmail.com`.

---

## 1. Create the Google Sheet
1. Navigate to [Google Sheets](https://sheets.new) while logged into your Google account.
2. Title the spreadsheet: **Mango Music - User Feedback**.
3. (Optional) You can leave sheet 1 blank; the script will automatically create and format the header row on the first feedback submission.

---

## 2. Add Google Apps Script
1. In your newly created Google Sheet, click **Extensions** > **Apps Script**.
2. Erase the default empty `myFunction` in `Code.gs`.
3. Copy the entire contents of [`docs/feedback/GoogleAppsScript.js`](./GoogleAppsScript.js) and paste it into the script editor.
4. Verify that `NOTIFICATION_EMAIL` is set to `bysahilworks@gmail.com`:
   ```javascript
   const NOTIFICATION_EMAIL = "bysahilworks@gmail.com";
   ```
5. Click **Save project** (disk icon or `Cmd + S` / `Ctrl + S`).

---

## 3. Deploy as a Web App
1. At the top-right of the Apps Script editor, click **Deploy** > **New deployment**.
2. Click the gear icon next to *Select type* and choose **Web app**.
3. Configure the deployment settings:
   - **Description**: `Mango Music Feedback Endpoint`
   - **Execute as**: `Me (<your-email>)`
   - **Who has access**: `Anyone` *(Note: This allows the Android app to submit feedback securely via HTTPS POST without embedding Google API keys or credentials in the app; your spreadsheet remains private to your account).*
4. Click **Deploy**.
5. When prompted for authorization:
   - Click **Authorize access**.
   - Select your Google account.
   - Click **Advanced** > **Go to Mango Music Feedback Service (unsafe)** if Google shows an unverified app screen (normal for personal scripts).
   - Click **Allow**.
6. Copy the generated **Web app URL** (format: `https://script.google.com/macros/s/.../exec`).

---

## 4. Connect to Android App
1. Open [`app/src/main/java/com/mangomusic/feedback/FeedbackConfig.kt`](../../app/src/main/java/com/mangomusic/feedback/FeedbackConfig.kt).
2. Set `DEFAULT_WEB_APP_URL` to your Web App URL:
   ```kotlin
   const val DEFAULT_WEB_APP_URL: String = "https://script.google.com/macros/s/AKfycbx.../exec"
   ```
3. That's it! When a user submits feedback in the app:
   - The feedback is recorded in your private Google Sheet.
   - An email is immediately sent to `bysahilworks@gmail.com`.
   - If the user provided a reply email, hitting "Reply" in your email client will reply directly to them.
