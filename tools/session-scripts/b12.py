import io
J = 'O:/base/app/src/main/java/com/music/spotui/'
R = 'O:/base/app/src/main/res/'

def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    if crlf:
        s = s.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf:
        s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

# ---- strings
edit(R + 'values/strings_future.xml', [(
    '    <string name="settings_group_app">App</string>\n',
    '    <string name="settings_group_app">App</string>\n'
    '    <string name="settings_group_quality">Sound quality</string>\n'
    '    <string name="settings_group_playing">While playing</string>\n'
    '    <string name="settings_section_advanced">Advanced</string>\n'
    '    <string name="settings_group_experimental">Experimental</string>\n'
    '    <string name="settings_group_touch">Touch</string>\n'
    '    <string name="settings_group_repair">Repair</string>\n'
    '    <string name="settings_haptics_title">Vibrate on touch</string>\n'
    '    <string name="settings_haptics_detail">A light tap under your finger for buttons, switches and long presses.</string>\n',
)])

# ---- haptics pref + switch-off
edit(J + 'data/preferences/SettingsPref.kt', [(
    'object AutoplayPref {',
    '/** "Vibrate on touch" (on by default): off silences every haptic the app plays ([com.music.spotui.di.Haptics]). */\n'
    'object HapticsPref {\n'
    '    private const val KEY = "haptics_on"\n'
    '    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))\n'
    '        private set\n\n'
    '    fun set(c: Context, value: Boolean) {\n'
    '        on = value\n'
    '        prefs(c).edit().putBoolean(KEY, value).apply()\n'
    '    }\n'
    '}\n\n'
    'object AutoplayPref {',
)])
OFF = 'if (!com.music.spotui.data.preferences.HapticsPref.on) return'
edit(J + 'di/Haptics.kt', [
    ('        val v = view?.get() ?: return\n        // Only while the app is in front',
     '        val v = view?.get() ?: return\n        ' + OFF + '\n        // Only while the app is in front'),
    ('    fun ramp(level: Float) {\n', '    fun ramp(level: Float) {\n        ' + OFF + '\n'),
    ('    fun thud() {\n', '    fun thud() {\n        ' + OFF + '\n'),
])
edit(J + 'ui/immersive/components/ImmersiveClickable.kt', [(
    '                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)\n',
    '                    if (com.music.spotui.data.preferences.HapticsPref.on) haptics.performHapticFeedback(HapticFeedbackType.LongPress)\n',
)])

# ---- icons
edit(J + 'ui/design/AppIcons.kt', [
    ('import com.adamglin.phosphoricons.regular.Info\n',
     'import com.adamglin.phosphoricons.regular.Info\nimport com.adamglin.phosphoricons.regular.SlidersHorizontal\n'),
    ('    val Storage: ImageVector get() = R.HardDrives\n',
     '    val Storage: ImageVector get() = R.HardDrives\n    val Advanced: ImageVector get() = R.SlidersHorizontal\n'),
])

