import SwiftUI
import sharedKit

struct MainControlView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @State private var selectedTab = 0
    
    var body: some View {
        ZStack {
            // Background
            LinearGradient(
                gradient: Gradient(colors: [Color.black, Color(hex: "1a1a2e")]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header
                HeaderView(viewModel: viewModel)
                
                // Tab selector
                TabSelectorView(selectedTab: $selectedTab)
                
                // Content based on selected tab
                TabView(selection: $selectedTab) {
                    SoundTabView(viewModel: viewModel)
                        .tag(0)
                    
                    LedTabView(viewModel: viewModel)
                        .tag(1)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
        }
        .navigationBarHidden(true)
    }
}

struct HeaderView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @State private var showSettings = false
    @State private var showDeviceInfo = false
    
    var body: some View {
        HStack {
            // Device name and status
            VStack(alignment: .leading) {
                Text(viewModel.deviceName)
                    .font(.title2.bold())
                    .foregroundColor(.white)
                
                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 8, height: 8)
                    Text("Connected")
                        .font(.caption)
                        .foregroundColor(.gray)
                    
                    if !viewModel.codecName.isEmpty {
                        Text("•")
                            .foregroundColor(.gray)
                        Text(viewModel.codecName)
                            .font(.caption)
                            .foregroundColor(.cyan)
                    }
                }
            }
            
            Spacer()
            
            // Device info button
            Button(action: { showDeviceInfo = true }) {
                Image(systemName: "info.circle")
                    .font(.title2)
                    .foregroundColor(.gray)
            }
            .sheet(isPresented: $showDeviceInfo) {
                DeviceInfoSheet(viewModel: viewModel)
            }
            
            // Settings button
            Button(action: { showSettings = true }) {
                Image(systemName: "gearshape.fill")
                    .font(.title2)
                    .foregroundColor(.gray)
            }
            .sheet(isPresented: $showSettings) {
                SettingsView(viewModel: viewModel)
            }
        }
        .padding()
    }
}

struct TabSelectorView: View {
    @Binding var selectedTab: Int
    
    var body: some View {
        HStack(spacing: 0) {
            TabButton(title: "Sound", icon: "speaker.wave.3.fill", isSelected: selectedTab == 0) {
                withAnimation(.spring()) { selectedTab = 0 }
            }
            
            TabButton(title: "LED", icon: "lightbulb.fill", isSelected: selectedTab == 1) {
                withAnimation(.spring()) { selectedTab = 1 }
            }
        }
        .padding(.horizontal)
    }
}

struct TabButton: View {
    let title: String
    let icon: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                Text(title)
                    .fontWeight(.semibold)
            }
            .foregroundColor(isSelected ? .black : .gray)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isSelected ? Color.cyan : Color.clear)
            .cornerRadius(10)
        }
    }
}

// Placeholder views - will be fully implemented
struct SoundTabView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // EQ Presets section
                EQPresetsView(viewModel: viewModel)
                
                // Fine-tune section
                FineTuneView(viewModel: viewModel)
                
                // Level meters placeholder
                LevelMetersView()
            }
            .padding()
        }
    }
}

struct LedTabView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // LED Preview
                LedPreviewView(viewModel: viewModel)
                
                // Brightness/Speed sliders
                LedControlsView(viewModel: viewModel)
                
                // Effect selector
                LedEffectsGridView(viewModel: viewModel)
                
                // Color pickers
                ColorPickerSection(viewModel: viewModel)
            }
            .padding()
        }
    }
}

// Placeholder implementations
struct EQPresetsView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Quick Presets")
                .font(.headline)
                .foregroundColor(.white)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    // Using shared module presets
                    ForEach(Array(EqPresets.shared.allPresets.enumerated()), id: \.element.id) { index, preset in
                        PresetButton(preset: preset, isSelected: viewModel.selectedPresetId == preset.id) {
                            viewModel.selectPreset(preset)
                        }
                    }
                }
            }
        }
    }
}

struct PresetButton: View {
    let preset: EqPreset
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Text(preset.icon)
                    .font(.title)
                Text(preset.name)
                    .font(.caption)
                    .foregroundColor(isSelected ? .black : .white)
            }
            .frame(width: 80, height: 80)
            .background(isSelected ? Color.cyan : Color.white.opacity(0.1))
            .cornerRadius(12)
        }
    }
}

struct FineTuneView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Fine Tune")
                .font(.headline)
                .foregroundColor(.white)
            
            VStack(spacing: 16) {
                SliderRow(title: "Bass", value: $viewModel.bass, color: .red)
                SliderRow(title: "Mid", value: $viewModel.mid, color: .green)
                SliderRow(title: "Treble", value: $viewModel.treble, color: .blue)
            }
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
        }
    }
}

struct SliderRow: View {
    let title: String
    @Binding var value: Double
    let color: Color
    
