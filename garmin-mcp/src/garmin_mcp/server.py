"""MCP server exposing Garmin Connect data (read-only) to Claude and other MCP clients."""

from __future__ import annotations

import base64
from datetime import date, timedelta
from typing import Any, Callable

from garminconnect import (
    GarminConnectAuthenticationError,
    GarminConnectConnectionError,
    GarminConnectNotFoundError,
    GarminConnectTooManyRequestsError,
)
from mcp.server.mcpserver import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations

from .client import get_client, reset_client

mcp = MCPServer(
    name="garmin-connect",
    instructions=(
        "Read-only přístup k datům uživatele z Garmin Connect: aktivity, spánek, "
        "tep, stres, Body Battery, HRV, tréninková připravenost a další. "
        "Data jsou dle data ve formátu YYYY-MM-DD; bez data se použije dnešek."
    ),
)

READ_ONLY = ToolAnnotations(readOnlyHint=True, openWorldHint=True)

# Fields kept when listing activities, so lists stay small.
ACTIVITY_FIELDS = (
    "activityId", "activityName", "startTimeLocal", "activityType", "distance",
    "duration", "movingDuration", "elevationGain", "averageSpeed", "averageHR",
    "maxHR", "calories", "averageRunningCadenceInStepsPerMinute", "avgPower",
    "aerobicTrainingEffect", "anaerobicTrainingEffect", "trainingEffectLabel",
    "activityTrainingLoad", "vO2MaxValue", "steps", "locationName",
)


def _day(d: str | None) -> str:
    if not d:
        return date.today().isoformat()
    try:
        return date.fromisoformat(d).isoformat()
    except ValueError:
        raise ToolError(f"Neplatné datum {d!r}, očekávám YYYY-MM-DD.") from None


