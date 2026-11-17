p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\ImmersivePlayer.kt"
s=open(p,encoding="utf-8",newline="").read()
def rep(o,n,c=1):
    global s
    assert s.count(o)>=1,o[:50]; s=s.replace(o,n,c)
rep("style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = lineH),",
    "// One clear lead: the title larger than the artist (both were 20 sp, reported as cluttered).\n                style = Immersive.Title.copy(fontSize = 22.sp, lineHeight = 26.sp),".replace("\n","\r\n" if "\r\n" in s else "\n"))
rep("style = Immersive.Title.copy(fontSize = 20.sp, lineHeight = lineH, fontWeight = FontWeight.W500),\n                color = tint.copy(alpha = 0.55f),".replace("\n","\r\n" if "\r\n" in s else "\n"),
    "style = Immersive.Title.copy(fontSize = 17.sp, lineHeight = lineH, fontWeight = FontWeight.Normal),\n                color = tint.copy(alpha = 0.6f),".replace("\n","\r\n" if "\r\n" in s else "\n"))
rep("t, color = Color.White, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,",
    "// Under the title in weight: a line to glance at, not a second headline.\n            t, color = Color.White.copy(alpha = 0.78f), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,".replace("\n","\r\n" if "\r\n" in s else "\n"))
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
