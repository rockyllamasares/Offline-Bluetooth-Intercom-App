# Offline Bluetooth Chat App with Earbuds Compatibility 🎧📱

An Android application designed for **offline communication (Intercom/Chat)** using Bluetooth Classic. It is specifically engineered to function without internet or cellular signals and features the capability to route audio through connected Bluetooth earbuds.

## ✨ Features

*   **Offline Communication**: Operates entirely without Wi-Fi or Mobile Data.
*   **Bluetooth Intercom**: Provides real-time voice communication between two devices.
*   **Earbuds Compatibility**: Supports Bluetooth headsets and earbuds for both voice input and output using a Synchronous Connection Oriented (SCO) link.
*   **Low Latency Audio**: Utilizes RFCOMM (Bluetooth Classic) for efficient and fast data transmission.
*   **Peer-to-Peer Connection**: Establishes direct device-to-device connections using `BluetoothSocket`.

## 🛠️ Tech Stack

*   **Platform**: Android (Native - Kotlin/Java)
*   **Protocol**: Bluetooth Classic (RFCOMM / SPP Profile)
*   **Audio Routing**: `AudioManager` (SCO Link for Bluetooth Headsets)
*   **Audio Processing**: `AudioRecord` (Capture) and `AudioTrack` (Playback)
*   **Compression**: Recommended use of Opus or AAC for smoother data transmission.

## 🚀 How It Works

1.  **Discovery**: The app scans for nearby devices running the same application.
2.  **Connection**: A secure RFCOMM socket connection is established using a unique UUID.
3.  **Audio Routing**: If a Bluetooth headset is detected, the app automatically activates the Bluetooth SCO link to ensure audio is routed correctly.
4.  **Data Stream**: Voice is captured via the microphone, compressed, and transmitted as byte arrays to the receiving device.

## 📋 Required Permissions

To ensure full functionality, the following permissions are required:
*   `BLUETOOTH_CONNECT`
*   `BLUETOOTH_SCAN`
*   `BLUETOOTH_ADVERTISE`
*   `ACCESS_FINE_LOCATION` (Necessary for device discovery)
*   `RECORD_AUDIO` (Required for the intercom feature)

## 📖 Development Guide

The current project structure follows the standard Android Gradle system. The core logic for Bluetooth and Audio handling should be implemented within `app/src/main/java`.

For detailed technical specifications and research, please refer to:
*   `Technical Recommendation_ Offline Bluetooth Intercom Application.md`
*   `bluetooth_intercom_research.md`

---
*Built for seamless and reliable offline communication.*
