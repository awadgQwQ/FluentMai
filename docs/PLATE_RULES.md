# FluentMai 牌子进度规则

FluentMai 的牌子进度使用曲库中的稳定谱面身份（歌曲 ID、SD/DX 类型、难度）与本地最佳成绩计算，不使用 Room 自增主键，也不根据图片或牌子名称猜测完成状态。

## 规则来源

- SEGA 官方公告（2020-01-15）：<https://maimai.sega.jp/news/2020-01-15/>
  - 极：指定版本分类全部歌曲的 BASIC～MASTER 达成 FULL COMBO。
  - 将：指定版本分类全部歌曲的 BASIC～MASTER 达成 SSS。
  - 神：指定版本分类全部歌曲的 BASIC～MASTER 达成 ALL PERFECT。
  - 舞舞：指定版本分类全部歌曲的 BASIC～MASTER 达成 FULL SYNC DX。
  - 覇者（界面使用中国玩家常用写法“霸者”）：当前收录全部 STANDARD 谱面的 BASIC～Re:MASTER 达成 CLEAR。
- SEGA 国际版玩法说明：<https://maimai.sega.com/play/howto/>
  - CLEAR 阈值为达成率 80% 及以上。

## 数据边界

- 版本牌按谱面的 `chartVersion` 归类；这能让后续追加的 SD/DX 谱面归入其实际追加版本，而不是歌曲最初收录版本。
- 版本牌排除 Re:MASTER；霸者包含 STANDARD 的 Re:MASTER，并排除 DX 谱面。
- 玩家未游玩或未取得所需 FC/FS 状态时会列为阻塞谱面，不会推断完成。
- 曲库缺少目标版本或没有可核验谱面时显示“数据不足”，不会宣布完成。
- 删除曲、地区差异和官方分类调整由当前有效曲库决定；FluentMai 不维护一份无法验证的隐藏例外表。
