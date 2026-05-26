part of '../flutter_secure_storage.dart';
// Documentation ignored because enums will be removed in a later release
//ignore_for_file: public_member_api_docs
//ignore_for_file: constant_identifier_names
//ignore_for_file: deprecated_member_use_from_same_package
//ignore_for_file: remove_deprecations_in_breaking_versions

enum KeyCipherAlgorithm {
  RSA_ECB_PKCS1Padding,
  RSA_ECB_OAEPwithSHA_256andMGF1Padding,
}

enum StorageCipherAlgorithm {
  AES_CBC_PKCS7Padding,
  AES_GCM_NoPadding,
}

/// Android master-key backing preference.
enum AndroidStorageSecurityLevel {
  /// Use StrongBox when the device supports it, otherwise use Android Keystore.
  automatic,

  /// Require StrongBox-backed keys and fail initialization when unavailable.
  strongBoxOnly,

  /// Use Android Keystore without requesting StrongBox.
  androidKeystore,
}

/// Specific options for Android platform.
class AndroidOptions extends Options {
  const AndroidOptions({
    @Deprecated(
      'EncryptedSharedPreferences will always be true, and will be '
      'removed in the next release',
    )
    bool encryptedSharedPreferences = false,
    bool resetOnError = false,
    KeyCipherAlgorithm keyCipherAlgorithm =
        KeyCipherAlgorithm.RSA_ECB_PKCS1Padding,
    StorageCipherAlgorithm storageCipherAlgorithm =
        StorageCipherAlgorithm.AES_CBC_PKCS7Padding,
    AndroidStorageSecurityLevel storageSecurityLevel =
        AndroidStorageSecurityLevel.automatic,
    bool userAuthenticationRequired = false,
    int userAuthenticationValidityDurationSeconds = 300,
    this.sharedPreferencesName,
    this.preferencesKeyPrefix,
    this.keystoreAlias,
  }) : _encryptedSharedPreferences = encryptedSharedPreferences,
       _resetOnError = resetOnError,
       _keyCipherAlgorithm = keyCipherAlgorithm,
       _storageCipherAlgorithm = storageCipherAlgorithm,
       _storageSecurityLevel = storageSecurityLevel,
       _userAuthenticationRequired = userAuthenticationRequired,
       _userAuthenticationValidityDurationSeconds =
           userAuthenticationValidityDurationSeconds;

  /// EncryptedSharedPrefences are only available on API 23 and greater
  final bool _encryptedSharedPreferences;

  /// When an error is detected, automatically reset all data. This will prevent
  /// fatal errors regarding an unknown key however keep in mind that it will
  /// PERMANENLTY erase the data when an error occurs.
  ///
  /// Defaults to false.
  final bool _resetOnError;

  /// If EncryptedSharedPrefences is set to false, you can select algorithm
  /// that will be used to encrypt secret key.
  /// By default RSA/ECB/PKCS1Padding if used.
  /// Newer RSA/ECB/OAEPWithSHA-256AndMGF1Padding is available from Android 6.
  /// Plugin will fall back to default algorithm in previous system versions.
  final KeyCipherAlgorithm _keyCipherAlgorithm;

  /// If EncryptedSharedPrefences is set to false, you can select algorithm
  /// that will be used to encrypt properties.
  /// By default AES/CBC/PKCS7Padding if used.
  /// Newer AES/GCM/NoPadding is available from Android 6.
  /// Plugin will fall back to default algorithm in previous system versions.
  final StorageCipherAlgorithm _storageCipherAlgorithm;

  /// Selects the Android master-key backing.
  ///
  /// Defaults to [AndroidStorageSecurityLevel.automatic], which requests
  /// StrongBox on Android 9+ devices that advertise StrongBox and falls back to
  /// Android Keystore when StrongBox is unavailable. Existing installations
  /// keep using their already-created master key alias.
  final AndroidStorageSecurityLevel _storageSecurityLevel;

