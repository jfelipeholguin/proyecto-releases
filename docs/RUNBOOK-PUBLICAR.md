# Runbook · Publicar actualización de Proyecto Android

## Regla de oro
Las actualizaciones se publican como **GitHub Release** en `jfelipeholguin/proyecto-releases` y Juan (o el usuario final) actualiza desde la app (botón "Actualizar" del auto-updater). **NUNCA** enviar APK por Telegram/chat.

## Pasos
1. **Bump de versión:** editar `app/build.gradle.kts` → `versionCode` (+1) y `versionName` (`X.Y.Z`).
2. **Build + tests:**
   ```
   cd ~/Documents/Building/Sende/proyecto-android
   ./gradlew :app:assembleDebug
   ```
3. **Publicar:** `./scripts/publish-release.sh <versionName> "<notas>"` → tag = `v{versionCode}`.
4. **Verificación post-publicación:** refrescar con cache-bypass (Ctrl+Shift+R) porque la página es estática.
5. **Avisar:** actualizar desde la app.

## Pasos detallados
1. **Bump de versión:** `versionCode` (+1) y `versionName` (`X.Y.Z`) en `app/build.gradle.kts` (defaultConfig).
2. **Build + tests:**
   `cd ~/Documents/Building/Sende/proyecto-android && ./gradlew :app:assembleDebug`
3. **Verificar APK en outputs:** `ls app/build/outputs/apk/debug/*.apk`
4. **Publicar:** `./scripts/publish-release.sh <versionName> "<notas>"` → tag = `v{versionCode}`, repo `jfelipeholguin/proyecto-releases`.
5. **Commit:** `git add -A && git commit -m "Bump vX.Y.Z"`.
6. **Avisar:** "publicado vX.Y.Z → actualiza desde la app".

⚠️ Antes de publicar, chequear que no exista ya el tag:
`gh release list --repo jfelipeholguin/proyecto-releases`

## Regla de oro (Juan): las actualizaciones se publican como GitHub Release y Juan actualiza desde la app (botón "Actualizar" del auto-updater). **NUNCA** enviar APK por Telegram/chat.
