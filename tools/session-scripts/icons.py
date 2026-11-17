import re, io, urllib.request
icons = [  # (name, phosphor slug, weight, comment group)
    ('Back', 'arrow-left', 'regular'), ('Chevron', 'caret-right', 'bold'), ('Check', 'check', 'bold'), ('Search', 'magnifying-glass', 'regular'),
    ('Settings', 'gear-six', 'regular'), ('Stats', 'chart-bar', 'regular'), ('Extensions', 'puzzle-piece', 'regular'),
    ('History', 'clock-counter-clockwise', 'regular'), ('AccountPage', 'user-circle-gear', 'regular'), ('SwitchAccount', 'users', 'regular'),
    ('Support', 'heart', 'regular'), ('Perks', 'crown', 'regular'), ('About', 'info', 'regular'), ('LogOut', 'sign-out', 'regular'),
    ('Account', 'user', 'regular'), ('Sources', 'plugs-connected', 'regular'), ('Playback', 'play', 'regular'), ('Transitions', 'waveform', 'regular'),
    ('Library', 'books', 'regular'), ('Downloads', 'download-simple', 'regular'), ('Lyrics', 'microphone-stage', 'regular'),
    ('Display', 'palette', 'regular'), ('Data', 'cell-signal-high', 'regular'), ('Storage', 'hard-drives', 'regular'),
    ('Advanced', 'sliders-horizontal', 'regular'),
]
out = ['''package com.music.spotui.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's icons, by what they mean. Every screen asks for an icon here instead of picking one
 * from a library, so one family is used throughout and an icon is changed in one place.
 *
 * The drawings are Phosphor Icons 2.1.1 (https://phosphoricons.com), copied in as path data:
 * MIT licence, copyright (c) 2020-2023 Phosphor Icons. Only the icons used are here.
 */
object AppIcons {
    private fun icon(name: String, path: String): Lazy<ImageVector> = lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 256f, viewportHeight = 256f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
    }
''']
for name, slug, w in icons:
    f = slug + ('' if w == 'regular' else '-' + w) + '.svg'
    url = 'https://unpkg.com/@phosphor-icons/core@2.1.1/assets/%s/%s' % (w, f)
    svg = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'python'}), timeout=30).read().decode()
    ds = re.findall(r'<path d="([^"]+)"', svg)
    assert ds and 'viewBox="0 0 256 256"' in svg and svg.count('<') == svg.count('<path') + 2, (slug, svg[:200])
    out.append('    /** Phosphor "%s" (%s). */\n    val %s: ImageVector by icon("%s", "%s")\n' % (slug, w, name, slug, ' '.join(ds)))
out.append('}\n')
io.open('O:/base/app/src/main/java/com/music/spotui/ui/design/AppIcons.kt', 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
g = 'O:/base/app/build.gradle.kts'
s = io.open(g, encoding='utf-8', newline='').read()
n = len(s)
s = re.sub(r'[ \t]*implementation\("com\.adamglin:phosphor-icon:1\.0\.0"\)\r?\n', '', s)
assert len(s) < n
io.open(g, 'w', encoding='utf-8', newline='').write(s)
print('ok', len(icons))
