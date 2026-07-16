from fluentmai_core import database
from fluentmai_core.import_pipeline import import_parsed_records
from fluentmai_core.models import Chart, ParsedScoreRecord, Song


def _seed_catalog(db_path):
    conn = database.connect(db_path)
    database.upsert_catalog(
        conn,
        [Song(song_id=8, title="True Love Song", artist="Kai", provider="test")],
        [Chart(song_id=8, chart_type="SD", difficulty_index=3, level="12", level_value=12.4)],
    )
    conn.close()


def test_import_inserts_and_deduplicates(tmp_path):
    db_path = str(tmp_path / "scores.db")
    _seed_catalog(db_path)
    records = [
        ParsedScoreRecord(
            title="True Love Song",
            song_type="standard",
            difficulty_index=3,
            level="12",
            achievements=100.0,
            dx_score=3000,
        ),
        ParsedScoreRecord(
            title="True Love Song",
            song_type="SD",
            difficulty_index=3,
            level="12",
            achievements=100.0,
            dx_score=3000,
        ),
    ]

    summary = import_parsed_records(records, source="fixture", db_path=db_path)

    assert summary.inserted == 1
    assert summary.skipped_duplicate == 1
    conn = database.connect(db_path)
    assert len(database.list_scores(conn)) == 1
    conn.close()


def test_import_updates_higher_score_and_skips_lower(tmp_path):
    db_path = str(tmp_path / "scores.db")
    _seed_catalog(db_path)
    low = ParsedScoreRecord(
        title="True Love Song",
        song_type="SD",
        difficulty_index=3,
        level="12",
        achievements=99.0,
        dx_score=2500,
    )
    high = ParsedScoreRecord(
        title="True Love Song",
        song_type="SD",
        difficulty_index=3,
        level="12",
        achievements=100.5,
        dx_score=3100,
        full_combo="fc",
    )

    assert import_parsed_records([low], source="fixture", db_path=db_path).inserted == 1
    assert import_parsed_records([high], source="fixture", db_path=db_path).updated == 1
    assert import_parsed_records([low], source="fixture", db_path=db_path).skipped_duplicate == 1

    conn = database.connect(db_path)
    row = database.list_scores(conn)[0]
    assert row["achievements"] == 100.5
    assert row["full_combo"] == "fc"
    conn.close()


def test_invalid_records_go_to_quarantine(tmp_path):
    db_path = str(tmp_path / "scores.db")
    records = [
        ParsedScoreRecord(title="", difficulty_index=3, achievements=100),
        ParsedScoreRecord(title="Bad", difficulty_index=9, achievements=100),
        ParsedScoreRecord(title="Bad", difficulty_index=3, achievements=101.5),
    ]

    summary = import_parsed_records(records, source="fixture", db_path=db_path)

    assert summary.inserted == 0
    assert summary.quarantined == 3
    conn = database.connect(db_path)
    reasons = {row["reason"] for row in database.recent_quarantine(conn)}
    assert "blank_title" in reasons
    assert "invalid_level_index" in reasons
    assert "invalid_achievement" in reasons
    conn.close()
