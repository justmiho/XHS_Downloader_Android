package com.neoruaa.xhsdn

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.other.GitHub
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private const val PREFS_NAME = "XHSDownloaderPrefs"

data class SettingsUiState(
    val createLivePhotos: Boolean = true,
    val useCustomNaming: Boolean = false,
    val template: TextFieldValue = TextFieldValue(NamingFormat.DEFAULT_TEMPLATE),
    val tokens: List<NamingFormat.TokenDefinition> = emptyList()
)

class SettingsViewModel(private val prefs: SharedPreferences) : ViewModel() {
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsUiState> = _state
    private var hasChanges = false

    private fun loadState(): SettingsUiState {
        val createLivePhotos = prefs.getBoolean("create_live_photos", true)
        val hasNewFlag = prefs.contains("use_custom_naming_format")
        val legacyFlag = prefs.getBoolean("use_metadata_file_names", false)
        val useCustomNaming = if (hasNewFlag) prefs.getBoolean("use_custom_naming_format", false) else legacyFlag
        var template = prefs.getString("custom_naming_template", NamingFormat.DEFAULT_TEMPLATE)
        if (template.isNullOrEmpty()) {
            template = NamingFormat.DEFAULT_TEMPLATE
        }
        return SettingsUiState(
            createLivePhotos = createLivePhotos,
            useCustomNaming = useCustomNaming,
            template = TextFieldValue(template),
            tokens = NamingFormat.getAvailableTokens()
        )
    }

    fun onCreateLivePhotosChange(enabled: Boolean) = updateState {
        it.copy(createLivePhotos = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onUseCustomNamingChange(enabled: Boolean) = updateState {
        it.copy(useCustomNaming = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onTemplateChange(value: TextFieldValue) = updateState {
        it.copy(template = value).also { newState ->
            persist(newState)
        }
    }

    fun onResetTemplate() = updateState {
        it.copy(template = TextFieldValue(NamingFormat.DEFAULT_TEMPLATE)).also { newState ->
            persist(newState)
        }
    }

    private fun persist(state: SettingsUiState) {
        hasChanges = true
        prefs.edit()
            .putBoolean("create_live_photos", state.createLivePhotos)
            .putBoolean("use_custom_naming_format", state.useCustomNaming)
            .putString("custom_naming_template", state.template.text.ifBlank { NamingFormat.DEFAULT_TEMPLATE })
            .remove("use_metadata_file_names")
            .apply()
    }

    fun hasChanges(): Boolean = hasChanges

    private fun updateState(block: (SettingsUiState) -> SettingsUiState) {
        viewModelScope.launch {
            _state.emit(block(_state.value))
        }
    }
}

class SettingsViewModelFactory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !isNightMode
        setContent {
            val controller = ThemeController(ColorSchemeMode.System)
            val uiState by viewModel.state.collectAsStateWithLifecycle()
            val topBarState = rememberTopAppBarState()
            MiuixTheme(controller = controller) {
                SettingsScreen(
                    uiState = uiState,
                    onBack = { finishWithResult() },
                    onCreateLivePhotosChange = viewModel::onCreateLivePhotosChange,
                    onUseCustomNamingChange = viewModel::onUseCustomNamingChange,
                    onTemplateChange = viewModel::onTemplateChange,
                    onResetTemplate = viewModel::onResetTemplate,
                    topBarState = topBarState
                )
            }
        }
    }

    private fun finishWithResult() {
        setResult(if (viewModel.hasChanges()) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onCreateLivePhotosChange: (Boolean) -> Unit,
    onUseCustomNamingChange: (Boolean) -> Unit,
    onTemplateChange: (TextFieldValue) -> Unit,
    onResetTemplate: () -> Unit,
    topBarState: top.yukonga.miuix.kmp.basic.TopAppBarState
) {
    val context = LocalContext.current
    val scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(state = topBarState)

    top.yukonga.miuix.kmp.basic.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
            .union(androidx.compose.foundation.layout.WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = context.getString(R.string.settings),
                navigationIcon = {
                    Icon(
                        imageVector = MiuixIcons.Useful.Back,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clickable { onBack() }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SmallTitle(text = "下载选项")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    cornerRadius = 18.dp,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PreferenceRow(
                            title = "生成Live Photo",
                            description = "图片+视频时自动生成Live Photo",
                            checked = uiState.createLivePhotos,
                            onCheckedChange = onCreateLivePhotosChange
                        )
                    }
                }
            }
            item {
                SmallTitle(text = "文件命名")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    cornerRadius = 18.dp,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PreferenceRow(
                            title = "启用自定义命名",
                            description = "使用模板命名文件",
                            checked = uiState.useCustomNaming,
                            onCheckedChange = onUseCustomNamingChange
                        )
                        TextField(
                            value = uiState.template,
                            onValueChange = onTemplateChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = "命名模板",
                            enabled = uiState.useCustomNaming,
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { })
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(text = "重置模板", onClick = onResetTemplate, enabled = uiState.useCustomNaming)
                            Text(
                                modifier = Modifier.padding(start = 10.dp),
                                text = "点击下方占位符可插入到光标处",
                                color = Color.Gray
                            )
                        }
                        TokenGrid(
                            tokens = uiState.tokens,
                            enabled = uiState.useCustomNaming,
                            onInsert = { placeholder ->
                                val current = uiState.template
                                val selectionStart = current.selection.start.coerceAtLeast(0)
                                val selectionEnd = current.selection.end.coerceAtLeast(0)
                                val min = minOf(selectionStart, selectionEnd)
                                val max = maxOf(selectionStart, selectionEnd)
                                val newText = buildString {
                                    append(current.text.substring(0, min))
                                    append(placeholder)
                                    append(current.text.substring(max))
                                }
                                val newSelection = min + placeholder.length
                                onTemplateChange(
                                    TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newSelection)
                                    )
                                )
                            }
                        )
                    }
                }
            }
            item {
                SmallTitle(text = "关于")
//                Card(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(start = 16.dp, end = 16.dp),
//                    cornerRadius = 18.dp,
//                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.background)
//                ) {
//                    Column(
//                        modifier = Modifier.padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp)
//                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NEORUAA/XHS_Downloader_Android"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Other.GitHub,
                                contentDescription = "GitHub",
                                modifier = Modifier.padding(end = 8.dp),
                                tint = Color.White
                            )
                            Text(
                                text = "前往 GitHub",
                                color = Color.White
                            )
                        }
//                    }
//                }
            }
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TokenGrid(
    tokens: List<NamingFormat.TokenDefinition>,
    enabled: Boolean,
    onInsert: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tokens.forEach { token ->
            TokenChip(
                token = token,
                enabled = enabled,
                onInsert = onInsert,
                modifier = Modifier.fillMaxWidth(0.48f)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TokenChip(
    token: NamingFormat.TokenDefinition,
    enabled: Boolean,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = MiuixTheme.colorScheme.surface
    Card(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable { onInsert(token.placeholder) } else Modifier),
        cornerRadius = 14.dp,
        colors = CardDefaults.defaultColors(
            color = if (enabled) MiuixTheme.colorScheme.surfaceVariant else MiuixTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            )
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = LocalContext.current.getString(token.labelResId))
            Text(text = token.placeholder, color = Color.Gray)
        }
    }
}
