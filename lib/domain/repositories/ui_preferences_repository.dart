/// 文件用途：仓储接口 - UI 本地偏好（仅本机，不参与同步）。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

/// UI 本地偏好仓储。
///
/// 设计说明：
/// - 与 AppSettingsEntity（通知/免打扰等“业务设置”）区分开，避免领域实体膨胀
/// - 该仓储的设置项默认为“按设备本地保存”，不通过 F12 同步传播
abstract class UiPreferencesRepository {
  /// 读取“任务列表毛玻璃效果”开关。
  ///
  /// 返回值：是否启用毛玻璃（默认 true）。
  /// 异常：读取失败时应返回默认值（由实现兜底）。
  Future<bool> getTaskListBlurEnabled();

  /// 保存“任务列表毛玻璃效果”开关。
  ///
  /// 参数：
  /// - [enabled] 是否启用。
  /// 返回值：Future（无返回值）。
  /// 异常：写入失败时可能抛出异常。
  Future<void> setTaskListBlurEnabled(bool enabled);
}

