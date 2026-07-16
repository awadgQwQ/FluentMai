from fluentmai_core import database
from fluentmai_core.models import Chart, Song
from fluentmai_core.wahlap import WahlapHtmlParser, normalize_auth_url, parse_cookie_string, parse_reqable_dump


WAHLAP_HTML = """
<html><body>
<form action="https://maimai.wahlap.com/maimai-mobile/record/musicDetail/" method="GET">
  <input type="hidden" name="diff" value="3">
  <div class="music_name_block">Test Song</div>
  <div class="music_lv_block">13+</div>
  <div class="music_score_block w_112 t_r f_l f_12">100.5000%</div>
  <div class="music_score_block w_190 t_r f_l f_12">1,234</div>
  <div class="music_kind_icon"><img src="images/music_dx.png"></div>
  <img src="images/music_icon_fc.png" class="h_30 f_r">
  <img src="images/music_icon_fsd.png" class="h_30 f_r">
</form>
</body></html>
"""


def test_wahlap_parser_extracts_score_and_resolves_chart(tmp_path):
    db_path = str(tmp_path / "test.db")
    conn = database.connect(db_path)
    database.upsert_catalog(
        conn,
        [Song(song_id=123, title="Test Song", provider="test")],
        [Chart(song_id=123, chart_type="DX", difficulty_index=3, level="13+", level_value=13.8)],
    )
    parser = WahlapHtmlParser(conn)

    records = parser.parse(WAHLAP_HTML)

    assert len(records) == 1
    record = records[0]
    assert record.title == "Test Song"
    assert record.song_id == 123
    assert record.song_type == "DX"
    assert record.difficulty_index == 3
    assert record.achievements == 100.5
    assert record.dx_score == 1234
    assert record.full_combo == "fc"
    assert record.full_sync == "fsd"
    conn.close()


def test_normalize_auth_url_upgrades_wahlap_callback():
    url = "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=secret"
    assert normalize_auth_url(url).startswith("https://tgk-wcaime.wahlap.com/")


def test_reqable_dump_cookie_parsing():
    dump = """
:method: GET
:authority: maimai.wahlap.com
user-agent: WeChat
cookie: _t=abc; userId=xyz; friendCodeList=1
accept-language: zh-CN
"""
    cookies, headers = parse_reqable_dump(dump)
    assert cookies["_t"] == "abc"
    assert cookies["userId"] == "xyz"
    assert headers["User-Agent"] == "WeChat"


def test_plain_cookie_parsing():
    cookies = parse_cookie_string("_t=abc; userId=xyz")
    assert cookies == {"_t": "abc", "userId": "xyz"}
