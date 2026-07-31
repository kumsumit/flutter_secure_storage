# flutter_secure_storage

[![Pub Version](https://img.shields.io/pub/v/flutter_secure_storage.svg)](https://pub.dev/packages/flutter_secure_storage)
[![Pub Version Prerelease](https://img.shields.io/pub/v/flutter_secure_storage.svg?include_prereleases)](https://pub.dev/packages/flutter_secure_storage)
[![Build Status](https://github.com/mogol/flutter_secure_storage/actions/workflows/code-quality.yml/badge.svg)](https://github.com/juliansteenbakker/flutter_secure_storage/actions/workflows/code-quality.yml)
[![Code Quality: Very Good Analysis](https://img.shields.io/badge/style-very_good_analysis-B22C89.svg)](https://pub.dev/packages/very_good_analysis)
[![Codecov](https://codecov.io/gh/juliansteenbakker/flutter_secure_storage/graph/badge.svg?token=UUVTJ6MS4A)](https://codecov.io/gh/juliansteenbakker/flutter_secure_storage)
[![GitHub Sponsors](https://img.shields.io/github/sponsors/juliansteenbakker)](https://github.com/sponsors/juliansteenbakker)

A Flutter plugin to securely store sensitive data in a key-value pair format using platform-specific secure storage solutions. It supports Android, iOS, macOS, Windows, and Linux.

## Features

- **Secure Data Storage**: Uses Keychain for iOS/macOS, custom secure ciphers with optional biometric authentication for Android, and platform-specific secure mechanisms for Windows, Linux, and Web.
- **Encryption**: Uses AES-256-GCM authenticated encryption backed by Android Keystore.
- **Cross-Platform**: Works seamlessly across Android, iOS, macOS, Windows, Linux, and Web.
- **Biometric Authentication**: Authentication-bound storage on Android API 28+ and Secure Enclave support on iOS/macOS.
- **Customizable Options**: Configure namespaces, hardware security, accessibility attributes, biometric requirements, and more.

## Important notice for Android

This package supports Android API 24 and newer with one modern encrypted
storage format.

**Key Changes:**
- AES-256-GCM authenticated encryption
- Android Keystore master keys, with optional StrongBox
- `AndroidOptions()` and `AndroidOptions.biometric()` constructors
- Namespace isolation for preferences and Keystore aliases
- No legacy cipher or storage migration

## Important notice for Web
flutter_secure_storage only works on HTTPS or localhost environments. [Please see this issue for more information.](https://github.com/juliansteenbakker/flutter_secure_storage/issues/320#issuecomment-976308930)

## Installation

If not present already, please call WidgetsFlutterBinding.ensureInitialized() in your main before you do anything with the MethodChannel. [Please see this issue  for more info.](https://github.com/juliansteenbakker/flutter_secure_storage/issues/336)

Add the dependency in your `pubspec.yaml` file:

```
dependencies:
flutter_secure_storage: ^<latest_version>
```

Then run:

`flutter pub get`

## Usage

### Import the Package


`import 'package:flutter_secure_storage/flutter_secure_storage.dart';`

### Create an Instance

```dart
// Default AES-256-GCM storage backed by Android Keystore
final storage = FlutterSecureStorage();

// Or with explicit Android options
final storage = FlutterSecureStorage(
  aOptions: AndroidOptions(),
);

// Biometric storage with graceful degradation
final storage = FlutterSecureStorage(
  aOptions: AndroidOptions.biometric(
    enforceBiometrics: false, // Works without biometrics
    biometricPromptTitle: 'Authenticate to access data',
  ),
);

// Strict biometric enforcement (requires device security)
final storage = FlutterSecureStorage(
  aOptions: AndroidOptions.biometric(
    enforceBiometrics: true, // Requires biometric/PIN/pattern
    biometricPromptTitle: 'Authentication Required',
  ),
);
```

### Write Data

`await storage.write(key: 'username', value: 'flutter_user');`

### Read Data

`String? username = await storage.read(key: 'username');`

### Delete Data

`await storage.delete(key: 'username');`

### Delete All Data

`await storage.deleteAll();`

### Check for Key Existence

`bool containsKey = await storage.containsKey(key: 'username');`

## Configuration

Each platform provides its own set of configuration options to tailor secure storage behavior. For example, on iOS, the `IOSOptions` class includes an `accessibility` option that determines when the app can access secure values stored in the Keychain.

The `accessibility` option allows you to specify conditions under which secure values are accessible. For instance:

- `first_unlock`: Enables access to secure values after the device is unlocked for the first time after a reboot.
- `first_unlock_this_device`: Allows access to secure values only after the device is unlocked for the first time since installation on this device.
- `unlocked` (default): Values are accessible only when the device is unlocked.

Here’s an example of configuring the accessibility option on iOS:

```dart
final options = IOSOptions(accessibility: KeychainAccessibility.first_unlock);
await storage.write(key: key, value: value, iOptions: options);
```

By setting `accessibility`, you can control when secure values are accessible, enhancing security and usability for your app on iOS. Similar platform-specific options are available for other platforms as well.

### Android

#### Disabling Auto Backup

_Note_ By default Android backups data on Google Drive. It can cause exception `java.security.InvalidKeyException: Failed to unwrap key`.
You need to:

- [Disable autobackup](https://developer.android.com/guide/topics/data/autobackup#EnablingAutoBackup), [details](https://github.com/juliansteenbakker/flutter_secure_storage/issues/13#issuecomment-421083742)
- [Exclude sharedprefs](https://developer.android.com/guide/topics/data/autobackup#IncludingFiles) used by `FlutterSecureStorage`, [details](https://github.com/juliansteenbakker/flutter_secure_storage/issues/43#issuecomment-471642126)

Add the following to your `android/app/src/main/AndroidManifest.xml`:

```xml
<application
  android:allowBackup="false"
  ...>
</application>
```

#### Modern Android storage

Android uses AES-256-GCM authenticated encryption with an AES-256 master key in
Android Keystore. It has one current storage format; old cipher formats and
automatic migration are intentionally unsupported.

Use `storageNamespace` to isolate both encrypted preferences and the Keystore
alias. `AndroidStorageSecurityLevel.automatic` requests StrongBox when the
device provides it and otherwise uses Android Keystore. Use `strongBoxOnly`
when fallback is unacceptable.

#### Biometric Authentication

Basic encrypted storage supports Android API 24 and newer. Authentication-bound
storage requires Android API 28 or newer.

##### Required Permissions

To use biometric authentication, add the following permission to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC"/>
```

The plugin manifest already contributes this normal permission to the merged
application manifest.

##### Using Biometric Authentication

You can enable biometric authentication using the `AndroidOptions.biometric()` constructor:

```dart
final storage = FlutterSecureStorage(
  aOptions: AndroidOptions.biometric(
    enforceBiometrics: true,
    biometricPromptTitle: 'Biometric authentication required',
  ),
);

// Strong biometric only — device credentials (PIN/pattern/password) rejected
final storage = FlutterSecureStorage(
  aOptions: AndroidOptions.biometric(
    enforceBiometrics: true,
    biometricType: AndroidBiometricType.strongBiometricOnly,
    biometricPromptTitle: 'Fingerprint required',
  ),
);
```

**Note:** When `enforceBiometrics: true`, the app will throw an exception if the device has no PIN, pattern, password, or biometric enrolled.

**`biometricType`** controls which methods satisfy authentication:

| Value                                       | Accepted methods                                        |
|---------------------------------------------|---------------------------------------------------------|
| `AndroidBiometricType.biometricOrDeviceCredential` | Class 3 biometrics **or** PIN / pattern / password (default) |
| `AndroidBiometricType.strongBiometricOnly`  | Class 3 (strong) biometrics only — credentials rejected |

On API 28, the platform prompt supports biometrics only. API 29 adds device
credential fallback. API 30 and newer enforce the selected authenticator types
in both the prompt and Keystore key.

##### Requirements

- **API Level**: Android 7.0 (API 24) minimum for basic encryption
- **API Level**: Android 9.0 (API 28) minimum for enforced biometric authentication
- **API Level**: Android 11.0 (API 30) minimum for `AndroidBiometricType.strongBiometricOnly` to be fully enforced
- **Device Security**: Device must have a PIN, pattern, password, or biometric enrolled (when using `enforceBiometrics: true`)
- **Permissions**: `USE_BIOMETRIC` permission in AndroidManifest.xml

### macOS & iOS

You also need to add Keychain Sharing as capability to your macOS runner. To achieve this, please add the following in *both* your `macos/Runner/DebugProfile.entitlements` *and* `macos/Runner/Release.entitlements` for macOS or for iOS `ios/Runner/DebugProfile.entitlements` *and* `ios/Runner/Release.entitlements`.

```
<key>keychain-access-groups</key>
<array/>
```

If you have set your application up to use App Groups then you will need to add the name of the App Group to the `keychain-access-groups` argument above. Failure to do so will result in values appearing to be written successfully but never actually being written at all. For example if your app has an App Group named "aoeu" then your value for above would instead read:

```
<key>keychain-access-groups</key>
<array>
	<string>$(AppIdentifierPrefix)aoeu</string>
</array>
```

If you are configuring this value through XCode then the string you set in the Keychain Sharing section would simply read "aoeu" with XCode appending the `$(AppIdentifierPrefix)` when it saves the configuration.

### Web

Flutter Secure Storage uses an experimental implementation using WebCrypto. Use at your own risk at this time. Feedback welcome to improve it. The intent is that the browser is creating the private key, and as a result, the encrypted strings in local_storage are not portable to other browsers or other machines and will only work on the same domain.

**It is VERY important that you have HTTP Strict Forward Secrecy enabled and the proper headers applied to your responses or you could be subject to a javascript hijack.**

Please see:

- https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
- https://www.netsparker.com/blog/web-security/http-security-headers/

#### application-specific key option

On the web, all keys are stored in LocalStorage. flutter_secure_storage has an option for the web to wrap this stored key with an application-specific key to make it more difficult to analyze.

```dart
final _storage = const FlutterSecureStorage(
  webOptions: WebOptions(
    wrapKey: '${your_application_specific_key}',
    wrapKeyIv: '${your_application_specific_iv}',
  ),
);
```

### Windows

You need the C++ ATL libraries installed along with the rest of Visual Studio Build Tools. Download them from [here](https://visualstudio.microsoft.com/downloads/?q=build+tools) and make sure the C++ ATL under optional is installed as well.

### Linux

You need to install [Libsecret](https://github.com/GNOME/libsecret) packages:

1. **development package**: on your machine to build the project
2. **runtime package**: to run the application (add it as a dependency after packaging your app).

<details>
	<summary>Apt / Dnf / Pacman</summary>

For Ubuntu / Debian-based distros (e.g., Linux Mint, PopOS):

```shell
sudo apt install libsecret-1-0 libsecret-1-dev
```

For Fedora / RHEL / CentOS distros:

```shell
sudo dnf install libsecret libsecret-devel
```

For Arch based distros (a single package containing both development and runtime modules):

```shell
sudo pacman -S libsecret
```


</details>

<details>
	<summary>Flatpak / Flathub</summary>

[Libsecret supports](https://github.com/GNOME/libsecret#libsecret) both `org.freedesktop.Secret` and `org.freedesktop.portal.Secret`
and is compatible with Flatpak or sandboxed apps.

Freedesktop runtime version 25.08 (also GNOME runtime 49, KDE runtime 6.10 and 5.15-25.08) provides libsecret; therefore, it no longer needs to be compiled ([flathub/shared-modules/#424](https://github.com/flathub/shared-modules/issues/424)) in your app manifest:

```yaml
runtime: org.freedesktop.Platform
runtime-version: '25.08' # Should be at least 25.08 (or newer)
```

> However, if you are still using an older runtime, you may use [Flathub Shared Modules](https://docs.flathub.org/docs/for-app-authors/shared-modules)
and add `shared-modules/libsecret/libsecret.json` (**no longer recommend** and will be removed).

</details>

<details>
	<summary>Snapcraft</summary>

If you using snapcraft to build the project, use the following:

```yaml
parts:
  your-app:
    plugin: flutter
    flutter-target: lib/main.dart
    build-packages:
      - libsecret-1-dev
    stage-packages:
      - libsecret-1-0
```

</details>

Apart from `libsecret`, you also need a keyring service. This is typically already installed by the desktop environment:

- [`gnome-keyring`](https://wiki.gnome.org/Projects/GnomeKeyring) (for Gnome users)
- [`kwalletmanager`](https://wiki.archlinux.org/title/KDE_Wallet) (for KDE users)
- Or a light provider such as [`secret-service`](https://github.com/yousefvand/secret-service)

For more details, including known issues and CI setup, see the [`flutter_secure_storage_linux` README](https://pub.dev/packages/flutter_secure_storage_linux).

## Integration Tests

To run the integration tests, navigate to the `example` directory and execute the following command:

`flutter drive --target=test_driver/app.dart`

This will launch the integration tests specified in the `test_driver` directory.

## Contributing

We welcome contributions to this project! To set up your workspace after cloning the repository, follow these steps:

1. Fetch the Flutter dependencies:
   `flutter pub get`

2. Activate `melos`:
   `dart pub global activate melos`

3. (Optional) Add pub executables to your path:
   `export PATH="$PATH":"$HOME/.pub-cache/bin"`

4. Bootstrap the workspace with `melos`:
   `melos bootstrap`

This will prepare the project for development by linking and configuring all required dependencies.

## API Reference

For a complete list of available methods and configuration options, refer to the [API documentation](https://pub.dev/documentation/flutter_secure_storage/latest/).

## License

This project is licensed under the BSD 3 License. See the [LICENSE](LICENSE) file for details.
