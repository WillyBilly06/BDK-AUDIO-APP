# BDK Audio App

A professional Android application designed to control and configure ESP32-based Bluetooth speakers with DSP (Digital Signal Processing) capabilities.

## Overview

BDK Audio App provides a comprehensive control interface for managing Bluetooth Low Energy (BLE) connected audio devices. The application enables users to adjust DSP parameters, monitor audio levels in real-time, control LED effects, and perform firmware updates over-the-air.

## Features

### Bluetooth Connectivity
- Automatic device scanning and connection via Bluetooth Low Energy
- Support for multiple device connections
- Real-time connection status monitoring
- Automatic reconnection capabilities

### DSP Control
- Real-time audio level visualization with frequency band meters (30Hz, 150Hz, 500Hz, 2kHz, 6kHz, 12kHz)
- High-pass filter configuration
- Bass boost control
- Master gain adjustment
- Compressor settings with threshold and ratio parameters

### LED Effects
- Multiple LED effect modes including:
  - Static color display
  - VU Meter visualization
  - Spectrum analyzer
  - Strobe effects
  - Fade transitions
  - Rainbow patterns
  - Fire simulation
  - Party mode
  - Wave effects

### Firmware Management
- Over-the-air (OTA) firmware updates via BLE
- Firmware version display
- Binary file selection from device storage

### User Interface
- Modern dark theme design
- Material Design 3 components
- Intuitive card-based layout
- Real-time parameter feedback

## Technical Specifications

### Requirements
- Android 8.0 (API level 26) or higher
- Bluetooth Low Energy support
- Location permissions for BLE scanning

### Permissions
- Bluetooth and Bluetooth Admin
- Fine and Coarse Location
- Bluetooth Scan and Connect (Android 12+)

### Architecture
- Language: Kotlin
- UI Framework: Android Views with Material Design
- Bluetooth: Android BLE API with custom GATT service integration

## Installation

1. Clone the repository
2. Open the project in Android Studio
3. Build and run on a compatible Android device

## Configuration

The application connects to ESP32 devices using the following BLE service:
- Service UUID: Custom ESP32 BLE service
- Characteristics: DSP parameters, LED control, and firmware update channels

## Developer

Created by WillyBilly

## License

This project is proprietary software. All rights reserved.

