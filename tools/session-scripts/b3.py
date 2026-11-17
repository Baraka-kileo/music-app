import re, io, os, urllib.request
J = 'O:/base/app/src/main/java/com/music/spotui/'
A = J + 'ui/design/AppIcons.kt'

# Material name -> (phosphor slug, weight)
M = {
 'PlayArrow': ('play', 'fill'), 'Pause': ('pause', 'fill'), 'SkipNext': ('skip-forward', 'fill'), 'SkipPrevious': ('skip-back', 'fill'),
 'MoreHoriz': ('dots-three', 'bold'), 'MoreVert': ('dots-three-vertical', 'bold'), 'Close': ('x', 'regular'), 'DeleteOutline': ('trash', 'regular'),
 'Shuffle': ('shuffle', 'regular'), 'Favorite': ('heart', 'fill'), 'FavoriteBorder': ('heart', 'regular'), 'Add': ('plus', 'regular'),
 'Star': ('star', 'fill'), 'StarBorder': ('star', 'regular'), 'Refresh': ('arrow-clockwise', 'regular'), 'GraphicEq': ('waveform', 'regular'),
 'QueueMusic': ('queue', 'regular'), 'Translate': ('translate', 'regular'), 'SwapVert': ('arrows-down-up', 'regular'), 'Share': ('share-network', 'regular'),
 'DragHandle': ('dots-six', 'bold'), 'Devices': ('devices', 'regular'), 'AutoAwesome': ('sparkle', 'regular'), 'PlaylistAdd': ('list-plus', 'regular'),
 'Repeat': ('repeat', 'regular'), 'RepeatOne': ('repeat-once', 'regular'), 'PersonAdd': ('user-plus', 'regular'), 'Person': ('user', 'regular'),
 'KeyboardArrowDown': ('caret-down', 'regular'), 'KeyboardArrowUp': ('caret-up', 'regular'), 'KeyboardArrowLeft': ('caret-left', 'regular'),
 'Folder': ('folder', 'regular'), 'Edit': ('pencil-simple', 'regular'), 'Block': ('prohibit', 'regular'), 'Album': ('vinyl-record', 'regular'),
 'Verified': ('seal-check', 'fill'), 'Timer': ('timer', 'regular'), 'ThumbDownOffAlt': ('thumbs-down', 'regular'), 'SwapHoriz': ('arrows-left-right', 'regular'),
 'Speed': ('gauge', 'regular'), 'SortByAlpha': ('sort-ascending', 'regular'), 'RotateRight': ('arrow-clockwise', 'regular'),
 'RotateLeft': ('arrow-counter-clockwise', 'regular'), 'Radio': ('radio', 'regular'), 'PushPin': ('push-pin', 'fill'), 'Podcasts': ('broadcast', 'regular'),
 'NotificationsNone': ('bell', 'regular'), 'MusicNote': ('music-note', 'regular'), 'Lyrics': ('microphone-stage', 'regular'), 'Link': ('link', 'regular'),
 'Lightbulb': ('lightbulb', 'regular'), 'IosShare': ('export', 'regular'), 'Info': ('info', 'regular'), 'FormatQuote': ('quotes', 'regular'),
 'Extension': ('puzzle-piece', 'regular'), 'Download': ('download-simple', 'regular'), 'ContentCopy': ('copy', 'regular'),
 'Bookmark': ('bookmark-simple', 'fill'), 'Bedtime': ('moon', 'regular'), 'ArrowUpward': ('arrow-up', 'regular'), 'ArrowDownward': ('arrow-down', 'regular'),
 'ArrowCircleDown': ('arrow-circle-down', 'regular'), 'Analytics': ('chart-bar', 'regular'), 'AddCircleOutline': ('plus-circle', 'regular'),
 'Abc': ('text-aa', 'regular'), 'Mic': ('microphone', 'regular'), 'Group': ('users', 'regular'), 'VolumeUp': ('speaker-high', 'regular'),
 'VolumeDown': ('speaker-low', 'regular'), 'PlaylistPlay': ('playlist', 'regular'), 'OpenInNew': ('arrow-square-out', 'regular'),
 # tabs: outline, and the filled twin for the tab one is in
 'TabHome': ('house', 'regular'), 'TabHomeOn': ('house', 'fill'), 'TabLibrary': ('books', 'regular'), 'TabLibraryOn': ('books', 'fill'),
 'TabSearch': ('magnifying-glass', 'regular'), 'TabSearchOn': ('magnifying-glass', 'bold'),
}
src = io.open(A, encoding='utf-8').read()
have = set(re.findall(r'val (\w+): ImageVector', src))
cache = 'C:/Users/Student/AppData/Local/Temp/phsvg/'
os.makedirs(cache, exist_ok=True)
add = ['\n    // By shape: the icons the screens name directly.\n']
for name, (slug, w) in M.items():
    if name in have:
        continue
    f = slug + ('' if w == 'regular' else '-' + w) + '.svg'
    cp = cache + w + '-' + f
    if not os.path.exists(cp):
        url = 'https://unpkg.com/@phosphor-icons/core@2.1.1/assets/%s/%s' % (w, f)
        data = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'python'}), timeout=30).read()
        open(cp, 'wb').write(data)
    svg = open(cp, encoding='utf-8').read()
    ds = re.findall(r'<path d="([^"]+)"', svg)
    assert ds and 'viewBox="0 0 256 256"' in svg and svg.count('<') == svg.count('<path') + 2, (slug, w, svg[:300])
    add.append('    /** Phosphor "%s" (%s). */\n    val %s: ImageVector by icon("%s", "%s")\n' % (slug, w, name, slug, ' '.join(ds)))
