// 文件用途：测试用 Fake 服务与受控时钟，供 Provider / 服务集成测试复用。
// 作者：Codex
// 创建日期：2026-03-06

class MutableTestClock {
  MutableTestClock(this._now);

  DateTime _now;

  DateTime now() => _now;

  void set(DateTime next) {
    _now = next;
  }

  void advance(Duration duration) {
    _now = _now.add(duration);
  }
}

class RecordingAsyncSink<T> {
  final List<T> values = <T>[];

  Future<void> call(T value) async {
    values.add(value);
  }
}
