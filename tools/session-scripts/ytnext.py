js=r"O:\base\api-ytmusic\src\main\resources\ytmusic\ytm-page.js"
s=open(js,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
anchor="  // getPlayerState: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued."
assert anchor in s
fn='''  // The song after this one in the page's own "Up next" (the queue it will play), so the app can show
  // it the moment next is tapped while the page loads it; null when the queue is not on the page.
  const nextItem = () => {
    try {
      const items = Array.from(document.querySelectorAll('ytmusic-player-queue-item'));
      const i = items.findIndex((el) => el.hasAttribute('selected'));
      const el = i >= 0 ? items[i + 1] : null;
      if (!el) return null;
      const d = el.data || (el.__data && el.__data.data) || {};
      const id = d.videoId || null;
      if (!id) return null;
      const t = el.querySelector('.song-title');
      const b = el.querySelector('.byline');
      const img = el.querySelector('img');
      return {
        trackId: id,
        title: t ? t.textContent.trim() : null,
        artist: b ? b.textContent.trim() : null,
        cover: img && img.src && img.src.startsWith('http') ? img.src : 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg',
      };
    } catch (e) { return null; }
  };

'''
s=s.replace(anchor,fn.replace("\n",nl)+anchor,1)
o="      at: Date.now(),"+nl+"    };"
assert o in s
s=s.replace(o,"      at: Date.now(),"+nl+"      next: nextItem(),"+nl+"    };",1)
o2="const key = [s.trackId, s.paused, s.buffering, s.ad, s.title, s.cover].join('|');"
assert o2 in s
s=s.replace(o2,"const key = [s.trackId, s.paused, s.buffering, s.ad, s.title, s.cover, s.next && s.next.trackId].join('|');")
open(js,"w",encoding="utf-8",newline="").write(s)

p=r"O:\base\api-ytmusic\src\main\kotlin\app\myspoti\ytmusic\YtmPlayer.kt"
k=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in k else "\n"
o3=("        return state("+nl+"            item,"+nl)
assert o3 in k
k=k.replace(o3,("        // The page's next song, shown at once when next is tapped (the page takes seconds to load it)."+nl+
"        val next = (o[\"next\"] as? JsonObject)?.let { n ->"+nl+
"            fun ns(key: String) = (n[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }"+nl+
"            ns(\"trackId\")?.let { nid ->"+nl+
"                NowPlaying("+nl+
"                    kind = ItemKind.TRACK, uri = \"${Ytm.PROVIDER}:track:$nid\", uid = null,"+nl+
"                    name = ns(\"title\").orEmpty(),"+nl+
"                    artists = ns(\"artist\")?.substringBefore(\" • \")?.let { listOf(ArtistRef(\"\", it.removeSuffix(\" - Topic\"))) }.orEmpty(),"+nl+
"                    album = null, imageUrl = ns(\"cover\"), durationMs = 0L, provider = \"context\","+nl+
"                )"+nl+
"            }"+nl+
"        }"+nl+
"        return state("+nl+"            item,"+nl+"            next = next,"+nl),1)
o4="    private fun state(item: NowPlaying, paused: Boolean, buffering: Boolean, positionMs: Long, at: Long) = PlayerState("+nl+"        item = item, contextUri = context,"
assert o4 in k
k=k.replace(o4,"    private fun state(item: NowPlaying, paused: Boolean, buffering: Boolean, positionMs: Long, at: Long, next: NowPlaying? = null) = PlayerState("+nl+"        item = item, next = next, contextUri = context,")
open(p,"w",encoding="utf-8",newline="").write(k); print("ok")
