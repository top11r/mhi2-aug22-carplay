# mmi_settings

The CarPlay settings menu in the Audi MHI2 HMI (K3346 / MU1438): **CarPlay settings**,
reached from MENU → Audi smartphone interface while no phone is connected. Each row is
a flag file in `/mnt/app` that the CarPlay patches read; the menu creates or deletes it,
remounting `/mnt/app` read-write only for that change when it is read-only.

GPL-3.0-or-later, copyright top11r, with an additional permission to combine it with the
head unit's own HMI classes – see [LICENSE](LICENSE).

## Build

```sh
src/mmi_settings/build.sh <stock lsd.jar>
```

The stock HMI classes are not in this repository: convert your own unit's `lsd.jxe` to a
jar and pass its path. Needs a Java 8 JDK (`JAVAC`, `JAR`, `JAVA` override the Zulu 8
default) and Python 3. The build

1. generates the text tables from `i18n/*.json` (`tools/gen_texts.py`),
2. compiles for the head unit's J9 (Java 1.4 class files),
3. runs `test/HostCheck` against the stock classes – the extended state machines, the
   model bank, the settings table and the texts – and stops on any failure,
4. writes `bin/mmi_settings.jar`, which `install.sh` copies to the unit.

## Layout

| Where | What |
|---|---|
| `java/com/mhi2/settings/Settings.java` | the rows: flag file, ticked = present or absent, when it takes effect, screen |
| `java/com/mhi2/settings/store/` | flag files and the remount (`FlagStore`), commands off the HMI thread (`Commands`) |
| `java/com/mhi2/settings/mount/terminalmode/` | screens, rows, state machine and note popups in the Audi smartphone interface |
| `java/de/audi/tghu/terminalmode/{hmi,sm}/Activator.java` | stock-named entry points; each falls back to stock on any error |
| `i18n/` | texts, one JSON file per language |

## Adding a language

Copy `i18n/en.json` to `i18n/<code>.json`, where `<code>` is the language's ISO 639-1 code
(`de`, `fr`, `cs`, …), and translate the texts. Keys you leave out are shown in English.
Keep `{placeholders}` as they are, save as UTF-8, and keep row labels short – a menu row
shows about 40 characters. The build refuses a file with a key that is not in `en.json`,
different placeholders, or text that is not UTF-8, and names the file and key. The menu
follows the HMI's language; a language without a file is shown in English.
