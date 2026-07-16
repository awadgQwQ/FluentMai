from __future__ import annotations

from typing import Any

import requests

from .database import iter_score_rows
from .models import normalize_song_type
from .privacy import redactor


DIVING_FISH_UPDATE_RECORDS_URL = "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
LXNS_UPDATE_RECORDS_URL = "https://maimai.lxns.net/api/v0/user/maimai/player/scores"
HTTP_TIMEOUT = 25


class UploadError(RuntimeError):
    pass


def score_rows_to_diving_fish(rows) -> list[dict[str, Any]]:
    payload: list[dict[str, Any]] = []
    for row in rows:
        payload.append(
            {
                "achievements": row["achievements"],
                "dxScore": row["dx_score"] or 0,
                "fc": row["full_combo"] or "",
                "fs": row["full_sync"] or "",
                "level_index": row["difficulty_index"],
                "title": _map_diving_fish_title(row["title"]),
                "type": normalize_song_type(row["chart_type"]),
            }
        )
    return payload


def score_rows_to_lxns(rows) -> list[dict[str, Any]]:
    payload: list[dict[str, Any]] = []
    for row in rows:
        if row["song_id"] is None or row["dx_score"] is None:
            continue
        payload.append(
            {
                "id": row["song_id"],
                "type": "dx" if normalize_song_type(row["chart_type"]) == "DX" else "standard",
                "level_index": row["difficulty_index"],
                "achievements": row["achievements"],
                "fc": row["full_combo"] or None,
                "fs": row["full_sync"] or None,
                "dx_score": row["dx_score"],
                "play_time": row["play_time"],
            }
        )
    return payload


def upload_diving_fish(import_token: str, rows, session: requests.Session | None = None) -> dict[str, Any]:
    token = import_token.strip()
    if not token:
        raise UploadError("Diving-Fish Import Token is required.")
    payload = score_rows_to_diving_fish(rows)
    response = (session or requests.Session()).post(
        DIVING_FISH_UPDATE_RECORDS_URL,
        json=payload,
        timeout=HTTP_TIMEOUT,
        headers={"Import-Token": token, "Content-Type": "application/json"},
    )
    if response.status_code not in range(200, 300):
        raise UploadError(_http_error("Diving-Fish upload", response))
    return {"count": len(payload), "response": _safe_json(response)}


def upload_lxns(user_token: str, rows, session: requests.Session | None = None) -> dict[str, Any]:
    token = user_token.strip()
    if not token:
        raise UploadError("LXNS user token is required.")
    scores = score_rows_to_lxns(rows)
    response = (session or requests.Session()).post(
        LXNS_UPDATE_RECORDS_URL,
        json={"scores": scores},
        timeout=HTTP_TIMEOUT,
        headers={
            "X-User-Token": token,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    if response.status_code not in range(200, 300):
        raise UploadError(_http_error("LXNS upload", response))
    body = _safe_json(response)
    if isinstance(body, dict) and body.get("success") is False:
        raise UploadError(redactor.redact(body.get("message") or "LXNS returned success=false"))
    return {"count": len(scores), "response": body}


def local_rows_for_upload(conn):
    return [row for row in iter_score_rows(conn) if row["title"]]


def _safe_json(response: requests.Response) -> Any:
    try:
        return response.json()
    except Exception:
        return redactor.redact(response.text)


def _http_error(label: str, response: requests.Response) -> str:
    return redactor.redact(f"{label}: HTTP {response.status_code}: {response.text[:240]}")


def _map_diving_fish_title(title: str) -> str:
    return {
        "Bad Apple!! feat.nomico": "Bad Apple!! feat nomico",
        "Help me, ERINNNNNN!!（Band ver.）": "Help me, ERINNNNNN!!",
    }.get(title, title)
