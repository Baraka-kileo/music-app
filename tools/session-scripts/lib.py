import re
h=r"O:\base\app\src\main\java\com\music\spotui\ui\screens\HomeScreen.kt"
s=open(h,encoding="utf-8",newline="").read()
s=s.replace("private fun ProfileSheet(","internal fun ProfileSheet(",1)
open(h,"w",encoding="utf-8",newline="").write(s)
p=r"O:\base\app\src\main\java\com\music\spotui\ui\screens\LibraryScreeen.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
old_sheet=s[s.index("    if (showAccount) {"):s.index("    }",s.index("    if (showAccount) {")+20)+5]
s=s.replace(old_sheet,"    // The avatar opens the same profile drawer as Home's." + nl + "    if (showAccount) ProfileSheet(onOpen = { route -> showAccount = false; navController.navigate(route) }) { showAccount = false }")
a=s.index("            Box(" + nl + "                modifier = Modifier" + nl + "                    .size(34.dp)")
b=s.index("            }" + nl + "        }" + nl, a)+len("            }"+nl)
s=s[:a]+s[b:]
t="            Text(" + nl + "                text = stringResource(R.string.navigationbar_musicappitems_yourlibrary_title),"
av=("            // Avatar on the left, as on Home and in the official app." + nl +
    "            com.music.spotui.ui.components.ProfileAvatar(" + nl +
    "                url = com.music.spotui.data.api.ProfileCache.imageUrl," + nl +
    "                name = com.music.spotui.data.api.ProfileCache.name," + nl +
    "                size = 34.dp," + nl +
    "                modifier = Modifier.clickable { showAccount = true }," + nl +
    "            )" + nl +
    "            Spacer(Modifier.width(12.dp))" + nl)
assert t in s
s=s.replace(t,av+t,1)
# order: search then add (Spotify)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
