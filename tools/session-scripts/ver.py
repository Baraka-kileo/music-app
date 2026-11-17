p=r"O:\base\api-spotify\src\main\kotlin\app\myspoti\spotify\SpotifyContent.kt"
s=open(p,encoding="utf-8",newline="").read()
old='artist(a)?.copy(verified = a.bool("onPlatformReputationTrait.verification.isVerified") ?: false)'
assert s.count(old)==2
s=s.replace(old,'artist(a)?.let { it.copy(verified = verifiedOf(a, it)) }')
nl="\r\n" if "\r\n" in s else "\n"
anchor="    private suspend fun artistUnion(uri: Uri) ="
s=s.replace(anchor,("    /** Verified by either field: the reputation trait is missing from some answers, where profile.verified says it"+nl+
"     *  (reported: no badge on verified artists). */"+nl+
"    private fun verifiedOf(a: kotlinx.serialization.json.JsonElement?, base: Artist): Boolean ="+nl+
"        a.bool(\"onPlatformReputationTrait.verification.isVerified\") == true || base.verified"+nl+nl+anchor),1)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
