// 文件用途：覆盖率统计与阶段门禁脚本，输出整体/分层覆盖率并可按阶段强制失败。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';
import 'dart:io';

void main(List<String> args) {
  final options = _CliOptions.parse(args);
  final config = CoverageConfig.load(options.configPath);
  final report = CoverageReport.parse(
    lcovPath: options.lcovPath,
    config: config,
  );

  stdout.writeln('覆盖率统计口径');
  stdout.writeln('- 统计根目录: ${config.trackedRoots.join(', ')}');
  stdout.writeln('- 排除规则: ${config.exclusions.map((e) => e.glob).join(', ')}');
  stdout.writeln('');

  stdout.writeln('覆盖率结果');
  stdout.writeln(
    '- 总体: ${report.overall.coverageString} (${report.overall.hit}/${report.overall.found})',
  );
  for (final layer in config.layers.keys) {
    final summary = report.layerSummaries[layer] ?? const CoverageSummary();
    stdout.writeln(
      '- $layer: ${summary.coverageString} (${summary.hit}/${summary.found})',
    );
  }

  if (options.phase == null) {
    return;
  }

  final phaseName = options.phase!;
  final phaseThreshold = config.phases[phaseName];
  if (phaseThreshold == null) {
    stderr.writeln('未知阶段：$phaseName');
    exitCode = 2;
    return;
  }

  stdout.writeln('');
  stdout.writeln('阶段门禁');
  stdout.writeln('- 当前阶段: $phaseName');
  stdout.writeln('- 总体门禁: ${phaseThreshold.overall.toStringAsFixed(2)}%');

  final failures = <String>[];
  if (report.overall.coverage + _epsilon < phaseThreshold.overall) {
    failures.add(
      '总体覆盖率 ${report.overall.coverageString} 低于 ${phaseThreshold.overall.toStringAsFixed(2)}%',
    );
  }

  for (final entry in phaseThreshold.layers.entries) {
    final summary = report.layerSummaries[entry.key] ?? const CoverageSummary();
    if (summary.coverage + _epsilon < entry.value) {
      failures.add(
        '${entry.key} 覆盖率 ${summary.coverageString} 低于 ${entry.value.toStringAsFixed(2)}%',
      );
    }
  }

  if (failures.isEmpty) {
    stdout.writeln('- 结果: 通过');
    return;
  }

  stdout.writeln('- 结果: 未通过');
  for (final failure in failures) {
    stdout.writeln('  - $failure');
  }

  if (options.enforce) {
    exitCode = 1;
  }
}

const double _epsilon = 0.0001;

class _CliOptions {
  const _CliOptions({
    required this.lcovPath,
    required this.configPath,
    required this.phase,
    required this.enforce,
  });

  final String lcovPath;
  final String configPath;
  final String? phase;
  final bool enforce;

  static _CliOptions parse(List<String> args) {
    var lcovPath = 'coverage/lcov.info';
    var configPath = 'tool/coverage_config.json';
    String? phase;
    var enforce = false;

    for (var index = 0; index < args.length; index++) {
      final arg = args[index];
      switch (arg) {
        case '--lcov':
          lcovPath = args[++index];
          break;
        case '--config':
          configPath = args[++index];
          break;
        case '--phase':
          phase = args[++index];
          break;
        case '--enforce':
          enforce = true;
          break;
        default:
          stderr.writeln('未知参数：$arg');
          exitCode = 64;
          throw const FormatException('invalid arguments');
      }
    }

    return _CliOptions(
      lcovPath: lcovPath,
      configPath: configPath,
      phase: phase,
      enforce: enforce,
    );
  }
}

class CoverageConfig {
  const CoverageConfig({
    required this.trackedRoots,
    required this.layers,
    required this.exclusions,
    required this.phases,
  });

  final List<String> trackedRoots;
  final Map<String, String> layers;
  final List<CoverageExclusion> exclusions;
  final Map<String, CoverageThreshold> phases;

  static CoverageConfig load(String path) {
    final file = File(path);
    if (!file.existsSync()) {
      throw FileSystemException('覆盖率配置不存在', path);
    }

    final root =
        jsonDecode(file.readAsStringSync(encoding: utf8))
            as Map<String, dynamic>;
    final trackedRoots = (root['trackedRoots'] as List<dynamic>)
        .cast<String>()
        .toList(growable: false);
    final layers = (root['layers'] as Map<String, dynamic>).map(
      (key, value) => MapEntry(key, value as String),
    );
    final exclusions = (root['exclusions'] as List<dynamic>)
        .map(
          (entry) => CoverageExclusion.fromJson(entry as Map<String, dynamic>),
        )
        .toList(growable: false);
    final phases = (root['phases'] as Map<String, dynamic>).map(
      (key, value) => MapEntry(
        key,
        CoverageThreshold.fromJson(value as Map<String, dynamic>),
      ),
    );

    return CoverageConfig(
      trackedRoots: trackedRoots,
      layers: layers,
      exclusions: exclusions,
      phases: phases,
    );
  }
}

