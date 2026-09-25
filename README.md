# KingFrame

Képernyővédő Android TV-re, ami a Google Fotókból kiválasztott képeidet mutatja – **hellyel és időponttal**.

A Google Photos API 2025 óta nem ad hozzáférést az albumokhoz és a helyadatokhoz, ezért a képek
a telefonon át jutnak a TV-re: a Google Fotókban kijelölöd őket → **Megosztás → KingFrame**, a telefonos
alkalmazás kiolvassa az eredeti fájlból a GPS-t és a készítés idejét, a TV felbontására méretezi a képet,
és a helyi Wi-Fi-n átküldi a TV-nek. Felhőbe semmi nem kerül.

## Részek

| Modul | Mi ez |
|---|---|
| `tv/` | Android TV alkalmazás: képernyővédő (DreamService), beállítások, képfogadó szerver (NanoHTTPD), párosítás PIN-kóddal |
| `phone/` | Telefonos alkalmazás: megosztás fogadása, EXIF és helynév, átméretezés, szinkron (WorkManager), TV-beállítások |
| `shared/` | Közös protokoll (kotlinx.serialization), arculat (színek, Plus Jakarta Sans), helyszínkeresés |
| `sharetest/` | Kis tesztalkalmazás: megmutatja, milyen EXIF-adat jön át a Google Fotókból megosztott képekkel |

## Funkciók

- Képernyővédő lassú nagyítással / panorámaúsztatással, többféle áttűnéssel
- Hely és dátum a képen, óra és időjárás (Open-Meteo) a sarokban
- „Ezen a napon”: évfordulós képek gyakrabban, „3 éve ezen a napon” felirattal
- Éjszakai mód (sötét képernyő vagy halvány óra)
- Telefonon: helyjavaslat a hely nélküli képekhez, többes kijelölés, kedvencek, elrejtés, szűrők,
  a TV összes beállítása
- „Teljes album – frissítés”: az újra megosztott album alapján átküldi az újakat, törli a kikerülteket

## Fordítás

Android Studio (JDK 21) vagy parancssorból:

```bash
./gradlew :phone:assembleRelease :tv:assembleRelease
```

Az APK-k: `phone/build/outputs/apk/release/`, `tv/build/outputs/apk/release/`.
(A release build egyelőre a debug kulccsal van aláírva, oldalról telepítéshez.)

## Képernyővédő beállítása Google TV-n

Google TV-n a menüből nem választható külső képernyővédő, egyszer a számítógépről kell beállítani:

```bash
adb shell settings put secure screensaver_components com.kiroland.gallery.tv/.PhotoDreamService
```

## Köszönet

- Időjárás és helyszínkeresés: [Open-Meteo](https://open-meteo.com) (CC BY 4.0)
- Betűtípus: [Plus Jakarta Sans](https://github.com/tokotype/PlusJakartaSans) (SIL OFL 1.1, lásd `shared/FONT_LICENSE_OFL.txt`)
- HTTP szerver a TV-n: [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)
