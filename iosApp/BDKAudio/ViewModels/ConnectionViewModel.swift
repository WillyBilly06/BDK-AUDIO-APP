import Foundation
import CoreBluetooth
import SwiftUI
import sharedKit

/// Main view model handling BLE connection and device state
class ConnectionViewModel: NSObject, ObservableObject {
    // Connection state
    @Published var isConnected = false
    @Published var isConnecting = false
    @Published var discoveredDevices: [BluetoothDevice] = []
    
    // Device info
    @Published var deviceName = "BDK Audio"
    @Published var firmwareVersion = "1.0.0"
    @Published var codecName = ""
    
    // EQ state
    @Published var selectedPresetId: Int32 = 0
    @Published var bass: Double = 50
    @Published var mid: Double = 50
    @Published var treble: Double = 50
    
    // LED state
    @Published var selectedEffect = LedEffect.solid
    @Published var brightness: Double = 100
    @Published var speed: Double = 50
    @Published var primaryColor: Color = .cyan
    @Published var secondaryColor: Color = .purple
    
    // Control toggles
    @Published var bassBoost = false
    @Published var bypassDsp = false
    @Published var channelFlip = false
    
    // BLE
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var cmdCharacteristic: CBCharacteristic?
    private var statusCharacteristic: CBCharacteristic?
    private var meterCharacteristic: CBCharacteristic?
    
    // UUIDs from shared module
    private let serviceUUID = CBUUID(string: BleUnifiedProtocol.shared.SERVICE_UUID_STRING)
    private let cmdUUID = CBUUID(string: BleUnifiedProtocol.shared.CHAR_CMD_UUID_STRING)
    private let statusUUID = CBUUID(string: BleUnifiedProtocol.shared.CHAR_STATUS_UUID_STRING)
    private let meterUUID = CBUUID(string: BleUnifiedProtocol.shared.CHAR_METER_UUID_STRING)
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    // MARK: - Scanning
    
    func startScanning() {
        discoveredDevices.removeAll()
        centralManager.scanForPeripherals(withServices: [serviceUUID], options: nil)
    }
    
    func stopScanning() {
        centralManager.stopScan()
    }
    
    // MARK: - Connection
    
    func connect(to device: BluetoothDevice) {
        // In real implementation, store peripheral reference during discovery
        isConnecting = true
        stopScanning()
    }
    
    func disconnect() {
        if let peripheral = peripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        isConnected = false
        peripheral = nil
    }
    
    // MARK: - Commands using shared module
    
    func selectPreset(_ preset: EqPreset) {
        selectedPresetId = preset.id
        bass = Double(preset.bass)
        mid = Double(preset.mid)
        treble = Double(preset.treble)
        
        let command = BleUnifiedProtocol.shared.buildSetEqPreset(presetId: preset.id)
        sendCommand(command)
    }
    
    func sendEq() {
        let command = BleUnifiedProtocol.shared.buildSetEq(
            bass: Int32(bass) - 50,
            mid: Int32(mid) - 50,
            treble: Int32(treble) - 50
        )
        sendCommand(command)
    }
    
    func selectEffect(_ effect: LedEffect) {
        selectedEffect = effect
        sendLedState()
    }
    
    func sendLedState() {
        let (r1, g1, b1) = colorComponents(primaryColor)
        let (r2, g2, b2) = colorComponents(secondaryColor)
        
        let command = BleUnifiedProtocol.shared.buildSetLed(
            effectId: selectedEffect.id,
            brightness: Int32(brightness * 2.55),
            speed: Int32(speed * 2.55),
            r1: r1, g1: g1, b1: b1,
            r2: r2, g2: g2, b2: b2,
            gradient: 0
        )
        sendCommand(command)
    }
    
    func renameDevice(_ name: String) {
        deviceName = name
        let command = BleUnifiedProtocol.shared.buildSetName(name: name)
        sendCommand(command)
    }
    
    func updateControls() {
        let controlByte = ControlFlags.shared.buildControlByte(
            bassBoost: bassBoost,
            bypassDsp: bypassDsp,
            channelFlip: channelFlip,
            twsMaster: false,
            twsSlave: false,
            mute: false
        )
        let command = BleUnifiedProtocol.shared.buildSetControl(controlByte: controlByte)
        sendCommand(command)
    }
    
    // MARK: - Private helpers
    
    private func sendCommand(_ data: KotlinByteArray) {
        guard let characteristic = cmdCharacteristic,
              let peripheral = peripheral else { return }
        
        let swiftData = Data(kotlinByteArray: data)
        peripheral.writeValue(swiftData, for: characteristic, type: .withResponse)
    }
    
