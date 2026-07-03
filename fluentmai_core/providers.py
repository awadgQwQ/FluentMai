from __future__ import annotations

from typing import Any

import requests

from .models import ParsedScoreRecord, as_float, as_int, normalize_song_type, sha256_text
from .privacy import redactor


HTTP_TIMEOUT = 25
DIVING_FISH_RECORDS_URL = "https://www.diving-fish.com/api/maimaidxprober/player/records"
DIVING_FISH_UPDATE_URL = "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
LXNS_PLAYER_URL = "https://maimai.lxns.net/api/v0/user/maimai/player"
LXNS_SCORES_URL = "https://maimai.lxns.net/api/v0/user/maimai/player/scores"


class ProviderError(RuntimeError):
    pass


class DivingFishProvider:
    name = "diving-fish"

    def __init__(self, session: requests.Session | None = None):
        self.session = session or requests.Session()

    def validate_import_token(self, import_token: str) -> bool:
        token = import_token.strip()
        if not token:
            return False
        response = self.session.post(
            DIVING_FISH_UPDATE_URL,
            json=[],
            timeout=HTTP_TIMEOUT,
            headers={"Import-Token": token, "Content-Type": "application/json"},
        )
        return response.status_code in range(200, 300)

    def fetch_records(self, import_token: str) -> list[dict[str, Any]]:
        token = import_token.strip()
        if not token:
            raise ProviderError("Diving-Fish Import Token is required.")
        response = self.session.get(
            DIVING_FISH_RECORDS_URL,
            timeout=HTTP_TIMEOUT,
            headers={"Import-Token": token, "Accept": "application/json"},
        )
        if response.status_code not in range(200, 300):
            raise ProviderError(_http_error("Diving-Fish records", response))
        payload = response.json()
        records = payload.get("records") if isinstance(payload, dict) else payload
        if not isinstance(records, list):
            raise ProviderError("Diving-Fish records response did not contain a records list.")
        return records

    def fetch_parsed_scores(self, import_token: str) -> list[ParsedScoreRecord]:
        return diving_fish_records_to_parsed(self.fetch_records(import_token))


class LxnsProvider:
    name = "lxns"

    def __init__(self, session: requests.Session | None = None):
        self.session = session or requests.Session()

    def fetch_player(self, user_token: str) -> dict[str, Any]:
        return self._get_json(LXNS_PLAYER_URL, user_token, "LXNS player")

    def fetch_records(self, user_token: str) -> list[dict[str, Any]]:
        payload = self._get_json(LXNS_SCORES_URL, user_token, "LXNS scores")
        data = payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
        if not isinstance(data, list):
            raise ProviderError("LXNS scores response did not contain a score list.")
        return data

    def fetch_parsed_scores(self, user_token: str) -> list[ParsedScoreRecord]:
        return lxns_records_to_parsed(self.fetch_records(user_token))

    def _get_json(self, url: str, user_token: str, label: str) -> Any:
        token = user_token.strip()
        if not token:
            raise ProviderError("LXNS user token is required.")
        response = self.session.get(
            url,
            timeout=HTTP_TIMEOUT,
            headers={
                "X-User-Token": token,
                "Accept": "application/json",
                "User-Agent": "FluentMai Windows",
            },
        )
        if response.status_code not in range(200, 300):
            raise ProviderError(_http_error(label, response))
        payload = response.json()
        if isinstance(payload, dict) and payload.get("success") is False:
            raise ProviderError(str(payload.get("message") or f"{label} returned success=false"))
        return payload


def diving_fish_records_to_parsed(records: list[dict[str, Any]]) -> list[ParsedScoreRecord]:
    parsed: list[ParsedScoreRecord] = []
    for index, item in enumerate(records):
        title = item.get("title") or item.get("song_name")
        level_index = item.get("level_index", item.get("levelIndex"))
        dx_score = item.get("dxScore", item.get("dx_score"))
        song_id = item.get("song_id", item.get("songId"))
        parsed.append(
            ParsedScoreRecord(
                title=str(title).strip() if title is not None else None,
                song_id=as_int(song_id),
                song_type=normalize_song_type(item.get("type") or item.get("songType")),
                difficulty_index=as_int(level_index),
                level=str(item.get("level") or item.get("level_label") or ""),
                achievements=as_float(item.get("achievements")),
                dx_score=as_int(dx_score),
                rank=str(item.get("rate") or item.get("rank") or ""),
                full_combo=item.get("fc"),
                full_sync=item.get("fs"),
                play_time=item.get("play_time") or item.get("playTime"),
                source_record_id=str(item.get("id") or item.get("_id") or index),
                raw_fingerprint=sha256_text(str(sorted(item.items()))),
            )
        )
    return parsed


def lxns_records_to_parsed(records: list[dict[str, Any]]) -> list[ParsedScoreRecord]:
    parsed: list[ParsedScoreRecord] = []
    for index, item in enumerate(records):
        title = item.get("song_name") or item.get("title")
        parsed.append(
            ParsedScoreRecord(
                title=str(title).strip() if title is not None else None,
                song_id=as_int(item.get("id") or item.get("song_id")),
                song_type=normalize_song_type(item.get("type")),
                difficulty_index=as_int(item.get("level_index")),
                level=str(item.get("level") or ""),
                achievements=as_float(item.get("achievements")),
                dx_score=as_int(item.get("dx_score") or item.get("dxScore")),
                rank=str(item.get("rate") or ""),
                full_combo=item.get("fc"),
                full_sync=item.get("fs"),
                play_time=item.get("play_time") or item.get("last_played_time"),
                source_record_id=str(item.get("id") or index),
                raw_fingerprint=sha256_text(str(sorted(item.items()))),
            )
        )
    return parsed


def _http_error(label: str, response: requests.Response) -> str:
    body = ""
    try:
        payload = response.json()
        body = payload.get("message") or payload.get("error") or str(payload)
    except Exception:
        body = response.text[:240]
    return redactor.redact(f"{label}: HTTP {response.status_code}: {body}")
