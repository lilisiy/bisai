import SwiftUI

struct HomeView: View {
    @StateObject private var bafaClient = BafaClient(uid: BafaDefaults.uid)
    @AppStorage("cameraUrl") private var cameraUrl = "http://10.230.31.69"
    @State private var showCameraSettings = false
    @State private var showHistory = false
    
    // Distance
    @State private var distance = ""
    @State private var distanceMin = ""
    @State private var distanceMax = ""
    @State private var distanceMinLimit = ""
    @State private var distanceMaxLimit = ""
    @State private var distanceSeries: [Float] = []
    @State private var alertMessage: String?
    @State private var confirmedAlertKeys: Set<String> = []
    @State private var currentActiveAlertKeys: Set<String> = []
    @State private var dirtyTopics: [String: Bool] = [:]
    @State private var selectedTab = 0
    
    private let subscribeTopics = [BafaDefaults.topicDistance, BafaDefaults.topicDistanceDown, BafaDefaults.topicDistanceUp]
    
    var body: some View {
        TabView(selection: $selectedTab) {
            // 距离页
            NavigationStack {
                ScrollView {
                    VStack(spacing: 16) {
                        DistanceCard(
                            distance: distance,
                            series: distanceSeries,
                            min: $distanceMin,
                            max: $distanceMax,
                            onMinUpload: { uploadThreshold(BafaDefaults.topicDistanceDown, distanceMin) },
                            onMaxUpload: { uploadThreshold(BafaDefaults.topicDistanceUp, distanceMax) }
                        )
                    }
                    .padding()
                }
                .navigationTitle("倒车影像")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(action: { showHistory = true }) {
                            Image(systemName: "clock.arrow.circlepath")
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(action: { showCameraSettings = true }) {
                            Image(systemName: "gearshape")
                        }
                    }
                }
            }
            .tabItem {
                Label("距离", systemImage: "ruler")
            }
            .tag(0)
            
            // 摄像头页
            NavigationStack {
                CameraStreamView(urlString: cameraUrl)
                    .navigationTitle("画面")
                    .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem {
                Label("画面", systemImage: "camera.fill")
            }
            .tag(1)
        }
        .onAppear {
            bafaClient.start(config: BafaClient.Config(topics: subscribeTopics, pullHistoryOnStart: true))
        }
        .onReceive(bafaClient.$latestValues) { values in
            handleLatestValues(values)
        }
        .sheet(isPresented: $showCameraSettings) {
            CameraSettingsView(url: $cameraUrl)
        }
        .sheet(isPresented: $showHistory) {
            HistoryView(client: bafaClient)
        }
        .alert("环境报警", isPresented: Binding<Bool>(
            get: { alertMessage != nil },
            set: { if !$0 { alertMessage = nil } }
        )) {
            Button("确定") {
                confirmedAlertKeys.formUnion(currentActiveAlertKeys)
                alertMessage = nil
            }
        } message: {
            Text(alertMessage ?? "")
        }
    }
    
    // MARK: - Logic
    
    private func uploadThreshold(_ topic: String, _ value: String) {
        let v = value.trimmingCharacters(in: .whitespaces)
        guard !v.isEmpty, Double(v) != nil else { return }
        bafaClient.publish(topic: topic, msg: v)
    }
    
    private func handleLatestValues(_ values: [String: String]) {
        // Distance
        if let v = values[BafaDefaults.topicDistance] {
            distance = v
            if let f = Float(v) {
                distanceSeries.append(f)
                if distanceSeries.count > 150 { distanceSeries.removeFirst() }
            }
        }
        if let v = values[BafaDefaults.topicDistanceDown] {
            distanceMinLimit = v
            applyFromServer(BafaDefaults.topicDistanceDown, v, distanceMin) { distanceMin = $0 }
        }
        if let v = values[BafaDefaults.topicDistanceUp] {
            distanceMaxLimit = v
            applyFromServer(BafaDefaults.topicDistanceUp, v, distanceMax) { distanceMax = $0 }
        }
        
        // Alert check
        var alerts: [String] = []
        var keys: Set<String> = []
        
        if let v = Float(distance) {
            if let min = Float(distanceMinLimit), v < min {
                alerts.append("距离 (\(v)cm) 低于下限 (\(min)cm)！")
                keys.insert("distance_low")
            }
            if let max = Float(distanceMaxLimit), v > max {
                alerts.append("距离 (\(v)cm) 超过上限 (\(max)cm)！")
                keys.insert("distance_high")
            }
        }
        
        currentActiveAlertKeys = keys
        confirmedAlertKeys.subtract(keys)
        
        let newAlerts = keys.subtracting(confirmedAlertKeys)
        if !newAlerts.isEmpty {
            alertMessage = alerts.joined(separator: "\n")
        } else if keys.isEmpty {
            alertMessage = nil
        }
    }
    
