p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\ImmersivePlayer.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
a=s.index("                    // ---- Switch to video / to audio: on the cover's lower corner, gone with a panel open ----")
b=s.index("                    // ---- The line being sung, above the title (a setting) ----")
s=s[:a]+s[b:]
o=("                                // Over a Canvas the video pill sits just above the title; over a cover it is on the cover."+nl+
"                                val y = if (canvasOn) titleTop() - (if (videoReady || videoOn) 46.dp else 0.dp) - 8.dp - 64.dp"+nl+
"                                else titleTop() - 64.dp - 2.dp"+nl)
assert o in s
s=s.replace(o,"                                val y = titleTop() - 64.dp - 2.dp"+nl)
o2="                            onSaveTo = { state.song?.let { com.music.spotui.ui.components.AppNotice.openSaveTo(it) } },"+nl+"                        )"
assert o2 in s
s=s.replace(o2,"                            onSaveTo = { state.song?.let { com.music.spotui.ui.components.AppNotice.openSaveTo(it) } },"+nl+
"                            // The video switch has one place, beside the like, whatever shows above"+nl+
"                            // (reported: a pill that moved between the cover's corner and the title)."+nl+
"                            video = if ((videoReady || videoOn) && panel == Panel.NONE) videoOn else null,"+nl+"                        )",1)
open(p,"w",encoding="utf-8",newline="").write(s)

q=r"O:\base\app\src\main\java\com\music\spotui\ui\components\AppNotice.kt"
t=open(q,encoding="utf-8",newline="").read()
o3="""                if (n.icon != null) Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(RoundedCornerShape(50)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(11.dp)) }"""
nl2="\r\n" if "\r\n" in t else "\n"
o3=o3.replace("\n",nl2); assert o3 in t
t=t.replace(o3,"""                // Large enough to read its shape (a star at 11 dp in 16 read as a heart, reported).
                if (n.icon != null) Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(RoundedCornerShape(50))
                        .background(Color(0xFF1C1C1E)).padding(2.dp).clip(RoundedCornerShape(50)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(12.dp)) }""".replace("\n",nl2))
open(q,"w",encoding="utf-8",newline="").write(t); print("ok")
