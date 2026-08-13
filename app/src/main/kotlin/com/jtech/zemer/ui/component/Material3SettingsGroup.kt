package com.jtech.zemer.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * A Material 3 Expressive style settings group component
 * @param title The title of the settings group
 * @param items List of settings items to display
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section title
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
            )
        }
        
        // The per-item card stack ([settingsCardCorners], shared with SettingsCardGroup): each
        // row is its own card, outer corners large, seams small, 4dp gaps - one geometry for
        // every settings surface so the two group components can never drift.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap)
        ) {
            items.forEachIndexed { index, item ->
                // No animateContentSize here: it was the SINGLE resizing card's affordance. On
                // per-item cards it ran one size animation per row on every screen open (the
                // rough settle-in), and row membership changes restructure the list anyway,
                // which a size animation cannot express.
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    // Shape, gap and fill come from SettingsCardGroup's shared pieces - a
                    // designer tweak lands on every card-stack surface at once.
                    shape = settingsCardShape(index, items.size),
                    colors = CardDefaults.cardColors(containerColor = settingsCardFill()),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Material3SettingsItemRow(item = item)
                }
            }
        }
    }
}

/**
 * Individual settings item row with Material 3 styling
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused && focusVisualsEnabled()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        label = "settings_item_focus_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused && focusVisualsEnabled()) MaterialTheme.colorScheme.outline else Color.Transparent,
        label = "settings_item_focus_border"
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clickable(
                    enabled = item.onClick != null,
                    onClick = { item.onClick?.invoke() }
                )
                .background(backgroundColor)
                .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with background
            item.icon?.let { icon ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (item.isHighlighted) 0.15f else 0.1f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.showBadge) {
                        BadgedBox(
                            badge = { 
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            }
                        ) {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = if (item.isHighlighted) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = if (item.isHighlighted) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Title and description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title content
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    item.title()
                }
                
                // Description if provided
                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    desc()
                }
            }
            
            // Trailing content
            item.trailingContent?.let { trailing ->
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/**
 * Data class for Material 3 settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val onClick: (() -> Unit)? = null
)
