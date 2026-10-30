import UIKit
import UserNotifications
import BojiLaiKit

/// iOS 入口：界面和功能都在共享的 Kotlin 代码里（BojiLaiKit），这里只负责启动和系统事件
@main
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var window: UIWindow?
    private let bridge = NativeBridgeImpl()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        let window = UIWindow(frame: UIScreen.main.bounds)
        self.window = window
        bridge.window = window
        IosApp.shared.start(bridge: bridge)
        window.rootViewController = IosApp.shared.createViewController()
        window.makeKeyAndVisible()
        IosApp.shared.onForeground()
        // 截图检查用：启动参数 -route settings 直接打开某个页面
        if let route = UserDefaults.standard.string(forKey: "route") {
            IosApp.shared.openRoute(route: route)
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main
        ) { _ in
            IosApp.shared.onForeground()
        }
        return true
    }

    // App 在前台时也显示通知（和安卓一样）
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    // 点通知：打开对应的页面
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let route = response.notification.request.content.userInfo["route"] as? String {
            IosApp.shared.openRoute(route: route)
        }
        completionHandler()
    }
}
