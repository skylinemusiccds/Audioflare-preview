package com.universe.audioflare.ui.component

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.universe.audioflare.R
import com.universe.audioflare.ui.theme.typo

@Composable
fun DescriptionView(
    modifier: Modifier = Modifier,
    text: String,
    limitLine: Int = 3,
    onTimeClicked: (time: String) -> Unit,
    onURLClicked: (url: String) -> Unit,
) {

    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    val maxLineAnimated by animateIntAsState(
        targetValue = if (expanded) 1000 else limitLine
    )
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }


    val timeRegex = Regex("""(\d+):(\d+)(?::(\d+))?""")
    val urlRegex = Regex("""https?://\S+""")
    val annotatedString = AnnotatedString.Builder()
    var currentIndex = 0
    val style = SpanStyle(
        color = Color(0xFF00B0FF), fontWeight = FontWeight.Normal
    )
    val combinedRegex = Regex("${timeRegex.pattern}|${urlRegex.pattern}")
    val matchedWords = combinedRegex.findAll(text)
    matchedWords.forEachIndexed { index, matchResult ->
        // Add text before the match
        if (matchResult.range.first > currentIndex) {
            annotatedString.append(text.substring(currentIndex, matchResult.range.first))
        }

        // Add the matched text with the given style
        annotatedString.withStyle(style) {
            if (timeRegex.matches(matchResult.value)) {
                pushStringAnnotation("CLICKABLE_USER_TIME", matchResult.value)
                append(matchResult.value)
                pop()
            } else if (urlRegex.matches(matchResult.value)) {
                pushStringAnnotation("CLICKABLE_USER_URL", matchResult.value)
                append(matchResult.value)
                pop()
            }
        }
        if (index == matchedWords.count() - 1) {
            annotatedString.append(text.substring(matchResult.range.last + 1, text.length))
        }

        // Update the current index to the end of the match
        currentIndex = matchResult.range.last + 1
    }
    if (matchedWords.count() == 0) {
        annotatedString.append(text)
    }

    Column(modifier.animateContentSize()) {
        Text(
            text = annotatedString.toAnnotatedString(),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        layoutResult?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(offset)
                            Log.w("DescriptionView", "Position: $position")
                            annotatedString
                                .toAnnotatedString()
                                .getStringAnnotations(
                                    start = position,
                                    end = position,
                                )
                                .firstOrNull { annotation ->
                                    Log.w("DescriptionView", "Annotation: ${annotation.tag}")
                                    annotation.tag.startsWith("CLICKABLE_USER_")
                                }
                                ?.let { annotation ->
                                    when (annotation.tag) {
                                        "CLICKABLE_USER_TIME" -> {
                                            Log.w("DescriptionView", "Time clicked: ${annotation.item}")
                                            onTimeClicked(annotation.item)
                                        }

                                        "CLICKABLE_USER_URL" -> {
                                            Log.w("DescriptionView", "URL clicked: ${annotation.item}")
                                            onURLClicked(annotation.item)
                                        }
                                    }
                                }
                        }
                    }
                },
            maxLines = maxLineAnimated,
            onTextLayout = { layoutResult = it },
            style = typo.bodyMedium
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = if (expanded) stringResource(id = R.string.less) else stringResource(id = R.string.more),
            color = Color.LightGray,
            modifier = Modifier.clickable {
                expanded = !expanded
            },
            style = typo.labelSmall
        )
    }
}