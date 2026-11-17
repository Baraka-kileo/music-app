p=r"O:\base\app\src\main\java\com\music\spotui\ui\components\AccountsList.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
a="            val active = account.profile == list.active"+nl
assert a in s
s=s.replace(a,a+"            // Signed in but another music account chosen (or none): not \"in use\". Reported: \"In use\" beside \"None\"."+nl+
 "            val inUse = active && com.music.spotui.data.preferences.AccountProviderPref.id == app.myspoti.api.account.AccountProviders.SPOTIFY"+nl,1)
old="if (active) stringResource(R.string.accounts_in_use) else null).joinToString(\" · \")"
assert old in s
s=s.replace(old,"when { inUse -> stringResource(R.string.accounts_in_use); active -> stringResource(R.string.accounts_not_in_use); else -> null }).joinToString(\" · \")")
s=s.replace("color = if (active) look.accent else look.muted, fontSize = 12.sp","color = if (inUse) look.accent else look.muted, fontSize = 12.sp")
s=s.replace("if (active) Icon(Icons.Filled.Check","if (inUse) Icon(Icons.Filled.Check")
s=s.replace("                // The account in use is removed by \"Log out\"; the others from here."+nl+"                else IconButton(",
 "                // The account in use is removed by \"Log out\"; the others from here."+nl+"                else if (!active) IconButton(")
open(p,"w",encoding="utf-8",newline="").write(s)
q=r"O:\base\app\src\main\res\values\accounts_strings.xml"
t=open(q,encoding="utf-8",newline="").read()
t=t.replace('    <string name="accounts_in_use">In use</string>','    <string name="accounts_in_use">In use</string>\n    <string name="accounts_not_in_use">Signed in, not in use</string>')
open(q,"w",encoding="utf-8",newline="").write(t); print("ok")
