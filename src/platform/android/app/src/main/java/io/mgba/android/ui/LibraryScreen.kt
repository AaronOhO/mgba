/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.ui

import android.graphics.BitmapFactory
import androidx.annotation.PluralsRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.mgba.android.R
import io.mgba.android.logic.library.LibraryGame
import io.mgba.android.logic.library.GameDataKind
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AppDestination {
    HOME,
    LIBRARY,
    PLAYER,
    SETTINGS,
}

private enum class LibraryFilter {
    ALL,
    FAVORITES,
    RECENT,
}

@Composable
internal fun LibraryExperience(
    destination: AppDestination,
    games: List<LibraryGame>,
    activeGameId: String?,
    message: String?,
    onNavigate: (AppDestination) -> Unit,
    onImport: () -> Unit,
    onPlay: (LibraryGame) -> Unit,
    onFavorite: (LibraryGame, Boolean) -> Unit,
    onRename: (LibraryGame, String) -> Unit,
    onDelete: (LibraryGame, Boolean) -> Unit,
    onRefreshCover: (LibraryGame) -> Unit,
    onChooseCover: (LibraryGame) -> Unit,
    onImportGameData: (LibraryGame, GameDataKind) -> Unit,
    onExportGameData: (LibraryGame, GameDataKind) -> Unit,
) {
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    val selectedGame = games.firstOrNull { it.id == selectedGameId }
    Box(modifier = Modifier.fillMaxSize()) {
        ProductScaffold(
            destination = destination,
            hasActiveGame = activeGameId != null,
            onNavigate = onNavigate,
            onImport = onImport,
        ) {
            when (destination) {
                AppDestination.HOME -> HomeScreen(
                    games = games,
                    onImport = onImport,
                    onPlay = onPlay,
                    onSelectGame = { selectedGameId = it.id },
                    onOpenLibrary = { onNavigate(AppDestination.LIBRARY) },
                )
                AppDestination.LIBRARY -> GameLibraryScreen(
                    games = games,
                    onImport = onImport,
                    onPlay = onPlay,
                    onSelectGame = { selectedGameId = it.id },
                )
                else -> Unit
            }
        }
        message?.let { GlobalLibraryMessage(it) }
    }
    selectedGame?.let { game ->
        GameDetailsDialog(
            game = game,
            active = activeGameId == game.id,
            onDismiss = { selectedGameId = null },
            onPlay = {
                selectedGameId = null
                onPlay(game)
            },
            onFavorite = { onFavorite(game, !game.favorite) },
            onRename = { onRename(game, it) },
            onDelete = { deleteSaveData ->
                selectedGameId = null
                onDelete(game, deleteSaveData)
            },
            onRefreshCover = { onRefreshCover(game) },
            onChooseCover = { onChooseCover(game) },
            onImportGameData = { onImportGameData(game, it) },
            onExportGameData = { onExportGameData(game, it) },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.GlobalLibraryMessage(message: String) {
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp).widthIn(max = 560.dp),
        color = Color(0xEE30353F),
        shape = RoundedCornerShape(14.dp),
        elevation = 8.dp,
    ) {
        Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun ProductScaffold(
    destination: AppDestination,
    hasActiveGame: Boolean,
    onNavigate: (AppDestination) -> Unit,
    onImport: () -> Unit,
    content: @Composable () -> Unit,
) = BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(AppBackground),
) {
    val wide = maxWidth >= 700.dp
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationPanel(
                destination = destination,
                hasActiveGame = hasActiveGame,
                onNavigate = onNavigate,
                onImport = onImport,
                modifier = Modifier.width(220.dp).fillMaxHeight(),
            )
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = AppBackground,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            ) { content() }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactHeader(onImport)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            BottomNavigation(destination, hasActiveGame, onNavigate)
        }
    }
}

