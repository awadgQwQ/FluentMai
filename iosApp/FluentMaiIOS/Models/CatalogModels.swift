import Foundation

struct CatalogEnvelope: Decodable {
    let songs: [Song]
}

struct Song: Decodable, Identifiable, Hashable {
    let id: Int
    let title: String
    let artist: String
    let genre: String
    let bpm: Int?
    let version: Int
    let difficulties: SongDifficulties

    var allCharts: [SongChart] {
        difficulties.standard + difficulties.dx
    }

    var hasStandard: Bool { !difficulties.standard.isEmpty }
    var hasDx: Bool { !difficulties.dx.isEmpty }
}

struct SongDifficulties: Decodable, Hashable {
    let standard: [SongChart]
    let dx: [SongChart]
}

struct SongChart: Decodable, Identifiable, Hashable {
    let type: String
    let difficulty: Int
    let level: String
    let levelValue: Double
    let noteDesigner: String
    let version: Int
    let notes: ChartNotes?

    var id: String { "\(type):\(difficulty):\(version)" }

    var typeLabel: String {
        type.lowercased() == "dx" ? "DX" : "标准"
    }

    var difficultyLabel: String {
        let labels = ["BASIC", "ADVANCED", "EXPERT", "MASTER", "Re:MASTER"]
        return labels.indices.contains(difficulty) ? labels[difficulty] : "难度 \(difficulty)"
    }

    enum CodingKeys: String, CodingKey {
        case type, difficulty, level, version, notes
        case levelValue = "level_value"
        case noteDesigner = "note_designer"
    }
}

struct ChartNotes: Decodable, Hashable {
    let total: Int
    let tap: Int
    let hold: Int
    let slide: Int
    let touch: Int
    let breakCount: Int

    enum CodingKeys: String, CodingKey {
        case total, tap, hold, slide, touch
        case breakCount = "break"
    }
}

enum CatalogChartMode: String, CaseIterable, Identifiable {
    case all = "全部"
    case standard = "标准"
    case dx = "DX"

    var id: String { rawValue }
}
