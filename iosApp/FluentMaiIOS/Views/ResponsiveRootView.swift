import SwiftUI

enum AppSection: String, CaseIterable, Identifiable {
    case catalog
    case scores
    case tools
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .catalog: "曲库"
        case .scores: "成绩"
        case .tools: "工具"
        case .settings: "设置"
        }
    }

    var systemImage: String {
        switch self {
        case .catalog: "music.note.list"
        case .scores: "chart.bar.fill"
        case .tools: "function"
        case .settings: "gearshape.fill"
        }
    }
}

struct ResponsiveRootView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedSection: AppSection? = .catalog

    var body: some View {
        if horizontalSizeClass == .regular {
            NavigationSplitView {
                List(AppSection.allCases, selection: $selectedSection) { section in
                    Label(section.title, systemImage: section.systemImage)
                        .tag(section)
                }
                .navigationTitle("FluentMai")
            } detail: {
                NavigationStack {
                    sectionView(selectedSection ?? .catalog)
                }
            }
        } else {
            TabView(selection: $selectedSection) {
                ForEach(AppSection.allCases) { section in
                    NavigationStack {
                        sectionView(section)
                    }
                    .tabItem { Label(section.title, systemImage: section.systemImage) }
                    .tag(Optional(section))
                }
            }
        }
    }

    @ViewBuilder
    private func sectionView(_ section: AppSection) -> some View {
        switch section {
        case .catalog: CatalogView()
        case .scores: ScoresView()
        case .tools: ToolsView()
        case .settings: SettingsView()
        }
    }
}
