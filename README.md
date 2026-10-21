# Moje Kamere — Android TV aplikacija

Android TV aplikacija za pregled RTSP kamera na lokalnoj mreži.

- **Grid pogled** — sve kamere uživo, jedna do druge.
- **Fullscreen** — OK/klik na kameru je otvori preko cijelog ekrana; strelice **lijevo/desno** na daljinskom mijenjaju kameru.
- **Podesiva lista** — Postavke → dodaj / uredi / obriši kameru (ime + RTSP adresa).
- Dolazi s dvije kamere već unesene (`192.168.0.103`, `192.168.0.207`).

---

## Kako dobiti `.apk` (bez instaliranja Android Studija) — GitHub Actions

1. Napravi besplatan nalog na https://github.com ako ga nemaš.
2. Napravi novi **repozitorij** (npr. `moje-kamere`), tip: **Private**.
3. Uploaduj sve fajlove iz ovog foldera u repozitorij (dugme **Add file → Upload files**, prevuci cijeli sadržaj).
4. Otvori karticu **Actions** → ako pita, klikni **I understand... enable Actions**.
5. Build **"Build APK"** se pokrene sam nakon uploada. Sačekaj ~3-5 min da se završi (zeleni kvačić).
6. Klikni na taj build → dole pod **Artifacts** klikni **MojeKamere-debug-apk** da skineš ZIP.
7. Unutra je **`app-debug.apk`** — to instaliraš na TV.

> Alternativa: otvori projekat u **Android Studio** → Build → Build APK(s).

---

## Instalacija na Android TV

1. Na TV-u: **Settings → Apps → Security** → uključi **Unknown sources** (nepoznati izvori) za aplikaciju kojom ćeš otvoriti APK.
2. Prebaci `app-debug.apk` na TV. Najlakše:
   - app **"Send files to TV"** (sa telefona), ili
   - USB stick, ili
   - `adb install app-debug.apk` ako koristiš ADB.
3. Otvori APK na TV-u → **Install** → **Open**.
4. Aplikacija **"Moje Kamere"** se pojavi u listi aplikacija na početnom ekranu TV-a.

---

## Promjena kamera / lozinki

U aplikaciji: **Postavke** (dugme gore desno) → **Uredi** kod kamere.
RTSP format: `rtsp://korisnik:lozinka@IP:554/stream1`

## Tehnički detalji

- Kotlin, Media3 **ExoPlayer** (RTSP), minSdk 21, compileSdk 34.
- Kamere se čuvaju lokalno (SharedPreferences) — ništa ne ide na internet osim ka samim kamerama.
