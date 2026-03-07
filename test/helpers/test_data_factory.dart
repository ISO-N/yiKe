// 文件用途：测试数据工厂，快速构建学习内容、复习任务、主题与设置相关测试数据。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';

class TestDataFactory {
  const TestDataFactory._();

  static const Uuid _uuid = Uuid();

  /// 创建一个最小可用的学习内容及其默认复习任务。
  static Future<int> createLearningItemWithPlan(
    ProviderContainer container, {
    String title = '测试学习内容',
    String? description,
    List<String> tags = const ['测试'],
    DateTime? learningDate,
  }) async {
    final useCase = container.read(createLearningItemUseCaseProvider);
    final result = await useCase.execute(
      CreateLearningItemParams(
        title: title,
        description: description,
        tags: tags,
        learningDate: learningDate ?? DateTime(2026, 3, 6),
      ),
    );
    return result.item.id!;
  }

  /// 创建一个仅用于断言的稳定 UUID。
  static String nextUuid() => _uuid.v4();
}
