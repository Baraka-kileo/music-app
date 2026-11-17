p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\lyrics\ImmersiveQueuePanel.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
a=s.index("    // The glass dock: shuffle, repeat and the sleep timer, floating over the foot of the queue.")
b=s.index("        SleepToggle()"+nl+"    }"+nl,a)+len("        SleepToggle()"+nl+"    }"+nl)
s=s[:a]+s[b:]
s=s.replace("contentPadding.calculateBottomPadding() + 88.dp","contentPadding.calculateBottomPadding() + 16.dp")
o=("            modifier = Modifier.weight(1f),"+nl+"        )"+nl+"        if (dock) QueueToggle(")
assert o in s
s=s.replace(o,("            modifier = Modifier.weight(1f),"+nl+"        )"+nl+
"        // Shuffle and repeat are the player's own, just below; the timer is the queue's (as the official app's)."+nl+
"        // Reported: a floating dock repeated them over the last rows."+nl+
"        if (!dock) SleepToggle()"+nl+"        if (dock) QueueToggle("),1)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
