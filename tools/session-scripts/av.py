p=r"O:\base\app\src\main\java\com\music\spotui\ui\screens\HomeScreen.kt"
s=open(p,encoding="utf-8",newline="").read()
a=s.index("        val avatarUrl = com.music.spotui.data.api.ProfileCache.imageUrl")
b=s.index("        WhatsNewBell {",a)
nl="\r\n" if "\r\n" in s[a:b] else "\n"
new="""        // Same avatar as everywhere else: picture, else the name's letter, else a person icon.
        Box(
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showProfile = true }
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
            com.music.spotui.ui.components.ProfileAvatar(
                url = com.music.spotui.data.api.ProfileCache.imageUrl,
                name = com.music.spotui.data.api.ProfileCache.name,
                size = 34.dp,
            )
        }
""".replace("\n",nl)
s=s[:a]+new+s[b:]
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
