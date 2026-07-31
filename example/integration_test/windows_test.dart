import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _storage = FlutterSecureStorage();

Future<void> _exerciseCrud({
  required String key,
  required String value,
}) async {
  await _storage.write(key: key, value: value);
  expect(await _storage.read(key: key), value);
  expect(await _storage.containsKey(key: key), isTrue);
  expect(await _storage.readAll(), containsPair(key, value));

  await _storage.delete(key: key);
  expect(await _storage.read(key: key), isNull);
  expect(await _storage.containsKey(key: key), isFalse);

  await _storage.write(key: key, value: value);
  await _storage.deleteAll();
  expect(await _storage.readAll(), isEmpty);
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUp(_storage.deleteAll);
  tearDown(_storage.deleteAll);

  group(
    'Windows DPAPI storage',
    () {
      testWidgets('basic CRUD', (_) async {
        await _exerciseCrud(key: 'key', value: 'value');
      });

      for (final entry in <String, String>{
        'URL': 'https://example.com/a?b=c',
        'path traversal text': r'..\..\not-a-path',
        'quotes and separators': r'''"<>|:*?/\''',
        'control character': '\u0009',
        'Unicode': 'clé-キー-🔑',
        'empty': '',
      }.entries) {
        testWidgets('round trips ${entry.key}', (_) async {
          await _exerciseCrud(key: entry.value, value: entry.value);
        });
      }

      testWidgets('preserves case-sensitive keys', (_) async {
        await _storage.write(key: 'key', value: 'lower');
        await _storage.write(key: 'KEY', value: 'upper');

        expect(await _storage.read(key: 'key'), 'lower');
        expect(await _storage.read(key: 'KEY'), 'upper');
      });
    },
    skip: kIsWeb || !Platform.isWindows ? 'Windows only' : null,
  );
}
