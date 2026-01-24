import SwiftUI
import sharedKit

struct ConnectionView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @State private var isScanning = false
    @State private var pulseAnimation = false
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                gradient: Gradient(colors: [Color.black, Color(hex: "1a1a2e")]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 30) {
                // Logo and pulse animation
                ZStack {
                    // Pulse circles
                    ForEach(0..<3) { index in
                        Circle()
                            .stroke(Color.cyan.opacity(0.3 - Double(index) * 0.1), lineWidth: 2)
                            .frame(width: 150 + CGFloat(index) * 40, height: 150 + CGFloat(index) * 40)
                            .scaleEffect(pulseAnimation ? 1.2 : 1.0)
                            .opacity(pulseAnimation ? 0 : 1)
                            .animation(
                                Animation.easeOut(duration: 1.5)
                                    .repeatForever(autoreverses: false)
                                    .delay(Double(index) * 0.3),
                                value: pulseAnimation
                            )
                    }
                    
                    // Speaker icon
                    Image(systemName: "hifispeaker.2.fill")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 80, height: 80)
                        .foregroundColor(.cyan)
                }
                .padding(.top, 60)
                
                Text("BDK Audio")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                
                Text(isScanning ? "Searching for devices..." : "Connect to your speaker")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                
                // Device list
                if !viewModel.discoveredDevices.isEmpty {
                    ScrollView {
                        VStack(spacing: 12) {
                            ForEach(viewModel.discoveredDevices, id: \.identifier) { device in
                                DeviceRow(device: device) {
                                    viewModel.connect(to: device)
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                    .frame(maxHeight: 300)
                }
                
                Spacer()
                
                // Scan button
                Button(action: {
                    isScanning.toggle()
                    if isScanning {
                        pulseAnimation = true
                        viewModel.startScanning()
                    } else {
                        pulseAnimation = false
                        viewModel.stopScanning()
                    }
                }) {
                    HStack {
                        Image(systemName: isScanning ? "stop.fill" : "antenna.radiowaves.left.and.right")
                        Text(isScanning ? "Stop Scanning" : "Scan for Devices")
                    }
                    .font(.headline)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.cyan)
                    .cornerRadius(15)
                }
                .padding(.horizontal, 40)
                .padding(.bottom, 40)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            pulseAnimation = true
        }
    }
}

struct DeviceRow: View {
    let device: BluetoothDevice
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack {
                Image(systemName: "hifispeaker.fill")
                    .foregroundColor(.cyan)
                    .font(.title2)
                
                VStack(alignment: .leading) {
                    Text(device.name)
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("Tap to connect")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .foregroundColor(.gray)
            }
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
        }
    }
}

// Placeholder for BluetoothDevice
struct BluetoothDevice: Identifiable {
    let identifier: String
    let name: String
    
    var id: String { identifier }
}

// Color extension for hex colors
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
