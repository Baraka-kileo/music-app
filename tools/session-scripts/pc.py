p=r"O:\base\app\src\main\java\com\music\spotui\data\api\ProfileCache.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
old=("            try {"+nl+"                while (true) {"+nl+"                    val requestGeneration = generation.get()"+nl+
"                    MyspotiRuntime.api.ready.first { it }"+nl+
"                    val me = runCatching { MyspotiRuntime.api.users.me() }.getOrNull()"+nl+
"                    // The session was lost meanwhile: this profile may be the account before, the next one's is read."+nl+
"                    if (generation.get() != requestGeneration) continue"+nl)
assert old in s
new=("            try {"+nl+"                var failures = 0"+nl+"                while (true) {"+nl+"                    val requestGeneration = generation.get()"+nl+
"                    MyspotiRuntime.api.ready.first { it }"+nl+
"                    val read = runCatching { MyspotiRuntime.api.users.me() }"+nl+
"                    // The session was lost meanwhile: this profile may be the account before, the next one's is read."+nl+
"                    if (generation.get() != requestGeneration) continue"+nl+
"                    // A failed read (the session still starting, the network) is tried again a few times:"+nl+
"                    // read once at launch, the profile stayed unknown (\"•\" for the avatar, no history) for the run."+nl+
"                    if (read.isFailure && failures < PROFILE_RETRIES) { failures++; kotlinx.coroutines.delay(2_000L * failures); continue }"+nl+
"                    val me = read.getOrNull()"+nl)
s=s.replace(old,new)
s=s.rstrip()+nl+nl+"/** Reads of the profile tried again after a failure, 2, 4, 6 then 8 s apart. */"+nl+"private const val PROFILE_RETRIES = 4"+nl
open(p,"w",encoding="utf-8",newline="").write(s)

q=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\screens\ImmersiveHomeProfile.kt"
t=open(q,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in t else "\n"
o="            Text(text = initial, style = Immersive.BodyStrong.copy(color = Immersive.Page, fontWeight = FontWeight.Bold))"
assert o in t
t=t.replace(o,"            // No name known: a person, not a bullet."+nl+
"            if (initial.isBlank() || initial == \"•\") androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Person, contentDescription = contentDescription, tint = Immersive.Page, modifier = Modifier.size(size * 0.55f))"+nl+
"            else "+o.strip())
open(q,"w",encoding="utf-8",newline="").write(t); print("ok")
