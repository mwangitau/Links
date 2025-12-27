package com.githow.links.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.githow.links.data.entity.ParseStatus
import com.githow.links.data.entity.EntrySource

/**
 * ParseStatusBadge - Color-coded badge for SMS parse status
 *
 * Color scheme:
 * 🟢 PARSED_SUCCESS    - Green (success)
 * 🔴 PARSE_ERROR       - Red (needs attention)
 * 🟠 MANUAL_REVIEW     - Orange (in progress)
 * 🔵 MANUALLY_ENTERED  - Blue (completed manually)
 * ⚪ UNPROCESSED       - Gray (pending)
 */
@Composable
fun ParseStatusBadge(
    parseStatus: ParseStatus,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    compact: Boolean = false
) {
    val (backgroundColor, textColor, icon, label) = when (parseStatus) {
        ParseStatus.PARSED_SUCCESS -> ParseStatusStyle(
            backgroundColor = Color(0xFF4CAF50),  // Green
            textColor = Color.White,
            icon = Icons.Default.CheckCircle,
            label = "Parsed"
        )
        ParseStatus.PARSE_ERROR -> ParseStatusStyle(
            backgroundColor = Color(0xFFE53935),  // Red
            textColor = Color.White,
            icon = Icons.Default.Warning,
            label = "Parse Error"
        )
        ParseStatus.MANUAL_REVIEW -> ParseStatusStyle(
            backgroundColor = Color(0xFFFF9800),  // Orange
            textColor = Color.White,
            icon = Icons.Default.Edit,
            label = "Reviewing"
        )
        ParseStatus.MANUALLY_ENTERED -> ParseStatusStyle(
            backgroundColor = Color(0xFF2196F3),  // Blue
            textColor = Color.White,
            icon = Icons.Default.Edit,
            label = "Manual Entry"
        )
        ParseStatus.UNPROCESSED -> ParseStatusStyle(
            backgroundColor = Color(0xFF9E9E9E),  // Gray
            textColor = Color.White,
            icon = Icons.Default.Info,
            label = "Unprocessed"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 4.dp else 8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showIcon) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(if (compact) 12.dp else 16.dp)
                )
            }

            Text(
                text = label,
                color = textColor,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * EntrySourceBadge - Shows if transaction was auto-parsed or manual
 */
@Composable
fun EntrySourceBadge(
    entrySource: EntrySource,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (backgroundColor, textColor, icon, label) = when (entrySource) {
        EntrySource.AUTO_PARSED -> ParseStatusStyle(
            backgroundColor = Color(0xFF4CAF50),  // Green
            textColor = Color.White,
            icon = Icons.Default.Info,
            label = "Auto"
        )
        EntrySource.MANUAL_SUPERVISOR -> ParseStatusStyle(
            backgroundColor = Color(0xFF2196F3),  // Blue
            textColor = Color.White,
            icon = Icons.Default.Person,
            label = "Manual"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 4.dp else 8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(if (compact) 12.dp else 16.dp)
            )

            Text(
                text = label,
                color = textColor,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Data class for badge styling
 */
private data class ParseStatusStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector,
    val label: String
)

/**
 * Preview with all statuses
 */
@Composable
fun ParseStatusBadgeRow(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ParseStatus.values().forEach { status ->
            ParseStatusBadge(parseStatus = status)
        }
    }
}