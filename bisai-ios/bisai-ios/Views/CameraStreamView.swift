import SwiftUI
import WebKit

// MARK: - WebView 容器（带加载/错误状态）
struct CameraStreamView: View {
    let urlString: String
    
    @State private var isLoading = true
    @State private var isError = false
    @State private var errorMessage: String?
    @State private var urlInput: String = ""
    @State private var showRetryView = false
    
    var body: some View {
        ZStack {
            // WebView 层
            WebViewRepresentable(
                urlString: urlString,
                isLoading: $isLoading,
                isError: $isError,
                errorMessage: $errorMessage
            )
            
            // 加载/出错覆盖层
            if isLoading || isError {
                Color(.systemBackground)
                    .opacity(0.95)
                    .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    if isLoading {
                        ProgressView()
                            .scaleEffect(1.5)
                        Text("正在连接...")
                            .foregroundColor(.secondary)
                    } else {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 64))
                            .foregroundColor(.orange)
                        Text("无法连接到网站")
                            .font(.title3)
                            .fontWeight(.semibold)
                        if let error = errorMessage {
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        Text("当前地址: \(urlString)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Button(action: {
                            isLoading = true
                            isError = false
                        }) {
                            Label("重试连接", systemImage: "arrow.clockwise")
                                .padding(.horizontal)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding()
            }
        }
    }
}

// MARK: - UIViewRepresentable 桥接
private struct WebViewRepresentable: UIViewRepresentable {
    let urlString: String
    @Binding var isLoading: Bool
    @Binding var isError: Bool
    @Binding var errorMessage: String?
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = true
        webView.allowsBackForwardNavigationGestures = true
        
        if let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }
        
        return webView
    }
    
    func updateUIView(_ webView: WKWebView, context: Context) {
        // 当 urlString 变化或重试时重新加载
        if context.coordinator.lastLoadedURL != urlString || (!isLoading && !isError) {
            context.coordinator.lastLoadedURL = urlString
            if let url = URL(string: urlString) {
                webView.load(URLRequest(url: url))
            }
        }
    }
    
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: WebViewRepresentable
        var lastLoadedURL: String?
        
        init(_ parent: WebViewRepresentable) {
            self.parent = parent
        }
        
        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = true
                self.parent.isError = false
                self.parent.errorMessage = nil
            }
        }
        
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }
        
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.isError = true
                self.parent.errorMessage = error.localizedDescription
            }
        }
        
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
                self.parent.isError = true
                self.parent.errorMessage = error.localizedDescription
            }
        }
        
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(.allow)
        }
    }
}