def _call(fn: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
    """Call a Garmin API method; re-login once if the session expired."""
    for attempt in range(2):
        try:
            return fn(get_client(), *args, **kwargs)
        except GarminConnectAuthenticationError as e:
            reset_client()
            if attempt:
                raise ToolError(f"Garmin odmítl přihlášení: {e}") from e
        except GarminConnectTooManyRequestsError as e:
            raise ToolError(f"Garmin omezil počet požadavků, zkus to později: {e}") from e
        except GarminConnectNotFoundError as e:
            raise ToolError(f"Nenalezeno: {e}") from e
        except GarminConnectConnectionError as e:
            raise ToolError(f"Chyba spojení s Garmin Connect: {e}") from e


def _slim_activity(a: dict[str, Any]) -> dict[str, Any]:
    out = {k: a[k] for k in ACTIVITY_FIELDS if a.get(k) is not None}
    if isinstance(out.get("activityType"), dict):
        out["activityType"] = out["activityType"].get("typeKey")
    return out


# --- Profil a zařízení ---------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def get_profile() -> dict[str, Any]:
    """Jméno uživatele, jednotky a seznam spárovaných Garmin zařízení."""
    return {
        "full_name": _call(lambda g: g.get_full_name()),
        "unit_system": _call(lambda g: g.get_unit_system()),
        "devices": [
            {k: d.get(k) for k in ("productDisplayName", "deviceId", "currentFirmwareVersion")}
            for d in _call(lambda g: g.get_devices()) or []
        ],
    }


# --- Denní souhrny -----------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def get_daily_summary(day: str | None = None) -> dict[str, Any]:
    """Denní souhrn: kroky, kalorie, vzdálenost, klidový tep, stres, Body Battery, intenzivní minuty."""
    return _call(lambda g: g.get_user_summary(_day(day)))


@mcp.tool(annotations=READ_ONLY)
def get_steps_range(start_date: str, end_date: str | None = None) -> list[dict[str, Any]]:
    """Počet kroků po dnech v zadaném rozsahu (YYYY-MM-DD)."""
    return _call(lambda g: g.get_daily_steps(_day(start_date), _day(end_date)))


@mcp.tool(annotations=READ_ONLY)
def get_heart_rate(day: str | None = None) -> dict[str, Any]:
    """Tep za den: klidový, min/max a průběh (pole [timestamp, bpm])."""
    return _call(lambda g: g.get_heart_rates(_day(day)))


@mcp.tool(annotations=READ_ONLY)
def get_sleep(day: str | None = None) -> dict[str, Any]:
    """Spánek pro noc končící daným dnem: fáze, skóre spánku, délka, HRV, dýchání."""
    data = _call(lambda g: g.get_sleep_data(_day(day))) or {}
    # Drop large per-minute arrays; keep the summary.
    for key in ("sleepMovement", "sleepLevels", "sleepRestlessMoments",
                "sleepHeartRate", "sleepStress", "sleepBodyBattery",
                "hrvData", "wellnessEpochRespirationDataDTOList",
                "wellnessSpO2SleepSummaryDTO"):
        data.pop(key, None)
    return data


@mcp.tool(annotations=READ_ONLY)
def get_stress(day: str | None = None) -> dict[str, Any]:
    """Úroveň stresu za den (průměr, maximum, rozložení)."""
    data = _call(lambda g: g.get_stress_data(_day(day))) or {}
    data.pop("stressValuesArray", None)
    data.pop("bodyBatteryValuesArray", None)
    return data


@mcp.tool(annotations=READ_ONLY)
def get_body_battery(start_date: str | None = None, end_date: str | None = None) -> list[dict[str, Any]]:
    """Body Battery po dnech (nabito/vybito, nejvyšší a nejnižší hodnota)."""
    start = _day(start_date)
    return _call(lambda g: g.get_body_battery(start, _day(end_date or start)))


@mcp.tool(annotations=READ_ONLY)
def get_hrv(day: str | None = None) -> dict[str, Any] | None:
    """Variabilita srdečního tepu (HRV): noční průměr, týdenní průměr, baseline a status."""
    data = _call(lambda g: g.get_hrv_data(_day(day)))
    if data:
        data.pop("hrvReadings", None)
    return data


@mcp.tool(annotations=READ_ONLY)
def get_respiration_and_spo2(day: str | None = None) -> dict[str, Any]:
    """Dechová frekvence a saturace kyslíkem (SpO2) za den."""
    resp = _call(lambda g: g.get_respiration_data(_day(day))) or {}
    spo2 = _call(lambda g: g.get_spo2_data(_day(day))) or {}
    for d in (resp, spo2):
        for k in [k for k, v in d.items() if isinstance(v, list)]:
            d.pop(k)
    return {"respiration": resp, "spo2": spo2}


# --- Trénink -----------------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def get_training_readiness(day: str | None = None) -> list[dict[str, Any]]:
    """Tréninková připravenost (Training Readiness) a její faktory."""
    return _call(lambda g: g.get_training_readiness(_day(day)))


@mcp.tool(annotations=READ_ONLY)
def get_training_status(day: str | None = None) -> dict[str, Any]:
    """Tréninkový stav, tréninková zátěž (akutní/chronická) a VO2 max."""
    return _call(lambda g: g.get_training_status(_day(day)))


@mcp.tool(annotations=READ_ONLY)
def get_vo2max_and_fitness_age(day: str | None = None) -> dict[str, Any]:
    """VO2 max (běh/kolo) a fitness věk."""
    return {
        "max_metrics": _call(lambda g: g.get_max_metrics(_day(day))),
        "fitness_age": _call(lambda g: g.get_fitnessage_data(_day(day))),
    }


@mcp.tool(annotations=READ_ONLY)
def get_race_predictions() -> dict[str, Any]:
    """Předpovědi závodních časů (5 km, 10 km, půlmaraton, maraton)."""
    return _call(lambda g: g.get_race_predictions())


@mcp.tool(annotations=READ_ONLY)
def get_personal_records() -> Any:
    """Osobní rekordy (nejrychlejší 1 km, 5 km, nejdelší běh atd.)."""
    return _call(lambda g: g.get_personal_record())


@mcp.tool(annotations=READ_ONLY)
def get_endurance_score(start_date: str | None = None, end_date: str | None = None) -> dict[str, Any]:
    """Endurance score (vytrvalostní skóre); výchozí rozsah posledních 12 týdnů."""
    end = _day(end_date)
    start = start_date or (date.fromisoformat(end) - timedelta(weeks=12)).isoformat()
    return _call(lambda g: g.get_endurance_score(_day(start), end))


# --- Aktivity ----------------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def list_activities(limit: int = 20, start: int = 0, activity_type: str | None = None) -> list[dict[str, Any]]:
    """Poslední aktivity (nejnovější první). activity_type např. running, cycling, swimming, walking, hiking, strength_training."""
    limit = max(1, min(limit, 100))
    acts = _call(lambda g: g.get_activities(start, limit, activity_type)) or []
    return [_slim_activity(a) for a in acts]


@mcp.tool(annotations=READ_ONLY)
def list_activities_by_date(start_date: str, end_date: str | None = None, activity_type: str | None = None) -> list[dict[str, Any]]:
    """Aktivity v rozsahu dat (YYYY-MM-DD)."""
    acts = _call(lambda g: g.get_activities_by_date(_day(start_date), _day(end_date), activity_type)) or []
    return [_slim_activity(a) for a in acts]


@mcp.tool(annotations=READ_ONLY)
def get_activity(activity_id: int) -> dict[str, Any]:
    """Detail jedné aktivity: souhrn, tepové zóny a mezičasy (laps/splits)."""
    aid = str(activity_id)
    result: dict[str, Any] = {"summary": _call(lambda g: g.get_activity(aid))}
    try:
        result["splits"] = _call(lambda g: g.get_activity_splits(aid))
    except Exception as e:  # noqa: BLE001 - splits are optional
        result["splits_error"] = str(e)
    try:
        result["hr_zones"] = _call(lambda g: g.get_activity_hr_in_timezones(aid))
    except Exception as e:  # noqa: BLE001
        result["hr_zones_error"] = str(e)
    return result


@mcp.tool(annotations=READ_ONLY)
def download_activity_gpx(activity_id: int) -> str:
    """Stáhne trasu aktivity jako GPX (XML text)."""
    from garminconnect import Garmin

    raw = _call(lambda g: g.download_activity(str(activity_id), Garmin.ActivityDownloadFormat.GPX))
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return base64.b64encode(raw).decode()


# --- Tělo --------------------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def get_body_composition(start_date: str | None = None, end_date: str | None = None) -> dict[str, Any]:
    """Váha a složení těla (BMI, tuk, svaly) – výchozí posledních 30 dní."""
    end = _day(end_date)
    start = start_date or (date.fromisoformat(end) - timedelta(days=30)).isoformat()
    return _call(lambda g: g.get_body_composition(_day(start), end))


@mcp.tool(annotations=READ_ONLY)
def get_hydration(day: str | None = None) -> dict[str, Any]:
    """Příjem tekutin za den."""
    return _call(lambda g: g.get_hydration_data(_day(day)))


# --- Plán --------------------------------------------------------------------

@mcp.tool(annotations=READ_ONLY)
def list_workouts(limit: int = 30) -> list[dict[str, Any]]:
    """Uložené tréninky (workouts) v Garmin Connect."""
    return _call(lambda g: g.get_workouts(0, max(1, min(limit, 100))))


@mcp.tool(annotations=READ_ONLY)
def get_goals(status: str = "active") -> list[dict[str, Any]]:
    """Cíle uživatele. status: active, future nebo past."""
    return _call(lambda g: g.get_goals(status))


def main() -> None:
    mcp.run("stdio")


if __name__ == "__main__":
    main()
