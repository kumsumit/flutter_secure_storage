part of '../flutter_secure_storage.dart';

/// Specific options for Windows platform.
class WindowsOptions extends Options {
  /// Creates options for the DPAPI-backed Windows storage implementation.
  const WindowsOptions();

  /// A predefined `WindowsOptions` instance with default settings.
  ///
  /// This can be used as a fallback or when no specific options are required.
  static const WindowsOptions defaultOptions = WindowsOptions();

  @override
  Map<String, String> toMap() => const <String, String>{};

  /// Creates a copy of these options.
  WindowsOptions copyWith() => WindowsOptions.defaultOptions;
}
