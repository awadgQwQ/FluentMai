import Foundation

struct PersistenceStore {
    private let fileManager = FileManager.default
    private let fileName = "fluentmai-ios-user-data-v1.json"

    func load() -> UserData {
        guard let url = try? storageURL(), fileManager.fileExists(atPath: url.path) else {
            return .empty
        }
        do {
            let data = try Data(contentsOf: url)
            return try Self.decoder.decode(UserData.self, from: data)
        } catch {
            return .empty
        }
    }

    func save(_ value: UserData) throws {
        let url = try storageURL()
        let data = try Self.encoder.encode(value)
        try data.write(to: url, options: [.atomic])
    }

    private func storageURL() throws -> URL {
        let directory = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        ).appendingPathComponent("FluentMai", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent(fileName)
    }

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
