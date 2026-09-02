import SwiftUI
import FamilyControls
import CoreImage.CIFilterBuiltins

// Landing-page palette.
extension Color {
    static let paper = Color(red: 1.0, green: 0.969, blue: 0.914)          // #FFF7E9
    static let paperWhite = Color(red: 1.0, green: 0.992, blue: 0.973)     // #FFFDF8
    static let ink = Color(red: 0.145, green: 0.204, blue: 0.227)          // #25343A
    static let inkSoft = Color(red: 0.376, green: 0.439, blue: 0.471)      // #607078
    static let coral = Color(red: 0.729, green: 0.396, blue: 0.310)        // #BA654F
    static let coralDeep = Color(red: 0.596, green: 0.302, blue: 0.231)    // #984D3B
    static let leafDeep = Color(red: 0.388, green: 0.502, blue: 0.427)     // #63806D
    static let sun = Color(red: 0.949, green: 0.812, blue: 0.482)          // #F2CF7B
}

struct RootView: View {
    @EnvironmentObject var model: AtzorModel
    @StateObject private var nfc = NfcKeyReader()

    @State private var showAppPicker = false
    @State private var showQrScanner = false
    @State private var showQrCode = false
    @State private var chosenDuration: TimeInterval? = 30 * 60
    @State private var toast: String?
    @State private var now = Date()

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            Color.paper.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 26) {
                    header
                    lockButton
                    if !model.sessionActive { durationChips }
                    appsCard
                    keysCard
                    if let toast { Text(toast).font(.footnote).foregroundStyle(Color.coralDeep) }
                    if let err = model.authorizationError {
                        Text("שגיאת הרשאה: \(err)").font(.footnote).foregroundStyle(.red)
                    }
                }
                .padding(24)
            }
        }
        .onReceive(tick) { now = $0 }
        .familyActivityPicker(isPresented: $showAppPicker, selection: $model.selection)
        .sheet(isPresented: $showQrScanner) {
            QrKeyScanner { code in
                showQrScanner = false
                toast = model.keyPresented(code) ? "המפתח התקבל." : "הקוד הזה הוא לא המפתח."
            }
            .ignoresSafeArea()
        }
        .sheet(isPresented: $showQrCode) { qrSheet }
    }

    private var header: some View {
        HStack {
            Circle().fill(Color.coral).frame(width: 13, height: 13)
            Text("עצור").font(.system(size: 28, weight: .bold, design: .serif)).foregroundStyle(Color.ink)
            Spacer()
        }
    }

    private var lockButton: some View {
        Button {
            if model.sessionActive {
                if model.keyOnly && model.hasKey {
                    toast = "הנעילה הזאת נפתחת רק עם המפתח."
                } else {
                    model.endSession()
                }
            } else if model.selection.applicationTokens.isEmpty && model.selection.categoryTokens.isEmpty {
                toast = "קודם בוחרים מה לחסום."
            } else if chosenDuration == nil && !model.hasKey {
                toast = "אין עדיין מפתח. רשמו תג או צרו קוד למטה."
            } else {
                model.startSession(duration: chosenDuration)
            }
        } label: {
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: model.sessionActive ? [Color.leafDeep, Color(red: 0.29, green: 0.39, blue: 0.33)]
                                                    : [Color.coral, Color.coralDeep],
                        startPoint: .top, endPoint: .bottom))
                    .frame(width: 206, height: 206)
                VStack(spacing: 6) {
                    Text(model.sessionActive ? "פתוח מחדש" : "עצור")
                        .font(.system(size: model.sessionActive ? 24 : 42, weight: .bold, design: .serif))
                        .foregroundStyle(.white)
                    if model.sessionActive {
                        Text(countdown).font(.callout).foregroundStyle(.white.opacity(0.8))
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var countdown: String {
        guard let end = model.sessionEndAt else { return "" }
        if end == .distantFuture { return "עד שהמפתח יגיד אחרת" }
        let left = max(0, Int(end.timeIntervalSince(now)))
        return String(format: "%d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
    }

    private var durationChips: some View {
        HStack(spacing: 8) {
            chip("30 דק׳", 30 * 60)
            chip("שעה", 60 * 60)
            chip("שעתיים", 2 * 60 * 60)
            chip("עד המפתח", nil)
        }
    }

    private func chip(_ label: String, _ duration: TimeInterval?) -> some View {
        let selected = chosenDuration == duration
        return Text(label)
            .font(.subheadline.weight(.bold))
            .padding(.horizontal, 15).padding(.vertical, 9)
            .background(selected ? Color.ink : Color.paperWhite, in: Capsule())
            .foregroundStyle(selected ? Color.paper : Color.inkSoft)
            .overlay(Capsule().stroke(selected ? Color.ink : Color.ink.opacity(0.14)))
            .onTapGesture { chosenDuration = duration }
    }

    private var appsCard: some View {
        card {
            HStack {
                Text("מה נחסם").font(.headline).foregroundStyle(Color.ink)
                Spacer()
                Button("לבחירה ←") { showAppPicker = true }
                    .font(.subheadline.weight(.bold)).tint(Color.coralDeep)
            }
            Text(model.selection.applicationTokens.isEmpty && model.selection.categoryTokens.isEmpty
                 ? "עוד לא בחרתם. אינסטגרם? טיקטוק? הכל?"
                 : "נבחרו \(model.selection.applicationTokens.count) אפליקציות ו‑\(model.selection.categoryTokens.count) קטגוריות")
                .font(.subheadline).foregroundStyle(Color.inkSoft)
        }
    }

    private var keysCard: some View {
        card {
            Text("המפתחות שלכם").font(.headline).foregroundStyle(Color.ink)
            HStack(spacing: 10) {
                Button(model.nfcTagId == nil ? "רישום תג NFC" : "תג רשום · סריקה") {
                    nfc.begin(prompt: "הצמידו את התג") { hex in
                        if model.nfcTagId == nil {
                            model.nfcTagId = hex
                            toast = "התג נרשם. מעכשיו הוא המפתח שלכם."
                        } else {
                            toast = model.keyPresented(hex) ? "המפתח התקבל." : "זה לא התג הרשום."
                        }
                    }
                }
                .buttonStyle(.borderedProminent).tint(Color.coralDeep)

                Button(model.qrSecret == nil ? "יצירת QR" : "הצגת QR") {
                    _ = model.ensureQrSecret()
                    showQrCode = true
                }
                .buttonStyle(.bordered).tint(Color.ink)

                if model.qrSecret != nil {
                    Button("סריקה") { showQrScanner = true }
                        .buttonStyle(.borderedProminent).tint(Color.leafDeep)
                }
            }
            .font(.subheadline.weight(.bold))
        }
    }

    private var qrSheet: some View {
        VStack(spacing: 16) {
            if let secret = model.qrSecret, let img = qrImage(secret) {
                Image(uiImage: img)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 260, height: 260)
            }
            Text("צלמו מסך והדפיסו. זה המפתח, אל תאבדו אותו ליד הספה.")
                .font(.footnote).foregroundStyle(Color.inkSoft)
        }
        .padding(30)
        .presentationDetents([.medium])
    }

    private func qrImage(_ string: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        guard let output = filter.outputImage?.transformed(by: .init(scaleX: 10, y: 10)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }

    @ViewBuilder
    private func card(@ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .trailing, spacing: 10) { content() }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(20)
            .background(Color.paperWhite, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.ink.opacity(0.14)))
    }
}
