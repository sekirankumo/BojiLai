import AVFoundation
import HealthKit
import ImageIO
import PhotosUI
import Speech
import UIKit
import UniformTypeIdentifiers
import UserNotifications
import BojiLaiKit

/// Kotlin 里 NativeBridge 接口的 iOS 实现：相机、相册、文件、Apple 健康、语音识别、通知。
/// 回调可以在任意线程调用，Kotlin 那边会切回主线程。
final class NativeBridgeImpl: NSObject, NativeBridge {
    weak var window: UIWindow?

    private let photos = PhotoHelper()
    private let files = FileHelper()
    private let speech = SpeechHelper()
    private let health = HKHealthStore()

    private func topViewController() -> UIViewController? {
        var vc = window?.rootViewController
        while let presented = vc?.presentedViewController { vc = presented }
        return vc
    }

    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async(execute: block) }
    }

    // MARK: - 外观、链接

    func setInterfaceStyle(style: Int32) {
        onMain {
            let styles: [UIUserInterfaceStyle] = [.unspecified, .light, .dark]
            self.window?.overrideUserInterfaceStyle = styles[Int(max(0, min(2, style)))]
        }
    }

    func openUrl(url: String) {
        guard let u = URL(string: url) else { return }
        onMain { UIApplication.shared.open(u) }
    }

    func openAppSettings() {
        guard let u = URL(string: UIApplication.openSettingsURLString) else { return }
        onMain { UIApplication.shared.open(u) }
    }

    // MARK: - 权限

    func requestSpeechPermission(onResult: @escaping (KotlinBoolean) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            guard status == .authorized else {
                onResult(KotlinBoolean(bool: false))
                return
            }
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                onResult(KotlinBoolean(bool: granted))
            }
        }
    }

    func requestNotificationPermission(onResult: @escaping (KotlinBoolean) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            onResult(KotlinBoolean(bool: ok))
        }
    }

    // MARK: - 通知

    private func content(_ title: String, _ body: String, _ route: String) -> UNMutableNotificationContent {
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        c.userInfo = ["route": route]
        return c
    }

    func showNotification(id: String, title: String, body: String, route: String) {
        let request = UNNotificationRequest(identifier: id, content: content(title, body, route), trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    func scheduleNotification(id: String, title: String, body: String, route: String, atEpochMs: Double) {
        let date = Date(timeIntervalSince1970: atEpochMs / 1000)
        let parts = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
        let request = UNNotificationRequest(identifier: id, content: content(title, body, route), trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    func cancelNotifications(prefix: String, onDone: @escaping () -> Void) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let ids = requests.map(\.identifier).filter { $0.hasPrefix(prefix) }
            center.removePendingNotificationRequests(withIdentifiers: ids)
            onDone()
        }
    }

    // MARK: - 后台任务

    func beginBackgroundTask() -> Int64 {
        var id: UIBackgroundTaskIdentifier = .invalid
        id = UIApplication.shared.beginBackgroundTask(withName: "plan") {
            UIApplication.shared.endBackgroundTask(id)
        }
        return Int64(id.rawValue)
    }

    func endBackgroundTask(id: Int64) {
        let task = UIBackgroundTaskIdentifier(rawValue: Int(id))
        if task != .invalid { UIApplication.shared.endBackgroundTask(task) }
    }

    // MARK: - 照片

    func takePhoto(onResult: @escaping ([NativePhoto]) -> Void) {
        onMain {
            guard let top = self.topViewController() else { return onResult([]) }
            self.photos.takePhoto(from: top, onResult: onResult)
        }
    }

    func pickPhotos(max: Int32, onResult: @escaping ([NativePhoto]) -> Void) {
        onMain {
            guard let top = self.topViewController() else { return onResult([]) }
            self.photos.pick(from: top, max: Int(max), onResult: onResult)
        }
    }

    func loadPhoto(path: String, maxSide: Int32, onResult: @escaping (String?) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            guard let image = UIImage(contentsOfFile: path) else { return onResult(nil) }
            let scaled = PhotoHelper.resized(image, maxSide: CGFloat(maxSide))
            onResult(scaled.jpegData(compressionQuality: 0.82)?.base64EncodedString())
        }
    }

    // MARK: - 文件

    func exportFile(name: String, text: String, onResult: @escaping (String?) -> Void) {
        onMain {
            guard let top = self.topViewController() else { return onResult("无法打开文件选择") }
            self.files.export(from: top, name: name, text: text, onResult: onResult)
        }
    }

    func importFile(onResult: @escaping (String?, String?) -> Void) {
        onMain {
            guard let top = self.topViewController() else { return onResult(nil, "无法打开文件选择") }
            self.files.pickText(from: top, onResult: onResult)
        }
    }

    // MARK: - Apple 健康

    private var bodyTypes: [HKQuantityType] {
        [HKObjectType.quantityType(forIdentifier: .bodyMass), HKObjectType.quantityType(forIdentifier: .bodyFatPercentage)]
            .compactMap { $0 }
    }

    func healthAvailable() -> Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    func healthRequest(onResult: @escaping (KotlinBoolean) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else { return onResult(KotlinBoolean(bool: false)) }
        health.requestAuthorization(toShare: nil, read: Set(bodyTypes)) { ok, _ in
            onResult(KotlinBoolean(bool: ok))
        }
    }

    func healthRead(sinceEpochMs: Double, onResult: @escaping ([NativeBodySample]?, String?) -> Void) {
        let since = Date(timeIntervalSince1970: sinceEpochMs / 1000)
        let predicate = HKQuery.predicateForSamples(withStart: since, end: nil, options: [])
        let group = DispatchGroup()
        let lock = NSLock()
        var samples: [NativeBodySample] = []
        var failure: String?
        for type in bodyTypes {
            let isWeight = type.identifier == HKQuantityTypeIdentifier.bodyMass.rawValue
            group.enter()
            let query = HKSampleQuery(
                sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil
            ) { _, results, error in
                lock.lock()
                if let error = error as NSError? {
                    // 没有授权时读不到数据，不算错误
                    if error.code != HKError.Code.errorAuthorizationDenied.rawValue &&
                        error.code != HKError.Code.errorAuthorizationNotDetermined.rawValue {
                        failure = error.localizedDescription
                    }
                }
                for case let s as HKQuantitySample in results ?? [] {
                    let value = isWeight
                        ? s.quantity.doubleValue(for: .gramUnit(with: .kilo))
                        : s.quantity.doubleValue(for: .percent()) * 100
                    samples.append(NativeBodySample(
                        id: s.uuid.uuidString,
                        timeMs: s.startDate.timeIntervalSince1970 * 1000,
                        kind: isWeight ? "weight" : "fat",
                        value: value
                    ))
                }
                lock.unlock()
                group.leave()
            }
            health.execute(query)
        }
        group.notify(queue: .global()) {
            if let failure = failure, samples.isEmpty { onResult(nil, failure) } else { onResult(samples, nil) }
        }
    }

    func openHealthApp() -> Bool {
        guard let url = URL(string: "x-apple-health://") else { return false }
        onMain { UIApplication.shared.open(url) }
        return true
    }

    // MARK: - 语音识别

    func speechStart(
        onText: @escaping (String) -> Void,
        onLevel: @escaping (KotlinFloat) -> Void,
        onReady: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        onMain {
            self.speech.start(
                onText: onText,
                onLevel: { onLevel(KotlinFloat(float: $0)) },
                onReady: onReady,
                onError: onError
            )
        }
    }

    func speechStop(onFinal: @escaping (String) -> Void) {
        onMain { self.speech.stop(onFinal: onFinal) }
    }

    func speechCancel() {
        onMain { self.speech.cancel() }
    }
}

