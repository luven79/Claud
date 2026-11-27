"""One-time interactive login: asks for email, password and MFA code, saves tokens."""

from __future__ import annotations

import getpass
import os
import sys
from pathlib import Path

from garminconnect import Garmin

from .client import tokenstore_path


def main() -> None:
    store = tokenstore_path()
    email = os.getenv("GARMIN_EMAIL") or input("Garmin e-mail: ").strip()
    password = os.getenv("GARMIN_PASSWORD") or getpass.getpass("Garmin heslo: ")

    garmin = Garmin(
        email=email,
        password=password,
        prompt_mfa=lambda: input("MFA kód (z e-mailu / aplikace): ").strip(),
    )
    Path(store).mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        garmin.login(store)
        garmin.client.dump(store)
    except Exception as e:  # noqa: BLE001 - show any failure to the user
        print(f"Přihlášení selhalo: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Přihlášen jako {garmin.get_full_name()}. Tokeny uloženy do {store}.")
    print("Server je teď použije automaticky – heslo už nepotřebuje.")


if __name__ == "__main__":
    main()
