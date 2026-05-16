package com.ordbit.svenska.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ordbit.svenska.Saved
import com.ordbit.svenska.Store
import com.ordbit.svenska.Vocab
import com.ordbit.svenska.Word
import kotlinx.coroutines.launch

/* =====================================================================
   VIEWMODEL — holds state, persists everything to the device via Store
   ===================================================================== */

class OrdbitViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    // Persistent state (saved on-device). Three-state marking, like WordBit.
    var statusNo by mutableStateOf<Set<String>>(emptySet()); private set
    var statusMid by mutableStateOf<Set<String>>(emptySet()); private set
    var statusYes by mutableStateOf<Set<String>>(emptySet()); private set
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    var theme by mutableStateOf("dagsljus"); private set
    var section by mutableStateOf("A1"); private set
    var mode by mutableStateOf("las"); private set

    // Session-only UI state
    var idx by mutableStateOf(0)
    var revealed by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val s = store.load()
            statusNo = s.statusNo
            statusMid = s.statusMid
            statusYes = s.statusYes
            favorites = s.favorites
            theme = s.theme
            section = s.section
            mode = s.mode
        }
    }

    private fun persist() {
        viewModelScope.launch {
            store.save(Saved(statusNo, statusMid, statusYes, favorites, theme, section, mode))
        }
    }

    fun setSection(s: String) { section = s; idx = 0; revealed = false; persist() }
    fun setMode(m: String) { mode = m; revealed = false; persist() }
    fun setTheme(t: String) { theme = t; persist() }

    /** "no" | "mid" | "yes" | "none" */
    fun statusOf(id: String): String = when {
        id in statusYes -> "yes"
        id in statusMid -> "mid"
        id in statusNo -> "no"
        else -> "none"
    }

    /** Sets a word's mark. Passing "none" clears it. A word lives in one set only. */
    fun setStatus(id: String, status: String) {
        statusNo = statusNo - id
        statusMid = statusMid - id
        statusYes = statusYes - id
        when (status) {
            "no" -> statusNo = statusNo + id
            "mid" -> statusMid = statusMid + id
            "yes" -> statusYes = statusYes + id
        }
        persist()
    }

    /** Used by the list rows: none -> no -> mid -> yes -> none. */
    fun cycleStatus(id: String) {
        setStatus(
            id,
            when (statusOf(id)) {
                "none" -> "no"
                "no" -> "mid"
                "mid" -> "yes"
                else -> "none"
            }
        )
    }

    fun toggleFav(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        persist()
    }
}

/* ===================== HELPERS ===================== */

private fun currentWords(vm: OrdbitViewModel): List<Word> =
    if (vm.section == "FAV") Vocab.all.filter { it.id in vm.favorites }
    else Vocab.bySection(vm.section)

private fun statusColor(status: String): Color = when (status) {
    "no" -> StatusNo
    "mid" -> StatusMid
    "yes" -> StatusYes
    else -> Color.Transparent
}

/** Adaptive display size — phrases are far longer than single words. */
private fun wordSize(text: String): Int = when {
    text.length > 24 -> 26
    text.length > 15 -> 34
    else -> 44
}

private data class Question(val word: Word, val options: List<String>, val correct: Int)

private fun makeQuestion(pool: List<Word>): Question {
    val word = pool.random()
    val distractors = Vocab.all
        .filter { it.en != word.en }
        .shuffled()
        .take(3)
        .map { it.en }
    val options = (distractors + word.en).shuffled()
    return Question(word, options, options.indexOf(word.en))
}

/* =====================================================================
   ROOT
   ===================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdbitApp(speak: (String) -> Unit) {
    val vm: OrdbitViewModel = viewModel()
    val pal = paletteById(vm.theme)
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    CompositionLocalProvider(LocalPalette provides pal) {
        Box(
            Modifier.fillMaxSize().background(pal.bg2),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .background(pal.bg)
            ) {
                Header(onTheme = { sheetOpen = true })
                SectionRow(vm)
                ProgressArea(vm)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (vm.mode) {
                        "las" -> LasMode(vm, speak)
                        "kort" -> KortMode(vm, speak)
                        "test" -> TestMode(vm)
                        "lista" -> ListaMode(vm, speak)
                    }
                }
                ModeBar(vm)
            }
        }

        if (sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                sheetState = sheetState,
                containerColor = pal.surface
            ) {
                SettingsSheet(vm) { sheetOpen = false }
            }
        }
    }
}

/* ===================== HEADER ===================== */

