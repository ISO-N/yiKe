/// 文件用途：应用路由配置（GoRouter），包含底部导航与 Modal 路由。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/utils/responsive_utils.dart';
import '../../presentation/pages/home/home_page.dart';
import '../../presentation/pages/calendar/calendar_page.dart';
import '../../presentation/pages/help/help_page.dart';
import '../../presentation/pages/input/input_page.dart';
import '../../presentation/pages/input/import_preview_page.dart';
import '../../presentation/pages/input/templates_page.dart';
import '../../presentation/pages/pomodoro/pomodoro_page.dart';
import '../../presentation/pages/debug/mock_data_generator_page.dart';
import '../../presentation/pages/statistics/statistics_page.dart';
import '../../presentation/pages/learning_item/learning_item_detail_page.dart';
import '../../presentation/pages/tasks/task_hub_page.dart';
import '../../presentation/pages/tasks/task_detail_sheet.dart';
import '../../presentation/pages/settings/export_page.dart';
import '../../presentation/pages/settings/backup_page.dart';
import '../../presentation/pages/settings/goal_settings_page.dart';
import '../../presentation/pages/settings/pomodoro_settings_page.dart';
import '../../presentation/pages/settings/theme_settings_page.dart';
import '../../presentation/pages/settings/settings_page.dart';
import '../../presentation/pages/settings/sync_settings_page.dart';
import '../../presentation/pages/topics/topic_detail_page.dart';
import '../../presentation/pages/topics/topics_page.dart';
import '../../presentation/pages/shell/shell_scaffold.dart';

/// App 路由 Provider。
final appRouterProvider = Provider<GoRouter>((ref) {
  return createAppRouter();
});

/// 创建应用路由实例。
///
/// 说明：
/// - 生产环境默认从 `/home` 启动
/// - 测试环境可传入自定义 [initialLocation]，以便复用真实路由定义验证深链与分支行为
GoRouter createAppRouter({String initialLocation = '/home'}) {
  return GoRouter(
    initialLocation: initialLocation,
    routes: [
      ShellRoute(
        builder: (context, state, child) {
          return ShellScaffold(child: child);
        },
        routes: [
          GoRoute(
            path: '/home',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: HomePage()),
          ),
          GoRoute(
            path: '/calendar',
            // 兼容旧深链：/calendar?openStats=1 过去用于打开统计 Sheet。
            // 现在统计为独立 Tab，因此统一重定向到 /statistics。
            redirect: (context, state) {
              if (state.uri.queryParameters['openStats'] == '1') {
                return '/statistics';
              }
              return null;
            },
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: CalendarPage()),
          ),
          GoRoute(
            path: '/settings',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: SettingsPage()),
          ),
          GoRoute(
            path: '/pomodoro',
            pageBuilder: (context, state) =>
                const NoTransitionPage(child: PomodoroPage()),
          ),
        ],
      ),
      // 帮助页：真实全屏页面，不在 Shell 内（不显示底部导航栏）。
      GoRoute(
        path: '/help',
        pageBuilder: (context, state) => const MaterialPage(child: HelpPage()),
      ),
      GoRoute(
        path: '/statistics',
        pageBuilder: (context, state) =>
            const MaterialPage(child: StatisticsPage()),
      ),

      // 旧路由兼容：保留 `/tasks` 作为独立任务中心入口，不再回落到首页“全部任务”视图。
      GoRoute(
        path: '/tasks',
        pageBuilder: (context, state) =>
            const MaterialPage(child: TaskHubPage()),
      ),
      GoRoute(path: '/settings/help', redirect: (context, state) => '/help'),
      GoRoute(
        path: '/tasks/detail/:learningItemId',
        pageBuilder: (context, state) {
          final id = int.tryParse(state.pathParameters['learningItemId'] ?? '');
          if (id == null) {
            return const MaterialPage(child: TaskHubPage());
          }
          // 说明：允许从上下文菜单直接进入“编辑基本信息”。
          // - edit=1：打开详情页后自动弹出编辑 Sheet
          // - 默认不弹出（避免影响正常查看详情流程）
          final openEdit = state.uri.queryParameters['edit'] == '1';
          return _bottomSheetPageIfDesktop(
            context,
            TaskDetailSheet(learningItemId: id, openEditOnLoad: openEdit),
            fallback: MaterialPage(
              child: TaskDetailSheet(
                learningItemId: id,
                openEditOnLoad: openEdit,
              ),
            ),
            dialogSize: const Size(760, 780),
            heightFactor: 0.88,
          );
        },
      ),
      GoRoute(
        path: '/input',
        pageBuilder: (context, state) {
          return _dialogPageIfDesktop(
            context,
            const InputPage(),
            fallback: const MaterialPage(
              fullscreenDialog: true,
              child: InputPage(),
            ),
          );
        },
      ),
      GoRoute(
        path: '/input/import',
        pageBuilder: (context, state) {
          return _dialogPageIfDesktop(
            context,
            const ImportPreviewPage(),
            fallback: const MaterialPage(
              fullscreenDialog: true,
              child: ImportPreviewPage(),
            ),
          );
        },
      ),
      GoRoute(
        path: '/input/templates',
        pageBuilder: (context, state) {
          return _dialogPageIfDesktop(
            context,
            const TemplatesPage(),
            fallback: const MaterialPage(
              fullscreenDialog: true,
              child: TemplatesPage(),
            ),
          );
        },
      ),
      GoRoute(
        path: '/items/:id',
        pageBuilder: (context, state) {
          final id = int.tryParse(state.pathParameters['id'] ?? '');
          if (id == null) {
            return const MaterialPage(child: HomePage());
          }
          return _dialogPageIfDesktop(
            context,
            LearningItemDetailPage(itemId: id),
            fallback: MaterialPage(child: LearningItemDetailPage(itemId: id)),
            dialogSize: const Size(720, 720),
          );
        },
      ),
      GoRoute(
        path: '/settings/export',
        pageBuilder: (context, state) {
          return const MaterialPage(
            fullscreenDialog: true,
            child: ExportPage(),
          );
        },
      ),
      GoRoute(
        path: '/settings/goals',
        pageBuilder: (context, state) {
          return const MaterialPage(child: GoalSettingsPage());
        },
      ),
      GoRoute(
        path: '/settings/theme',
        pageBuilder: (context, state) {
          return const MaterialPage(child: ThemeSettingsPage());
        },
      ),
      GoRoute(
        path: '/settings/pomodoro',
        pageBuilder: (context, state) {
          return const MaterialPage(child: PomodoroSettingsPage());
        },
      ),
      GoRoute(
        path: '/settings/backup',
        pageBuilder: (context, state) {
          return const MaterialPage(child: BackupPage());
        },
      ),
      GoRoute(
        path: '/settings/debug/mock-data',
        pageBuilder: (context, state) {
          return _dialogPageIfDesktop(
            context,
            const MockDataGeneratorPage(),
            fallback: const MaterialPage(
              fullscreenDialog: true,
              child: MockDataGeneratorPage(),
            ),
            dialogSize: const Size(680, 760),
          );
        },
      ),
      GoRoute(
        path: '/settings/sync',
        pageBuilder: (context, state) {
          return const MaterialPage(child: SyncSettingsPage());
        },
      ),
      GoRoute(
        path: '/topics',
        pageBuilder: (context, state) {
          return const MaterialPage(child: TopicsPage());
        },
      ),
      GoRoute(
        path: '/topics/:id',
        pageBuilder: (context, state) {
          final id = int.tryParse(state.pathParameters['id'] ?? '');
          if (id == null) {
            return const MaterialPage(child: TopicsPage());
          }
          return MaterialPage(child: TopicDetailPage(topicId: id));
        },
      ),
    ],
  );
}

