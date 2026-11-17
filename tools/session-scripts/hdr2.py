p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\ImmersivePlayer.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
s=s.replace("""            // Scrolled past the controls: the official app's slim bar at the top (title, add, pause,
            // the progress line), in the song's colour.""".replace("\n",nl),
"""            // Scrolled past the controls: a floating bar at the top (cover, title, star, pause, the
            // progress line) in the song's colour; a tap brings the player back.""".replace("\n",nl))
s=s.replace("""                enter = androidx.compose.animation.fadeIn(tween(180)),
                exit = androidx.compose.animation.fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) { ScrolledHeader(state, actions, songCardTones(palette).bar) }""".replace("\n",nl),
"""                enter = androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.slideInVertically(tween(240)) { -it / 2 },
                exit = androidx.compose.animation.fadeOut(tween(160)) + androidx.compose.animation.slideOutVertically(tween(200)) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val headerScope = androidx.compose.runtime.rememberCoroutineScope()
                ScrolledHeader(state, actions, songCardTones(palette).bar) { headerScope.launch { pageScroll.animateScrollTo(0) } }
            }""".replace("\n",nl))
i=s.index("import androidx.compose.foundation.interaction.MutableInteractionSource")
s=s[:i]+"import androidx.compose.foundation.border"+nl+"import androidx.compose.ui.draw.shadow"+nl+s[i:]
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