@Composable
private fun Header(onTheme: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row {
                Text("Ord", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp, color = pal.ink)
                Text("bit", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                    fontSize = 24.sp, color = pal.accent)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "SVENSKA \u00b7 ENGELSKA",
                fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold,
                color = pal.muted
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(pal.surface)
                .border(1.dp, pal.line, RoundedCornerShape(13.dp))
                .clickable { onTheme() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u25D0", fontSize = 19.sp, color = pal.ink)
        }
    }
}

/* ===================== SECTION ROW (levels + categories) ===================== */

@Composable
private fun SectionRow(vm: OrdbitViewModel) {
    val pal = LocalPalette.current
    val ids = Vocab.sections.map { it.id } + "FAV"
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(ids) { id ->
            val active = vm.section == id
            val isFav = id == "FAV"
            val isCat = !isFav && Vocab.isCategory(id)
            val main = when {
                isFav -> "\u2605"
                isCat -> Vocab.sectionName(id)
                else -> id
            }
            val sub = when {
                isFav -> "Favoriter"
                isCat -> "kategori"
                else -> Vocab.sectionName(id)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) pal.ink else Color.Transparent)
                    .border(1.dp, if (active) pal.ink else pal.line, RoundedCornerShape(11.dp))
                    .clickable { vm.setSection(id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(main, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (active) pal.bg else pal.muted)
                Text(sub, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    color = if (active) pal.bg.copy(alpha = 0.85f) else pal.muted)
            }
        }
    }
}

/* ===================== PROGRESS ===================== */

@Composable
private fun ProgressArea(vm: OrdbitViewModel) {
    val pal = LocalPalette.current
    val words = currentWords(vm)
    val knownCount = words.count { it.id in vm.statusYes }
    val pct = if (words.isEmpty()) 0 else knownCount * 100 / words.size
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(99.dp)).background(pal.bg2)
        ) {
            Box(
                Modifier.fillMaxWidth(pct / 100f).height(6.dp)
                    .clip(RoundedCornerShape(99.dp)).background(StatusYes)
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$knownCount av ${words.size} kan du", fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, color = pal.muted)
            Text("$pct%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = pal.muted)
        }
    }
}

/* ===================== SHARED PIECES ===================== */

@Composable
private fun PosTag(text: String) {
    val pal = LocalPalette.current
    Box(
        Modifier.clip(RoundedCornerShape(7.dp)).background(pal.accentSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Bold, color = pal.accent)
    }
}

@Composable
private fun StarButton(on: Boolean, size: Int = 38, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(if (on) "\u2605" else "\u2606", fontSize = (size * 0.6).sp,
            color = if (on) pal.accent else pal.muted)
    }
}

@Composable
private fun SpeakButton(onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(pal.bg)
            .border(1.dp, pal.line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("\u25B8", fontSize = 13.sp, fontWeight = FontWeight.Black, color = pal.accent)
        Text("Uttala", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = pal.ink)
    }
}

/** The three-state marking buttons: Kan inte / Osäker / Kan. */
@Composable
private fun StatusButtons(current: String, onSet: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip("Kan inte", "no", current, Modifier.weight(1f), onSet)
        StatusChip("Os\u00e4ker", "mid", current, Modifier.weight(1f), onSet)
        StatusChip("Kan", "yes", current, Modifier.weight(1f), onSet)
    }
}

@Composable
private fun StatusChip(
    label: String,
    key: String,
    current: String,
    modifier: Modifier,
    onSet: (String) -> Unit
) {
    val pal = LocalPalette.current
    val active = current == key
    val color = statusColor(key)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) color else pal.bg)
            .border(1.5.dp, if (active) color else pal.line, RoundedCornerShape(12.dp))
            .clickable { onSet(if (active) "none" else key) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
            color = if (active) Color.White else pal.muted)
    }
}

@Composable
private fun NavButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pal.surface)
            .border(1.dp, pal.line, RoundedCornerShape(14.dp))
            .alpha(if (enabled) 1f else 0.3f)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 24.sp, color = pal.ink)
    }
}

@Composable
private fun Counter(current: Int, total: Int) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$current", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
            fontSize = 17.sp, color = pal.accent)
        Text(" av $total", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, color = pal.ink)
    }
}

@Composable
private fun ThinDivider() {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
}

@Composable
private fun EmptyState(message: String) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Text("\u2606", fontSize = 42.sp, color = pal.muted.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Text(message, fontSize = 14.sp, color = pal.muted, textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        }
    }
}

