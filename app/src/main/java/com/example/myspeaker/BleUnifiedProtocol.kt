package com.example.myspeaker

import java.util.UUID
// Re-export shared protocol for backwards compatibility
import com.example.myspeaker.shared.BleUnifiedProtocol as SharedProtocol

// ========== Type aliases for response parsers (must be at top level) ==========
typealias StatusEq = SharedProtocol.StatusEq
typealias StatusLed = SharedProtocol.StatusLed
typealias FullStatus = SharedProtocol.FullStatus

/**
 * BLE Unified Protocol - Android wrapper for shared module
 * 
 * This provides UUID objects for Android BLE APIs while
 * using the cross-platform protocol from the shared module.
 */
object BleUnifiedProtocol {
    
    // ========== UUIDs as Java UUID objects for Android BLE ==========
    val SERVICE_UUID: UUID = UUID.fromString(SharedProtocol.SERVICE_UUID_STRING)
    val CHAR_CMD_UUID: UUID = UUID.fromString(SharedProtocol.CHAR_CMD_UUID_STRING)
    val CHAR_STATUS_UUID: UUID = UUID.fromString(SharedProtocol.CHAR_STATUS_UUID_STRING)
    val CHAR_METER_UUID: UUID = UUID.fromString(SharedProtocol.CHAR_METER_UUID_STRING)
    
    // CCCD for notifications
    val CCCD_UUID: UUID = UUID.fromString(SharedProtocol.CCCD_UUID_STRING)
    
    // ========== Re-export command/response IDs from shared ==========
    val Cmd = SharedProtocol.Cmd
    val Resp = SharedProtocol.Resp
    val Error = SharedProtocol.Error
    
    // ========== Re-export EQ presets from shared ==========
    val EQ_PRESETS = SharedProtocol.EQ_PRESETS
    val EQ_PRESET_VALUES = SharedProtocol.EQ_PRESET_VALUES
    
    // ========== Command Builders - delegate to shared ==========
    
    fun buildSetEq(bass: Int, mid: Int, treble: Int) = SharedProtocol.buildSetEq(bass, mid, treble)
    fun buildSetEqPreset(presetId: Int) = SharedProtocol.buildSetEqPreset(presetId)
    fun buildSetControl(controlByte: Int) = SharedProtocol.buildSetControl(controlByte)
    fun buildSetName(name: String) = SharedProtocol.buildSetName(name)
    
    fun buildSetLed(
        effectId: Int,
        brightness: Int,
        speed: Int,
        r1: Int, g1: Int, b1: Int,
        r2: Int, g2: Int, b2: Int,
        gradient: Int
    ) = SharedProtocol.buildSetLed(effectId, brightness, speed, r1, g1, b1, r2, g2, b2, gradient)
    
    fun buildSetLedEffect(effectId: Int) = SharedProtocol.buildSetLedEffect(effectId)
    fun buildSetLedBrightness(brightness: Int) = SharedProtocol.buildSetLedBrightness(brightness)
    fun buildSoundMute(muted: Boolean) = SharedProtocol.buildSoundMute(muted)
    fun buildSoundDelete(soundType: Int) = SharedProtocol.buildSoundDelete(soundType)
    fun buildSoundUploadStart(soundType: Int, size: Int) = SharedProtocol.buildSoundUploadStart(soundType, size)
    fun buildSoundUploadData(seq: Int, data: ByteArray) = SharedProtocol.buildSoundUploadData(seq, data)
    fun buildSoundUploadEnd() = SharedProtocol.buildSoundUploadEnd()
    fun buildOtaBegin(size: Int) = SharedProtocol.buildOtaBegin(size)
    fun buildOtaData(seq: Int, data: ByteArray) = SharedProtocol.buildOtaData(seq, data)
    fun buildOtaEnd() = SharedProtocol.buildOtaEnd()
    fun buildOtaAbort() = SharedProtocol.buildOtaAbort()
    fun buildRequestStatus() = SharedProtocol.buildRequestStatus()
    fun buildPing() = SharedProtocol.buildPing()
    
    fun parseStatusEq(data: ByteArray) = SharedProtocol.parseStatusEq(data)
    fun parseStatusLed(data: ByteArray) = SharedProtocol.parseStatusLed(data)
    fun parseFullStatus(data: ByteArray) = SharedProtocol.parseFullStatus(data)
}

