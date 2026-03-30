# Technical Recommendation: Offline Bluetooth Intercom Application

## Introduction

The purpose of this document is to provide a technical recommendation for developing an offline intercom application that utilizes Bluetooth for communication. The primary requirement is the ability to function without an internet connection or cellular signal, and it must be capable of routing audio to connected Bluetooth earbuds.

## Technical Overview

Developing an offline Bluetooth intercom application requires using **Bluetooth Classic** (specifically the RFCOMM protocol) for direct communication between two mobile devices. Audio routing, especially when Bluetooth earbuds are connected, is a critical aspect that requires careful implementation.

### Bluetooth Classic (RFCOMM) for Communication

**RFCOMM (Radio Frequency Communication)** is a protocol that provides an emulated serial port over Bluetooth. It is standardly used for data transfer between two Bluetooth devices. In the context of an intercom app, RFCOMM will be used to establish a peer-to-peer connection between two phones. Once connected, each device will have a `BluetoothSocket` that allows sending and receiving data through `InputStream` and `OutputStream` [1].

The data transfer process involves capturing audio from the microphone, compressing it to reduce data size, and then sending it as byte arrays through the `OutputStream`. At the other end, the receiving device reads the bytes from the `InputStream`, decompresses the audio, and plays it through the speaker or connected headset [1].

### Handling Bluetooth Earbuds and Audio Routing

A significant challenge is ensuring that audio correctly passes through connected Bluetooth earbuds for both input (microphone) and output (speaker). Mobile devices can maintain multiple Bluetooth connections using different profiles. For example, a phone-to-phone connection for data might use **Serial Port Profile (SPP)** or custom RFCOMM, while a phone-to-earbuds connection might use **A2DP (Advanced Audio Distribution Profile)** for high-quality music or **HFP/HSP (Hands-Free Profile/Headset Profile)** for voice calls [2].

To route audio to Bluetooth earbuds for voice communication, the use of a **Synchronous Connection Oriented (SCO)** link is required. SCO is a two-way, full-duplex connection used for voice data. On Android, this can be activated using `AudioManager.startBluetoothSco()` and `AudioManager.setBluetoothScoOn(true)`. It is also important to set the audio mode to `MODE_IN_COMMUNICATION` to indicate that the application is currently being used for communication. Note that SCO provides lower audio quality (typically 8kHz or 16kHz mono) compared to A2DP, but it is necessary for two-way voice via Bluetooth headsets [2] [3].

## Feasibility and Challenges

Developing this type of application is highly feasible, but several challenges must be considered:

*   **Bluetooth API Complexity**: Working directly with the Bluetooth Classic API on Android and iOS can be complex, especially handling different Android versions and changes in Bluetooth behavior over time.
*   **Audio Latency and Quality**: Compressing, sending, and decompressing audio over Bluetooth can introduce latency. Choosing the right audio codec and buffer size is essential to maintain acceptable voice quality and low latency.
*   **Connection Management**: Maintaining a stable Bluetooth connection, especially in dynamic environments where devices may move in and out of range, requires robust connection management logic.
*   **Battery Usage**: Continuous use of the Bluetooth radio and audio processing can have a significant impact on the device's battery consumption.

## Recommended Technical Approach

For a robust and efficient implementation, the following approach is recommended:

### Platform

While cross-platform frameworks like React Native or Flutter can be used, developing the application using **native development (Kotlin/Java for Android and Swift/Objective-C for iOS)** provides the best control over low-level Bluetooth and audio routing APIs. This allows for better optimization and handling of specific use cases related to Bluetooth earbuds.

### Key Implementation Steps