    private func applyFromServer(_ topic: String, _ incoming: String, _ current: String, set: (String) -> Void) {
        if dirtyTopics[topic] == true && incoming != current { return }
        set(incoming)
        if incoming == current { dirtyTopics[topic] = nil }
    }
}

// MARK: - Distance Card

struct DistanceCard: View {
    let distance: String
    let series: [Float]
    @Binding var min: String
    @Binding var max: String
    let onMinUpload: () -> Void
    let onMaxUpload: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            HStack {
                Text("📏")
                    .font(.system(size: 48))
                VStack(alignment: .leading) {
                    Text("距离")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text(distance.isEmpty ? "-- cm" : "\(distance) cm")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
                Spacer()
            }
            
            LineChartView(points: series, color: .green)
                .frame(height: 160)
            
            VStack(spacing: 12) {
                HStack {
                    TextField("下限报警值", text: $min)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.decimalPad)
                    Button(action: onMinUpload) {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(Double(min.trimmingCharacters(in: .whitespaces)) == nil)
                }
                HStack {
                    TextField("上限报警值", text: $max)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.decimalPad)
                    Button(action: onMaxUpload) {
                        Image(systemName: "paperplane.fill")
                    }
                    .disabled(Double(max.trimmingCharacters(in: .whitespaces)) == nil)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(radius: 3)
    }
}

// MARK: - Line Chart (Canvas)

struct LineChartView: View {
    let points: [Float]
    let color: Color
    
    var body: some View {
        Canvas { context, size in
            guard points.count > 1 else { return }
            
            let minV = points.min() ?? 0
            let maxV = points.max() ?? 1
            let range = maxV > minV ? maxV - minV : 1
            
            let stepX = size.width / CGFloat(points.count - 1)
            
            var path = SwiftUI.Path()
            path.move(to: CGPoint(x: 0, y: size.height - CGFloat((points[0] - minV) / range) * size.height))
            
            for i in 1..<points.count {
                let x = CGFloat(i) * stepX
                let y = size.height - CGFloat((points[i] - minV) / range) * size.height
                path.addLine(to: CGPoint(x: x, y: y))
            }
            
            context.stroke(path, with: .color(color), lineWidth: 3)
            
            for i in 0..<points.count {
                let x = points.count == 1 ? size.width / 2 : CGFloat(i) * stepX
                let y = size.height - CGFloat((points[i] - minV) / range) * size.height
                context.fill(Path(ellipseIn: CGRect(x: x - 5, y: y - 5, width: 10, height: 10)), with: .color(color))
            }
        }
    }
}

// MARK: - Camera Settings

struct CameraSettingsView: View {
    @Binding var url: String
    @Environment(\.dismiss) private var dismiss
    @State private var input: String = ""
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("摄像头/网站地址")) {
                    TextField("输入网址或 IP", text: $input)
                        .autocapitalization(.none)
                        .keyboardType(.URL)
                }
                Section(footer: Text("• 输入完整网址如 http://10.230.31.69\n• 或输入 IP 自动补全 http://")) {
                    EmptyView()
                }
            }
            .navigationTitle("地址设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        let trimmed = input.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty {
                            url = trimmed.hasPrefix("http") ? trimmed : "http://\(trimmed)"
                        }
                        dismiss()
                    }
                }
            }
            .onAppear { input = url }
        }
    }
}

// MARK: - History View

struct HistoryView: View {
    @ObservedObject var client: BafaClient
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            let list = client.historyByTopic[BafaDefaults.topicDistance] ?? []
            List(list.reversed()) { entry in
                VStack(alignment: .leading) {
                    Text(entry.msg)
                        .font(.headline)
                    Text(entry.timestamp, style: .time)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("历史记录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }
}
