import SwiftUI

struct CatalogView: View {
    @EnvironmentObject private var model: AppModel
    @State private var query = ""
    @State private var selectedGenre = "全部"
    @State private var chartMode: CatalogChartMode = .all

    private var filteredSongs: [Song] {
        model.filteredSongs(
            query: query,
            genre: selectedGenre == "全部" ? nil : selectedGenre,
            chartMode: chartMode
        )
    }

    var body: some View {
        Group {
            switch model.catalogState {
            case .loading:
                ProgressView("正在读取本地公开曲库…")
            case .failed(let message):
                ContentUnavailableView(
                    "曲库不可用",
                    systemImage: "exclamationmark.triangle",
                    description: Text(message)
                )
            case .ready:
                catalogContent
            }
        }
        .navigationTitle("曲库")
        .searchable(text: $query, prompt: "标题、曲师、编号或自定义别名")
        .navigationDestination(for: Song.self) { song in
            SongDetailView(song: song)
        }
    }

    private var catalogContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                filters
                HStack {
                    Label("\(filteredSongs.count) 首", systemImage: "music.note")
                    Spacer()
                    Text("离线公开数据")
                        .foregroundStyle(.secondary)
                }
                .font(.subheadline)

                if filteredSongs.isEmpty {
                    ContentUnavailableView.search(text: query)
                        .frame(maxWidth: .infinity, minHeight: 280)
                } else {
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 300, maximum: 480), spacing: 14)],
                        spacing: 14
                    ) {
                        ForEach(filteredSongs) { song in
                            NavigationLink(value: song) {
                                SongCard(song: song, aliases: model.aliases(for: song.id))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .frame(maxWidth: 1_200)
            .padding()
            .frame(maxWidth: .infinity)
        }
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("谱面类型", selection: $chartMode) {
                ForEach(CatalogChartMode.allCases) { mode in
                    Text(mode.rawValue).tag(mode)
                }
            }
            .pickerStyle(.segmented)

            Menu {
                Button("全部") { selectedGenre = "全部" }
                ForEach(model.genres, id: \.self) { genre in
                    Button(genre) { selectedGenre = genre }
                }
            } label: {
                Label(selectedGenre, systemImage: "line.3.horizontal.decrease.circle")
            }
        }
    }
}

private struct SongCard: View {
    let song: Song
    let aliases: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(song.title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Text(song.artist)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Text("#\(song.id)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                Text(song.genre)
                    .lineLimit(1)
                if let bpm = song.bpm {
                    Text("BPM \(bpm)")
                }
                Text("\(song.allCharts.count) 谱")
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(song.allCharts.prefix(6)) { chart in
                        DifficultyPill(chart: chart)
                    }
                }
            }

            if !aliases.isEmpty {
                Text("别名：\(aliases.joined(separator: " · "))")
                    .font(.caption)
                    .foregroundStyle(.cyan)
                    .lineLimit(1)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(.quaternary, lineWidth: 1)
        }
    }
}

struct DifficultyPill: View {
    let chart: SongChart

    var body: some View {
        Text("\(chart.typeLabel) \(chart.level)")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .foregroundStyle(.white)
            .background(difficultyColor, in: Capsule())
    }

    private var difficultyColor: Color {
        switch chart.difficulty {
        case 0: .green
        case 1: .yellow.opacity(0.85)
        case 2: .red.opacity(0.85)
        case 3: .purple
        default: .indigo
        }
    }
}