@Composable
private fun NavigationPanel(
    destination: AppDestination,
    hasActiveGame: Boolean,
    onNavigate: (AppDestination) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandHeader()
        Spacer(Modifier.height(18.dp))
        NavigationButton(
            stringResource(R.string.navigation_home),
            Icons.Default.Home,
            destination == AppDestination.HOME,
        ) { onNavigate(AppDestination.HOME) }
        NavigationButton(
            stringResource(R.string.navigation_library),
            Icons.Default.MenuBook,
            destination == AppDestination.LIBRARY,
        ) { onNavigate(AppDestination.LIBRARY) }
        if (hasActiveGame) {
            NavigationButton(
                stringResource(R.string.navigation_player),
                Icons.Default.PlayArrow,
                destination == AppDestination.PLAYER,
            ) { onNavigate(AppDestination.PLAYER) }
        }
        NavigationButton(
            stringResource(R.string.action_settings),
            Icons.Default.Settings,
            destination == AppDestination.SETTINGS,
        ) { onNavigate(AppDestination.SETTINGS) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF07130C))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.library_add_game),
                color = Color(0xFF07130C),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactHeader(onImport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandHeader(Modifier.weight(1f))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF07130C))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.library_add), color = Color(0xFF07130C), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.mgba_256),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("mGBA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.library_brand_subtitle), color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavigationButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Accent else MutedText)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) Color.White else MutedText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BottomNavigation(
    destination: AppDestination,
    hasActiveGame: Boolean,
    onNavigate: (AppDestination) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelBackground).padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CompactNavigationButton(
            stringResource(R.string.navigation_home),
            Icons.Default.Home,
            destination == AppDestination.HOME,
        ) { onNavigate(AppDestination.HOME) }
        CompactNavigationButton(
            stringResource(R.string.navigation_library),
            Icons.Default.MenuBook,
            destination == AppDestination.LIBRARY,
        ) { onNavigate(AppDestination.LIBRARY) }
        if (hasActiveGame) {
            CompactNavigationButton(
                stringResource(R.string.navigation_player),
                Icons.Default.PlayArrow,
                destination == AppDestination.PLAYER,
            ) { onNavigate(AppDestination.PLAYER) }
        }
        CompactNavigationButton(
            stringResource(R.string.action_settings),
            Icons.Default.Settings,
            destination == AppDestination.SETTINGS,
        ) { onNavigate(AppDestination.SETTINGS) }
    }
}

@Composable
private fun CompactNavigationButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Accent else MutedText)
        Text(label, color = if (selected) Accent else MutedText, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun HomeScreen(
    games: List<LibraryGame>,
    onImport: () -> Unit,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    if (games.isEmpty()) {
        EmptyLibrary(onImport)
        return
    }
    val continueGame = games.filter { it.lastPlayedAt > 0L }.maxByOrNull(LibraryGame::lastPlayedAt)
        ?: games.first()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(stringResource(R.string.home_welcome), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.home_subtitle), color = MutedText, fontSize = 14.sp)
        }
        item { ContinueCard(continueGame, onPlay, onSelectGame) }
        item {
            SectionHeader(
                title = stringResource(R.string.home_recent_games),
                action = stringResource(R.string.home_view_library),
                onAction = onOpenLibrary,
            )
        }
        item { GameCardRow(games.take(4), onPlay, onSelectGame) }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.mgba_256),
            contentDescription = null,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.library_empty_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.library_empty_description), color = MutedText, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF07130C))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_import_first_game), color = Color(0xFF07130C), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ContinueCard(
    game: LibraryGame,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
) = BoxWithConstraints {
    val wide = maxWidth >= 640.dp
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        backgroundColor = PanelBackground,
        elevation = 0.dp,
    ) {
        if (wide) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(game, Modifier.width(130.dp))
                Spacer(Modifier.width(24.dp))
                ContinueDetails(game, onPlay, onSelectGame, Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(game, Modifier.width(96.dp))
                Spacer(Modifier.width(16.dp))
                ContinueDetails(game, onPlay, onSelectGame, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ContinueDetails(
    game: LibraryGame,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(stringResource(R.string.home_continue_playing), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(game.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (game.lastPlayedAt > 0L) {
            Text(
                stringResource(R.string.game_last_played, formatDate(game.lastPlayedAt)),
                color = MutedText,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPlay(game) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF07130C))
                Text(stringResource(R.string.game_play), color = Color(0xFF07130C), fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { onSelectGame(game) }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.game_more_actions), tint = Color.White)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = Accent) }
    }
}

@Composable
private fun GameCardRow(
    games: List<LibraryGame>,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        games.forEach { game ->
            GameCard(game, onPlay, onSelectGame, Modifier.weight(1f))
        }
        repeat((4 - games.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun GameLibraryScreen(
    games: List<LibraryGame>,
    onImport: () -> Unit,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    val visibleGames = games.filter { game ->
        game.name.contains(query.trim(), ignoreCase = true) && when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.FAVORITES -> game.favorite
            LibraryFilter.RECENT -> game.lastPlayedAt > 0L
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.library_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(quantityString(R.plurals.library_game_count, games.size), color = MutedText, fontSize = 13.sp)
            }
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF07130C))
                Text(stringResource(R.string.library_add_game), color = Color(0xFF07130C), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.library_search)) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = Color.White,
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = DividerColor,
                leadingIconColor = MutedText,
                placeholderColor = MutedText,
            ),
            shape = RoundedCornerShape(14.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.values().forEach { option ->
                FilterButton(option, option == filter) { filter = option }
            }
        }
        if (visibleGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (games.isEmpty()) R.string.library_empty_title else R.string.library_no_results),
                    color = MutedText,
                    fontSize = 16.sp,
                )
            }
        } else {
            GameGrid(visibleGames, onPlay, onSelectGame)
        }
    }
}

