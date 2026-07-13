import Charts
import SwiftUI

private enum ScoreBucket: String, CaseIterable, Identifiable {
    case newBest = "B15"
    case oldBest = "B35"
    case all = "全部"

    var id: String { rawValue }
}

struct ScoresView: View {
    @EnvironmentObject private var model: AppModel
    @State private var bucket: ScoreBucket = .newBest

    private var summary: RatingSummary { model.ratingSummary }

    private var visibleScores: [ScoreEntry] {
        switch bucket {
        case .newBest: summary.newBest
        case .oldBest: summary.oldBest
        case .all:
            model.userData.scores.sorted { $0.playedAt > $1.playedAt }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                summaryGrid
                trendCard

                Picker("成绩范围", selection: $bucket) {
                    ForEach(ScoreBucket.allCases) { value in
                        Text(value.rawValue).tag(value)
                    }
                }
                .pickerStyle(.segmented)

                if visibleScores.isEmpty {
                    ContentUnavailableView(
                        "暂无成绩",
                        systemImage: "chart.bar",
                        description: Text("在曲库详情中选择谱面并录入成绩。")
                    )
                    .frame(maxWidth: .infinity, minHeight: 260)
                } else {
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 300, maximum: 460), spacing: 14)],
                        spacing: 14
                    ) {
                        ForEach(visibleScores) { score in
                            ScoreCard(score: score)
                        }
                    }
                }
            }
            .frame(maxWidth: 1_200)
            .padding()
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("成绩")
    }

    private var summaryGrid: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 150, maximum: 260), spacing: 12)],
            spacing: 12
        ) {
            SummaryCard(title: "总 Rating", value: String(summary.totalRating), systemImage: "sparkles")
            SummaryCard(title: "B15", value: "\(summary.newBest.count) / 15", systemImage: "bolt.fill")
            SummaryCard(title: "B35", value: "\(summary.oldBest.count) / 35", systemImage: "clock.fill")
            SummaryCard(
                title: "候补 / 不计入",
                value: "\(summary.outsideBestCount) / \(summary.ineligibleCount)",
                systemImage: "tray.full.fill"
            )
        }
    }

    @ViewBuilder
    private var trendCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Rating 趋势")
                .font(.headline)
            if model.userData.ratingHistory.isEmpty {
                Text("保存第一条成绩后开始记录本地趋势。")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 120, alignment: .center)
            } else {
                Chart(model.userData.ratingHistory) { point in
                    LineMark(
                        x: .value("时间", point.recordedAt),
                        y: .value("Rating", point.rating)
                    )
                    .interpolationMethod(.monotone)
                    AreaMark(
                        x: .value("时间", point.recordedAt),
                        y: .value("Rating", point.rating)
                    )
                    .foregroundStyle(.cyan.opacity(0.12))
                }
                .chartYAxis { AxisMarks(position: .leading) }
                .frame(height: 210)
            }
        }
        .padding(16)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct SummaryCard: View {
    let title: String
    let value: String
    let systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: systemImage)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title2.monospacedDigit().bold())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct ScoreCard: View {
    let score: ScoreEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(score.title)
                        .font(.headline)
                        .lineLimit(2)
                    Text("\(score.chartType.uppercased()) · \(score.difficultyLabel) · \(score.level)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Text("+\(score.rating)")
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(.cyan)
            }
            Text("\(score.achievement, format: .number.precision(.fractionLength(4)))%")
                .font(.title3.monospacedDigit().weight(.semibold))
            Text(score.playedAt, format: .dateTime.year().month().day().hour().minute())
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