1.  **Permissions**: Request necessary Bluetooth permissions (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`) and audio permissions (`RECORD_AUDIO`) from the user [4].
2.  **Discovery and Pairing**: Implement device discovery using `BluetoothAdapter` to find nearby devices running the intercom app. While pairing is typically required, some techniques allow direct connection without prior pairing by using `createRfcommSocketToServiceRecord()` and `listenUsingRfcommWithServiceRecord()` with the same UUID [5].
3.  **Socket Connection**: Establish an RFCOMM socket connection between two devices using a unique UUID. One device will act as the server (opening a `BluetoothServerSocket`) and the other as the client (establishing a `BluetoothSocket`) [1].
4.  **Audio Processing**:
    *   **Capture**: Use `AudioRecord` to capture raw PCM audio data from the device's microphone.
    *   **Compression**: To reduce data size and improve Bluetooth transfer performance, audio compression is necessary. Codecs like Opus or AAC can be used. Android's `MediaCodec` API can be used for hardware-accelerated encoding and decoding [6].
    *   **Transmission**: Send the compressed audio data as byte arrays through the `OutputStream` of the `BluetoothSocket`.
    *   **Playback**: On the receiving device, read the bytes from the `InputStream`, decompress the audio, and play it using `AudioTrack` [1].
5.  **Audio Routing for Earbuds**: This is the most critical part for your requirement. The following sequence of actions is required to route audio to a connected Bluetooth headset:
    *   Check if a Bluetooth headset is connected (HFP/HSP profile).
    *   Start the Bluetooth SCO audio connection using `AudioManager.startBluetoothSco()`.
    *   Wait for the `ACTION_SCO_AUDIO_STATE_UPDATED` broadcast to confirm that the SCO connection has been established.
    *   Set the audio mode to `AudioManager.MODE_IN_COMMUNICATION`.
    *   Set `AudioManager.setBluetoothScoOn(true)`.
    *   When communication is finished, remember to call `AudioManager.stopBluetoothSco()` and return the audio mode to its previous state [3].

### Alternative Library: Google Nearby Connections API

For developers who want a higher level of abstraction and easier connection management, the **Google Nearby Connections API** is an excellent alternative. It enables device discovery, connection establishment, and data transfer between nearby devices using various technologies (Bluetooth, Wi-Fi Direct, etc.) without having to directly manage low-level Bluetooth sockets. However, controlling specific audio routing to Bluetooth earbuds might be more difficult using this API and may still require some direct `AudioManager` calls [7].

## Conclusion

Developing an offline Bluetooth intercom application that supports Bluetooth earbuds is a technically feasible project. The core strategy centers on using Bluetooth Classic (RFCOMM) for data transfer and careful management of the `AudioManager` for audio routing via the SCO profile. While there are challenges in Bluetooth API complexity and audio management, native development on Android provides the necessary control to meet these requirements. Choosing the right audio codec and implementing robust connection management will be key to the application's success.

## References

[1] Transfer Bluetooth data | Connectivity | Android Developers. (n.d.). Retrieved from [https://developer.android.com/develop/connectivity/bluetooth/transfer-data](https://developer.android.com/develop/connectivity/bluetooth/transfer-data)
[2] Bluetooth in Mobile App Development: Essential Overview - COBE. (2024, October 30). Retrieved from [https://www.cobeisfresh.com/blog/bluetooth-in-mobile-app-development-essential-overview](https://www.cobeisfresh.com/blog/bluetooth-in-mobile-app-development-essential-overview)
[3] AudioManager.StartBluetoothSco Method (Android.Media). (n.d.). Retrieved from [https://learn.microsoft.com/en-us/dotnet/api/android.media.audiomanager.startbluetoothsco?view=net-android-35.0](https://learn.microsoft.com/en-us/dotnet/api/android.media.audiomanager.startbluetoothsco?view=net-android-35.0)
[4] Bluetooth App Development: Understanding iOS, Android and Cross ... (2023, October 2). Retrieved from [https://www.yeti.co/blog/bluetooth-app-development-understanding-ios-android-and-cross-platform-solutions](https://www.yeti.co/blog/bluetooth-app-development-understanding-ios-android-and-cross-platform-solutions)
[5] Creating Bluetooth sockets on Android without pairing. (2018, March 26). Retrieved from [https://albertarmea.com/post/bt-auto-connect/](https://albertarmea.com/post/bt-auto-connect/)
[6] TechNote_BluetoothAudio · google/oboe Wiki. (n.d.). Retrieved from [https://github.com/google/oboe/wiki/TechNote_BluetoothAudio](https://github.com/google/oboe/wiki/TechNote_BluetoothAudio)
[7] Google Nearby Connections API. (n.d.). Retrieved from [https://developers.google.com/nearby/connections/overview](https://developers.google.com/nearby/connections/overview)