Page<dynamic> _dialogPageIfDesktop(
  BuildContext context,
  Widget child, {
  required Page<dynamic> fallback,
  Size dialogSize = const Size(600, 500),
}) {
  final isDesktop =
      MediaQuery.of(context).size.width >= ResponsiveBreakpoints.desktop;
  if (!isDesktop) return fallback;

  return CustomTransitionPage(
    opaque: false,
    barrierDismissible: true,
    barrierColor: Colors.black54,
    transitionDuration: const Duration(milliseconds: 200),
    child: Center(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: dialogSize.width,
          maxHeight: dialogSize.height,
          minWidth: 360,
          minHeight: 360,
        ),
        child: Material(
          borderRadius: BorderRadius.circular(16),
          clipBehavior: Clip.antiAlias,
          child: child,
        ),
      ),
    ),
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      final fade = CurvedAnimation(parent: animation, curve: Curves.easeOut);
      final scale = Tween<double>(begin: 0.98, end: 1.0).animate(fade);
      return FadeTransition(
        opacity: fade,
        child: ScaleTransition(scale: scale, child: child),
      );
    },
  );
}

Page<dynamic> _bottomSheetPageIfDesktop(
  BuildContext context,
  Widget child, {
  required Page<dynamic> fallback,
  Size dialogSize = const Size(600, 500),
  double heightFactor = 0.86,
}) {
  final isDesktop =
      MediaQuery.of(context).size.width >= ResponsiveBreakpoints.desktop;
  if (isDesktop) {
    return _dialogPageIfDesktop(
      context,
      child,
      fallback: fallback,
      dialogSize: dialogSize,
    );
  }

  return CustomTransitionPage(
    opaque: false,
    barrierDismissible: true,
    barrierColor: Colors.black54,
    transitionDuration: const Duration(milliseconds: 220),
    child: Align(
      alignment: Alignment.bottomCenter,
      child: FractionallySizedBox(
        heightFactor: heightFactor,
        widthFactor: 1,
        child: Material(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
          clipBehavior: Clip.antiAlias,
          child: child,
        ),
      ),
    ),
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      final curve = CurvedAnimation(parent: animation, curve: Curves.easeOut);
      final offset = Tween<Offset>(
        begin: const Offset(0, 0.08),
        end: Offset.zero,
      ).animate(curve);
      return FadeTransition(
        opacity: curve,
        child: SlideTransition(position: offset, child: child),
      );
    },
  );
}