# ---- settings screen
S = J + 'ui/immersive/pages/ImmersiveSettingsScreen.kt'
P = 'com.music.spotui.data.preferences.'
native_row = '''            row {
                var nativeAudio by remember { mutableStateOf(com.music.spotui.di.NativeAudio.enabled) }
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_native_audio_title), checked = nativeAudio,
                    onChecked = { nativeAudio = it; com.music.spotui.di.NativeAudio.set(ctx, it) },
                    detail = stringResource(R.string.settings_native_audio_detail),
                )
            }
'''
transitions_row = '''            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_transitions_title), detail = stringResource(R.string.settings_transitions_detail),
                    value = transitionValue(crossfade),
                    chevron = true, onClick = { open(SettingsPage.Crossfade) },
                )
            }
'''
repair_start = '        immersiveGroup("repair") {\n            // The hidden Web Player stuck or crashed: rebuild it without leaving the app.\n'
s = io.open(S, encoding='utf-8', newline='').read().replace('\r\n', '\n')
i = s.index(repair_start)
j = s.index('// ---------------------------------------------------------------- Accounts')
repair_block = s[i:j]
# the repair group, minus the two closing braces of the page and the function
k = repair_block.rindex('        }\n    }\n}\n')
repair_group = repair_block[:k] + '        }\n'
advanced = '''/** What few people need: the experimental player, touch vibration, and the repair tools. */
@Composable
private fun AdvancedPage(back: () -> Unit) {
    val ctx = LocalContext.current
    ImmersivePage(title = stringResource(R.string.settings_section_advanced), onBack = back) {
        immersiveGroup("touch", headerRes = R.string.settings_group_touch) {
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_haptics_title), checked = ''' + P + '''HapticsPref.on,
                    onChecked = { ''' + P + '''HapticsPref.set(ctx, it) },
                    detail = stringResource(R.string.settings_haptics_detail),
                )
            }
        }
        immersiveGroup("experimental", headerRes = R.string.settings_group_experimental) {
''' + native_row + '''        }
''' + repair_group.replace('immersiveGroup("repair")', 'immersiveGroup("repair", headerRes = R.string.settings_group_repair)') + '''    }
}

'''
s = s[:i] + '    }\n}\n\n' + advanced + s[j:]
io.open(S, 'w', encoding='utf-8', newline='').write(s)

edit(S, [
    ('LyricsLanguage, App, Accounts, Library }', 'LyricsLanguage, App, Accounts, Library, Advanced }'),
    ('            SettingsPage.Library -> LibraryPage(navController, back)\n',
     '            SettingsPage.Library -> LibraryPage(navController, back)\n            SettingsPage.Advanced -> AdvancedPage(back)\n'),
    ('            row { ImmersiveGroupRow(stringResource(R.string.settings_section_app), icon = com.music.spotui.ui.design.AppIcons.About, chevron = true, onClick = { open(SettingsPage.App) }) }\n',
     '            row { ImmersiveGroupRow(stringResource(R.string.settings_section_advanced), icon = com.music.spotui.ui.design.AppIcons.Advanced, chevron = true, onClick = { open(SettingsPage.Advanced) }) }\n'
     '            row { ImmersiveGroupRow(stringResource(R.string.settings_section_app), icon = com.music.spotui.ui.design.AppIcons.About, chevron = true, onClick = { open(SettingsPage.App) }) }\n'),
    ('immersiveGroup("quality", footerRes = R.string.quality_streaming_detail)',
     'immersiveGroup("quality", headerRes = R.string.settings_group_quality, footerRes = R.string.quality_streaming_detail)'),
    ('        immersiveGroup("playback") {\n            row {\n                ImmersiveSwitchRow(\n                    stringResource(R.string.settings_autoplay_title)',
     '        immersiveGroup("playback", headerRes = R.string.settings_group_playing) {\n            row {\n                ImmersiveSwitchRow(\n                    stringResource(R.string.settings_autoplay_title)'),
    (transitions_row, ''),
    (native_row, ''),
    ('    val crossfade = com.music.spotui.data.preferences.CrossfadePref.seconds\n    // The first source of the audio order', '    // The first source of the audio order'),
])

# ---- search index
edit(J + 'ui/screens/SettingsSearch.kt', [
    ('SettingsEntry(R.string.settings_native_audio_title, R.string.settings_native_audio_detail, "Playback"),',
     'SettingsEntry(R.string.settings_native_audio_title, R.string.settings_native_audio_detail, "Advanced"),\n'
     '        SettingsEntry(R.string.settings_haptics_title, R.string.settings_haptics_detail, "Advanced"),\n'
     '        SettingsEntry(R.string.settings_section_advanced, 0, "Advanced"),'),
    ('SettingsEntry(R.string.restart_player_title, R.string.restart_player_detail, "App"),',
     'SettingsEntry(R.string.restart_player_title, R.string.restart_player_detail, "Advanced"),'),
    ('SettingsEntry(R.string.diagnostic_export_title, R.string.diagnostic_export_detail, "App"),',
     'SettingsEntry(R.string.diagnostic_export_title, R.string.diagnostic_export_detail, "Advanced"),'),
])

