// 文件用途：NoteMigrationParser 单元测试，覆盖空文本、列表、段落与前导空行等迁移规则。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/note_migration_parser.dart';

void main() {
  group('NoteMigrationParser', () {
    test('空文本返回空描述与空子任务', () {
      final result = NoteMigrationParser.parse('  \n  ');
      expect(result.description, isNull);
      expect(result.subtasks, isEmpty);
    });

    test('列表文本会提取为子任务', () {
      final result = NoteMigrationParser.parse(
        '- 第一条\n2. 第二条\n③ 第三条\n• 第四条',
      );
      expect(result.description, isNull);
      expect(result.subtasks, <String>['第一条', '第二条', '第三条', '第四条']);
    });

    test('单行文本迁移到描述', () {
      final result = NoteMigrationParser.parse('只保留为描述');
      expect(result.description, '只保留为描述');
      expect(result.subtasks, isEmpty);
    });

    test('多行且无列表时第一段为描述，其余为子任务', () {
      final result = NoteMigrationParser.parse(
        '第一段说明\n第二行补充\n\n子任务一\n子任务二',
      );
      expect(result.description, '第一段说明\n第二行补充');
      expect(result.subtasks, <String>['子任务一', '子任务二']);
    });

    test('前导空行时会回退到首个非空行作为描述', () {
      final result = NoteMigrationParser.parse('\n\n首行描述\n第二行');
      expect(result.description, '首行描述');
      expect(result.subtasks, <String>['第二行']);
    });
  });
}
