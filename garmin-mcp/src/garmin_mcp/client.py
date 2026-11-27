"""Garmin Connect client: login from saved tokens, or from credentials as a fallback."""

from __future__ import annotations

import os
import threading
from pathlib import Path

from garminconnect import Garmin, GarminConnectAuthenticationError
from mcp.server.mcpserver.exceptions import ToolError

DEFAULT_TOKENSTORE = "~/.garminconnect"

_lock = threading.Lock()
_client: Garmin | None = None


def tokenstore_path() -> str:
    return str(Path(os.getenv("GARMINTOKENS", DEFAULT_TOKENSTORE)).expanduser())


def get_client() -> Garmin:
    """Return a logged-in Garmin client, creating it on first use."""
    global _client
    with _lock:
        if _client is not None:
            return _client

        store = tokenstore_path()
        email = os.getenv("GARMIN_EMAIL")
        password = os.getenv("GARMIN_PASSWORD")

        garmin = Garmin(email=email, password=password)
        try:
            garmin.login(store)
        except GarminConnectAuthenticationError as e:
            raise ToolError(
                "Přihlášení do Garmin Connect selhalo. Spusť jednou "
                "`garmin-mcp-login` (uloží tokeny do "
                f"{store}) nebo nastav GARMIN_EMAIL a GARMIN_PASSWORD. "
                f"Detail: {e}"
            ) from e

        _client = garmin
        return _client


def reset_client() -> None:
    global _client
    with _lock:
        _client = None
