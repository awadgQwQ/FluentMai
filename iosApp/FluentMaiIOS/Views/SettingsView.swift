import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var versionDraft = 24_006

    var body: some View {
        Form {
            Section {
                TextField("当前大版本编号", value: $versionDraft, format: .number)
                    .keyboardType(.numberPad)
                Button("应用版本") {
                    model.setCurrentVersion(versionDraft)
                }
                .disabled(versionDraft <= 0 || versionDraft == model.userData.currentVersionId)
            } header: {
                Text("Rating 版本")
            } footer: {
                Text("版本编号决定 B15（当前版本）与 B35（旧版本）分桶。公开曲库当前最高编号可作为参考。")
            }

            Section("本地数据") {
                LabeledContent("曲库", value: catalogDescription)
                LabeledContent("成绩", value: "\(model.userData.scores.count) 条")
                LabeledContent(
                    "自定义别名",
                    value: "\(model.userData.aliases.values.reduce(0) { $0 + $1.count }) 个"
                )
                LabeledContent("趋势点", value: "\(model.userData.ratingHistory.count) 个")
                LabeledContent("数据结构", value: "v\(model.userData.schemaVersion)")
                if let persistenceError = model.persistenceError {
                    Label(persistenceError, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                }
            }

            Section {
                Label("公开曲库随应用离线打包", systemImage: "checkmark.shield.fill")
                Label("成绩、趋势和别名只保存在本机", systemImage: "iphone.gen3")
                Label("MVP 不接收 Cookie、Token 或网页缓存", systemImage: "key.slash.fill")
                Label("不会访问 Android 应用数据库", systemImage: "externaldrive.badge.xmark")
            } header: {
                Text("隐私边界")
            } footer: {
                Text("如需协助排错，只发送崩溃日志、页面截图和操作步骤；请勿发送登录链接、Cookie 或 Token。")
            }

            Section("关于") {
                LabeledContent("产品", value: "FluentMai iOS MVP")
                LabeledContent("共享领域层", value: "Kotlin Multiplatform")
                LabeledContent("界面", value: "SwiftUI · iPhone / iPad")
            }
        }
        .navigationTitle("设置")
        .onAppear {
            versionDraft = model.userData.currentVersionId
        }
    }

    private var catalogDescription: String {
        switch model.catalogState {
        case .loading: "读取中"
        case .ready(let count): "\(count) 首"
        case .failed: "不可用"
        }
    }
}
