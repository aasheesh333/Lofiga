import Flutter
import UIKit
import MediaPlayer

@main
@objc class AppDelegate: FlutterAppDelegate {
  private let CHANNEL = "com.example.lofiga/audio_query"

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    let controller = window?.rootViewController as! FlutterViewController
    let channel = FlutterMethodChannel(name: CHANNEL, binaryMessenger: controller.binaryMessenger)

    channel.setMethodCallHandler { [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) in
      switch call.method {
      case "querySongs":
        self?.handleQuerySongs(result: result)
      case "permissionsStatus":
        // On iOS 14.5+ the permission is automatic for MediaPlayer
        if #available(iOS 9.3, *) {
          result(MPMediaLibrary.authorizationStatus() == .authorized)
        } else {
          result(true)
        }
      case "permissionsRequest":
        // iOS will prompt automatically when app tries to access music library
        if #available(iOS 9.3, *) {
          MPMediaLibrary.requestAuthorization { status in
            result(status == .authorized)
          }
        } else {
          result(true)
        }
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  private func handleQuerySongs(result: @escaping FlutterResult) {
    let query = MPMediaQuery.songs()
    query.addFilterPredicate(MPMediaPropertyPredicate(
      value: NSNumber(value: false),
      forProperty: MPMediaItemPropertyIsCloudItem
    ))

    var songs: [[String: Any]] = []

    if let items = query.items {
      for item in items {
        guard let title = item.title, let assetURL = item.assetURL else { continue }
        songs.append([
          "id": item.persistentID,
          "title": title,
          "artist": item.artist ?? "Unknown Artist",
          "data": assetURL.absoluteString,
          "duration": item.playbackDuration * 1000, // seconds -> ms
          "date_added": Int(item.dateAdded?.timeIntervalSince1970 ?? 0),
        ])
      }
    }

    result(songs)
  }
}