  /// When true, the Android Keystore master key is created with
  /// `setUserAuthenticationRequired(true)`, so the secure hardware itself
  /// refuses to use the key unless the user has authenticated (strong biometric
  /// or device credential) within
  /// [_userAuthenticationValidityDurationSeconds]. This binds the key to the
  /// user's presence, not just possession of the device.
  ///
  /// The app must trigger an authentication (e.g. via a biometric prompt)
  /// before reading/writing, otherwise the operation throws
  /// `UserNotAuthenticatedException`. Defaults to false. Only affects newly
  /// created master keys.
  final bool _userAuthenticationRequired;

  /// How long, in seconds, a successful authentication keeps the master key
  /// usable before the user must authenticate again. Must be >= 1. Ignored when
  /// [_userAuthenticationRequired] is false. Defaults to 300 (5 minutes).
  final int _userAuthenticationValidityDurationSeconds;

  /// The name of the sharedPreference database to use.
  /// You can select your own name if you want. A default name will
  /// be used if nothing is provided here.
  ///
  /// WARNING: If you change this you can't retrieve already saved preferences.
  final String? sharedPreferencesName;

  /// The prefix for a shared preference key. The prefix is used to make sure
  /// the key is unique to your application. If not provided, a default prefix
  /// will be used.
  ///
  /// WARNING: If you change this you can't retrieve already saved preferences.
  final String? preferencesKeyPrefix;

  /// The Android Keystore alias for the AES-256-GCM master key that wraps this
  /// store. Set a custom alias to isolate this storage's master key from other
  /// `FlutterSecureStorage` instances — required when you want
  /// [_userAuthenticationRequired] to apply to *this* store only, since the
  /// default alias is shared across all instances in the app.
  ///
  /// WARNING: If you change this you can't retrieve already saved preferences.
  final String? keystoreAlias;

  static const AndroidOptions defaultOptions = AndroidOptions();

  @override
  Map<String, String> toMap() => <String, String>{
    'encryptedSharedPreferences': '$_encryptedSharedPreferences',
    'resetOnError': '$_resetOnError',
    'keyCipherAlgorithm': _keyCipherAlgorithm.name,
    'storageCipherAlgorithm': _storageCipherAlgorithm.name,
    'storageSecurityLevel': _storageSecurityLevel.name,
    'userAuthenticationRequired': '$_userAuthenticationRequired',
    'userAuthenticationValidityDurationSeconds':
        '$_userAuthenticationValidityDurationSeconds',
    'sharedPreferencesName': sharedPreferencesName ?? '',
    'preferencesKeyPrefix': preferencesKeyPrefix ?? '',
    'keystoreAlias': keystoreAlias ?? '',
  };

  AndroidOptions copyWith({
    bool? encryptedSharedPreferences,
    bool? resetOnError,
    KeyCipherAlgorithm? keyCipherAlgorithm,
    StorageCipherAlgorithm? storageCipherAlgorithm,
    AndroidStorageSecurityLevel? storageSecurityLevel,
    bool? userAuthenticationRequired,
    int? userAuthenticationValidityDurationSeconds,
    String? preferencesKeyPrefix,
    String? sharedPreferencesName,
    String? keystoreAlias,
  }) => AndroidOptions(
    encryptedSharedPreferences:
        encryptedSharedPreferences ?? _encryptedSharedPreferences,
    resetOnError: resetOnError ?? _resetOnError,
    keyCipherAlgorithm: keyCipherAlgorithm ?? _keyCipherAlgorithm,
    storageCipherAlgorithm: storageCipherAlgorithm ?? _storageCipherAlgorithm,
    storageSecurityLevel: storageSecurityLevel ?? _storageSecurityLevel,
    userAuthenticationRequired:
        userAuthenticationRequired ?? _userAuthenticationRequired,
    userAuthenticationValidityDurationSeconds:
        userAuthenticationValidityDurationSeconds ??
        _userAuthenticationValidityDurationSeconds,
    sharedPreferencesName: sharedPreferencesName,
    preferencesKeyPrefix: preferencesKeyPrefix,
    keystoreAlias: keystoreAlias ?? this.keystoreAlias,
  );
}