// MARK: - 拍照和相册

final class PhotoHelper: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate {
    private var onCamera: (([NativePhoto]) -> Void)?
    private var onPick: (([NativePhoto]) -> Void)?

    func takePhoto(from vc: UIViewController, onResult: @escaping ([NativePhoto]) -> Void) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else { return onResult([]) }
        onCamera = onResult
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = self
        vc.present(picker, animated: true)
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)
        let callback = onCamera
        onCamera = nil
        guard let image = info[.originalImage] as? UIImage else { callback?([]); return }
        DispatchQueue.global(qos: .userInitiated).async {
            let path = PhotoHelper.save(image)
            callback?(path.map { [NativePhoto(path: $0, takenAtMs: Date().timeIntervalSince1970 * 1000)] } ?? [])
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        onCamera?([])
        onCamera = nil
    }

    func pick(from vc: UIViewController, max: Int, onResult: @escaping ([NativePhoto]) -> Void) {
        onPick = onResult
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = max
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        vc.present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        let callback = onPick
        onPick = nil
        if results.isEmpty { callback?([]); return }
        let group = DispatchGroup()
        let lock = NSLock()
        var photos: [(Int, NativePhoto)] = []
        for (index, result) in results.enumerated() {
            group.enter()
            result.itemProvider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
                defer { group.leave() }
                guard let data = data, let image = UIImage(data: data), let path = PhotoHelper.save(image) else { return }
                let taken = PhotoHelper.takenAt(data)
                lock.lock()
                photos.append((index, NativePhoto(path: path, takenAtMs: taken.map { $0.timeIntervalSince1970 * 1000 } ?? -1)))
                lock.unlock()
            }
        }
        group.notify(queue: .global()) {
            callback?(photos.sorted { $0.0 < $1.0 }.map(\.1))
        }
    }

    /// 转正、缩小到长边 2048 后存成 JPEG，返回路径
    static func save(_ image: UIImage) -> String? {
        let scaled = resized(image, maxSide: 2048)
        guard let data = scaled.jpegData(compressionQuality: 0.9) else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("photo_\(UUID().uuidString).jpg")
        do {
            try data.write(to: url)
            return url.path
        } catch {
            return nil
        }
    }

    /// 缩小到长边 maxSide（同时按方向转正）
    static func resized(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        let scale = longest > maxSide ? maxSide / longest : 1
        let size = CGSize(width: floor(image.size.width * scale), height: floor(image.size.height * scale))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }

    /// 照片 EXIF 里的拍摄时间
    static func takenAt(_ data: Data) -> Date? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any],
              let exif = props[kCGImagePropertyExifDictionary as String] as? [String: Any],
              let text = exif[kCGImagePropertyExifDateTimeOriginal as String] as? String
        else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return f.date(from: text)
    }
}

