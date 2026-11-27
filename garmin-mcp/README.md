# Garmin Connect MCP server

MCP server, přes který Claude (Claude Desktop, Claude Code a další MCP klienti) čte tvoje data z Garmin Connect:
aktivity, spánek, tep, stres, Body Battery, HRV, tréninkovou připravenost, VO2 max, váhu a další.

Server je **jen pro čtení** – nic v Garmin Connect nemění. Používá neoficiální knihovnu
[`garminconnect`](https://github.com/cyberjunky/python-garminconnect) (Garmin pro osobní účty nemá veřejné API),
takže se může občas rozbít, když Garmin změní přihlašování.

## Instalace

Potřebuješ Python 3.10+ a ideálně [uv](https://docs.astral.sh/uv/).

```bash
cd garmin-mcp
uv sync            # nebo: python -m venv .venv && .venv/bin/pip install -e .
```

## Přihlášení (jednou)

```bash
uv run garmin-mcp-login
```

Zeptá se na e-mail, heslo a případně MFA kód. Tokeny uloží do `~/.garminconnect`
(jiné místo nastavíš proměnnou `GARMINTOKENS`). Heslo se nikam neukládá; tokeny vydrží cca rok
a server si je sám obnovuje.

Alternativně můžeš serveru dát `GARMIN_EMAIL` a `GARMIN_PASSWORD` jako proměnné prostředí
(nefunguje s MFA).

## Připojení ke Claude

### Claude Code

```bash
claude mcp add garmin -- uv --directory /cesta/k/garmin-mcp run garmin-mcp
```

### Claude Desktop

Do `claude_desktop_config.json` (Settings → Developer → Edit Config):

```json
{
  "mcpServers": {
    "garmin": {
      "command": "uv",
      "args": ["--directory", "/cesta/k/garmin-mcp", "run", "garmin-mcp"]
    }
  }
}
```

Pak Claude restartuj a zeptej se třeba: *„Jak jsem spal tento týden a jsem připravený na dnešní interval?“*

## Nástroje

| Nástroj | Co vrací |
|---|---|
| `get_profile` | jméno, jednotky, zařízení |
| `get_daily_summary` | kroky, kalorie, klidový tep, stres, Body Battery za den |
| `get_steps_range` | kroky po dnech v rozsahu |
| `get_heart_rate` | tep za den |
| `get_sleep` | spánek a skóre spánku |
| `get_stress` | stres za den |
| `get_body_battery` | Body Battery po dnech |
| `get_hrv` | HRV a jeho status |
| `get_respiration_and_spo2` | dýchání a SpO2 |
| `get_training_readiness` | tréninková připravenost |
| `get_training_status` | tréninkový stav a zátěž |
| `get_vo2max_and_fitness_age` | VO2 max a fitness věk |
| `get_race_predictions` | předpovědi závodních časů |
| `get_personal_records` | osobní rekordy |
| `get_endurance_score` | vytrvalostní skóre |
| `list_activities` | poslední aktivity (lze filtrovat typem) |
| `list_activities_by_date` | aktivity v rozsahu dat |
| `get_activity` | detail aktivity, mezičasy, tepové zóny |
| `download_activity_gpx` | GPX trasa aktivity |
| `get_body_composition` | váha a složení těla |
| `get_hydration` | pitný režim |
| `list_workouts` | uložené tréninky |
| `get_goals` | cíle |

Data se zadávají jako `YYYY-MM-DD`; když datum vynecháš, použije se dnešek.