    private func colorComponents(_ color: Color) -> (Int32, Int32, Int32) {
        // Convert SwiftUI Color to RGB
        let uiColor = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Int32(r * 255), Int32(g * 255), Int32(b * 255))
    }
    
    // MARK: - Response handlers
    
    private func handleStatusResponse(_ data: Data) {
        guard data.count > 0 else { return }
        
        let responseId = data[0]
        let payload = data.dropFirst()
        
        switch responseId {
        case BleUnifiedProtocol.Resp.shared.STATUS_EQ:
            handleEqStatus(Array(payload))
        case BleUnifiedProtocol.Resp.shared.STATUS_LED:
            handleLedStatus(Array(payload))
        case BleUnifiedProtocol.Resp.shared.STATUS_CONTROL:
            handleControlStatus(Array(payload))
        case BleUnifiedProtocol.Resp.shared.FULL_STATUS:
            handleFullStatus(Array(payload))
        default:
            break
        }
    }
    
    private func handleEqStatus(_ data: [UInt8]) {
        let kotlinData = data.toKotlinByteArray()
        if let status = BleUnifiedProtocol.shared.parseStatusEq(data: kotlinData) {
            DispatchQueue.main.async {
                self.bass = Double(status.bass) + 50
                self.mid = Double(status.mid) + 50
                self.treble = Double(status.treble) + 50
            }
        }
    }
    
    private func handleLedStatus(_ data: [UInt8]) {
        let kotlinData = data.toKotlinByteArray()
        if let status = BleUnifiedProtocol.shared.parseStatusLed(data: kotlinData) {
            DispatchQueue.main.async {
                self.selectedEffect = LedEffect.companion.fromId(id: status.effectId)
                self.brightness = Double(status.brightness) / 2.55
                self.speed = Double(status.speed) / 2.55
                self.primaryColor = Color(red: Double(status.r1)/255, green: Double(status.g1)/255, blue: Double(status.b1)/255)
                self.secondaryColor = Color(red: Double(status.r2)/255, green: Double(status.g2)/255, blue: Double(status.b2)/255)
            }
        }
    }
    
    private func handleControlStatus(_ data: [UInt8]) {
        guard data.count >= 1 else { return }
        let controlByte = Int32(data[0])
        DispatchQueue.main.async {
            self.bassBoost = ControlFlags.shared.isBassBoostEnabled(controlByte: controlByte)
            self.bypassDsp = ControlFlags.shared.isBypassDspEnabled(controlByte: controlByte)
            self.channelFlip = ControlFlags.shared.isChannelFlipEnabled(controlByte: controlByte)
        }
    }
    
    private func handleFullStatus(_ data: [UInt8]) {
        let kotlinData = data.toKotlinByteArray()
        if let status = BleUnifiedProtocol.shared.parseFullStatus(data: kotlinData) {
            DispatchQueue.main.async {
                self.bass = Double(status.eq.bass) + 50
                self.mid = Double(status.eq.mid) + 50
                self.treble = Double(status.eq.treble) + 50
                
                self.selectedEffect = LedEffect.companion.fromId(id: status.led.effectId)
                self.brightness = Double(status.led.brightness) / 2.55
                self.speed = Double(status.led.speed) / 2.55
                
                self.deviceName = status.deviceName
                self.firmwareVersion = status.firmwareVersion
                
                let controlByte = status.controlByte
                self.bassBoost = ControlFlags.shared.isBassBoostEnabled(controlByte: controlByte)
                self.bypassDsp = ControlFlags.shared.isBypassDspEnabled(controlByte: controlByte)
                self.channelFlip = ControlFlags.shared.isChannelFlipEnabled(controlByte: controlByte)
            }
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension ConnectionViewModel: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            // Ready to scan
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? "Unknown Device"
        let device = BluetoothDevice(identifier: peripheral.identifier.uuidString, name: name)
        
        if !discoveredDevices.contains(where: { $0.identifier == device.identifier }) {
            DispatchQueue.main.async {
                self.discoveredDevices.append(device)
            }
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        DispatchQueue.main.async {
            self.isConnected = true
            self.isConnecting = false
        }
        peripheral.delegate = self
        peripheral.discoverServices([serviceUUID])
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.isConnected = false
        }
    }
}

// MARK: - CBPeripheralDelegate

extension ConnectionViewModel: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        
        for service in services {
            if service.uuid == serviceUUID {
                peripheral.discoverCharacteristics([cmdUUID, statusUUID, meterUUID], for: service)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        
        for characteristic in characteristics {
            switch characteristic.uuid {
            case cmdUUID:
                cmdCharacteristic = characteristic
            case statusUUID:
                statusCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            case meterUUID:
                meterCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            default:
                break
            }
        }
        
        // Request full status after discovery
        let command = BleUnifiedProtocol.shared.buildRequestStatus()
        sendCommand(command)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        
        if characteristic.uuid == statusUUID {
            handleStatusResponse(data)
        } else if characteristic.uuid == meterUUID {
            // Handle meter data for level visualization
        }
    }
}

// MARK: - Data conversion helpers

extension Data {
    init(kotlinByteArray: KotlinByteArray) {
        var bytes = [UInt8]()
        for i in 0..<kotlinByteArray.size {
            bytes.append(UInt8(bitPattern: kotlinByteArray.get(index: i)))
        }
        self.init(bytes)
    }
}

extension Array where Element == UInt8 {
    func toKotlinByteArray() -> KotlinByteArray {
        let kotlinArray = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            kotlinArray.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return kotlinArray
    }
}
