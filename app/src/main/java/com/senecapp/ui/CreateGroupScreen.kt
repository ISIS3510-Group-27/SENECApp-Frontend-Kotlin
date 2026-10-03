package com.senecapp.ui


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senecapp.data.Interest

@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState = CreateGroupUiState(catalogueLoading = false),
    onLoadCatalogue: () -> Unit = {},
    onName: (String) -> Unit = {},
    onCategory: (String) -> Unit = {},
    onDescription: (String) -> Unit = {},
    onContactEmail: (String) -> Unit = {},
    onToggleInterest: (Int) -> Unit = {},
    onSubmit: () -> Unit = {},
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onLoadCatalogue() }

    DemoView("Propose a group", showSampleLabel = false) {
        state.created?.let { created ->
            Confirmation(created.name, created.reviewStatus, created.tags, onDone)
            return@DemoView
        }

        TextButton(onClick = onBack) { Text("← Back to My RSOs", color = viewAccent) }

        when {
            state.catalogueLoading -> ViewText("Loading categories…", color = viewMuted)
            state.catalogueError != null -> {
                ViewText(state.catalogueError)
                TextButton(onClick = onLoadCatalogue) { Text("Retry", color = viewAccent) }
            }
            else -> {
                Field("Name", state.name, onName, state.nameError, "Club de Robótica Uniandes")
                CategoryPicker(state, onCategory)
                Field(
                    label = "Description",
                    value = state.description,
                    onValueChange = onDescription,
                    error = state.descriptionError,
                    placeholder = "What the group does, when it meets, who it is for.",
                    singleLine = false,
                )
                Field("Contact email (optional)", state.contactEmail, onContactEmail, state.emailError,
                    "club@uniandes.edu.co")
                InterestChips(state, onToggleInterest)

                state.error?.let { ViewText(it, color = viewAccent) }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = state.canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = viewAccent, contentColor = viewBackground,
                        disabledContainerColor = viewSecondary, disabledContentColor = viewMuted,
                    ),
                    onClick = onSubmit,
                ) {
                    Text(
                        if (state.submitting) "Sending…" else "Send proposal",
                        fontFamily = viewNunito,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                ViewText("An administrator reviews every proposal before it appears in Discover.",
                    color = viewMuted)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    placeholder: String,
    singleLine: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ViewText(label.uppercase(), color = viewMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 4,
            isError = error != null,
            placeholder = { Text(placeholder, color = viewMuted, fontFamily = viewNunito, fontSize = 13.sp) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = viewForeground, unfocusedTextColor = viewForeground,
                focusedBorderColor = viewAccent, unfocusedBorderColor = viewSecondary,
                cursorColor = viewAccent, errorBorderColor = viewPrimary,
            ),
        )
        error?.let { ViewText(it, color = viewAccent) }
    }
}

@Composable
private fun CategoryPicker(state: CreateGroupUiState, onCategory: (String) -> Unit) {
    ViewText("CATEGORY", color = viewMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.categories.forEach { category ->
            FilterChip(
                selected = state.categorySlug == category.slug,
                onClick = { onCategory(category.slug) },
                label = { Text(category.label, fontFamily = viewNunito) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = viewSecondary, labelColor = viewForeground,
                    selectedContainerColor = viewAccent, selectedLabelColor = viewBackground,
                ),
            )
        }
    }
}

@Composable
private fun InterestChips(state: CreateGroupUiState, onToggle: (Int) -> Unit) {
    // Everything the student might pick: what was inferred, plus anything they added by hand.
    val shown = (state.suggested + state.interests.filter { it.id in state.selectedInterestIds })
        .distinctBy { it.id }
    ViewText("INTERESTS", color = viewMuted)
    when {
        state.interests.isEmpty() -> ViewText("No interests available.", color = viewMuted)
        shown.isEmpty() -> ViewText(
            "Keep writing — interests are suggested from the name and description.",
            color = viewMuted,
        )
        else -> {
            ViewText("Suggested from what you wrote. Tap to change them.", color = viewMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { interest -> InterestChip(interest, interest.id in state.selectedInterestIds, onToggle) }
            }
            if (state.selectedInterestIds.isEmpty()) {
                ViewText("With none selected, the backend infers them from your text instead.",
                    color = viewMuted)
            }
        }
    }
}

@Composable
private fun InterestChip(interest: Interest, selected: Boolean, onToggle: (Int) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(interest.id) },
        label = { Text(interest.name, fontFamily = viewNunito) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = viewSecondary, labelColor = viewForeground,
            selectedContainerColor = viewAccent, selectedLabelColor = viewBackground,
        ),
    )
}

@Composable
private fun Confirmation(name: String, reviewStatus: String, tags: List<String>, onDone: () -> Unit) {
    ViewCard {
        ViewText("Proposal sent", heading = true)
        ViewText(name, color = viewAccent)
        ViewText(
            when (reviewStatus) {
                "pending" -> "Status: pending review. It shows in My RSOs with a badge until an administrator approves it."
                "approved" -> "Status: approved."
                else -> "Status: $reviewStatus"
            },
            color = viewMuted,
        )
        if (tags.isNotEmpty()) ViewText("Interests attached: ${tags.joinToString(", ")}", color = viewMuted)
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = viewAccent, contentColor = viewBackground),
        onClick = onDone,
    ) {
        Text("Back to My RSOs", fontFamily = viewNunito, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun CreateGroupPreview() {
    CreateGroupScreen()
}
