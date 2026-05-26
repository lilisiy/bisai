import Foundation
import Network

// MARK: - Bafa 云 TCP 客户端
class BafaClient: ObservableObject {
    private let host: String
    private let port: UInt16
    private let uid: String
    
    @Published var latestValues: [String: String] = [:]
    @Published var historyByTopic: [String: [HistoryEntry]] = [:]
    
    private var connection: NWConnection?
    private var isRunning = false
    private var reconnectTask: Task<Void, Never>?
    private var heartbeatTask: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?
    private var pendingTopics: [String] = []
    
    struct HistoryEntry: Identifiable {
        var id: String { "\(topic)-\(timestamp)" }
        let topic: String
        let msg: String
        let timestamp: Date
    }
    
    struct Config {
        let topics: [String]
        let pullHistoryOnStart: Bool
    }
    
    init(host: String = "bemfa.com", port: UInt16 = 8344, uid: String) {
        self.host = host
        self.port = port
        self.uid = uid
    }
    
    // MARK: - Public API
    
    func start(config: Config) {
        stop()
        isRunning = true
        pendingTopics = config.topics
        
        reconnectTask = Task { [weak self] in
            guard let self = self else { return }
            var retry = 0
            while self.isRunning && !Task.isCancelled {
                do {
                    try await self.connect()
                    await self.subscribe(topics: self.pendingTopics)
                    if config.pullHistoryOnStart {
                        for topic in self.pendingTopics {
                            if !self.isRunning { break }
                            self.requestHistoryOnce(topic: topic)
                            try? await Task.sleep(nanoseconds: 80_000_000)
                        }
                    }
                    await self.receiveLoop()
                } catch {
                    // 连接断开，重试
                }
                guard self.isRunning else { break }
                retry = min(retry + 1, 6)
                let delay = UInt64(pow(2.0, Double(retry))) * 500_000_000
                try? await Task.sleep(nanoseconds: delay)
            }
        }
        
        // 心跳
        heartbeatTask = Task { [weak self] in
            while self?.isRunning == true && !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                self?.sendRaw("ping\r\n")
            }
        }
    }
    
    func stop() {
        isRunning = false
        reconnectTask?.cancel()
        heartbeatTask?.cancel()
        receiveTask?.cancel()
        connection?.cancel()
        connection = nil
    }
    
    func subscribe(topics: [String]) async {
        for t in topics where !t.isEmpty {
            sendRaw("cmd=1&uid=\(uid)&topic=\(t)\r\n")
        }
    }
    
    func requestHistoryOnce(topic: String) {
        sendRaw("cmd=3&uid=\(uid)&topic=\(topic)\r\n")
    }
    
    func publish(topic: String, msg: String) {
        sendRaw("cmd=2&uid=\(uid)&topic=\(topic)&msg=\(msg)\r\n")
    }
    
    // MARK: - Private
    
    private func connect() async throws {
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: NWEndpoint.Port(integerLiteral: port))
        connection = NWConnection(to: endpoint, using: .tcp)
        connection?.start(queue: .global())
        
        // 等待连接就绪
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            connection?.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    cont.resume()
                case .failed(let err):
                    cont.resume(throwing: err)
                default:
                    break
                }
            }
        }
    }
    
    private func receiveLoop() async {
        guard let conn = connection else { return }
        
        // 使用 NWConnection 的 receive 循环
        var buffer = Data()
        while isRunning && !Task.isCancelled {
            do {
                let data = try await receive(conn)
                buffer.append(data)
                // 按行解析
                while let newline = buffer.firstIndex(of: 0x0A) {
                    let lineData = buffer[..<newline]
                    if let line = String(data: lineData, encoding: .utf8) {
                        handleLine(line)
                    }
                    buffer.removeSubrange(...newline)
                }
            } catch {
                break
            }
        }
    }
    
    private func receive(_ conn: NWConnection) async throws -> Data {
        try await withCheckedThrowingContinuation { cont in
            conn.receive(minimumIncompleteLength: 1, maximumLength: 4096) { data, _, _, error in
                if let error = error {
                    cont.resume(throwing: error)
                } else {
                    cont.resume(returning: data ?? Data())
                }
            }
        }
    }
    
    private func handleLine(_ line: String) {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.contains("topic="), trimmed.contains("msg=") else { return }
        
        let pairs = parseKeyValue(trimmed)
        guard let topic = pairs["topic"], let msg = pairs["msg"] else { return }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.latestValues[topic] = msg
            
            let entry = HistoryEntry(topic: topic, msg: msg, timestamp: Date())
            var list = self.historyByTopic[topic] ?? []
            list.append(entry)
            if list.count > 200 {
                list = Array(list.suffix(200))
            }
            self.historyByTopic[topic] = list
        }
    }
    
    private func parseKeyValue(_ s: String) -> [String: String] {
        var result: [String: String] = [:]
        let parts = s.split(separator: "&")
        for part in parts {
            let kv = part.split(separator: "=", maxSplits: 1)
            if kv.count == 2 {
                result[String(kv[0])] = String(kv[1])
            }
        }
        return result
    }
    
    private func sendRaw(_ cmd: String) {
        guard let conn = connection else { return }
        let data = cmd.data(using: .utf8)!
        conn.send(content: data, completion: .contentProcessed { _ in })
    }
}

// MARK: - 巴法云默认配置
enum BafaDefaults {
    static let uid = "e7a1c889becf42b7b25439a0e4618c6a"
    static let topicDistance = "distance"
    static let topicDistanceDown = "distancedown"
    static let topicDistanceUp = "distanceup"
}
