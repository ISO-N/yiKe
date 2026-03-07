// 文件用途：NotificationDedupStore 测试，覆盖 24 小时去重、异常回退与连续打卡里程碑状态持久化。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/infrastructure/notification/notification_dedup_store.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late NotificationDedupStore store;

  setUp(() {
    db = createInMemoryDatabase();
    store = NotificationDedupStore(
      dao: SettingsDao(db),
      secureStorageService: SecureStorageService(),
    );
  });

  tearDown(() async {
    await db.close();
  });

  group('NotificationDedupStore', () {
    test('shouldSend 会按 TTL 做通知去重', () async {
      final now = DateTime(2026, 3, 7, 10);

      expect(await store.shouldSend('goal', now: now), isTrue);

      await store.setLastSent('goal', now);
      expect(
        await store.shouldSend('goal', now: now.add(const Duration(hours: 12))),
        isFalse,
      );
      expect(
        await store.shouldSend('goal', now: now.add(const Duration(hours: 25))),
        isTrue,
      );
    });

    test('损坏的加密内容会回退为可发送状态', () async {
      await SettingsDao(db).upsertValue('notification_last_sent:streak', '坏数据');

      expect(await store.getLastSent('streak'), isNull);
      expect(await store.shouldSend('streak'), isTrue);
    });

    test('会持久化并读取连续打卡里程碑状态', () async {
      final sentAt = DateTime(2026, 3, 7, 9, 30);
      await store.setStreakMilestoneState(milestone: 30, sentAt: sentAt);

      final state = await store.getStreakMilestoneState();
      expect(state.milestone, 30);
      expect(state.sentAt, sentAt);
    });
  });
}
