import Combine
import Foundation
import FluentMaiShared

enum CatalogLoadState: Equatable {
    case loading
    case ready(Int)
    case failed(String)
}

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var songs: [Song] = []
    @Published private(set) var catalogState: CatalogLoadState = .loading
    @Published private(set) var userData: UserData
    @Published private(set) var persistenceError: String?

    private let persistence: PersistenceStore
    private let domain = IosDomainBridge()

    init(persistence: PersistenceStore = PersistenceStore()) {
        self.persistence = persistence
        userData = persistence.load()
        Task { [weak self] in
            await self?.loadCatalog()
        }
    }

    var genres: [String] {
        Array(Set(songs.map(\.genre))).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    var ratingSummary: RatingSummary {
        let analyzer = IosRatingAnalyzer(currentVersionId: Int32(userData.currentVersionId))
        userData.scores.forEach { score in
            analyzer.addScore(
                scoreKey: score.id,
                levelValue: score.levelValue,
                achievement: score.achievement,
                chartVersion: Int32(score.chartVersion)
            )
        }
        let snapshot = analyzer.build()
        let comparator: (ScoreEntry, ScoreEntry) -> Bool = { left, right in
            if left.rating != right.rating { return left.rating > right.rating }
            if left.achievement != right.achievement { return left.achievement > right.achievement }
            if left.levelValue != right.levelValue { return left.levelValue > right.levelValue }
            return left.id < right.id
        }
        let newBest = userData.scores
            .filter { $0.chartVersion == userData.currentVersionId }
            .sorted(by: comparator)
            .prefix(15)
        let oldBest = userData.scores
            .filter { $0.chartVersion > 0 && $0.chartVersion < userData.currentVersionId }
            .sorted(by: comparator)
            .prefix(35)
        return RatingSummary(
            newBest: Array(newBest),
            oldBest: Array(oldBest),
            totalRating: Int(snapshot.totalRating),
            ineligibleCount: Int(snapshot.ineligibleCount),
            outsideBestCount: Int(snapshot.outsideBestCount)
        )
    }

    func filteredSongs(query: String, genre: String?, chartMode: CatalogChartMode) -> [Song] {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return songs.filter { song in
            if let genre, song.genre != genre { return false }
            switch chartMode {
            case .all: break
            case .standard where !song.hasStandard: return false
            case .dx where !song.hasDx: return false
            default: break
            }
            guard !trimmedQuery.isEmpty else { return true }
            let fields = [song.title, song.artist, song.genre, String(song.id)] + aliases(for: song.id)
            return fields.contains { $0.localizedCaseInsensitiveContains(trimmedQuery) }
        }
    }

    func aliases(for songId: Int) -> [String] {
        userData.aliases[String(songId)] ?? []
    }

    func addAlias(_ value: String, for songId: Int) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let key = String(songId)
        var values = userData.aliases[key] ?? []
        guard !values.contains(where: { $0.caseInsensitiveCompare(trimmed) == .orderedSame }) else { return }
        values.append(trimmed)
        userData.aliases[key] = values.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        persist()
    }

    func removeAlias(_ value: String, for songId: Int) {
        let key = String(songId)
        let remaining = (userData.aliases[key] ?? []).filter { $0 != value }
        if remaining.isEmpty {
            userData.aliases.removeValue(forKey: key)
        } else {
            userData.aliases[key] = remaining
        }
        persist()
    }

    func score(for songId: Int, chart: SongChart) -> ScoreEntry? {
        userData.scores.first { $0.id == scoreKey(songId: songId, chart: chart) }
    }

    func saveScore(song: Song, chart: SongChart, achievement: Double, playedAt: Date = Date()) {
        guard achievement.isFinite, (0.0...101.0).contains(achievement) else { return }
        let previousRating = ratingSummary.totalRating
        let key = scoreKey(songId: song.id, chart: chart)
        let entry = ScoreEntry(
            id: key,
            songId: song.id,
            title: song.title,
            chartType: chart.type,
            difficulty: chart.difficulty,
            difficultyLabel: chart.difficultyLabel,
            level: chart.level,
            levelValue: chart.levelValue,
            chartVersion: chart.version,
            achievement: achievement,
            rating: Int(domain.calculateRating(levelValue: chart.levelValue, achievement: achievement)),
            playedAt: playedAt
        )
        if let index = userData.scores.firstIndex(where: { $0.id == key }) {
            userData.scores[index] = entry
        } else {
            userData.scores.append(entry)
        }
        appendHistoryIfChanged(previousRating: previousRating)
        persist()
    }

    func setCurrentVersion(_ value: Int) {
        guard value > 0, value != userData.currentVersionId else { return }
        let previousRating = ratingSummary.totalRating
        userData.currentVersionId = value
        appendHistoryIfChanged(previousRating: previousRating)
        persist()
    }

    func previewRating(levelValue: Double, achievement: Double) -> Int {
        guard levelValue.isFinite, achievement.isFinite,
              (0.1...20.0).contains(levelValue), (0.0...101.0).contains(achievement) else { return 0 }
        return Int(domain.calculateRating(levelValue: levelValue, achievement: achievement))
    }

    private func loadCatalog() async {
        guard let url = Bundle.main.url(forResource: "lxns_song_list_fallback", withExtension: "json") else {
            catalogState = .failed("公开曲库资源未打包")
            return
        }
        do {
            let decodedSongs = try await Task.detached(priority: .userInitiated) {
                let data = try Data(contentsOf: url)
                return try JSONDecoder().decode(CatalogEnvelope.self, from: data).songs
                    .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
            }.value
            songs = decodedSongs
            catalogState = .ready(decodedSongs.count)
        } catch {
            catalogState = .failed("公开曲库读取失败：\(error.localizedDescription)")
        }
    }

    private func appendHistoryIfChanged(previousRating: Int) {
        let currentRating = ratingSummary.totalRating
        guard currentRating != previousRating || userData.ratingHistory.isEmpty else { return }
        userData.ratingHistory.append(
            RatingHistoryPoint(id: UUID(), recordedAt: Date(), rating: currentRating)
        )
        if userData.ratingHistory.count > 500 {
            userData.ratingHistory.removeFirst(userData.ratingHistory.count - 500)
        }
    }

    private func scoreKey(songId: Int, chart: SongChart) -> String {
        "\(songId):\(chart.type.lowercased()):\(chart.difficulty)"
    }

    private func persist() {
        do {
            try persistence.save(userData)
            persistenceError = nil
        } catch {
            persistenceError = "本地保存失败：\(error.localizedDescription)"
        }
    }
}