private const val EMPTY_FAV =
    "Inga favoriter \u00e4n.\nTryck p\u00e5 stj\u00e4rnan vid ett ord f\u00f6r att spara det h\u00e4r."

/* ===================== MODE: LÅS ===================== */

@Composable
private fun LasMode(vm: OrdbitViewModel, speak: (String) -> Unit) {
    val pal = LocalPalette.current
    val words = currentWords(vm)
    if (words.isEmpty()) {
        EmptyState(if (vm.section == "FAV") EMPTY_FAV else "Inga ord h\u00e4r \u00e4n.")
        return
    }
    val idx = vm.idx.coerceIn(0, words.size - 1)
    val word = words[idx]
    val fav = word.id in vm.favorites
    val status = vm.statusOf(word.id)

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(pal.surface)
                .border(1.dp, pal.line, RoundedCornerShape(24.dp))
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PosTag(word.pos)
                StarButton(fav) { vm.toggleFav(word.id) }
            }
            Text(
                word.sv,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                fontSize = wordSize(word.sv).sp, lineHeight = (wordSize(word.sv) + 4).sp,
                color = pal.ink,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(10.dp))
            SpeakButton { speak(word.sv) }
            Text(word.en, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = pal.ink,
                modifier = Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(16.dp))
            ThinDivider()
            Spacer(Modifier.height(14.dp))
            Text("EXEMPEL", fontSize = 10.sp, letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold, color = pal.muted)
            Spacer(Modifier.height(8.dp))
            Text(word.exSv, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                fontSize = 17.sp, lineHeight = 24.sp, color = pal.ink)
            Spacer(Modifier.height(6.dp))
            Text(word.exEn, fontSize = 14.sp, lineHeight = 20.sp, color = pal.muted)

            Spacer(Modifier.weight(1f))

            Text("HUR VÄL KAN DU ORDET?", fontSize = 10.sp, letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold, color = pal.muted)
            Spacer(Modifier.height(8.dp))
            StatusButtons(status) { vm.setStatus(word.id, it) }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NavButton("\u2039", idx > 0) { vm.idx = idx - 1; vm.revealed = false }
                Counter(idx + 1, words.size)
                NavButton("\u203A", idx < words.size - 1) { vm.idx = idx + 1; vm.revealed = false }
            }
        }
    }
}

/* ===================== MODE: KORT ===================== */

@Composable
private fun KortMode(vm: OrdbitViewModel, speak: (String) -> Unit) {
    val pal = LocalPalette.current
    val words = currentWords(vm)
    if (words.isEmpty()) {
        EmptyState(if (vm.section == "FAV") EMPTY_FAV else "Inga ord h\u00e4r \u00e4n.")
        return
    }
    val idx = vm.idx.coerceIn(0, words.size - 1)
    val word = words[idx]
    val fav = word.id in vm.favorites
    val status = vm.statusOf(word.id)

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(pal.surface)
                .border(1.dp, pal.line, RoundedCornerShape(24.dp))
                .clickable { vm.revealed = !vm.revealed }
                .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PosTag(word.pos)
                StarButton(fav) { vm.toggleFav(word.id) }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    word.sv,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = wordSize(word.sv).sp, lineHeight = (wordSize(word.sv) + 4).sp,
                    color = pal.ink, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                SpeakButton { speak(word.sv) }

                if (vm.revealed) {
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(1.dp).background(pal.line))
                    Spacer(Modifier.height(18.dp))
                    Text(word.en, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        color = pal.ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(word.exSv, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        fontSize = 16.sp, lineHeight = 23.sp, color = pal.ink,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(5.dp))
                    Text(word.exEn, fontSize = 13.sp, lineHeight = 19.sp, color = pal.muted,
                        textAlign = TextAlign.Center)
                } else {
                    Spacer(Modifier.height(22.dp))
                    Text("Tryck p\u00e5 kortet f\u00f6r att se \u00f6vers\u00e4ttningen",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = pal.muted,
                        textAlign = TextAlign.Center)
                }
            }

            if (vm.revealed) {
                StatusButtons(status) { vm.setStatus(word.id, it) }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NavButton("\u2039", idx > 0) { vm.idx = idx - 1; vm.revealed = false }
                Counter(idx + 1, words.size)
                NavButton("\u203A", idx < words.size - 1) { vm.idx = idx + 1; vm.revealed = false }
            }
        }
    }
}

/* ===================== MODE: TEST ===================== */