@Composable
private fun FilterButton(filter: LibraryFilter, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (filter) {
            LibraryFilter.ALL -> R.string.library_filter_all
            LibraryFilter.FAVORITES -> R.string.library_filter_favorites
            LibraryFilter.RECENT -> R.string.library_filter_recent
        },
    )
    Text(
        label,
        color = if (selected) Color(0xFF07130C) else MutedText,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else PanelBackground)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun GameGrid(
    games: List<LibraryGame>,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
) = BoxWithConstraints {
    val columns = when {
        maxWidth >= 900.dp -> 6
        maxWidth >= 700.dp -> 5
        maxWidth >= 520.dp -> 4
        else -> 3
    }
    val rows = games.chunked(columns)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(rows, key = { row -> row.joinToString("-") { it.id } }) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { game -> GameCard(game, onPlay, onSelectGame, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: LibraryGame,
    onPlay: (LibraryGame) -> Unit,
    onSelectGame: (LibraryGame) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .semantics {
                contentDescription = game.name
                role = Role.Button
            }
            .clickable { onPlay(game) }
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.widthIn(max = 118.dp).fillMaxWidth()) {
            CoverArt(game, Modifier.fillMaxWidth())
            if (game.favorite) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xCC111318), CircleShape).padding(6.dp),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                }
            }
            IconButton(
                onClick = { onSelectGame(game) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0xCC111318), CircleShape),
            ) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.game_more_actions), tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            game.name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (game.lastPlayedAt > 0L) formatDate(game.lastPlayedAt) else stringResource(R.string.game_never_played),
            color = MutedText,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CoverArt(game: LibraryGame, modifier: Modifier) {
    val path = game.coverFile?.takeIf(File::isFile)?.absolutePath
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = path) {
        value = path?.let { coverPath ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(coverPath)?.asImageBitmap() }
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF303741), Color(0xFF15181E))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = stringResource(R.string.game_cover_description, game.name),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.mgba_256),
                contentDescription = stringResource(R.string.game_cover_description, game.name),
                modifier = Modifier.fillMaxWidth(0.48f),
                alpha = 0.35f,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun GameDetailsDialog(
    game: LibraryGame,
    active: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (Boolean) -> Unit,
    onRefreshCover: () -> Unit,
    onChooseCover: () -> Unit,
    onImportGameData: (GameDataKind) -> Unit,
    onExportGameData: (GameDataKind) -> Unit,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var pendingImportKind by remember { mutableStateOf<GameDataKind?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val widthFraction = if (maxWidth > maxHeight) 0.68f else 0.92f
            Surface(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .heightIn(max = maxHeight * 0.92f)
                    .widthIn(max = 900.dp),
                color = PanelBackground,
                shape = RoundedCornerShape(24.dp),
            ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    CoverArt(game, Modifier.width(140.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(game.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(game.sourceName, color = MutedText, fontSize = 12.sp, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.game_file_size, formatBytes(game.fileSize)), color = MutedText, fontSize = 12.sp)
                        Text(quantityString(R.plurals.game_play_count, game.playCount), color = MutedText, fontSize = 12.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onPlay,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Accent),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF07130C))
                                Text(stringResource(R.string.game_play), color = Color(0xFF07130C), fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onFavorite,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                            ) {
                                Icon(
                                    if (game.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(
                                        if (game.favorite) R.string.game_remove_favorite else R.string.game_add_favorite,
                                    ),
                                    tint = Accent,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Divider(color = DividerColor)
                GameManagementActions(
                    onRename = { showRename = true },
                    onChooseCover = onChooseCover,
                    onRefreshCover = onRefreshCover,
                    onDelete = { showDelete = true },
                )
                Divider(color = DividerColor)
                Text(
                    stringResource(R.string.game_data_title),
                    color = MutedText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                GameDataActions(
                    kind = GameDataKind.BATTERY_SAVE,
                    canExport = active || game.saveFile.isFile,
                    onImport = { pendingImportKind = GameDataKind.BATTERY_SAVE },
                    onExport = { onExportGameData(GameDataKind.BATTERY_SAVE) },
                )
                GameDataActions(
                    kind = GameDataKind.QUICK_STATE,
                    canExport = game.stateFile.isFile,
                    onImport = { pendingImportKind = GameDataKind.QUICK_STATE },
                    onExport = { onExportGameData(GameDataKind.QUICK_STATE) },
                )
            }
            }
        }
    }
    if (showRename) {
        RenameDialog(
            game = game,
            onDismiss = { showRename = false },
            onRename = {
                showRename = false
                onRename(it)
            },
        )
    }
    if (showDelete) {
        DeleteGameDialog(
            game = game,
            onDismiss = { showDelete = false },
            onDelete = {
                showDelete = false
                onDelete(it)
            },
        )
    }
    pendingImportKind?.let { kind ->
        ReplaceGameDataDialog(
            game = game,
            kind = kind,
            onDismiss = { pendingImportKind = null },
            onConfirm = {
                pendingImportKind = null
                onImportGameData(kind)
            },
        )
    }
}

@Composable
private fun GameDataActions(
    kind: GameDataKind,
    canExport: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GameDataAction(
            icon = Icons.Default.FileUpload,
            label = if (kind == GameDataKind.BATTERY_SAVE) R.string.game_import_save
            else R.string.game_import_state,
            modifier = Modifier.weight(1f),
            onClick = onImport,
        )
        GameDataAction(
            icon = Icons.Default.FileDownload,
            label = if (kind == GameDataKind.BATTERY_SAVE) R.string.game_export_save
            else R.string.game_export_state,
            modifier = Modifier.weight(1f),
            enabled = canExport,
            onClick = onExport,
        )
    }
}

@Composable
private fun GameDataAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = if (enabled) Accent else MutedText.copy(alpha = 0.45f))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(label),
            color = if (enabled) Color.White else MutedText.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReplaceGameDataDialog(
    game: LibraryGame,
    kind: GameDataKind,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (kind == GameDataKind.BATTERY_SAVE) R.string.game_replace_save_title
                    else R.string.game_replace_state_title,
                ),
                color = Color.White,
            )
        },
        text = {
            Text(
                stringResource(
                    if (kind == GameDataKind.BATTERY_SAVE) R.string.game_replace_save_description
                    else R.string.game_replace_state_description,
                    game.name,
                ),
                color = MutedText,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_choose_file), color = Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        backgroundColor = PanelBackground,
    )
}

