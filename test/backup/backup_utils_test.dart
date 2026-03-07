// 文件用途：单元测试 - 备份/恢复工具（规范化 JSON 与 checksum）。
// 作者：Codex
// 创建日期：2026-02-28

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/backup_utils.dart';

void main() {
  test('canonicalizeDataJson 应对 key 排序与按 uuid 排序数组元素', () async {
    final data = <String, dynamic>{
      'settings': {'b': 1, 'a': 2},
      'reviewTasks': [
        {'uuid': 'b', 'x': 1},
        {'x': 2, 'uuid': 'a'},
      ],
      'learningItems': [
        {'uuid': '2', 'title': 't'},
        {'title': 't', 'uuid': '1'},
      ],
    };

    final canonical = BackupUtils.canonicalizeDataJson(data);
    expect(
      canonical,
      '{"learningItems":[{"title":"t","uuid":"1"},{"title":"t","uuid":"2"}],'
      '"reviewTasks":[{"uuid":"a","x":2},{"uuid":"b","x":1}],'
      '"settings":{"a":2,"b":1}}',
    );

    final c1 = await BackupUtils.sha256Hex(canonical);
    final c2 = await BackupUtils.sha256Hex(canonical);
    expect(c1, startsWith('sha256:'));
    expect(c1, c2);
  });

  test('formatLocalIsoWithOffset、checksum 结果与取消令牌行为保持稳定', () async {
    final localIso = BackupUtils.formatLocalIsoWithOffset(
      DateTime(2026, 3, 7, 9, 8, 7),
    );
    expect(localIso, contains('2026-03-07T09:08:07'));
    expect(RegExp(r'[+-]\d{2}:\d{2}$').hasMatch(localIso), isTrue);

    final result = await BackupUtils.computeChecksumForDataInIsolate(
      <String, dynamic>{
        'reviewTasks': <Map<String, dynamic>>[
          <String, dynamic>{'uuid': 'b', 'status': 'pending'},
          <String, dynamic>{'uuid': 'a', 'status': 'done'},
        ],
        'settings': <String, dynamic>{'theme': 'dark'},
      },
    );
    expect(result.canonicalJson, contains('"uuid":"a"'));
    expect(result.canonicalJson, contains('"uuid":"b"'));
    expect(result.checksum, startsWith('sha256:'));
    expect(
      result.payloadSize,
      BackupUtils.utf8BytesLength(result.canonicalJson),
    );

    final token = BackupCancelToken();
    expect(token.isCanceled, isFalse);
    token.cancel();
    expect(token.isCanceled, isTrue);
    expect(() => token.throwIfCanceled(), throwsA(isA<BackupCanceledException>()));
  });
}