@Composable
private fun TestMode(vm: OrdbitViewModel) {
    val pal = LocalPalette.current
    val pool = currentWords(vm)
    if (pool.isEmpty()) {
        EmptyState(
            if (vm.section == "FAV")
                "Inga favoriter att testa \u00e4n.\nSpara n\u00e5gra ord med stj\u00e4rnan f\u00f6rst."
            else "Inga ord att testa h\u00e4r \u00e4n."
        )
        return
    }

    var score by rememberSaveable(vm.section) { mutableStateOf(0) }
    var total by rememberSaveable(vm.section) { mutableStateOf(0) }
    var question by remember(vm.section) { mutableStateOf(makeQuestion(pool)) }
    var picked by remember(vm.section) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("R\u00c4TT SVAR", fontSize = 11.sp, letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold, color = pal.muted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$score", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, color = pal.accent)
                Text(" / $total", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = pal.muted)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(pal.surface)
                .border(1.dp, pal.line, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Text("VAD BETYDER", fontSize = 11.sp, letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold, color = pal.muted,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                question.word.sv,
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                fontSize = wordSize(question.word.sv).sp,
                lineHeight = (wordSize(question.word.sv) + 4).sp, color = pal.ink,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            Text("V\u00e4lj r\u00e4tt \u00f6vers\u00e4ttning", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = pal.muted,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

            Spacer(Modifier.height(18.dp))

            question.options.forEachIndexed { i, option ->
                val answered = picked != null
                val isCorrect = i == question.correct
                val isPicked = i == picked
                val bg = when {
                    !answered -> pal.surface
                    isCorrect -> StatusYes
                    isPicked -> StatusNo
                    else -> pal.surface
                }
                val border = when {
                    !answered -> pal.line
                    isCorrect -> StatusYes
                    isPicked -> StatusNo
                    else -> pal.line
                }
                val textColor = when {
                    !answered -> pal.ink
                    isCorrect -> Color.White
                    isPicked -> Color.White
                    else -> pal.muted
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(1.5.dp, border, RoundedCornerShape(14.dp))
                        .alpha(if (answered && !isCorrect && !isPicked) 0.5f else 1f)
                        .then(
                            if (!answered) Modifier.clickable {
                                picked = i
                                total += 1
                                if (i == question.correct) {
                                    score += 1
                                    vm.setStatus(question.word.id, "yes")
                                } else {
                                    vm.setStatus(question.word.id, "no")
                                }
                            } else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = textColor)
                    if (answered && isCorrect) Text("\u2713", fontSize = 16.sp, color = Color.White)
                    if (answered && isPicked && !isCorrect)
                        Text("\u2715", fontSize = 15.sp, color = Color.White)
                }
            }
        }

        if (picked != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.ink)
                    .clickable {
                        question = makeQuestion(pool)
                        picked = null
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("N\u00e4sta fr\u00e5ga  \u203A", fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
                    color = pal.bg)
            }
        }
    }
}

/* ===================== MODE: LISTA ===================== */

@Composable
private fun ListaMode(vm: OrdbitViewModel, speak: (String) -> Unit) {
    val pal = LocalPalette.current
    val words = currentWords(vm)
    if (words.isEmpty()) {
        EmptyState(if (vm.section == "FAV") EMPTY_FAV else "Inga ord h\u00e4r \u00e4n.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Text(
                "Tryck p\u00e5 ett ord: Kan inte \u2192 Os\u00e4ker \u2192 Kan",
                fontSize = 11.sp, fontWeight = FontWeight.Medium, color = pal.muted,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        items(words) { word ->
            val status = vm.statusOf(word.id)
            val fav = word.id in vm.favorites
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(pal.surface)
                    .border(1.dp, pal.line, RoundedCornerShape(15.dp))
                    .clickable { vm.cycleStatus(word.id) }
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // status dot
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (status == "none") Color.Transparent else statusColor(status))
                        .border(
                            1.5.dp,
                            if (status == "none") pal.line else statusColor(status),
                            CircleShape
                        )
                )
                Column(Modifier.weight(1f)) {
                    Text(word.sv, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        color = if (status == "yes") pal.muted else pal.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(word.en, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = pal.muted)
                }
                StarButton(fav, size = 34) { vm.toggleFav(word.id) }
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.bg)
                        .border(1.dp, pal.line, RoundedCornerShape(10.dp))
                        .clickable { speak(word.sv) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u25B8", fontSize = 13.sp, fontWeight = FontWeight.Black,
                        color = pal.accent)
                }
            }
        }
    }
}

/* ===================== MODE BAR ===================== */

