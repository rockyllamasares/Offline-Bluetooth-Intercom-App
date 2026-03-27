# Technical Recommendation: Offline Bluetooth Intercom Application

## Panimula

Ang layunin ng dokumentong ito ay magbigay ng teknikal na rekomendasyon para sa pagbuo ng isang offline na intercom application na gumagamit ng Bluetooth para sa komunikasyon. Ang pangunahing kinakailangan ay ang kakayahan nitong gumana nang walang koneksyon sa internet o cellular signal, at dapat itong makapag-ruta ng audio sa mga nakakonektang Bluetooth earbuds.

## Teknikal na Pangkalahatang-ideya

Ang pagbuo ng isang offline na Bluetooth intercom application ay nangangailangan ng paggamit ng **Bluetooth Classic** (partikular ang RFCOMM protocol) para sa direktang komunikasyon sa pagitan ng dalawang mobile device. Ang audio routing, lalo na kapag may nakakonektang Bluetooth earbuds, ay isang kritikal na aspeto na nangangailangan ng maingat na pagpapatupad.

### Bluetooth Classic (RFCOMM) para sa Komunikasyon

Ang **RFCOMM (Radio Frequency Communication)** ay isang protocol na nagbibigay ng emulated serial port sa ibabaw ng Bluetooth. Ito ang karaniwang ginagamit para sa data transfer sa pagitan ng dalawang Bluetooth device. Sa konteksto ng isang intercom app, ang RFCOMM ay gagamitin upang magtatag ng isang peer-to-peer na koneksyon sa pagitan ng dalawang telepono. Kapag nakakonekta, ang bawat device ay magkakaroon ng `BluetoothSocket` na magpapahintulot sa pagpapadala at pagtanggap ng data sa pamamagitan ng `InputStream` at `OutputStream` [1].

Ang proseso ng paglilipat ng data ay kinabibilangan ng pagkuha ng audio mula sa mikropono, pag-compress nito upang mabawasan ang laki ng data, at pagkatapos ay ipadala ito bilang mga byte array sa pamamagitan ng `OutputStream`. Sa kabilang dulo, babasahin ng tumatanggap na device ang mga byte mula sa `InputStream`, ide-decompress ang audio, at ipe-play ito sa pamamagitan ng speaker o nakakonektang headset [1].

### Paghawak ng Bluetooth Earbuds at Audio Routing

Ang isang mahalagang hamon ay ang pagtiyak na ang audio ay nararapat na dumaan sa nakakonektang Bluetooth earbuds para sa parehong input (mikropono) at output (speaker). Ang mga mobile device ay maaaring magpanatili ng maraming koneksyon sa Bluetooth na gumagamit ng iba't ibang profile. Halimbawa, ang koneksyon ng telepono sa telepono para sa data ay maaaring gumamit ng **Serial Port Profile (SPP)** o custom RFCOMM, habang ang koneksyon ng telepono sa earbuds ay maaaring gumamit ng **A2DP (Advanced Audio Distribution Profile)** para sa mataas na kalidad na musika o **HFP/HSP (Hands-Free Profile/Headset Profile)** para sa voice calls [2].

Upang ma-ruta ang audio sa Bluetooth earbuds para sa voice communication, kinakailangan ang paggamit ng **Synchronous Connection Oriented (SCO)** link. Ang SCO ay isang two-way, full-duplex na koneksyon na ginagamit para sa voice data. Sa Android, ito ay maaaring i-activate gamit ang `AudioManager.startBluetoothSco()` at `AudioManager.setBluetoothScoOn(true)`. Mahalaga ring itakda ang audio mode sa `MODE_IN_COMMUNICATION` upang ipahiwatig na ang application ay kasalukuyang ginagamit para sa komunikasyon. Dapat tandaan na ang SCO ay nagbibigay ng mas mababang kalidad ng audio (karaniwang 8kHz o 16kHz mono) kumpara sa A2DP, ngunit ito ay kinakailangan para sa two-way voice sa pamamagitan ng Bluetooth headsets [2] [3].

## Pagiging Posible at mga Hamon

Ang pagbuo ng ganitong uri ng application ay lubos na posible, ngunit may ilang mga hamon na dapat isaalang-alang:

