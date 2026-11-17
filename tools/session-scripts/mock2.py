import re
p = 'O:/design-system-mockup.html'
s = open(p, encoding='utf-8').read()
m = {'graphic_eq': 'waveform', 'lyrics': 'microphone-stage', 'palette': 'palette', 'download': 'download-simple',
     'extension': 'puzzle-piece', 'person': 'user', 'chevron_right': 'caret-right', 'settings': 'gear-six',
     'insights': 'chart-bar', 'history': 'clock-counter-clockwise', 'devices': 'devices', 'campaign': 'megaphone',
     'logout': 'sign-out', 'search': 'magnifying-glass', 'tune': 'sliders-horizontal', 'info': 'info', 'arrow_back': 'arrow-left'}
def sub(mo):
    cls, style, name = mo.group(1) or '', mo.group(2) or '', mo.group(3)
    return '<i class="ph ph-%s ms%s"%s></i>' % (m[name], cls, style)
s, n = re.subn(r'<span class="ms( ch)?"( style="[^"]*")?>([a-z_]+)</span>', sub, s)
assert n > 25, n
def rep(a, b):
    global s
    assert s.count(a) == 1, (s.count(a), a[:60])
    s = s.replace(a, b)
rep('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0">',
    '<link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/regular/style.css">\n<link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/fill/style.css">')
rep('.ms{font-family:"Material Symbols Rounded";font-size:22px;line-height:1;font-style:normal;display:inline-block}',
    '.ms{font-size:22px;line-height:1;display:inline-block}\n'
    '.tab{position:absolute;left:16px;right:16px;bottom:16px;display:flex;gap:10px;align-items:center}\n'
    '.tab .bar{flex:1;display:flex;justify-content:space-around;padding:10px 6px;border-radius:999px;background:rgba(30,24,40,.72);border:1px solid var(--edge);backdrop-filter:blur(24px)}\n'
    '.tab .bar div{font-size:10.5px;font-weight:600;color:var(--t3);text-align:center}.tab .bar div i{display:block;font-size:23px;margin-bottom:2px}\n'
    '.tab .bar .on{color:#fff}.tab .fab{width:54px;height:54px;border-radius:50%;background:rgba(30,24,40,.72);border:1px solid var(--edge);display:flex;align-items:center;justify-content:center;font-size:23px}\n'
    '.hap{display:grid;grid-template-columns:auto 1fr;gap:6px 12px;font-size:12.5px;color:var(--t2)}.hap b{display:inline;font-size:12.5px;color:#fff;margin:0}')
rep('''<small>One rounded icon family, white on a glass tile. Today the app mixes three icon styles.</small></div>''',
    '''<div class="ic" style="margin-top:10px;align-items:center;font-size:24px;gap:16px"><i class="ph ph-heart"></i><i class="ph-fill ph-heart" style="color:#FF6B8A"></i><i class="ph ph-house"></i><i class="ph-fill ph-house"></i><i class="ph ph-shuffle"></i><i class="ph ph-repeat"></i><i class="ph-fill ph-play"></i></div>
      <small>Phosphor icons: one modern family. Outline by default, the filled twin when something is active (liked, current tab, playing).</small></div>''')
rep('''<small>One switch, one picker, two buttons, used everywhere.</small></div>''',
    '''<small>One switch, one picker, two buttons, used everywhere.</small></div>
    <div class="tok"><b>Touch feedback</b>
      <div class="hap"><b>Tick</b><span>switch, picker, tab change, slider steps</span><b>Click</b><span>play, pause, skip, main buttons</span><b>Pop</b><span>like, add to playlist, download done</span><b>Thud</b><span>long-press menu, drag to reorder</span><b>None</b><span>scrolling, plain navigation, typing</span></div>
      <small>Four vibrations with fixed meanings, plus a press-shrink on everything tappable. One switch in Settings turns them off.</small></div>''')
rep('''          <div class="row"><span class="tile"><i class="ph ph-info ms"></i></span><div class="t"><b>About</b></div><i class="ph ph-caret-right ms ch"></i></div>
        </div>
      </div></div></div>''', '''          <div class="row"><span class="tile"><i class="ph ph-info ms"></i></span><div class="t"><b>About</b></div><i class="ph ph-caret-right ms ch"></i></div>
        </div>
        <div class="tab"><div class="bar"><div class="on"><i class="ph-fill ph-house"></i>Home</div><div><i class="ph ph-compass"></i>Explore</div><div><i class="ph ph-books"></i>Library</div></div><div class="fab"><i class="ph ph-magnifying-glass"></i></div></div>
      </div></div></div>''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('ok', n)
