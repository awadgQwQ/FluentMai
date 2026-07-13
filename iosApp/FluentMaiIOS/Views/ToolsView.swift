import FluentMaiShared
import SwiftUI

private struct AchievementPreview {
    let maximum: Double
    let lossPerJudgement: Double
    let result: Double
    let tolerated: Int
}

struct ToolsView: View {
    @EnvironmentObject private var model: AppModel

    @State private var levelValue = 14.0
    @State private var achievement = 100.5

    @State private var tap = 500
    @State private var hold = 50
    @State private var slide = 80
    @State private var touch = 40
    @State private var breakCount = 20
    @State private var noteKind = "BREAK"
    @State private var judgement = "GREAT"
    @State private var occurrences = 1
    @State private var targetAchievement = 100.0
    @State private var achievementPreview: AchievementPreview?

    private let domain = IosDomainBridge()

    private let noteKinds = ["TAP", "HOLD", "SLIDE", "TOUCH", "BREAK"]
    private let judgements = ["CRITICAL_PERFECT", "PERFECT_HIGH", "PERFECT", "GREAT", "GOOD", "MISS"]

    private var selectedNoteCount: Int {
        switch noteKind {
        case "TAP": tap
        case "HOLD": hold
        case "SLIDE": slide
        case "TOUCH": touch
        default: breakCount
        }
    }

    private var achievementInputIsValid: Bool {
        [tap, hold, slide, touch, breakCount].allSatisfy { $0 >= 0 }
            && tap + hold + slide + touch + breakCount > 0
            && occurrences >= 0
            && occurrences <= selectedNoteCount
            && targetAchievement >= 0
            && targetAchievement <= (breakCount > 0 ? 101.0 : 100.0)
    }

    var body: some View {
        Form {
            Section {
                TextField("谱面定数", value: $levelValue, format: .number.precision(.fractionLength(1)))
                    .keyboardType(.decimalPad)
                TextField("达成率", value: $achievement, format: .number.precision(.fractionLength(4)))
                    .keyboardType(.decimalPad)
                LabeledContent(
                    "单曲 Rating",
                    value: String(model.previewRating(levelValue: levelValue, achievement: achievement))
                )
                LabeledContent(
                    "系数",
                    value: domain.calculateCoefficient(achievement: achievement)
                        .formatted(.number.precision(.fractionLength(1)))
                )
            } header: {
                Label("单曲 Rating 计算器", systemImage: "function")
            } footer: {
                Text("达成率按 100.5% 封顶，计算逻辑与 Android 共用。")
            }

            Section {
                Stepper("Tap：\(tap)", value: $tap, in: 0...3_000)
                Stepper("Hold：\(hold)", value: $hold, in: 0...1_000)
                Stepper("Slide：\(slide)", value: $slide, in: 0...1_000)
                Stepper("Touch：\(touch)", value: $touch, in: 0...1_000)
                Stepper("Break：\(breakCount)", value: $breakCount, in: 0...1_000)

                Picker("音符类型", selection: $noteKind) {
                    ForEach(noteKinds, id: \.self) { Text($0.replacingOccurrences(of: "_", with: " ")).tag($0) }
                }
                Picker("判定", selection: $judgement) {
                    ForEach(judgements, id: \.self) { Text($0.replacingOccurrences(of: "_", with: " ")).tag($0) }
                }
                Stepper("出现次数：\(occurrences)", value: $occurrences, in: 0...max(selectedNoteCount, 0))
                TextField(
                    "目标达成率",
                    value: $targetAchievement,
                    format: .number.precision(.fractionLength(4))
                )
                .keyboardType(.decimalPad)

                Button("计算判定损失") {
                    let result = domain.calculateAchievement(
                        tap: Int32(tap),
                        hold: Int32(hold),
                        slide: Int32(slide),
                        touch: Int32(touch),
                        breakCount: Int32(breakCount),
                        noteKind: noteKind,
                        judgement: judgement,
                        occurrences: Int32(occurrences),
                        targetAchievement: targetAchievement
                    )
                    achievementPreview = AchievementPreview(
                        maximum: result.maximumAchievement,
                        lossPerJudgement: result.lossPerJudgement,
                        result: result.resultingAchievement,
                        tolerated: Int(result.toleratedOccurrences)
                    )
                }
                .disabled(!achievementInputIsValid)

                if let preview = achievementPreview {
                    LabeledContent("理论上限", value: preview.maximum.formatted(.number.precision(.fractionLength(4))) + "%")
                    LabeledContent("单次损失", value: preview.lossPerJudgement.formatted(.number.precision(.fractionLength(6))) + "%")
                    LabeledContent("结果", value: preview.result.formatted(.number.precision(.fractionLength(4))) + "%")
                    LabeledContent("目标可容忍次数", value: String(preview.tolerated))
                }
            } header: {
                Label("判定损失计算器", systemImage: "sum")
            } footer: {
                Text("物量与判定只在内存中计算，不写入成绩。")
            }
        }
        .navigationTitle("工具")
    }
}
