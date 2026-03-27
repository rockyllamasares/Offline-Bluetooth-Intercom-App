# Bluetooth Intercom App Research Findings

## 1. Core Technology: Bluetooth Classic (RFCOMM)
- **Mechanism**: Use `BluetoothSocket` and `BluetoothServerSocket` for a peer-to-peer connection between two Android devices.
- **Data Transfer**: Audio data is captured from the microphone, compressed (e.g., using Opus or AAC), and sent as byte arrays over the `OutputStream`. The receiving device reads from the `InputStream` and plays the audio.
- **Offline Capability**: Works entirely without internet or cellular signal as it relies on direct Bluetooth radio communication.

## 2. Handling Bluetooth Earbuds (Concurrent Connections)
- **Multiple Profiles**: A phone can maintain multiple Bluetooth connections if they use different profiles.
  - **Phone-to-Phone**: Uses **SPP (Serial Port Profile)** or custom RFCOMM for data.
  - **Phone-to-Earbuds**: Uses **A2DP** (for high-quality music) or **HFP/HSP** (for voice calls).
- **Audio Routing (The Challenge)**: By default, Android might route audio to the built-in speaker/mic. To use the earbuds:
  - Use `AudioManager.startBluetoothSco()` to trigger the Synchronous Connection Oriented (SCO) link, which is used for voice.
  - Set `AudioManager.setBluetoothScoOn(true)`.
  - Set the audio mode to `MODE_IN_COMMUNICATION`.
  - **Note**: SCO provides lower audio quality (8kHz or 16kHz mono) but is necessary for two-way voice via Bluetooth headsets.

## 3. Implementation Steps
1. **Permissions**: Request `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, and `RECORD_AUDIO`.
2. **Discovery & Pairing**: Use `BluetoothAdapter` to find nearby devices.
3. **Socket Connection**: Establish an RFCOMM connection using a shared UUID.
4. **Audio Processing**:
   - **Capture**: Use `AudioRecord` to get raw PCM data.
   - **Compression**: Use `MediaCodec` or a library like Opus to reduce data size for smooth transmission over Bluetooth.
   - **Transmission**: Send compressed packets over the Bluetooth socket.
   - **Playback**: Receive packets, decompress, and play using `AudioTrack`.
5. **Routing**: Monitor Bluetooth headset connection state and toggle SCO to ensure audio goes through the earbuds.

## 4. Recommendations
- **Framework**: React Native or Flutter can be used with native modules, but pure Native (Kotlin/Java) is more reliable for low-level Bluetooth and Audio routing.
- **Library**: Consider using `Google Nearby Connections API` which abstracts some of the Bluetooth/WiFi Direct complexity, though manual Bluetooth Socket gives more control over the "earbuds" requirement.