# ---- main buttons click haptic
edit(J + 'ui/screens/OnboardingStyle.kt', [(
    '.pressable(0.97f, onClick = onClick)', '.pressable(0.97f, onClick = { com.music.spotui.di.Haptics.click(); onClick() })')])
edit(J + 'ui/immersive/screens/ImmersiveHomeProfile.kt', [(
    'modifier = modifier.immersiveClickable(onClick = onClick).dsGlass(Ds.Card, fill = Ds.Glass2)',
    'modifier = modifier.immersiveClickable(onClick = { com.music.spotui.di.Haptics.click(); onClick() }).dsGlass(Ds.Card, fill = Ds.Glass2)')])

# ---- look step
L = J + 'ui/look/LookStep.kt'
edit(L, [
    ('''        Crossfade(look, animationSpec = tween(350), label = "lookBackdrop") {
            if (it == Look.IMMERSIVE) {
                MeshGradientBackground(cyclingMeshPalette(LookMeshPalettes), colorFadeMillis = MESH_CYCLE_MILLIS)
            } else {
                Box(Modifier.fillMaxSize().background(AppBackground))
            }
        }
''', '''        // One ground for the whole of the onboarding, whichever look is being tried.
        com.music.spotui.ui.screens.OnboardingBackdrop(dim = 0.45f)
'''),
    ('''            Button(
                onClick = onNext,
                shape = if (immersive) RoundedCornerShape(14.dp) else ButtonDefaults.shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (immersive) Color.White else AppAccent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(stringResource(if (offer != null) R.string.look_offer_try else R.string.setup_continue), fontFamily = family, fontSize = 16.sp,
                    fontWeight = if (immersive) FontWeight.SemiBold else FontWeight.Bold)
            }
''', '''            com.music.spotui.ui.screens.OnboardingCta(
                stringResource(if (offer != null) R.string.look_offer_try else R.string.setup_continue), onNext,
                Modifier.fillMaxWidth().height(56.dp),
            )
'''),
    ('    val subtle = if (immersive) Color.White.copy(alpha = 0.7f) else Color(0xFFB3B3B3)\n\n    Box(Modifier.fillMaxSize()) {',
     '    val subtle = Color.White.copy(alpha = 0.7f)\n\n    Box(Modifier.fillMaxSize()) {'),
    ('            .background(if (immersive) Color.White.copy(alpha = 0.14f) else Color(0xFF242424), outer)',
     '            .background(Color.White.copy(alpha = 0.10f), outer)'),
    ('''                    immersive -> Color.White.copy(alpha = 0.92f)
                    else -> AppAccent
''', '''                    else -> Color.White.copy(alpha = 0.92f)
'''),
])

# ---- launcher icon
D = R + 'drawable/'
bars = [(29, 14), (40, 30), (51, 42), (62, 24), (73, 12)]
def fg(color, comment):
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- ' + comment + ' -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"', '    android:viewportWidth="108" android:viewportHeight="108">']
    for x, h in bars:
        top = 54 - h / 2.0 + 3
        out.append('    <path android:fillColor="%s" android:pathData="M%d,%.1fa3,3 0,0 1,6,0v%da3,3 0,0 1,-6,0z" />' % (color, x, top, h - 6))
    out.append('</vector>')
    return '\n'.join(out) + '\n'
io.open(D + 'ic_launcher_foreground.xml', 'w', encoding='utf-8', newline='\n').write(fg('#FFFFFFFF', 'The mark: five level bars, the same as the one on the welcome screen.'))
io.open(D + 'ic_launcher_monochrome.xml', 'w', encoding='utf-8', newline='\n').write(fg('#FFFFFFFF', 'The mark for themed icons: five level bars.'))
io.open(D + 'ic_launcher_background.xml', 'w', encoding='utf-8', newline='\n').write('''<?xml version="1.0" encoding="utf-8"?>
<!-- The app's one gradient (orange, pink, violet), corner to corner. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="14" android:startY="10" android:endX="94" android:endY="98">
                <item android:offset="0" android:color="#FFFF6B2C" />
                <item android:offset="0.5" android:color="#FFE5467C" />
                <item android:offset="1" android:color="#FF7C3DF2" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
print('ok')
