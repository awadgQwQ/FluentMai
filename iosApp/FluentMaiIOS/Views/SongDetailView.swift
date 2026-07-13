import SwiftUI

struct SongDetailView: View {
    @EnvironmentObject private var model: AppModel
    let song: Song

    @State private var aliasDraft = ""
    @State private var selectedChart: SongChart?

    var body: some View {
        Form {
            Section("曲目信息") {
                LabeledContent("曲师", value: song.artist)
                LabeledContent("分类", value: song.genre)
                LabeledContent("曲目编号", value: String(song.id))
                LabeledContent("版本编号", value: String(song.version))
                if let bpm = song.bpm {
                    LabeledContent("BPM", value: String(bpm))
                }
            }

            Section {
                HStack {
                    TextField("添加便于搜索的本地别名", text: $aliasDraft)
                    Button("添加") {
                        model.addAlias(aliasDraft, for: song.id)
                        aliasDraft = ""
                    }
                    .disabled(aliasDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                ForEach(model.aliases(for: song.id), id: \.self) { alias in
                    HStack {
                        Label(alias, systemImage: "tag")
                        Spacer()
                        Button(role: .destructive) {
                            model.removeAlias(alias, for: song.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .buttonStyle(.borderless)
                    }
                }
            } header: {
                Text("自定义别名")
            } footer: {
                Text("别名只保存在此设备，不上传，也不包含登录信息。")
            }

            Section("谱面与本地成绩") {
                ForEach(song.allCharts) { chart in
                    ChartDetailRow(
                        chart: chart,
                        score: model.score(for: song.id, chart: chart),
                        onEdit: { selectedChart = chart }
                    )
                }
            }
        }
        .navigationTitle(song.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $selectedChart) { chart in
            ScoreEditorSheet(
                song: song,
                chart: chart,
                existingScore: model.score(for: song.id, chart: chart)
            )
            .environmentObject(model)
        }
    }
}

private struct ChartDetailRow: View {
    let chart: SongChart
    let score: ScoreEntry?
    let onEdit: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                DifficultyPill(chart: chart)
                Text(chart.difficultyLabel)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text("定数 \(chart.levelValue, format: .number.precision(.fractionLength(1)))")
                    .font(.subheadline.monospacedDigit())
            }
            if !chart.noteDesigner.isEmpty {
                Text("谱师：\(chart.noteDesigner)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let notes = chart.notes {
                Text("\(notes.total) 物量 · Tap \(notes.tap) · Hold \(notes.hold) · Slide \(notes.slide) · Touch \(notes.touch) · Break \(notes.breakCount)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            HStack {
                if let score {
                    Text("\(score.achievement, format: .number.precision(.fractionLength(4)))%")
                        .font(.body.monospacedDigit().weight(.semibold))
                    Text("Rating \(score.rating)")
                        .foregroundStyle(.cyan)
                } else {
                    Text("尚无本地成绩")
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(score == nil ? "录入" : "更新", action: onEdit)
                    .buttonStyle(.bordered)
            }
        }
        .padding(.vertical, 6)
    }
}

private struct ScoreEditorSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    let song: Song
    let chart: SongChart
    let existingScore: ScoreEntry?

    @State private var achievement: Double
    @State private var playedAt: Date

    init(song: Song, chart: SongChart, existingScore: ScoreEntry?) {
        self.song = song
        self.chart = chart
        self.existingScore = existingScore
        _achievement = State(initialValue: existingScore?.achievement ?? 100.0)
        _playedAt = State(initialValue: existingScore?.playedAt ?? Date())
    }

    private var inputIsValid: Bool {
        achievement.isFinite && (0.0...101.0).contains(achievement)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("谱面") {
                    LabeledContent("曲目", value: song.title)
                    LabeledContent("难度", value: "\(chart.typeLabel) · \(chart.difficultyLabel) · \(chart.level)")
                    LabeledContent("定数", value: chart.levelValue.formatted(.number.precision(.fractionLength(1))))
                }
                Section {
                    TextField(
                        "达成率",
                        value: $achievement,
                        format: .number.precision(.fractionLength(4))
                    )
                    .keyboardType(.decimalPad)
                    DatePicker("游玩时间", selection: $playedAt)
                } header: {
                    Text("成绩")
                } footer: {
                    Text("有效范围 0.0000%–101.0000%；同一谱面只保留最新录入。")
                }
                Section("即时计算") {
                    LabeledContent(
                        "单曲 Rating",
                        value: String(model.previewRating(levelValue: chart.levelValue, achievement: achievement))
                    )
                }
            }
            .navigationTitle(existingScore == nil ? "录入成绩" : "更新成绩")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        model.saveScore(song: song, chart: chart, achievement: achievement, playedAt: playedAt)
                        dismiss()
                    }
                    .disabled(!inputIsValid)
                }
            }
        }
    }
}
