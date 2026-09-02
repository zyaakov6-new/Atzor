import Foundation
import CoreNFC

/// Reads the hardware UID of any NFC tag; the hex string is the key.
/// Requires the "Near Field Communication Tag Reading" capability and
/// NFCReaderUsageDescription in Info.plist.
final class NfcKeyReader: NSObject, ObservableObject, NFCTagReaderSessionDelegate {

    private var session: NFCTagReaderSession?
    private var onTag: ((String) -> Void)?

    func begin(prompt: String, onTag: @escaping (String) -> Void) {
        guard NFCTagReaderSession.readingAvailable else { return }
        self.onTag = onTag
        session = NFCTagReaderSession(pollingOption: [.iso14443, .iso15693, .iso18092], delegate: self)
        session?.alertMessage = prompt
        session?.begin()
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        self.session = nil
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first else { return }
        session.connect(to: tag) { [weak self] _ in
            let uid: Data? = {
                switch tag {
                case .miFare(let t): return t.identifier
                case .iso7816(let t): return t.identifier
                case .iso15693(let t): return t.identifier
                case .feliCa(let t): return t.currentIDm
                @unknown default: return nil
                }
            }()
            if let uid {
                let hex = uid.map { String(format: "%02x", $0) }.joined()
                DispatchQueue.main.async { self?.onTag?(hex) }
                session.alertMessage = "✓"
                session.invalidate()
            } else {
                session.invalidate(errorMessage: "תג לא נתמך")
            }
        }
    }
}
