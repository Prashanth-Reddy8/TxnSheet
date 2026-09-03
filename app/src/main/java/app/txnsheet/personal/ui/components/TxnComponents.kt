package app.txnsheet.personal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Atm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.txnsheet.personal.data.local.TransactionEntity
import app.txnsheet.personal.ui.formatMoney
import app.txnsheet.personal.ui.formatTime
import app.txnsheet.personal.ui.redactSourceText
import app.txnsheet.personal.ui.statusLabel
import app.txnsheet.personal.ui.theme.LocalTxnSemanticColors
import app.txnsheet.personal.ui.theme.TxnMono
import app.txnsheet.personal.ui.theme.TxnSpacing
import app.txnsheet.personal.ui.transactionA11yLabel

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

@Composable
fun MoneyText(
    amountMinor: Long,
    currency: String,
    direction: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val semantic = LocalTxnSemanticColors.current
    val color = when (direction) {
        "DEBIT" -> semantic.debit
        "CREDIT" -> semantic.credit
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = formatMoney(amountMinor, currency, direction),
        modifier = modifier,
        color = color,
        style = if (prominent) {
            MaterialTheme.typography.displayLarge.copy(
                fontFamily = TxnMono,
                fontFeatureSettings = "tnum",
            )
        } else {
            MaterialTheme.typography.titleMedium.copy(
                fontFamily = TxnMono,
                fontFeatureSettings = "tnum",
            )
        },
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    zoneId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val a11yLabel = transactionA11yLabel(transaction, zoneId)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = a11yLabel },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TxnSpacing.rowMinHeight)
                .padding(vertical = 12.dp),
        ) {
            val stacked = maxWidth < 340.dp || fontScale >= 1.3f
            if (stacked) {
                Row(verticalAlignment = Alignment.Top) {
                    MethodGlyph(transaction.method)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        TransactionIdentity(transaction, zoneId)
                        Spacer(Modifier.height(8.dp))
                        transaction.amountMinor?.let {
                            MoneyText(
                                amountMinor = it,
                                currency = transaction.currency,
                                direction = transaction.direction,
                            )
                        } ?: Text("Amount needed", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(6.dp))
                        TransactionStatusTag(transaction.status)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MethodGlyph(transaction.method)
                    Spacer(Modifier.width(12.dp))
                    TransactionIdentity(transaction, zoneId, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        transaction.amountMinor?.let {
                            MoneyText(
                                amountMinor = it,
                                currency = transaction.currency,
                                direction = transaction.direction,
                            )
                        } ?: Text("Amount needed", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        TransactionStatusTag(transaction.status, compact = true)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TransactionIdentity(
    transaction: TransactionEntity,
    zoneId: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = transaction.counterparty?.takeIf(String::isNotBlank)
                ?: transaction.institution?.takeIf(String::isNotBlank)
                ?: "Transaction",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOfNotNull(
                transaction.category,
                transaction.method.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase),
                transaction.accountLast4?.let { "••$it" },
                formatTime(transaction.eventTimeEpochMs, zoneId),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MethodGlyph(method: String) {
    val icon = when (method) {
        "CARD" -> Icons.Outlined.CreditCard
        "UPI" -> Icons.Outlined.PhoneAndroid
        "BANK_TRANSFER" -> Icons.Outlined.AccountBalance
        "ATM" -> Icons.Outlined.Atm
        "CASH", "FEE" -> Icons.Outlined.Payments
        else -> Icons.Outlined.ReceiptLong
    }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun TransactionStatusTag(
    status: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val semantic = LocalTxnSemanticColors.current
    val normalized = status.uppercase()
    val (foreground, background) = when (normalized) {
        "LOCAL", "SYNCED" -> semantic.credit to semantic.creditContainer
        "REVIEW" -> semantic.review to semantic.reviewContainer
        "FAILED" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        "IGNORED" -> semantic.ignored to MaterialTheme.colorScheme.surfaceVariant
        else -> semantic.queued to semantic.queuedContainer
    }
    val icon = statusIcon(normalized)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = background,
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            if (!compact || normalized !in setOf("LOCAL", "SYNCED")) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun statusIcon(status: String): ImageVector = when (status) {
    "LOCAL", "SYNCED" -> Icons.Outlined.CheckCircle
    "REVIEW" -> Icons.Outlined.Visibility
    "FAILED" -> Icons.Outlined.ErrorOutline
    "IGNORED" -> Icons.Outlined.RemoveCircleOutline
    else -> Icons.Outlined.HourglassTop
}

@Composable
fun HealthBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val semantic = LocalTxnSemanticColors.current
    val (contentColor, containerColor, icon) = when (tone) {
        BannerTone.Good -> Triple(semantic.credit, semantic.creditContainer, Icons.Outlined.CheckCircle)
        BannerTone.Warning -> Triple(semantic.review, semantic.reviewContainer, Icons.Outlined.ErrorOutline)
        BannerTone.Error -> Triple(
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer,
            Icons.Outlined.ErrorOutline,
        )
        BannerTone.Info -> Triple(semantic.queued, semantic.queuedContainer, Icons.Outlined.MoreHoriz)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = actionLabel,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = onAction)
                            .sizeIn(minHeight = 48.dp)
                            .padding(vertical = 13.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

enum class BannerTone { Good, Warning, Error, Info }

@Composable
fun SensitiveSourcePreview(
    sourceText: String?,
    modifier: Modifier = Modifier,
) {
    val redacted = sourceText?.takeIf(String::isNotBlank)?.let(::redactSourceText)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("REDACTED SOURCE", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = redacted ?: "Source text is unavailable. Compare this item in the original app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ReadinessRow(
    title: String,
    supportingText: String,
    ready: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalTxnSemanticColors.current
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 64.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ready) semantic.credit else semantic.review,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    supportingText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = TxnSpacing.rowMinHeight)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke() ?: Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun TxnEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ReceiptLong,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun StickyActionDock(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    destructiveLabel: String? = null,
    onDestructive: (() -> Unit)? = null,
    working: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled && !working,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(primaryLabel)
            }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(secondaryLabel)
                }
            }
            if (destructiveLabel != null && onDestructive != null) {
                Button(
                    onClick = onDestructive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(destructiveLabel)
                }
            }
        }
    }
}
