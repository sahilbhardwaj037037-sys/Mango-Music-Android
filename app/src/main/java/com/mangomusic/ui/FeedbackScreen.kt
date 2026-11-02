package com.mangomusic.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.feedback.*
import kotlinx.coroutines.launch

private val GoldAccent = Color(0xFFE5B842)
private val DarkCardBg = Color(0xFF18181B)
private val BorderMuted = Color(0xFF2C2C30)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackClient: FeedbackClient? = null
) {
    val context = LocalContext.current
    val client = feedbackClient ?: remember(context) { FeedbackClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedCategory by rememberSaveable { mutableStateOf(FeedbackCategory.BUG_REPORT) }
    var message by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var submissionSuccess by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("feedback-screen")
    ) {
        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (submissionSuccess) {
                // Success State View
                FeedbackSuccessView(
                    onDone = onBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                // Feedback Form View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Category Selection Header
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Feedback Category",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select the option that best matches your feedback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Category Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeedbackCategory.values().forEach { category ->
                            val isSelected = category == selectedCategory
                            FeedbackCategoryChip(
                                category = category,
                                isSelected = isSelected,
                                onClick = {
                                    if (!isSubmitting) {
                                        selectedCategory = category
                                        errorMessage = null
                                    }
                                }
                            )
                        }
                    }

                    // Message Field Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Message",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAccent
                            )
                        }

                        OutlinedTextField(
                            value = message,
                            onValueChange = {
                                message = it
                                if (errorMessage != null) errorMessage = null
                            },
                            placeholder = {
                                Text(
                                    text = "Tell us what you'd like to share...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            minLines = 5,
                            maxLines = 10,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feedback-message-input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardBg,
                                unfocusedContainerColor = DarkCardBg,
                                disabledContainerColor = DarkCardBg.copy(alpha = 0.5f),
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = BorderMuted,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = GoldAccent
                            )
                        )
                    }

                    // Optional Email Field Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Your email (optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Only needed if you would like us to reply back to you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = {
                                Text(
                                    text = "name@example.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            enabled = !isSubmitting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feedback-email-input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCardBg,
                                unfocusedContainerColor = DarkCardBg,
                                disabledContainerColor = DarkCardBg.copy(alpha = 0.5f),
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = BorderMuted,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = GoldAccent
                            )
                        )
                    }

                    // Device Info Badge
                    DeviceInfoBadge(
                        appName = "Mango Music",
                        versionName = "1.0",
                        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = "Android ${Build.VERSION.RELEASE}"
                    )

                    // Error Message Banner (Preserves user message while reporting error)
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feedback-error-banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { errorMessage = null },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Dismiss error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (message.isBlank()) {
                                errorMessage = "Please enter your message before sending."
                                return@Button
                            }
                            isSubmitting = true
                            errorMessage = null

                            coroutineScope.launch {
                                val submission = client.collectSubmission(
                                    category = selectedCategory,
                                    message = message,
                                    email = email
                                )
                                val result = client.submitFeedback(submission)
                                isSubmitting = false
                                when (result) {
                                    is FeedbackSubmissionResult.Success -> {
                                        submissionSuccess = true
                                    }
                                    is FeedbackSubmissionResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        enabled = message.isNotBlank() && !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("feedback-submit-button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color(0xFF121214),
                            disabledContainerColor = Color(0xFF28282C),
                            disabledContentColor = Color(0xFF73737D)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF121214)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Sending Feedback...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Send Feedback",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FeedbackCategoryChip(
    category: FeedbackCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) GoldAccent.copy(alpha = 0.16f) else DarkCardBg
    val borderColor = if (isSelected) GoldAccent else BorderMuted
    val textColor = if (isSelected) GoldAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

@Composable
private fun DeviceInfoBadge(
    appName: String,
    versionName: String,
    deviceInfo: String,
    androidVersion: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderMuted),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.12f))
                    .border(0.5.dp, GoldAccent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "$appName v$versionName · $deviceInfo · $androidVersion",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "App and system version are attached to help diagnose issues.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeedbackSuccessView(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("feedback-success-view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GoldAccent.copy(alpha = 0.14f))
                .border(1.5.dp, GoldAccent.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Thank You!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Your feedback has been received. We appreciate your input and suggestions to make Mango Music better.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .widthIn(min = 160.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldAccent,
                contentColor = Color(0xFF121214)
            )
        ) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
