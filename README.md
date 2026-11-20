# Dost AI (Android)

Kotlin, Jetpack Compose, Material 3, Room we Firebase AI Logic bilen gurlan akylly söhbetdeş programma.

## GitHub-da APK ýygnamak (GitHub Actions)

Bu taslama **GitHub Actions** arkaly awtomatik APK ýygnamaga taýýar edilendir (`.github/workflows/build-apk.yml`).

### Ädimler:
1. Kody GitHub repozitoriýaňyza ýükläň (`main` ýa-da `master` şahasyna `push` ediň).
2. GitHub-da **Actions** bölümine giriň — **Build Android APK** iş akymy awtomatik başlar (islän wagtyňyz **Run workflow** bilen el bilen hem işledip bilersiňiz).
3. Iş akymy tamamlanandan soň, aşakdaky **Artifacts** bölüminden **`DostAI-debug-apk`** faýlyny ýükläp alyň (`app-debug.apk`).

> **Bellik:** Workflow taslamadaky `debug.keystore.base64` faýlyndan `debug.keystore` döredýär we `app/google-services.json` içindäki gol (SHA-1/SHA-256) bilen gabat gelýän APK ýygnaýar.

### Islege görä Release APK gol çekmek (GitHub Secrets)
Eger Play Store ýa-da öz gol çekilen `release` APK-ňyzy ýygnamak isleseňiz, GitHub repozitoriýaňyzyň **Settings → Secrets and variables → Actions** bölüminde şu 3 sany gizlin açary (secret) goşuň:
- `KEYSTORE_BASE64` — `.jks` keystore faýlyňyzyň `base64` görnüşi (key alias: `upload`)
- `STORE_PASSWORD` — keystore paroly
- `KEY_PASSWORD` — açar paroly

Bu açarlar goşulanda workflow goşmaça **`DostAI-release-apk`** (`app-release.apk`) faýlyny hem döredip ýüklär.

---

## Local Build

```bash
[ -f debug.keystore ] || base64 -d debug.keystore.base64 > debug.keystore
gradle :app:testDebugUnitTest :app:assembleDebug
```