@Composable
private fun ModeBar(vm: OrdbitViewModel) {
    val pal = LocalPalette.current
    val modes = listOf(
        Triple("las", "L\u00c5S", "\u25C9"),
        Triple("kort", "KORT", "\u25A2"),
        Triple("test", "TEST", "\u2713"),
        Triple("lista", "LISTA", "\u2261")
    )
    Row(
        Modifier.fillMaxWidth().background(pal.bg).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (id, label, glyph) ->
            val active = vm.mode == id
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (active) pal.surface else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) pal.line else Color.Transparent,
                        RoundedCornerShape(13.dp)
                    )
                    .clickable { vm.setMode(id) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(glyph, fontSize = 18.sp, color = if (active) pal.accent else pal.muted)
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    color = if (active) pal.accent else pal.muted)
            }
        }
    }
}

/* ===================== SETTINGS / THEMES / STATS SHEET ===================== */

@Composable
private fun SettingsSheet(vm: OrdbitViewModel, onClose: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
    ) {
        Text("Tema", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, color = pal.ink)
        Spacer(Modifier.height(3.dp))
        Text("V\u00e4lj f\u00e4rgtema. Allt sparas p\u00e5 din enhet.", fontSize = 12.5.sp,
            color = pal.muted)
        Spacer(Modifier.height(16.dp))

        Palettes.chunked(3).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                rowItems.forEach { p ->
                    val selected = vm.theme == p.id
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(15.dp))
                            .background(pal.bg)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) pal.accent else pal.line,
                                RoundedCornerShape(15.dp)
                            )
                            .clickable { vm.setTheme(p.id) }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(p.bg, p.accent, p.ink).forEach { c ->
                                Box(
                                    Modifier.size(15.dp).clip(CircleShape).background(c)
                                        .border(1.dp, pal.line, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = pal.ink)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ThinDivider()
        Spacer(Modifier.height(16.dp))

        Text("STATISTIK", fontSize = 11.sp, letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold, color = pal.muted)
        Spacer(Modifier.height(12.dp))

        // Three-state totals
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatTile("Kan", vm.statusYes.size, StatusYes, Modifier.weight(1f))
            StatTile("Os\u00e4ker", vm.statusMid.size, StatusMid, Modifier.weight(1f))
            StatTile("Kan inte", vm.statusNo.size, StatusNo, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ord du kan, totalt", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = pal.ink)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${vm.statusYes.size}", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black, fontSize = 16.sp, color = pal.accent)
                Text(" / ${Vocab.all.size}", fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = pal.muted)
            }
        }
        Spacer(Modifier.height(12.dp))

        Vocab.sections.forEach { sec ->
            val sw = Vocab.bySection(sec.id)
            val known = sw.count { it.id in vm.statusYes }
            val pct = if (sw.isEmpty()) 0 else known * 100 / sw.size
            Column(Modifier.padding(bottom = 9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (sec.category) sec.name else "${sec.id} \u00b7 ${sec.name}",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = pal.ink
                    )
                    Text("$known/${sw.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = pal.muted)
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
                        .background(pal.bg2)
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct / 100f).height(5.dp)
                            .clip(RoundedCornerShape(99.dp)).background(StatusYes)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        ThinDivider()
        Spacer(Modifier.height(16.dp))

        Text("M\u00c4RKEN", fontSize = 11.sp, letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold, color = pal.muted)
        Spacer(Modifier.height(12.dp))

        val known = vm.statusYes.size
        val a1Done = Vocab.bySection("A1").all { it.id in vm.statusYes }
        val badges = listOf(
            "F\u00f6rsta steget" to (known >= 1),
            "Tio ord" to (known >= 10),
            "A1 klar" to a1Done,
            "Femtio ord" to (known >= 50),
            "Hundra ord" to (known >= 100),
            "Hela ordboken" to (known >= Vocab.all.size)
        )
        badges.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowItems.forEach { (name, earned) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (earned) pal.accentSoft else pal.bg)
                            .border(
                                1.dp,
                                if (earned) pal.accent else pal.line,
                                RoundedCornerShape(11.dp)
                            )
                            .alpha(if (earned) 1f else 0.55f)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(if (earned) "\u2605" else "\u2606", fontSize = 14.sp,
                            color = if (earned) pal.accent else pal.muted)
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (earned) pal.ink else pal.muted)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(pal.ink)
                .clickable { onClose() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Klar", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = pal.bg)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(label: String, count: Int, color: Color, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(pal.bg)
            .border(1.dp, pal.line, RoundedCornerShape(13.dp))
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(7.dp))
        Text("$count", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
            fontSize = 19.sp, color = pal.ink)
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = pal.muted)
    }
}
