# Release minification is enabled (see app/build.gradle.kts). No custom keep rules are needed
# today: persistence goes through plain org.json.JSONObject key/value access and
# LanguageScript.valueOf()/.name (both safe under default R8 rules, not reflection), and there's
# no Gson/Moshi/kotlinx.serialization, Room, or Dagger/Hilt in this app. AndroidX/Compose ship
# their own consumer rules, which AGP merges automatically. Add rules here if R8 strips or
# renames something a future dependency needs reflective access to.
