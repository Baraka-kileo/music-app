p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\sheets\ImmersiveSongOptionsSheet.kt"
s=open(p,encoding="utf-8",newline="").read()
o="""            icon = if (liked) null else com.music.spotui.ui.design.AppIcons.AddCircleOutline,"""
n="""            // The look's like is the star, as in the player (not the official app's plus and tick).
            icon = if (liked) com.music.spotui.ui.design.AppIcons.Star else com.music.spotui.ui.design.AppIcons.StarBorder,"""
nl="\r\n" if "\r\n" in s else "\n"
assert o in s
s=s.replace(o,n.replace("\n",nl))
o2="            leading = if (liked) ({ LikedTick() }) else null,"+nl
assert o2 in s
s=s.replace(o2,"")
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