*   **Pagiging Kumplikado ng Bluetooth API**: Ang direktang pagtatrabaho sa Bluetooth Classic API sa Android at iOS ay maaaring maging kumplikado, lalo na sa paghawak ng iba't ibang bersyon ng Android at mga pagbabago sa pag-uugali ng Bluetooth sa paglipas ng panahon.
*   **Audio Latency at Kalidad**: Ang pag-compress, pagpapadala, at pag-decompress ng audio sa ibabaw ng Bluetooth ay maaaring magpakilala ng latency. Ang pagpili ng tamang audio codec at buffer size ay mahalaga upang mapanatili ang katanggap-tanggap na kalidad ng boses at mababang latency.
*   **Pamamahala ng Koneksyon**: Ang pagpapanatili ng matatag na koneksyon sa Bluetooth, lalo na sa mga dynamic na kapaligiran kung saan maaaring lumabas at pumasok ang mga device sa saklaw, ay nangangailangan ng matatag na lohika sa pamamahala ng koneksyon.
*   **Paggamit ng Baterya**: Ang patuloy na paggamit ng Bluetooth radio at audio processing ay maaaring magkaroon ng malaking epekto sa paggamit ng baterya ng device.

## Inirerekomendang Teknikal na Diskarte

Para sa isang matatag at mahusay na pagpapatupad, inirerekomenda ang sumusunod na diskarte:

### Platform

Bagama't ang mga cross-platform framework tulad ng React Native o Flutter ay maaaring magamit, ang pagbuo ng application gamit ang **native development (Kotlin/Java para sa Android at Swift/Objective-C para sa iOS)** ay magbibigay ng pinakamahusay na kontrol sa low-level na Bluetooth at audio routing API. Ito ay magpapahintulot para sa mas mahusay na pag-optimize at paghawak ng mga partikular na kaso ng paggamit na may kaugnayan sa Bluetooth earbuds.

### Mga Pangunahing Hakbang sa Pagpapatupad