// MARK: - 导出 / 导入文件

final class FileHelper: NSObject, UIDocumentPickerDelegate {
    private var onExport: ((String?) -> Void)?
    private var onImport: ((String?, String?) -> Void)?

    func export(from vc: UIViewController, name: String, text: String, onResult: @escaping (String?) -> Void) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            return onResult(error.localizedDescription)
        }
        onImport = nil
        onExport = onResult
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        picker.delegate = self
        vc.present(picker, animated: true)
    }

    func pickText(from vc: UIViewController, onResult: @escaping (String?, String?) -> Void) {
        onExport = nil
        onImport = onResult
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .text, .data], asCopy: true)
        picker.delegate = self
        vc.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if let done = onExport {
            onExport = nil
            done(nil)
        }
        if let done = onImport {
            onImport = nil
            guard let url = urls.first else { return done(nil, nil) }
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                done(try String(contentsOf: url, encoding: .utf8), nil)
            } catch {
                done(nil, "无法读取文件：\(error.localizedDescription)")
            }
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onExport?("")
        onExport = nil
        onImport?(nil, nil)
        onImport = nil
    }
}

// MARK: - 语音识别

/// 连续听写：一直识别到用户点「说完了」。系统一段识别结束（停顿太久或到了时长上限）就接着开下一段，
/// 段与段之间加逗号，和安卓版一样。
final class SpeechHelper {
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var segments: [String] = []
    private var current = ""
    private var running = false
    private var stopping = false
    private var lastLevel = Date.distantPast

