p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\PlayerBridge.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
o="    // Read at the moment of the tap, not captured: the lambdas below live as long as the screen."+nl+"    val actions = remember(vm, navController) {"
assert o in s
s=s.replace(o,"    // Read at the moment of the tap, not captured: the lambdas below live as long as the screen."+nl+
"    // The song as it is now: [state] itself was the first song's for good (reported: the like notice"+nl+
"    // and its \"Change\" named the song before)."+nl+
"    val now by androidx.compose.runtime.rememberUpdatedState(state)"+nl+
"    val actions = remember(vm, navController) {")
lines=s.split(nl)
for i in range(len(lines)):
    if 189<=i+1<=260 and "state." in lines[i] and "now." not in lines[i]:
        lines[i]=lines[i].replace("state.song","now.song").replace("state.title","now.title").replace("state.artist","now.artist").replace("state.coverUrl","now.coverUrl")
s=nl.join(lines)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