    var body: some View {
        VStack {
            HStack {
                Text(title)
                    .foregroundColor(.white)
                Spacer()
                Text("\(Int(value))")
                    .foregroundColor(.gray)
            }
            
            Slider(value: $value, in: 0...100)
                .accentColor(color)
        }
    }
}

struct LevelMetersView: View {
    var body: some View {
        VStack(alignment: .leading) {
            Text("Level Meters")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                MeterBar(label: "30Hz", level: 0.6, color: .red)
                MeterBar(label: "60Hz", level: 0.8, color: .orange)
                MeterBar(label: "100Hz", level: 0.5, color: .yellow)
            }
            .frame(height: 100)
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
        }
    }
}

struct MeterBar: View {
    let label: String
    let level: Double
    let color: Color
    
    var body: some View {
        VStack {
            GeometryReader { geo in
                VStack {
                    Spacer()
                    Rectangle()
                        .fill(color)
                        .frame(height: geo.size.height * level)
                        .cornerRadius(4)
                }
            }
            Text(label)
                .font(.caption2)
                .foregroundColor(.gray)
        }
    }
}

struct LedPreviewView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        RoundedRectangle(cornerRadius: 20)
            .fill(
                LinearGradient(
                    colors: [viewModel.primaryColor, viewModel.secondaryColor],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .frame(height: 60)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
            )
    }
}

struct LedControlsView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        VStack(spacing: 16) {
            SliderRow(title: "Brightness", value: $viewModel.brightness, color: .yellow)
            SliderRow(title: "Speed", value: $viewModel.speed, color: .purple)
        }
        .padding()
        .background(Color.white.opacity(0.1))
        .cornerRadius(12)
    }
}

struct LedEffectsGridView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    let columns = [
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible())
    ]
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Effects")
                .font(.headline)
                .foregroundColor(.white)
            
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(LedEffect.entries, id: \.id) { effect in
                    EffectButton(
                        effect: effect,
                        isSelected: viewModel.selectedEffect.id == effect.id
                    ) {
                        viewModel.selectEffect(effect)
                    }
                }
            }
        }
    }
}

struct EffectButton: View {
    let effect: LedEffect
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(effect.displayName)
                .font(.caption)
                .foregroundColor(isSelected ? .black : .white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(isSelected ? Color.cyan : Color.white.opacity(0.1))
                .cornerRadius(8)
        }
    }
}

struct ColorPickerSection: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Colors")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 20) {
                VStack {
                    ColorPicker("Primary", selection: $viewModel.primaryColor)
                        .labelsHidden()
                    Text("Primary")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                
                VStack {
                    ColorPicker("Secondary", selection: $viewModel.secondaryColor)
                        .labelsHidden()
                    Text("Secondary")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
        }
    }
}

struct DeviceInfoSheet: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                Section("Device") {
                    InfoRow(title: "Name", value: viewModel.deviceName)
                    InfoRow(title: "Firmware", value: viewModel.firmwareVersion)
                    InfoRow(title: "Codec", value: viewModel.codecName)
                }
                
                Section("Sounds") {
                    ForEach(SoundType.entries, id: \.id) { soundType in
                        SoundRow(soundType: soundType, viewModel: viewModel)
                    }
                }
            }
            .navigationTitle("Device Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct InfoRow: View {
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundColor(.gray)
        }
    }
}

struct SoundRow: View {
    let soundType: SoundType
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        HStack {
            Text(soundType.displayName)
            Spacer()
            Button("Upload") {
                // TODO: Implement sound upload
            }
            .buttonStyle(.bordered)
        }
    }
}

struct SettingsView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                Section("Audio") {
                    Toggle("Bass Boost", isOn: $viewModel.bassBoost)
                    Toggle("Bypass DSP", isOn: $viewModel.bypassDsp)
                    Toggle("Swap Channels", isOn: $viewModel.channelFlip)
                }
                
                Section("Device") {
                    NavigationLink("Rename Device") {
                        RenameDeviceView(viewModel: viewModel)
                    }
                    
                    NavigationLink("Firmware Update") {
                        OtaUpdateView(viewModel: viewModel)
                    }
                }
                
                Section {
                    Button("Disconnect", role: .destructive) {
                        viewModel.disconnect()
                        dismiss()
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct RenameDeviceView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    @State private var newName = ""
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        Form {
            TextField("Device Name", text: $newName)
            
            Button("Save") {
                viewModel.renameDevice(newName)
                dismiss()
            }
            .disabled(newName.isEmpty)
        }
        .navigationTitle("Rename Device")
        .onAppear {
            newName = viewModel.deviceName
        }
    }
}

struct OtaUpdateView: View {
    @ObservedObject var viewModel: ConnectionViewModel
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Current Version: \(viewModel.firmwareVersion)")
            
            Button("Check for Updates") {
                // TODO: Implement OTA check
            }
            .buttonStyle(.borderedProminent)
        }
        .navigationTitle("Firmware Update")
    }
}
