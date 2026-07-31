part of '../flutter_secure_storage.dart';

/// Controls which authentication methods an Android biometric prompt accepts.
enum AndroidBiometricType {
  /// Only Class 3 (strong) biometrics are accepted.
  strongBiometricOnly,

  /// Strong biometrics or the device credential are accepted.
  biometricOrDeviceCredential,
}

/// Android master-key backing preference.
enum AndroidStorageSecurityLevel {
  /// Use StrongBox when available, otherwise use Android Keystore.
  automatic,

  /// Require StrongBox-backed keys and fail when unavailable.
  strongBoxOnly,

  /// Use Android Keystore without requesting StrongBox.
  androidKeystore,
}

/// Android-specific secure-storage options.
class AndroidOptions extends Options {
  /// Creates standard AES-GCM secure storage.
  const AndroidOptions({
    this.resetOnError = true,
    this.enforceBiometrics = false,
    this.biometricType = AndroidBiometricType.biometricOrDeviceCredential,
    this.requireBiometricConfirmation = true,
    this.storageSecurityLevel = AndroidStorageSecurityLevel.automatic,
    this.userAuthenticationValidityDurationSeconds = 300,
    this.preferencesKeyPrefix,
    this.storageNamespace,
    this.biometricPromptTitle,
    this.biometricPromptSubtitle,
    this.biometricPromptNegativeButton,
  });

  /// Creates authentication-bound storage with AES-GCM protection.
  const AndroidOptions.biometric({
    this.resetOnError = true,
    this.enforceBiometrics = true,
    this.biometricType = AndroidBiometricType.biometricOrDeviceCredential,
    this.requireBiometricConfirmation = true,
    this.storageSecurityLevel = AndroidStorageSecurityLevel.automatic,
    this.userAuthenticationValidityDurationSeconds = 300,
    this.preferencesKeyPrefix,
    this.storageNamespace,
    this.biometricPromptTitle,
    this.biometricPromptSubtitle,
    this.biometricPromptNegativeButton,
  });

  /// Whether corrupt storage is erased and re-created.
  final bool resetOnError;

  /// Whether each storage configuration requires user authentication.
  final bool enforceBiometrics;

  /// Authentication methods accepted by Android.
  final AndroidBiometricType biometricType;

  /// Whether passive biometrics require explicit confirmation.
  final bool requireBiometricConfirmation;

  /// Keystore hardware preference for the master key.
  final AndroidStorageSecurityLevel storageSecurityLevel;

  /// Seconds for which a successful authentication remains valid.
  final int userAuthenticationValidityDurationSeconds;

  /// Prefix added to every logical key.
  final String? preferencesKeyPrefix;

  /// Isolates data preferences, configuration, and Android Keystore aliases.
  final String? storageNamespace;

  /// Title shown in the Android authentication prompt.
  final String? biometricPromptTitle;

  /// Subtitle shown in the Android authentication prompt.
  final String? biometricPromptSubtitle;

  /// Negative-button label used when device-credential fallback is unavailable.
  final String? biometricPromptNegativeButton;

  /// Default Android options.
  static const AndroidOptions defaultOptions = AndroidOptions();

  @override
  Map<String, String> toMap() => <String, String>{
    'resetOnError': '$resetOnError',
    'enforceBiometrics': '$enforceBiometrics',
    'biometricType': biometricType.name,
    'requireBiometricConfirmation': '$requireBiometricConfirmation',
    'storageSecurityLevel': storageSecurityLevel.name,
    'userAuthenticationValidityDurationSeconds':
        '$userAuthenticationValidityDurationSeconds',
    'preferencesKeyPrefix': preferencesKeyPrefix ?? '',
    'storageNamespace': storageNamespace ?? '',
    'biometricPromptTitle':
        biometricPromptTitle ?? 'Authenticate to access secure storage',
    'biometricPromptSubtitle':
        biometricPromptSubtitle ?? 'Use biometrics or device credentials',
    'biometricPromptNegativeButton': biometricPromptNegativeButton ?? 'Cancel',
  };

  /// Returns a copy with selected values replaced.
  AndroidOptions copyWith({
    bool? resetOnError,
    bool? enforceBiometrics,
    AndroidBiometricType? biometricType,
    bool? requireBiometricConfirmation,
    AndroidStorageSecurityLevel? storageSecurityLevel,
    int? userAuthenticationValidityDurationSeconds,
    String? preferencesKeyPrefix,
    String? storageNamespace,
    String? biometricPromptTitle,
    String? biometricPromptSubtitle,
    String? biometricPromptNegativeButton,
  }) => AndroidOptions(
    resetOnError: resetOnError ?? this.resetOnError,
    enforceBiometrics: enforceBiometrics ?? this.enforceBiometrics,
    biometricType: biometricType ?? this.biometricType,
    requireBiometricConfirmation:
        requireBiometricConfirmation ?? this.requireBiometricConfirmation,
    storageSecurityLevel: storageSecurityLevel ?? this.storageSecurityLevel,
    userAuthenticationValidityDurationSeconds:
        userAuthenticationValidityDurationSeconds ??
        this.userAuthenticationValidityDurationSeconds,
    preferencesKeyPrefix: preferencesKeyPrefix ?? this.preferencesKeyPrefix,
    storageNamespace: storageNamespace ?? this.storageNamespace,
    biometricPromptTitle: biometricPromptTitle ?? this.biometricPromptTitle,
    biometricPromptSubtitle:
        biometricPromptSubtitle ?? this.biometricPromptSubtitle,
    biometricPromptNegativeButton:
        biometricPromptNegativeButton ?? this.biometricPromptNegativeButton,
  );
}