class CoverageExclusion {
  const CoverageExclusion({required this.glob, required this.reason});

  factory CoverageExclusion.fromJson(Map<String, dynamic> json) {
    return CoverageExclusion(
      glob: json['glob'] as String,
      reason: json['reason'] as String,
    );
  }

  final String glob;
  final String reason;

  RegExp get pattern => _globToRegExp(glob);
}

class CoverageThreshold {
  const CoverageThreshold({required this.overall, required this.layers});

  factory CoverageThreshold.fromJson(Map<String, dynamic> json) {
    return CoverageThreshold(
      overall: (json['overall'] as num).toDouble(),
      layers: (json['layers'] as Map<String, dynamic>).map(
        (key, value) => MapEntry(key, (value as num).toDouble()),
      ),
    );
  }

  final double overall;
  final Map<String, double> layers;
}

class CoverageReport {
  const CoverageReport({required this.overall, required this.layerSummaries});

  final CoverageSummary overall;
  final Map<String, CoverageSummary> layerSummaries;

  static CoverageReport parse({
    required String lcovPath,
    required CoverageConfig config,
  }) {
    final file = File(lcovPath);
    if (!file.existsSync()) {
      throw FileSystemException('lcov 报告不存在', lcovPath);
    }

    final summaries = <String, CoverageSummaryBuilder>{};
    String? currentFile;

    for (final line in file.readAsLinesSync(encoding: utf8)) {
      if (line.startsWith('SF:')) {
        currentFile = _normalizePath(line.substring(3));
        summaries.putIfAbsent(currentFile, CoverageSummaryBuilder.new);
      } else if (line.startsWith('LF:') && currentFile != null) {
        summaries[currentFile]!.found = int.parse(line.substring(3));
      } else if (line.startsWith('LH:') && currentFile != null) {
        summaries[currentFile]!.hit = int.parse(line.substring(3));
      }
    }

    final trackedSummaries = summaries.entries.where((entry) {
      final filePath = entry.key;
      final inRoot = config.trackedRoots.any(filePath.startsWith);
      if (!inRoot) return false;
      return !config.exclusions.any((rule) => rule.pattern.hasMatch(filePath));
    });

    final overallBuilder = CoverageSummaryBuilder();
    final layerBuilders = {
      for (final layer in config.layers.keys) layer: CoverageSummaryBuilder(),
    };

    for (final entry in trackedSummaries) {
      final filePath = entry.key;
      final builder = entry.value;
      overallBuilder.add(builder);
      final layer = _resolveLayer(filePath, config.layers);
      if (layer != null) {
        layerBuilders[layer]!.add(builder);
      }
    }

    return CoverageReport(
      overall: overallBuilder.build(),
      layerSummaries: {
        for (final entry in layerBuilders.entries)
          entry.key: entry.value.build(),
      },
    );
  }
}

class CoverageSummary {
  const CoverageSummary({this.found = 0, this.hit = 0});

  final int found;
  final int hit;

  double get coverage => found == 0 ? 0 : (hit / found) * 100;

  String get coverageString => '${coverage.toStringAsFixed(2)}%';
}

class CoverageSummaryBuilder {
  int found = 0;
  int hit = 0;

  void add(CoverageSummaryBuilder other) {
    found += other.found;
    hit += other.hit;
  }

  CoverageSummary build() => CoverageSummary(found: found, hit: hit);
}

String _normalizePath(String rawPath) => rawPath.replaceAll('\\', '/');

String? _resolveLayer(String filePath, Map<String, String> layers) {
  for (final entry in layers.entries) {
    if (filePath.startsWith(entry.value)) {
      return entry.key;
    }
  }
  return null;
}

RegExp _globToRegExp(String glob) {
  final buffer = StringBuffer('^');
  for (var index = 0; index < glob.length; index++) {
    final char = glob[index];
    if (char == '*') {
      final nextIsStar = index + 1 < glob.length && glob[index + 1] == '*';
      if (nextIsStar) {
        buffer.write('.*');
        index++;
      } else {
        buffer.write('[^/]*');
      }
      continue;
    }
    if (char == '?') {
      buffer.write('.');
      continue;
    }
    if (r'\.+()|[]{}^$'.contains(char)) {
      buffer.write(r'\');
    }
    buffer.write(char);
  }
  buffer.write(r'$');
  return RegExp(buffer.toString());
}