    private var onText: ((String) -> Void)?
    private var onError: ((String) -> Void)?
    private var onFinal: ((String) -> Void)?

    private var joined: String {
        (segments + [current]).filter { !$0.isEmpty }.joined(separator: "，")
    }

    func start(
        onText: @escaping (String) -> Void,
        onLevel: @escaping (Float) -> Void,
        onReady: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        cancel()
        guard let recognizer = recognizer, recognizer.isAvailable else {
            onError("语音识别暂时不可用，请检查网络或稍后再试")
            return
        }
        self.onText = onText
        self.onError = onError
        segments = []
        current = ""
        stopping = false
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            startTask()
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                guard let self = self else { return }
                self.request?.append(buffer)
                let level = SpeechHelper.level(buffer)
                DispatchQueue.main.async {
                    // 大约每 50 毫秒报一次音量
                    if Date().timeIntervalSince(self.lastLevel) >= 0.05 {
                        self.lastLevel = Date()
                        onLevel(level)
                    }
                }
            }
            engine.prepare()
            try engine.start()
            running = true
            onReady()
        } catch {
            cancel()
            onError("无法打开麦克风：\(error.localizedDescription)")
        }
    }

    private func startTask() {
        guard let recognizer = recognizer else { return }
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = true }
        if #available(iOS 16, *) { req.addsPunctuation = true }
        request = req
        task = recognizer.recognitionTask(with: req) { [weak self] result, error in
            DispatchQueue.main.async { self?.handle(task: req, result: result, error: error) }
        }
    }

    private func handle(task req: SFSpeechAudioBufferRecognitionRequest, result: SFSpeechRecognitionResult?, error: Error?) {
        // 已经换到下一段了，旧的那段不管
        guard req === request else { return }
        if let result = result {
            current = result.bestTranscription.formattedString
            onText?(joined)
        }
        let ended = error != nil || result?.isFinal == true
        guard ended else { return }
        if stopping {
            finish()
            return
        }
        if !current.isEmpty {
            segments.append(current)
            current = ""
        }
        if let error = error as NSError?, running, !SpeechHelper.isSilence(error) {
            let callback = onError
            cancel()
            callback?("语音识别出错：\(error.localizedDescription)")
            return
        }
        // 一段结束了，接着听下一段
        if running { startTask() }
    }

    private static func isSilence(_ error: NSError) -> Bool {
        // 1110：没听到说话；216 / 301：识别被结束或取消
        [1110, 216, 301, 203].contains(error.code)
    }

    func stop(onFinal: @escaping (String) -> Void) {
        guard running else { return onFinal(joined) }
        self.onFinal = onFinal
        stopping = true
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        // 最后一句最多等 2 秒
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in self?.finish() }
    }

    private func finish() {
        guard let done = onFinal else { return }
        onFinal = nil
        let text = joined
        cancel()
        done(text)
    }

    func cancel() {
        running = false
        task?.cancel()
        task = nil
        request = nil
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        onText = nil
        onError = nil
    }

    /// 一段录音的音量 0–1
    private static func level(_ buffer: AVAudioPCMBuffer) -> Float {
        guard let data = buffer.floatChannelData?[0], buffer.frameLength > 0 else { return 0 }
        var sum: Float = 0
        for i in 0..<Int(buffer.frameLength) { sum += data[i] * data[i] }
        let rms = sqrt(sum / Float(buffer.frameLength))
        return min(1, rms * 8)
    }
}