@Composable
private fun GameManagementAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    modifier: Modifier,
    color: Color = Color.White,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GameManagementActions(
    onRename: () -> Unit,
    onChooseCover: () -> Unit,
    onRefreshCover: () -> Unit,
    onDelete: () -> Unit,
) = BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    if (maxWidth < 900.dp) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GameManagementAction(Icons.Default.Edit, R.string.game_rename, Modifier.weight(1f), onClick = onRename)
                GameManagementAction(
                    Icons.Default.PhotoLibrary,
                    R.string.game_choose_cover,
                    Modifier.weight(1f),
                    onClick = onChooseCover,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GameManagementAction(
                    Icons.Default.Refresh,
                    R.string.game_refresh_cover,
                    Modifier.weight(1f),
                    onClick = onRefreshCover,
                )
                GameManagementAction(
                    Icons.Default.Delete,
                    R.string.game_delete,
                    Modifier.weight(1f),
                    Color(0xFFFF8A80),
                    onClick = onDelete,
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GameManagementAction(Icons.Default.Edit, R.string.game_rename, Modifier.weight(1f), onClick = onRename)
            GameManagementAction(
                Icons.Default.PhotoLibrary,
                R.string.game_choose_cover,
                Modifier.weight(1f),
                onClick = onChooseCover,
            )
            GameManagementAction(
                Icons.Default.Refresh,
                R.string.game_refresh_cover,
                Modifier.weight(1f),
                onClick = onRefreshCover,
            )
            GameManagementAction(
                Icons.Default.Delete,
                R.string.game_delete,
                Modifier.weight(1f),
                Color(0xFFFF8A80),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun RenameDialog(game: LibraryGame, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember(game.id) { mutableStateOf(game.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_rename), color = Color.White) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Accent),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }) {
                Text(stringResource(R.string.action_save), color = Accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        backgroundColor = PanelBackground,
    )
}

@Composable
private fun DeleteGameDialog(game: LibraryGame, onDismiss: () -> Unit, onDelete: (Boolean) -> Unit) {
    var deleteSaveData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_delete_title), color = Color.White) },
        text = {
            Column {
                Text(stringResource(R.string.game_delete_description, game.name), color = MutedText)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deleteSaveData,
                        onCheckedChange = { deleteSaveData = it },
                        colors = CheckboxDefaults.colors(checkedColor = Accent),
                    )
                    Text(stringResource(R.string.game_delete_save_data), color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(deleteSaveData) }) {
                Text(stringResource(R.string.game_delete), color = Color(0xFFFF8A80))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        backgroundColor = PanelBackground,
    )
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun quantityString(@PluralsRes resource: Int, quantity: Int): String =
    LocalContext.current.resources.getQuantityString(resource, quantity, quantity)

private val MutedText = Color(0xFFADB5C3)
private val DividerColor = Color(0xFF30353F)