1.  **Mga Pahintulot**: Humiling ng kinakailangang mga pahintulot sa Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`) at audio (`RECORD_AUDIO`) mula sa user [4].
2.  **Pagtuklas at Pagpapares**: Ipatupad ang pagtuklas ng device gamit ang `BluetoothAdapter` upang mahanap ang mga kalapit na device na nagpapatakbo ng intercom app. Bagama't ang pagpapares ay karaniwang kinakailangan, ang ilang mga diskarte ay maaaring magpapahintulot sa 
direktang koneksyon nang walang paunang pagpapares sa pamamagitan ng paggamit ng `createRfcommSocketToServiceRecord()` at `listenUsingRfcommWithServiceRecord()` na may parehong UUID [5].
3.  **Koneksyon ng Socket**: Magtatag ng isang RFCOMM socket connection sa pagitan ng dalawang device gamit ang isang natatanging UUID. Ang isang device ay kikilos bilang server (magbubukas ng `BluetoothServerSocket`) at ang isa ay bilang client (magtatatag ng `BluetoothSocket`) [1].
4.  **Pagproseso ng Audio**:
    *   **Pagkuha**: Gamitin ang `AudioRecord` upang makuha ang raw PCM audio data mula sa mikropono ng device.
    *   **Compression**: Upang mabawasan ang laki ng data at mapabuti ang pagganap sa paglipat ng Bluetooth, kinakailangan ang audio compression. Ang mga codec tulad ng Opus o AAC ay maaaring gamitin. Ang `MediaCodec` API ng Android ay maaaring gamitin para sa hardware-accelerated encoding at decoding [6].
    *   **Pagpapadala**: Ipadala ang compressed audio data bilang mga byte array sa pamamagitan ng `OutputStream` ng `BluetoothSocket`.
    *   **Pag-playback**: Sa tumatanggap na device, basahin ang mga byte mula sa `InputStream`, i-decompress ang audio, at i-play ito gamit ang `AudioTrack` [1].
5.  **Audio Routing para sa Earbuds**: Ito ang pinakamahalagang bahagi para sa iyong kinakailangan. Ang sumusunod na pagkakasunod-sunod ng mga aksyon ay kinakailangan upang ma-ruta ang audio sa isang nakakonektang Bluetooth headset:
    *   Suriin kung may nakakonektang Bluetooth headset (HFP/HSP profile).
    *   Simulan ang Bluetooth SCO audio connection gamit ang `AudioManager.startBluetoothSco()`.
    *   Maghintay para sa `ACTION_SCO_AUDIO_STATE_UPDATED` broadcast upang kumpirmahin na ang SCO connection ay naitatag.
    *   Itakda ang audio mode sa `AudioManager.MODE_IN_COMMUNICATION`.
    *   Itakda ang `AudioManager.setBluetoothScoOn(true)`.
    *   Kapag tapos na ang komunikasyon, huwag kalimutang tawagan ang `AudioManager.stopBluetoothSco()` at ibalik ang audio mode sa dati nitong estado [3].

### Alternatibong Library: Google Nearby Connections API

Para sa mga developer na nais ng mas mataas na antas ng abstraction at mas madaling pamamahala ng koneksyon, ang **Google Nearby Connections API** ay isang mahusay na alternatibo. Ito ay nagbibigay-daan sa pagtuklas ng device, pagtatatag ng koneksyon, at paglilipat ng data sa pagitan ng mga kalapit na device gamit ang iba't ibang teknolohiya (Bluetooth, Wi-Fi Direct, atbp.) nang hindi kinakailangang direktang pamahalaan ang mga low-level na Bluetooth socket. Gayunpaman, maaaring mas mahirap kontrolin ang tiyak na audio routing sa mga Bluetooth earbuds gamit ang API na ito, na maaaring mangailangan pa rin ng ilang direktang `AudioManager` na tawag [7].

## Konklusyon

Ang pagbuo ng isang offline na Bluetooth intercom application na sumusuporta sa Bluetooth earbuds ay isang teknikal na magagawa na proyekto. Ang pangunahing diskarte ay nakasentro sa paggamit ng Bluetooth Classic (RFCOMM) para sa data transfer at maingat na pamamahala ng `AudioManager` para sa audio routing sa SCO profile. Bagama't may mga hamon sa pagiging kumplikado ng Bluetooth API at pamamahala ng audio, ang native development sa Android ay nagbibigay ng kinakailangang kontrol upang matugunan ang mga kinakailangan na ito. Ang pagpili ng tamang audio codec at pagpapatupad ng matatag na pamamahala ng koneksyon ay magiging susi sa tagumpay ng application.

## Mga Sanggunian

[1] Transfer Bluetooth data | Connectivity | Android Developers. (n.d.). Retrieved from [https://developer.android.com/develop/connectivity/bluetooth/transfer-data](https://developer.android.com/develop/connectivity/bluetooth/transfer-data)
[2] Bluetooth in Mobile App Development: Essential Overview - COBE. (2024, October 30). Retrieved from [https://www.cobeisfresh.com/blog/bluetooth-in-mobile-app-development-essential-overview](https://www.cobeisfresh.com/blog/bluetooth-in-mobile-app-development-essential-overview)
[3] AudioManager.StartBluetoothSco Method (Android.Media). (n.d.). Retrieved from [https://learn.microsoft.com/en-us/dotnet/api/android.media.audiomanager.startbluetoothsco?view=net-android-35.0](https://learn.microsoft.com/en-us/dotnet/api/android.media.audiomanager.startbluetoothsco?view=net-android-35.0)
[4] Bluetooth App Development: Understanding iOS, Android and Cross ... (2023, October 2). Retrieved from [https://www.yeti.co/blog/bluetooth-app-development-understanding-ios-android-and-cross-platform-solutions](https://www.yeti.co/blog/bluetooth-app-development-understanding-ios-android-and-cross-platform-solutions)
[5] Creating Bluetooth sockets on Android without pairing. (2018, March 26). Retrieved from [https://albertarmea.com/post/bt-auto-connect/](https://albertarmea.com/post/bt-auto-connect/)
[6] TechNote_BluetoothAudio · google/oboe Wiki. (n.d.). Retrieved from [https://github.com/google/oboe/wiki/TechNote_BluetoothAudio](https://github.com/google/oboe/wiki/TechNote_BluetoothAudio)
[7] Google Nearby Connections API. (n.d.). Retrieved from [https://developers.google.com/nearby/connections/overview](https://developers.google.com/nearby/connections/overview) (Note: This link is a general overview, specific details on audio routing with earbuds might require further investigation within the API documentation.)
