// 文件用途：SpeechInputField Widget 测试，覆盖初始化失败、识别成功、权限引导与异常提示分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:permission_handler_platform_interface/permission_handler_platform_interface.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/infrastructure/speech/speech_service.dart';
import 'package:yike/presentation/widgets/speech_input_field.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeSpeechService fakeSpeechService;
  late _FakePermissionHandlerPlatform fakePermissionPlatform;
  late PermissionHandlerPlatform previousPermissionPlatform;

  Future<TextEditingController> pumpField(WidgetTester tester) async {
    final controller = TextEditingController(text: '原始文本');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          speechServiceProvider.overrideWithValue(fakeSpeechService),
        ],
        child: MaterialApp(
          home: Scaffold(
            body: Padding(
              padding: const EdgeInsets.all(24),
              child: SpeechInputField(
                controller: controller,
                labelText: '描述',
                hintText: '请输入内容',
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    return controller;
  }

  setUp(() {
    fakeSpeechService = _FakeSpeechService();
    fakePermissionPlatform = _FakePermissionHandlerPlatform();
    previousPermissionPlatform = PermissionHandlerPlatform.instance;
    PermissionHandlerPlatform.instance = fakePermissionPlatform;
  });

  tearDown(() {
    PermissionHandlerPlatform.instance = previousPermissionPlatform;
  });

  group('SpeechInputField', () {
    testWidgets('初始化失败后会禁用语音按钮', (tester) async {
      fakeSpeechService.initializeResult = false;

      await pumpField(tester);

      final iconButton = tester.widget<IconButton>(find.byType(IconButton));
      expect(iconButton.onPressed, isNull);
    });

    testWidgets('识别成功后会展示实时结果并允许停止录音', (tester) async {
      fakeSpeechService.emittedText = '新的识别结果';
      final controller = await pumpField(tester);

      await tester.tap(find.byTooltip('语音输入'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('录音中...'), findsOneWidget);
      expect(find.text('新的识别结果'), findsWidgets);

      await tester.tap(find.text('停止'));
      await tester.pumpAndSettle();

      expect(fakeSpeechService.stopCalled, isTrue);
      expect(controller.text, '新的识别结果');
      expect(find.text('录音中...'), findsNothing);
    });

    testWidgets('麦克风权限异常时会恢复原文并支持前往系统设置', (tester) async {
      fakeSpeechService.startError = StateError('语音识别需要麦克风权限');
      final controller = await pumpField(tester);

      await tester.tap(find.byTooltip('语音输入'));
      await tester.pumpAndSettle();

      expect(find.text('需要麦克风权限'), findsOneWidget);
      expect(controller.text, '原始文本');

      await tester.tap(find.text('去设置'));
      await tester.pumpAndSettle();

      expect(fakePermissionPlatform.openSettingsCalled, isTrue);
    });

    testWidgets('识别异常时会恢复原文并弹出失败提示', (tester) async {
      fakeSpeechService.startError = StateError('服务异常');
      final controller = await pumpField(tester);

      await tester.tap(find.byTooltip('语音输入'));
      await tester.pumpAndSettle();

      expect(controller.text, '原始文本');
      expect(find.textContaining('语音识别失败'), findsOneWidget);
    });
  });
}

class _FakeSpeechService extends SpeechService {
  bool initializeResult = true;
  Object? startError;
  String? emittedText;
  bool stopCalled = false;

  @override
  Future<bool> initialize() async {
    return initializeResult;
  }

  @override
  Future<void> startListening({
    required void Function(String text) onResult,
    String? localeId,
    bool partialResults = true,
  }) async {
    final error = startError;
    if (error != null) {
      throw error;
    }
    final text = emittedText;
    if (text != null) {
      onResult(text);
    }
  }

  @override
  Future<void> stop() async {
    stopCalled = true;
  }
}

class _FakePermissionHandlerPlatform extends PermissionHandlerPlatform {
  bool openSettingsCalled = false;

  @override
  Future<PermissionStatus> checkPermissionStatus(Permission permission) async {
    return PermissionStatus.denied;
  }

  @override
  Future<ServiceStatus> checkServiceStatus(Permission permission) async {
    return ServiceStatus.disabled;
  }

  @override
  Future<bool> openAppSettings() async {
    openSettingsCalled = true;
    return true;
  }

  @override
  Future<Map<Permission, PermissionStatus>> requestPermissions(
    List<Permission> permissions,
  ) async {
    return <Permission, PermissionStatus>{
      for (final permission in permissions) permission: PermissionStatus.denied,
    };
  }

  @override
  Future<bool> shouldShowRequestPermissionRationale(
    Permission permission,
  ) async {
    return false;
  }
}
