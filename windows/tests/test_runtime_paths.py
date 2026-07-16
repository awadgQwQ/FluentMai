from pathlib import Path

import fetch_profile
import sync_data
from fluentmai_core import database


def test_all_database_entry_points_honor_explicit_runtime_path(monkeypatch, tmp_path):
    expected = tmp_path / "fluentmai-test.db"
    monkeypatch.setenv("FLUENTMAI_DB_PATH", str(expected))

    assert Path(fetch_profile.default_db_path()) == expected
    assert Path(sync_data.default_db_path()) == expected
    assert Path(database.default_db_path()) == expected


def test_default_database_paths_do_not_point_to_repository_root(monkeypatch):
    monkeypatch.delenv("FLUENTMAI_DB_PATH", raising=False)

    for path in (fetch_profile.default_db_path(), sync_data.default_db_path(), database.default_db_path()):
        assert Path(path).name == "maimai_data.db"