k = src.rindex('}')
io.open(A, 'w', encoding='utf-8', newline='\n').write(src[:k] + '\n'.join(add) + '}\n')
have |= set(M)

# ---- swap every Material icon reference in the Immersive screens
pat = re.compile(r'(?:androidx\.compose\.material\.icons\.)?Icons\.(?:AutoMirrored\.)?(?:Rounded|Outlined|Filled|Default)\.(\w+)')
n = 0; missing = set()
for root, _, files in os.walk(J + 'ui/immersive'):
    for fn in files:
        if not fn.endswith('.kt'): continue
        p = os.path.join(root, fn)
        s = io.open(p, encoding='utf-8', newline='').read()
        def sub(m):
            global n
            if m.group(1) not in have:
                missing.add(m.group(1)); return m.group(0)
            n += 1
            return 'com.music.spotui.ui.design.AppIcons.' + m.group(1)
        t = pat.sub(sub, s)
        if t != s:
            io.open(p, 'w', encoding='utf-8', newline='').write(t)
print('swapped', n, 'missing', sorted(missing))

def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    s = s.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf: s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

# ---- tab bar: outline icons, filled in the current tab; a tick on changing tab; a little slimmer
T = J + 'ui/immersive/shell/ImmersiveTabBar.kt'
edit(T, [
    ('''    Icon(
        painter = painterResource(item.icon),
''', '''    val I = com.music.spotui.ui.design.AppIcons
    Icon(
        imageVector = when (item) {
            Routes.Home -> if (selected) I.TabHomeOn else I.TabHome
            Routes.Library -> if (selected) I.TabLibraryOn else I.TabLibrary
            else -> if (selected) I.TabSearchOn else I.TabSearch
        },
'''),
    ('private fun TabIcon(item: Routes, tint: Color) {', 'private fun TabIcon(item: Routes, tint: Color, selected: Boolean) {'),
    ('icon = { TabIcon(item, tint) },', 'icon = { TabIcon(item, tint, selectedTab == item.route) },'),
    ('''                        if (selectedTab == Routes.Search.route) Immersive.Accent else Immersive.Text.copy(alpha = 0.65f),
''', '''                        if (selectedTab == Routes.Search.route) Immersive.Accent else Immersive.Text.copy(alpha = 0.65f),
                        selectedTab == Routes.Search.route,
'''),
    ('                    onClick = { currentOnTabClick(item) },', '                    onClick = { if (selectedTab != item.route) com.music.spotui.di.Haptics.tick(); currentOnTabClick(item) },'),
    ('                onClick = { currentOnTabClick(Routes.Search) },', '                onClick = { if (selectedTab != Routes.Search.route) com.music.spotui.di.Haptics.tick(); currentOnTabClick(Routes.Search) },'),
    ('private val TAB_VERTICAL_PADDING = 7.dp', 'private val TAB_VERTICAL_PADDING = 5.dp'),
    ('        modifier = Modifier.size(22.dp),\n    )\n}', '        modifier = Modifier.size(23.dp),\n    )\n}'),
])

# ---- shuffle and repeat in the player: on unless the user turned them off
edit(J + 'data/preferences/SettingsPref.kt', [(
    'private const val KEY = "immersive_player_modes"\n    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, false))',
    'private const val KEY = "immersive_player_modes"\n    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))')])
print('ok')
