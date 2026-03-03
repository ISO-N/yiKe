// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'database.dart';

// ignore_for_file: type=lint
class $LearningItemsTable extends LearningItems
    with TableInfo<$LearningItemsTable, LearningItem> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LearningItemsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 50,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _tagsMeta = const VerificationMeta('tags');
  @override
  late final GeneratedColumn<String> tags = GeneratedColumn<String>(
    'tags',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('[]'),
  );
  static const VerificationMeta _learningDateMeta = const VerificationMeta(
    'learningDate',
  );
  @override
  late final GeneratedColumn<DateTime> learningDate = GeneratedColumn<DateTime>(
    'learning_date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isDeletedMeta = const VerificationMeta(
    'isDeleted',
  );
  @override
  late final GeneratedColumn<bool> isDeleted = GeneratedColumn<bool>(
    'is_deleted',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_deleted" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  @override
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isMockDataMeta = const VerificationMeta(
    'isMockData',
  );
  @override
  late final GeneratedColumn<bool> isMockData = GeneratedColumn<bool>(
    'is_mock_data',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_mock_data" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    title,
    note,
    description,
    tags,
    learningDate,
    createdAt,
    updatedAt,
    isDeleted,
    deletedAt,
    isMockData,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'learning_items';
  @override
  VerificationContext validateIntegrity(
    Insertable<LearningItem> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('tags')) {
      context.handle(
        _tagsMeta,
        tags.isAcceptableOrUnknown(data['tags']!, _tagsMeta),
      );
    }
    if (data.containsKey('learning_date')) {
      context.handle(
        _learningDateMeta,
        learningDate.isAcceptableOrUnknown(
          data['learning_date']!,
          _learningDateMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_learningDateMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('is_deleted')) {
      context.handle(
        _isDeletedMeta,
        isDeleted.isAcceptableOrUnknown(data['is_deleted']!, _isDeletedMeta),
      );
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    }
    if (data.containsKey('is_mock_data')) {
      context.handle(
        _isMockDataMeta,
        isMockData.isAcceptableOrUnknown(
          data['is_mock_data']!,
          _isMockDataMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LearningItem map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LearningItem(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      ),
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      ),
      tags: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tags'],
      )!,
      learningDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}learning_date'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
      isDeleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_deleted'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      ),
      isMockData: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_mock_data'],
      )!,
    );
  }

  @override
  $LearningItemsTable createAlias(String alias) {
    return $LearningItemsTable(attachedDatabase, alias);
  }
}

class LearningItem extends DataClass implements Insertable<LearningItem> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份/恢复的“合并去重”与外键修复（uuid → id 映射）
  /// - 迁移时会通过 SQL 为历史库补齐该列并回填为真实 UUID，再建立唯一索引
  final String uuid;

  /// 学习内容标题（必填，≤50字）。
  final String title;

  /// 备注内容（可选，v1.0 MVP 仅纯文本）。
  final String? note;

  /// 描述内容（可选，v2.6：替代 note 的结构化入口）。
  ///
  /// 说明：
  /// - 本次变更采用渐进式迁移：保留 note 字段不删除
  /// - 迁移完成后：旧 note 会被迁移到 description 与 learning_subtasks，并置空 note
  final String? description;

  /// 标签列表（JSON 字符串，如 ["Java","英语"]）。
  final String tags;

  /// 学习日期（首次录入日期，用于生成复习计划）。
  final DateTime learningDate;

  /// 创建时间。
  final DateTime createdAt;

  /// 更新时间（可空）。
  final DateTime? updatedAt;

  /// 是否已停用（软删除标记）。
  ///
  /// 说明：
  /// - Drift 字段名：isDeleted
  /// - 数据库列名：is_deleted
  /// - 仅用于“停用学习内容”，查询列表需默认过滤 is_deleted=0
  final bool isDeleted;

  /// 停用时间（Unix epoch 毫秒；与 createdAt/updatedAt 保持一致）。
  ///
  /// 说明：
  /// - Drift 字段名：deletedAt
  /// - 数据库列名：deleted_at
  final DateTime? deletedAt;

  /// 是否为模拟数据（v3.1：用于 Debug 模式生成/清理、同步/导出隔离）。
  final bool isMockData;
  const LearningItem({
    required this.id,
    required this.uuid,
    required this.title,
    this.note,
    this.description,
    required this.tags,
    required this.learningDate,
    required this.createdAt,
    this.updatedAt,
    required this.isDeleted,
    this.deletedAt,
    required this.isMockData,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['title'] = Variable<String>(title);
    if (!nullToAbsent || note != null) {
      map['note'] = Variable<String>(note);
    }
    if (!nullToAbsent || description != null) {
      map['description'] = Variable<String>(description);
    }
    map['tags'] = Variable<String>(tags);
    map['learning_date'] = Variable<DateTime>(learningDate);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    map['is_deleted'] = Variable<bool>(isDeleted);
    if (!nullToAbsent || deletedAt != null) {
      map['deleted_at'] = Variable<DateTime>(deletedAt);
    }
    map['is_mock_data'] = Variable<bool>(isMockData);
    return map;
  }

  LearningItemsCompanion toCompanion(bool nullToAbsent) {
    return LearningItemsCompanion(
      id: Value(id),
      uuid: Value(uuid),
      title: Value(title),
      note: note == null && nullToAbsent ? const Value.absent() : Value(note),
      description: description == null && nullToAbsent
          ? const Value.absent()
          : Value(description),
      tags: Value(tags),
      learningDate: Value(learningDate),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
      isDeleted: Value(isDeleted),
      deletedAt: deletedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(deletedAt),
      isMockData: Value(isMockData),
    );
  }

  factory LearningItem.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LearningItem(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      title: serializer.fromJson<String>(json['title']),
      note: serializer.fromJson<String?>(json['note']),
      description: serializer.fromJson<String?>(json['description']),
      tags: serializer.fromJson<String>(json['tags']),
      learningDate: serializer.fromJson<DateTime>(json['learningDate']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
      isDeleted: serializer.fromJson<bool>(json['isDeleted']),
      deletedAt: serializer.fromJson<DateTime?>(json['deletedAt']),
      isMockData: serializer.fromJson<bool>(json['isMockData']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'title': serializer.toJson<String>(title),
      'note': serializer.toJson<String?>(note),
      'description': serializer.toJson<String?>(description),
      'tags': serializer.toJson<String>(tags),
      'learningDate': serializer.toJson<DateTime>(learningDate),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
      'isDeleted': serializer.toJson<bool>(isDeleted),
      'deletedAt': serializer.toJson<DateTime?>(deletedAt),
      'isMockData': serializer.toJson<bool>(isMockData),
    };
  }

  LearningItem copyWith({
    int? id,
    String? uuid,
    String? title,
    Value<String?> note = const Value.absent(),
    Value<String?> description = const Value.absent(),
    String? tags,
    DateTime? learningDate,
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
    bool? isDeleted,
    Value<DateTime?> deletedAt = const Value.absent(),
    bool? isMockData,
  }) => LearningItem(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    title: title ?? this.title,
    note: note.present ? note.value : this.note,
    description: description.present ? description.value : this.description,
    tags: tags ?? this.tags,
    learningDate: learningDate ?? this.learningDate,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
    isDeleted: isDeleted ?? this.isDeleted,
    deletedAt: deletedAt.present ? deletedAt.value : this.deletedAt,
    isMockData: isMockData ?? this.isMockData,
  );
  LearningItem copyWithCompanion(LearningItemsCompanion data) {
    return LearningItem(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      title: data.title.present ? data.title.value : this.title,
      note: data.note.present ? data.note.value : this.note,
      description: data.description.present
          ? data.description.value
          : this.description,
      tags: data.tags.present ? data.tags.value : this.tags,
      learningDate: data.learningDate.present
          ? data.learningDate.value
          : this.learningDate,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      isDeleted: data.isDeleted.present ? data.isDeleted.value : this.isDeleted,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
      isMockData: data.isMockData.present
          ? data.isMockData.value
          : this.isMockData,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LearningItem(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('title: $title, ')
          ..write('note: $note, ')
          ..write('description: $description, ')
          ..write('tags: $tags, ')
          ..write('learningDate: $learningDate, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    uuid,
    title,
    note,
    description,
    tags,
    learningDate,
    createdAt,
    updatedAt,
    isDeleted,
    deletedAt,
    isMockData,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LearningItem &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.title == this.title &&
          other.note == this.note &&
          other.description == this.description &&
          other.tags == this.tags &&
          other.learningDate == this.learningDate &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.isDeleted == this.isDeleted &&
          other.deletedAt == this.deletedAt &&
          other.isMockData == this.isMockData);
}

class LearningItemsCompanion extends UpdateCompanion<LearningItem> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<String> title;
  final Value<String?> note;
  final Value<String?> description;
  final Value<String> tags;
  final Value<DateTime> learningDate;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  final Value<bool> isDeleted;
  final Value<DateTime?> deletedAt;
  final Value<bool> isMockData;
  const LearningItemsCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.title = const Value.absent(),
    this.note = const Value.absent(),
    this.description = const Value.absent(),
    this.tags = const Value.absent(),
    this.learningDate = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  });
  LearningItemsCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required String title,
    this.note = const Value.absent(),
    this.description = const Value.absent(),
    this.tags = const Value.absent(),
    required DateTime learningDate,
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  }) : title = Value(title),
       learningDate = Value(learningDate);
  static Insertable<LearningItem> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<String>? title,
    Expression<String>? note,
    Expression<String>? description,
    Expression<String>? tags,
    Expression<DateTime>? learningDate,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<bool>? isDeleted,
    Expression<DateTime>? deletedAt,
    Expression<bool>? isMockData,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (title != null) 'title': title,
      if (note != null) 'note': note,
      if (description != null) 'description': description,
      if (tags != null) 'tags': tags,
      if (learningDate != null) 'learning_date': learningDate,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (isDeleted != null) 'is_deleted': isDeleted,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (isMockData != null) 'is_mock_data': isMockData,
    });
  }

  LearningItemsCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<String>? title,
    Value<String?>? note,
    Value<String?>? description,
    Value<String>? tags,
    Value<DateTime>? learningDate,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
    Value<bool>? isDeleted,
    Value<DateTime?>? deletedAt,
    Value<bool>? isMockData,
  }) {
    return LearningItemsCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      title: title ?? this.title,
      note: note ?? this.note,
      description: description ?? this.description,
      tags: tags ?? this.tags,
      learningDate: learningDate ?? this.learningDate,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      isDeleted: isDeleted ?? this.isDeleted,
      deletedAt: deletedAt ?? this.deletedAt,
      isMockData: isMockData ?? this.isMockData,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (tags.present) {
      map['tags'] = Variable<String>(tags.value);
    }
    if (learningDate.present) {
      map['learning_date'] = Variable<DateTime>(learningDate.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (isDeleted.present) {
      map['is_deleted'] = Variable<bool>(isDeleted.value);
    }
    if (deletedAt.present) {
      map['deleted_at'] = Variable<DateTime>(deletedAt.value);
    }
    if (isMockData.present) {
      map['is_mock_data'] = Variable<bool>(isMockData.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LearningItemsCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('title: $title, ')
          ..write('note: $note, ')
          ..write('description: $description, ')
          ..write('tags: $tags, ')
          ..write('learningDate: $learningDate, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }
}

class $LearningSubtasksTable extends LearningSubtasks
    with TableInfo<$LearningSubtasksTable, LearningSubtask> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LearningSubtasksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _learningItemIdMeta = const VerificationMeta(
    'learningItemId',
  );
  @override
  late final GeneratedColumn<int> learningItemId = GeneratedColumn<int>(
    'learning_item_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES learning_items (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _contentMeta = const VerificationMeta(
    'content',
  );
  @override
  late final GeneratedColumn<String> content = GeneratedColumn<String>(
    'content',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 500,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _sortOrderMeta = const VerificationMeta(
    'sortOrder',
  );
  @override
  late final GeneratedColumn<int> sortOrder = GeneratedColumn<int>(
    'sort_order',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isMockDataMeta = const VerificationMeta(
    'isMockData',
  );
  @override
  late final GeneratedColumn<bool> isMockData = GeneratedColumn<bool>(
    'is_mock_data',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_mock_data" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    learningItemId,
    content,
    sortOrder,
    createdAt,
    updatedAt,
    isMockData,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'learning_subtasks';
  @override
  VerificationContext validateIntegrity(
    Insertable<LearningSubtask> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('learning_item_id')) {
      context.handle(
        _learningItemIdMeta,
        learningItemId.isAcceptableOrUnknown(
          data['learning_item_id']!,
          _learningItemIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_learningItemIdMeta);
    }
    if (data.containsKey('content')) {
      context.handle(
        _contentMeta,
        content.isAcceptableOrUnknown(data['content']!, _contentMeta),
      );
    } else if (isInserting) {
      context.missing(_contentMeta);
    }
    if (data.containsKey('sort_order')) {
      context.handle(
        _sortOrderMeta,
        sortOrder.isAcceptableOrUnknown(data['sort_order']!, _sortOrderMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('is_mock_data')) {
      context.handle(
        _isMockDataMeta,
        isMockData.isAcceptableOrUnknown(
          data['is_mock_data']!,
          _isMockDataMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {uuid},
  ];
  @override
  LearningSubtask map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LearningSubtask(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      learningItemId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}learning_item_id'],
      )!,
      content: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content'],
      )!,
      sortOrder: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sort_order'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
      isMockData: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_mock_data'],
      )!,
    );
  }

  @override
  $LearningSubtasksTable createAlias(String alias) {
    return $LearningSubtasksTable(attachedDatabase, alias);
  }
}

class LearningSubtask extends DataClass implements Insertable<LearningSubtask> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份合并去重、跨设备映射的稳定 key
  /// - 插入时自动生成，避免空字符串触发唯一约束冲突
  final String uuid;

  /// 外键：关联的学习内容 ID（删除学习内容时级联删除）。
  final int learningItemId;

  /// 子任务内容（必填）。
  final String content;

  /// 排序顺序（同一 learningItemId 内从 0 开始递增）。
  final int sortOrder;

  /// 创建时间。
  final DateTime createdAt;

  /// 更新时间（可空）。
  final DateTime? updatedAt;

  /// 是否为模拟数据（用于 Debug 生成/清理、同步/导出隔离）。
  final bool isMockData;
  const LearningSubtask({
    required this.id,
    required this.uuid,
    required this.learningItemId,
    required this.content,
    required this.sortOrder,
    required this.createdAt,
    this.updatedAt,
    required this.isMockData,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['learning_item_id'] = Variable<int>(learningItemId);
    map['content'] = Variable<String>(content);
    map['sort_order'] = Variable<int>(sortOrder);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    map['is_mock_data'] = Variable<bool>(isMockData);
    return map;
  }

  LearningSubtasksCompanion toCompanion(bool nullToAbsent) {
    return LearningSubtasksCompanion(
      id: Value(id),
      uuid: Value(uuid),
      learningItemId: Value(learningItemId),
      content: Value(content),
      sortOrder: Value(sortOrder),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
      isMockData: Value(isMockData),
    );
  }

  factory LearningSubtask.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LearningSubtask(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      learningItemId: serializer.fromJson<int>(json['learningItemId']),
      content: serializer.fromJson<String>(json['content']),
      sortOrder: serializer.fromJson<int>(json['sortOrder']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
      isMockData: serializer.fromJson<bool>(json['isMockData']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'learningItemId': serializer.toJson<int>(learningItemId),
      'content': serializer.toJson<String>(content),
      'sortOrder': serializer.toJson<int>(sortOrder),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
      'isMockData': serializer.toJson<bool>(isMockData),
    };
  }

  LearningSubtask copyWith({
    int? id,
    String? uuid,
    int? learningItemId,
    String? content,
    int? sortOrder,
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
    bool? isMockData,
  }) => LearningSubtask(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    learningItemId: learningItemId ?? this.learningItemId,
    content: content ?? this.content,
    sortOrder: sortOrder ?? this.sortOrder,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
    isMockData: isMockData ?? this.isMockData,
  );
  LearningSubtask copyWithCompanion(LearningSubtasksCompanion data) {
    return LearningSubtask(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      learningItemId: data.learningItemId.present
          ? data.learningItemId.value
          : this.learningItemId,
      content: data.content.present ? data.content.value : this.content,
      sortOrder: data.sortOrder.present ? data.sortOrder.value : this.sortOrder,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      isMockData: data.isMockData.present
          ? data.isMockData.value
          : this.isMockData,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LearningSubtask(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('content: $content, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    uuid,
    learningItemId,
    content,
    sortOrder,
    createdAt,
    updatedAt,
    isMockData,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LearningSubtask &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.learningItemId == this.learningItemId &&
          other.content == this.content &&
          other.sortOrder == this.sortOrder &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.isMockData == this.isMockData);
}

class LearningSubtasksCompanion extends UpdateCompanion<LearningSubtask> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<int> learningItemId;
  final Value<String> content;
  final Value<int> sortOrder;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  final Value<bool> isMockData;
  const LearningSubtasksCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.learningItemId = const Value.absent(),
    this.content = const Value.absent(),
    this.sortOrder = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  });
  LearningSubtasksCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required int learningItemId,
    required String content,
    this.sortOrder = const Value.absent(),
    required DateTime createdAt,
    this.updatedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  }) : learningItemId = Value(learningItemId),
       content = Value(content),
       createdAt = Value(createdAt);
  static Insertable<LearningSubtask> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<int>? learningItemId,
    Expression<String>? content,
    Expression<int>? sortOrder,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<bool>? isMockData,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (learningItemId != null) 'learning_item_id': learningItemId,
      if (content != null) 'content': content,
      if (sortOrder != null) 'sort_order': sortOrder,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (isMockData != null) 'is_mock_data': isMockData,
    });
  }

  LearningSubtasksCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<int>? learningItemId,
    Value<String>? content,
    Value<int>? sortOrder,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
    Value<bool>? isMockData,
  }) {
    return LearningSubtasksCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      learningItemId: learningItemId ?? this.learningItemId,
      content: content ?? this.content,
      sortOrder: sortOrder ?? this.sortOrder,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      isMockData: isMockData ?? this.isMockData,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (learningItemId.present) {
      map['learning_item_id'] = Variable<int>(learningItemId.value);
    }
    if (content.present) {
      map['content'] = Variable<String>(content.value);
    }
    if (sortOrder.present) {
      map['sort_order'] = Variable<int>(sortOrder.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (isMockData.present) {
      map['is_mock_data'] = Variable<bool>(isMockData.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LearningSubtasksCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('content: $content, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }
}

class $ReviewTasksTable extends ReviewTasks
    with TableInfo<$ReviewTasksTable, ReviewTask> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ReviewTasksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _learningItemIdMeta = const VerificationMeta(
    'learningItemId',
  );
  @override
  late final GeneratedColumn<int> learningItemId = GeneratedColumn<int>(
    'learning_item_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES learning_items (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _reviewRoundMeta = const VerificationMeta(
    'reviewRound',
  );
  @override
  late final GeneratedColumn<int> reviewRound = GeneratedColumn<int>(
    'review_round',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _scheduledDateMeta = const VerificationMeta(
    'scheduledDate',
  );
  @override
  late final GeneratedColumn<DateTime> scheduledDate =
      GeneratedColumn<DateTime>(
        'scheduled_date',
        aliasedName,
        false,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _occurredAtMeta = const VerificationMeta(
    'occurredAt',
  );
  @override
  late final GeneratedColumn<DateTime> occurredAt = GeneratedColumn<DateTime>(
    'occurred_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('pending'),
  );
  static const VerificationMeta _completedAtMeta = const VerificationMeta(
    'completedAt',
  );
  @override
  late final GeneratedColumn<DateTime> completedAt = GeneratedColumn<DateTime>(
    'completed_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _skippedAtMeta = const VerificationMeta(
    'skippedAt',
  );
  @override
  late final GeneratedColumn<DateTime> skippedAt = GeneratedColumn<DateTime>(
    'skipped_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isMockDataMeta = const VerificationMeta(
    'isMockData',
  );
  @override
  late final GeneratedColumn<bool> isMockData = GeneratedColumn<bool>(
    'is_mock_data',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_mock_data" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    learningItemId,
    reviewRound,
    scheduledDate,
    occurredAt,
    status,
    completedAt,
    skippedAt,
    createdAt,
    updatedAt,
    isMockData,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'review_tasks';
  @override
  VerificationContext validateIntegrity(
    Insertable<ReviewTask> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('learning_item_id')) {
      context.handle(
        _learningItemIdMeta,
        learningItemId.isAcceptableOrUnknown(
          data['learning_item_id']!,
          _learningItemIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_learningItemIdMeta);
    }
    if (data.containsKey('review_round')) {
      context.handle(
        _reviewRoundMeta,
        reviewRound.isAcceptableOrUnknown(
          data['review_round']!,
          _reviewRoundMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_reviewRoundMeta);
    }
    if (data.containsKey('scheduled_date')) {
      context.handle(
        _scheduledDateMeta,
        scheduledDate.isAcceptableOrUnknown(
          data['scheduled_date']!,
          _scheduledDateMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_scheduledDateMeta);
    }
    if (data.containsKey('occurred_at')) {
      context.handle(
        _occurredAtMeta,
        occurredAt.isAcceptableOrUnknown(data['occurred_at']!, _occurredAtMeta),
      );
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('completed_at')) {
      context.handle(
        _completedAtMeta,
        completedAt.isAcceptableOrUnknown(
          data['completed_at']!,
          _completedAtMeta,
        ),
      );
    }
    if (data.containsKey('skipped_at')) {
      context.handle(
        _skippedAtMeta,
        skippedAt.isAcceptableOrUnknown(data['skipped_at']!, _skippedAtMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    if (data.containsKey('is_mock_data')) {
      context.handle(
        _isMockDataMeta,
        isMockData.isAcceptableOrUnknown(
          data['is_mock_data']!,
          _isMockDataMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ReviewTask map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ReviewTask(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      learningItemId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}learning_item_id'],
      )!,
      reviewRound: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}review_round'],
      )!,
      scheduledDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}scheduled_date'],
      )!,
      occurredAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}occurred_at'],
      ),
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      completedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}completed_at'],
      ),
      skippedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}skipped_at'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
      isMockData: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_mock_data'],
      )!,
    );
  }

  @override
  $ReviewTasksTable createAlias(String alias) {
    return $ReviewTasksTable(attachedDatabase, alias);
  }
}

class ReviewTask extends DataClass implements Insertable<ReviewTask> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份/恢复的“合并去重”与外键修复（uuid → id 映射）
  /// - 迁移时会通过 SQL 为历史库补齐该列并回填为真实 UUID，再建立唯一索引
  final String uuid;

  /// 外键：关联的学习内容 ID（删除学习内容时级联删除）。
  final int learningItemId;

  /// 复习轮次（1-10）。
  ///
  /// 说明：数据库层不再使用 CHECK 约束限制范围，最大轮次由应用层控制。
  final int reviewRound;

  /// 计划复习日期。
  final DateTime scheduledDate;

  /// 任务发生时间（用于任务中心时间线排序与游标分页）。
  ///
  /// 口径（与 spec-performance-optimization.md 一致）：
  /// - pending：occurredAt = scheduledDate
  /// - done：occurredAt = completedAt ?? scheduledDate
  /// - skipped：occurredAt = skippedAt ?? scheduledDate
  ///
  /// 说明：
  /// - 该列为性能优化新增列（v10），用于避免分页查询中反复计算 CASE/COALESCE 导致索引失效
  /// - 迁移会回填历史数据；应用层在所有写入口维护该列，尽量保证非空
  final DateTime? occurredAt;

  /// 任务状态：pending(待复习)/done(已完成)/skipped(已跳过)。
  final String status;

  /// 完成时间（完成后记录）。
  final DateTime? completedAt;

  /// 跳过时间（跳过后记录）。
  final DateTime? skippedAt;

  /// 创建时间。
  final DateTime createdAt;

  /// 更新时间（用于同步冲突解决，v3.0 新增）。
  final DateTime? updatedAt;

  /// 是否为模拟数据（v3.1：用于 Debug 模式生成/清理、同步/导出隔离）。
  final bool isMockData;
  const ReviewTask({
    required this.id,
    required this.uuid,
    required this.learningItemId,
    required this.reviewRound,
    required this.scheduledDate,
    this.occurredAt,
    required this.status,
    this.completedAt,
    this.skippedAt,
    required this.createdAt,
    this.updatedAt,
    required this.isMockData,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['learning_item_id'] = Variable<int>(learningItemId);
    map['review_round'] = Variable<int>(reviewRound);
    map['scheduled_date'] = Variable<DateTime>(scheduledDate);
    if (!nullToAbsent || occurredAt != null) {
      map['occurred_at'] = Variable<DateTime>(occurredAt);
    }
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || completedAt != null) {
      map['completed_at'] = Variable<DateTime>(completedAt);
    }
    if (!nullToAbsent || skippedAt != null) {
      map['skipped_at'] = Variable<DateTime>(skippedAt);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    map['is_mock_data'] = Variable<bool>(isMockData);
    return map;
  }

  ReviewTasksCompanion toCompanion(bool nullToAbsent) {
    return ReviewTasksCompanion(
      id: Value(id),
      uuid: Value(uuid),
      learningItemId: Value(learningItemId),
      reviewRound: Value(reviewRound),
      scheduledDate: Value(scheduledDate),
      occurredAt: occurredAt == null && nullToAbsent
          ? const Value.absent()
          : Value(occurredAt),
      status: Value(status),
      completedAt: completedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(completedAt),
      skippedAt: skippedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(skippedAt),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
      isMockData: Value(isMockData),
    );
  }

  factory ReviewTask.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ReviewTask(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      learningItemId: serializer.fromJson<int>(json['learningItemId']),
      reviewRound: serializer.fromJson<int>(json['reviewRound']),
      scheduledDate: serializer.fromJson<DateTime>(json['scheduledDate']),
      occurredAt: serializer.fromJson<DateTime?>(json['occurredAt']),
      status: serializer.fromJson<String>(json['status']),
      completedAt: serializer.fromJson<DateTime?>(json['completedAt']),
      skippedAt: serializer.fromJson<DateTime?>(json['skippedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
      isMockData: serializer.fromJson<bool>(json['isMockData']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'learningItemId': serializer.toJson<int>(learningItemId),
      'reviewRound': serializer.toJson<int>(reviewRound),
      'scheduledDate': serializer.toJson<DateTime>(scheduledDate),
      'occurredAt': serializer.toJson<DateTime?>(occurredAt),
      'status': serializer.toJson<String>(status),
      'completedAt': serializer.toJson<DateTime?>(completedAt),
      'skippedAt': serializer.toJson<DateTime?>(skippedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
      'isMockData': serializer.toJson<bool>(isMockData),
    };
  }

  ReviewTask copyWith({
    int? id,
    String? uuid,
    int? learningItemId,
    int? reviewRound,
    DateTime? scheduledDate,
    Value<DateTime?> occurredAt = const Value.absent(),
    String? status,
    Value<DateTime?> completedAt = const Value.absent(),
    Value<DateTime?> skippedAt = const Value.absent(),
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
    bool? isMockData,
  }) => ReviewTask(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    learningItemId: learningItemId ?? this.learningItemId,
    reviewRound: reviewRound ?? this.reviewRound,
    scheduledDate: scheduledDate ?? this.scheduledDate,
    occurredAt: occurredAt.present ? occurredAt.value : this.occurredAt,
    status: status ?? this.status,
    completedAt: completedAt.present ? completedAt.value : this.completedAt,
    skippedAt: skippedAt.present ? skippedAt.value : this.skippedAt,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
    isMockData: isMockData ?? this.isMockData,
  );
  ReviewTask copyWithCompanion(ReviewTasksCompanion data) {
    return ReviewTask(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      learningItemId: data.learningItemId.present
          ? data.learningItemId.value
          : this.learningItemId,
      reviewRound: data.reviewRound.present
          ? data.reviewRound.value
          : this.reviewRound,
      scheduledDate: data.scheduledDate.present
          ? data.scheduledDate.value
          : this.scheduledDate,
      occurredAt: data.occurredAt.present
          ? data.occurredAt.value
          : this.occurredAt,
      status: data.status.present ? data.status.value : this.status,
      completedAt: data.completedAt.present
          ? data.completedAt.value
          : this.completedAt,
      skippedAt: data.skippedAt.present ? data.skippedAt.value : this.skippedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      isMockData: data.isMockData.present
          ? data.isMockData.value
          : this.isMockData,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ReviewTask(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('reviewRound: $reviewRound, ')
          ..write('scheduledDate: $scheduledDate, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('status: $status, ')
          ..write('completedAt: $completedAt, ')
          ..write('skippedAt: $skippedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    uuid,
    learningItemId,
    reviewRound,
    scheduledDate,
    occurredAt,
    status,
    completedAt,
    skippedAt,
    createdAt,
    updatedAt,
    isMockData,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ReviewTask &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.learningItemId == this.learningItemId &&
          other.reviewRound == this.reviewRound &&
          other.scheduledDate == this.scheduledDate &&
          other.occurredAt == this.occurredAt &&
          other.status == this.status &&
          other.completedAt == this.completedAt &&
          other.skippedAt == this.skippedAt &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.isMockData == this.isMockData);
}

class ReviewTasksCompanion extends UpdateCompanion<ReviewTask> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<int> learningItemId;
  final Value<int> reviewRound;
  final Value<DateTime> scheduledDate;
  final Value<DateTime?> occurredAt;
  final Value<String> status;
  final Value<DateTime?> completedAt;
  final Value<DateTime?> skippedAt;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  final Value<bool> isMockData;
  const ReviewTasksCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.learningItemId = const Value.absent(),
    this.reviewRound = const Value.absent(),
    this.scheduledDate = const Value.absent(),
    this.occurredAt = const Value.absent(),
    this.status = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.skippedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  });
  ReviewTasksCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
    this.occurredAt = const Value.absent(),
    this.status = const Value.absent(),
    this.completedAt = const Value.absent(),
    this.skippedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.isMockData = const Value.absent(),
  }) : learningItemId = Value(learningItemId),
       reviewRound = Value(reviewRound),
       scheduledDate = Value(scheduledDate);
  static Insertable<ReviewTask> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<int>? learningItemId,
    Expression<int>? reviewRound,
    Expression<DateTime>? scheduledDate,
    Expression<DateTime>? occurredAt,
    Expression<String>? status,
    Expression<DateTime>? completedAt,
    Expression<DateTime>? skippedAt,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<bool>? isMockData,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (learningItemId != null) 'learning_item_id': learningItemId,
      if (reviewRound != null) 'review_round': reviewRound,
      if (scheduledDate != null) 'scheduled_date': scheduledDate,
      if (occurredAt != null) 'occurred_at': occurredAt,
      if (status != null) 'status': status,
      if (completedAt != null) 'completed_at': completedAt,
      if (skippedAt != null) 'skipped_at': skippedAt,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (isMockData != null) 'is_mock_data': isMockData,
    });
  }

  ReviewTasksCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<int>? learningItemId,
    Value<int>? reviewRound,
    Value<DateTime>? scheduledDate,
    Value<DateTime?>? occurredAt,
    Value<String>? status,
    Value<DateTime?>? completedAt,
    Value<DateTime?>? skippedAt,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
    Value<bool>? isMockData,
  }) {
    return ReviewTasksCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      learningItemId: learningItemId ?? this.learningItemId,
      reviewRound: reviewRound ?? this.reviewRound,
      scheduledDate: scheduledDate ?? this.scheduledDate,
      occurredAt: occurredAt ?? this.occurredAt,
      status: status ?? this.status,
      completedAt: completedAt ?? this.completedAt,
      skippedAt: skippedAt ?? this.skippedAt,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      isMockData: isMockData ?? this.isMockData,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (learningItemId.present) {
      map['learning_item_id'] = Variable<int>(learningItemId.value);
    }
    if (reviewRound.present) {
      map['review_round'] = Variable<int>(reviewRound.value);
    }
    if (scheduledDate.present) {
      map['scheduled_date'] = Variable<DateTime>(scheduledDate.value);
    }
    if (occurredAt.present) {
      map['occurred_at'] = Variable<DateTime>(occurredAt.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (completedAt.present) {
      map['completed_at'] = Variable<DateTime>(completedAt.value);
    }
    if (skippedAt.present) {
      map['skipped_at'] = Variable<DateTime>(skippedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (isMockData.present) {
      map['is_mock_data'] = Variable<bool>(isMockData.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ReviewTasksCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('reviewRound: $reviewRound, ')
          ..write('scheduledDate: $scheduledDate, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('status: $status, ')
          ..write('completedAt: $completedAt, ')
          ..write('skippedAt: $skippedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('isMockData: $isMockData')
          ..write(')'))
        .toString();
  }
}

class $ReviewRecordsTable extends ReviewRecords
    with TableInfo<$ReviewRecordsTable, ReviewRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ReviewRecordsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _reviewTaskIdMeta = const VerificationMeta(
    'reviewTaskId',
  );
  @override
  late final GeneratedColumn<int> reviewTaskId = GeneratedColumn<int>(
    'review_task_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES review_tasks (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _actionMeta = const VerificationMeta('action');
  @override
  late final GeneratedColumn<String> action = GeneratedColumn<String>(
    'action',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 20,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _occurredAtMeta = const VerificationMeta(
    'occurredAt',
  );
  @override
  late final GeneratedColumn<DateTime> occurredAt = GeneratedColumn<DateTime>(
    'occurred_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    reviewTaskId,
    action,
    occurredAt,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'review_records';
  @override
  VerificationContext validateIntegrity(
    Insertable<ReviewRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('review_task_id')) {
      context.handle(
        _reviewTaskIdMeta,
        reviewTaskId.isAcceptableOrUnknown(
          data['review_task_id']!,
          _reviewTaskIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_reviewTaskIdMeta);
    }
    if (data.containsKey('action')) {
      context.handle(
        _actionMeta,
        action.isAcceptableOrUnknown(data['action']!, _actionMeta),
      );
    } else if (isInserting) {
      context.missing(_actionMeta);
    }
    if (data.containsKey('occurred_at')) {
      context.handle(
        _occurredAtMeta,
        occurredAt.isAcceptableOrUnknown(data['occurred_at']!, _occurredAtMeta),
      );
    } else if (isInserting) {
      context.missing(_occurredAtMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  ReviewRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ReviewRecord(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      reviewTaskId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}review_task_id'],
      )!,
      action: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}action'],
      )!,
      occurredAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}occurred_at'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $ReviewRecordsTable createAlias(String alias) {
    return $ReviewRecordsTable(attachedDatabase, alias);
  }
}

class ReviewRecord extends DataClass implements Insertable<ReviewRecord> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：用于备份/恢复合并去重（records 以 uuid 作为不可变事件标识）。
  final String uuid;

  /// 外键：关联复习任务 ID（删除任务时级联删除记录）。
  final int reviewTaskId;

  /// 行为类型。
  ///
  /// 取值建议：
  /// - 'done'：完成
  /// - 'skipped'：跳过
  /// - 'undo'：撤销（done/skipped → pending）
  final String action;

  /// 行为发生时间（用于时间线/统计口径）。
  final DateTime occurredAt;

  /// 创建时间（写入数据库时间）。
  final DateTime createdAt;
  const ReviewRecord({
    required this.id,
    required this.uuid,
    required this.reviewTaskId,
    required this.action,
    required this.occurredAt,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['review_task_id'] = Variable<int>(reviewTaskId);
    map['action'] = Variable<String>(action);
    map['occurred_at'] = Variable<DateTime>(occurredAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  ReviewRecordsCompanion toCompanion(bool nullToAbsent) {
    return ReviewRecordsCompanion(
      id: Value(id),
      uuid: Value(uuid),
      reviewTaskId: Value(reviewTaskId),
      action: Value(action),
      occurredAt: Value(occurredAt),
      createdAt: Value(createdAt),
    );
  }

  factory ReviewRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ReviewRecord(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      reviewTaskId: serializer.fromJson<int>(json['reviewTaskId']),
      action: serializer.fromJson<String>(json['action']),
      occurredAt: serializer.fromJson<DateTime>(json['occurredAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'reviewTaskId': serializer.toJson<int>(reviewTaskId),
      'action': serializer.toJson<String>(action),
      'occurredAt': serializer.toJson<DateTime>(occurredAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  ReviewRecord copyWith({
    int? id,
    String? uuid,
    int? reviewTaskId,
    String? action,
    DateTime? occurredAt,
    DateTime? createdAt,
  }) => ReviewRecord(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    reviewTaskId: reviewTaskId ?? this.reviewTaskId,
    action: action ?? this.action,
    occurredAt: occurredAt ?? this.occurredAt,
    createdAt: createdAt ?? this.createdAt,
  );
  ReviewRecord copyWithCompanion(ReviewRecordsCompanion data) {
    return ReviewRecord(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      reviewTaskId: data.reviewTaskId.present
          ? data.reviewTaskId.value
          : this.reviewTaskId,
      action: data.action.present ? data.action.value : this.action,
      occurredAt: data.occurredAt.present
          ? data.occurredAt.value
          : this.occurredAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ReviewRecord(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('reviewTaskId: $reviewTaskId, ')
          ..write('action: $action, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, uuid, reviewTaskId, action, occurredAt, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ReviewRecord &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.reviewTaskId == this.reviewTaskId &&
          other.action == this.action &&
          other.occurredAt == this.occurredAt &&
          other.createdAt == this.createdAt);
}

class ReviewRecordsCompanion extends UpdateCompanion<ReviewRecord> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<int> reviewTaskId;
  final Value<String> action;
  final Value<DateTime> occurredAt;
  final Value<DateTime> createdAt;
  const ReviewRecordsCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.reviewTaskId = const Value.absent(),
    this.action = const Value.absent(),
    this.occurredAt = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  ReviewRecordsCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required int reviewTaskId,
    required String action,
    required DateTime occurredAt,
    this.createdAt = const Value.absent(),
  }) : reviewTaskId = Value(reviewTaskId),
       action = Value(action),
       occurredAt = Value(occurredAt);
  static Insertable<ReviewRecord> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<int>? reviewTaskId,
    Expression<String>? action,
    Expression<DateTime>? occurredAt,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (reviewTaskId != null) 'review_task_id': reviewTaskId,
      if (action != null) 'action': action,
      if (occurredAt != null) 'occurred_at': occurredAt,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  ReviewRecordsCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<int>? reviewTaskId,
    Value<String>? action,
    Value<DateTime>? occurredAt,
    Value<DateTime>? createdAt,
  }) {
    return ReviewRecordsCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      reviewTaskId: reviewTaskId ?? this.reviewTaskId,
      action: action ?? this.action,
      occurredAt: occurredAt ?? this.occurredAt,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (reviewTaskId.present) {
      map['review_task_id'] = Variable<int>(reviewTaskId.value);
    }
    if (action.present) {
      map['action'] = Variable<String>(action.value);
    }
    if (occurredAt.present) {
      map['occurred_at'] = Variable<DateTime>(occurredAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ReviewRecordsCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('reviewTaskId: $reviewTaskId, ')
          ..write('action: $action, ')
          ..write('occurredAt: $occurredAt, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $AppSettingsTableTable extends AppSettingsTable
    with TableInfo<$AppSettingsTableTable, AppSettingsTableData> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AppSettingsTableTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _keyMeta = const VerificationMeta('key');
  @override
  late final GeneratedColumn<String> key = GeneratedColumn<String>(
    'key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _valueMeta = const VerificationMeta('value');
  @override
  late final GeneratedColumn<String> value = GeneratedColumn<String>(
    'value',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [id, key, value, updatedAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'app_settings_table';
  @override
  VerificationContext validateIntegrity(
    Insertable<AppSettingsTableData> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('key')) {
      context.handle(
        _keyMeta,
        key.isAcceptableOrUnknown(data['key']!, _keyMeta),
      );
    } else if (isInserting) {
      context.missing(_keyMeta);
    }
    if (data.containsKey('value')) {
      context.handle(
        _valueMeta,
        value.isAcceptableOrUnknown(data['value']!, _valueMeta),
      );
    } else if (isInserting) {
      context.missing(_valueMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  AppSettingsTableData map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AppSettingsTableData(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      key: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}key'],
      )!,
      value: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}value'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $AppSettingsTableTable createAlias(String alias) {
    return $AppSettingsTableTable(attachedDatabase, alias);
  }
}

class AppSettingsTableData extends DataClass
    implements Insertable<AppSettingsTableData> {
  /// 主键 ID。
  final int id;

  /// 设置键名（唯一）。
  final String key;

  /// 设置值（JSON 字符串）。
  final String value;

  /// 更新时间。
  final DateTime updatedAt;
  const AppSettingsTableData({
    required this.id,
    required this.key,
    required this.value,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['key'] = Variable<String>(key);
    map['value'] = Variable<String>(value);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  AppSettingsTableCompanion toCompanion(bool nullToAbsent) {
    return AppSettingsTableCompanion(
      id: Value(id),
      key: Value(key),
      value: Value(value),
      updatedAt: Value(updatedAt),
    );
  }

  factory AppSettingsTableData.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AppSettingsTableData(
      id: serializer.fromJson<int>(json['id']),
      key: serializer.fromJson<String>(json['key']),
      value: serializer.fromJson<String>(json['value']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'key': serializer.toJson<String>(key),
      'value': serializer.toJson<String>(value),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  AppSettingsTableData copyWith({
    int? id,
    String? key,
    String? value,
    DateTime? updatedAt,
  }) => AppSettingsTableData(
    id: id ?? this.id,
    key: key ?? this.key,
    value: value ?? this.value,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  AppSettingsTableData copyWithCompanion(AppSettingsTableCompanion data) {
    return AppSettingsTableData(
      id: data.id.present ? data.id.value : this.id,
      key: data.key.present ? data.key.value : this.key,
      value: data.value.present ? data.value.value : this.value,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsTableData(')
          ..write('id: $id, ')
          ..write('key: $key, ')
          ..write('value: $value, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, key, value, updatedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AppSettingsTableData &&
          other.id == this.id &&
          other.key == this.key &&
          other.value == this.value &&
          other.updatedAt == this.updatedAt);
}

class AppSettingsTableCompanion extends UpdateCompanion<AppSettingsTableData> {
  final Value<int> id;
  final Value<String> key;
  final Value<String> value;
  final Value<DateTime> updatedAt;
  const AppSettingsTableCompanion({
    this.id = const Value.absent(),
    this.key = const Value.absent(),
    this.value = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  AppSettingsTableCompanion.insert({
    this.id = const Value.absent(),
    required String key,
    required String value,
    this.updatedAt = const Value.absent(),
  }) : key = Value(key),
       value = Value(value);
  static Insertable<AppSettingsTableData> custom({
    Expression<int>? id,
    Expression<String>? key,
    Expression<String>? value,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (key != null) 'key': key,
      if (value != null) 'value': value,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  AppSettingsTableCompanion copyWith({
    Value<int>? id,
    Value<String>? key,
    Value<String>? value,
    Value<DateTime>? updatedAt,
  }) {
    return AppSettingsTableCompanion(
      id: id ?? this.id,
      key: key ?? this.key,
      value: value ?? this.value,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (key.present) {
      map['key'] = Variable<String>(key.value);
    }
    if (value.present) {
      map['value'] = Variable<String>(value.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsTableCompanion(')
          ..write('id: $id, ')
          ..write('key: $key, ')
          ..write('value: $value, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $LearningTemplatesTable extends LearningTemplates
    with TableInfo<$LearningTemplatesTable, LearningTemplate> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LearningTemplatesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 30,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titlePatternMeta = const VerificationMeta(
    'titlePattern',
  );
  @override
  late final GeneratedColumn<String> titlePattern = GeneratedColumn<String>(
    'title_pattern',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 50,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _notePatternMeta = const VerificationMeta(
    'notePattern',
  );
  @override
  late final GeneratedColumn<String> notePattern = GeneratedColumn<String>(
    'note_pattern',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _tagsMeta = const VerificationMeta('tags');
  @override
  late final GeneratedColumn<String> tags = GeneratedColumn<String>(
    'tags',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('[]'),
  );
  static const VerificationMeta _sortOrderMeta = const VerificationMeta(
    'sortOrder',
  );
  @override
  late final GeneratedColumn<int> sortOrder = GeneratedColumn<int>(
    'sort_order',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    name,
    titlePattern,
    notePattern,
    tags,
    sortOrder,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'learning_templates';
  @override
  VerificationContext validateIntegrity(
    Insertable<LearningTemplate> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('title_pattern')) {
      context.handle(
        _titlePatternMeta,
        titlePattern.isAcceptableOrUnknown(
          data['title_pattern']!,
          _titlePatternMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_titlePatternMeta);
    }
    if (data.containsKey('note_pattern')) {
      context.handle(
        _notePatternMeta,
        notePattern.isAcceptableOrUnknown(
          data['note_pattern']!,
          _notePatternMeta,
        ),
      );
    }
    if (data.containsKey('tags')) {
      context.handle(
        _tagsMeta,
        tags.isAcceptableOrUnknown(data['tags']!, _tagsMeta),
      );
    }
    if (data.containsKey('sort_order')) {
      context.handle(
        _sortOrderMeta,
        sortOrder.isAcceptableOrUnknown(data['sort_order']!, _sortOrderMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LearningTemplate map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LearningTemplate(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      titlePattern: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title_pattern'],
      )!,
      notePattern: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note_pattern'],
      ),
      tags: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tags'],
      )!,
      sortOrder: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}sort_order'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
    );
  }

  @override
  $LearningTemplatesTable createAlias(String alias) {
    return $LearningTemplatesTable(attachedDatabase, alias);
  }
}

class LearningTemplate extends DataClass
    implements Insertable<LearningTemplate> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份/恢复的合并去重（避免 id 冲突）
  /// - 迁移时会通过 SQL 为历史库补齐该列并回填为真实 UUID，再建立唯一索引
  final String uuid;

  /// 模板名称（用户可读，≤30）。
  final String name;

  /// 标题模板（必填，≤50）。
  final String titlePattern;

  /// 备注模板（可选）。
  final String? notePattern;

  /// 默认标签（JSON 字符串，如 ["英语","单词"]）。
  final String tags;

  /// 排序字段（越小越靠前）。
  final int sortOrder;

  /// 创建时间。
  final DateTime createdAt;

  /// 更新时间（可空）。
  final DateTime? updatedAt;
  const LearningTemplate({
    required this.id,
    required this.uuid,
    required this.name,
    required this.titlePattern,
    this.notePattern,
    required this.tags,
    required this.sortOrder,
    required this.createdAt,
    this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['name'] = Variable<String>(name);
    map['title_pattern'] = Variable<String>(titlePattern);
    if (!nullToAbsent || notePattern != null) {
      map['note_pattern'] = Variable<String>(notePattern);
    }
    map['tags'] = Variable<String>(tags);
    map['sort_order'] = Variable<int>(sortOrder);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    return map;
  }

  LearningTemplatesCompanion toCompanion(bool nullToAbsent) {
    return LearningTemplatesCompanion(
      id: Value(id),
      uuid: Value(uuid),
      name: Value(name),
      titlePattern: Value(titlePattern),
      notePattern: notePattern == null && nullToAbsent
          ? const Value.absent()
          : Value(notePattern),
      tags: Value(tags),
      sortOrder: Value(sortOrder),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
    );
  }

  factory LearningTemplate.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LearningTemplate(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      name: serializer.fromJson<String>(json['name']),
      titlePattern: serializer.fromJson<String>(json['titlePattern']),
      notePattern: serializer.fromJson<String?>(json['notePattern']),
      tags: serializer.fromJson<String>(json['tags']),
      sortOrder: serializer.fromJson<int>(json['sortOrder']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'name': serializer.toJson<String>(name),
      'titlePattern': serializer.toJson<String>(titlePattern),
      'notePattern': serializer.toJson<String?>(notePattern),
      'tags': serializer.toJson<String>(tags),
      'sortOrder': serializer.toJson<int>(sortOrder),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
    };
  }

  LearningTemplate copyWith({
    int? id,
    String? uuid,
    String? name,
    String? titlePattern,
    Value<String?> notePattern = const Value.absent(),
    String? tags,
    int? sortOrder,
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
  }) => LearningTemplate(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    name: name ?? this.name,
    titlePattern: titlePattern ?? this.titlePattern,
    notePattern: notePattern.present ? notePattern.value : this.notePattern,
    tags: tags ?? this.tags,
    sortOrder: sortOrder ?? this.sortOrder,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
  );
  LearningTemplate copyWithCompanion(LearningTemplatesCompanion data) {
    return LearningTemplate(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      name: data.name.present ? data.name.value : this.name,
      titlePattern: data.titlePattern.present
          ? data.titlePattern.value
          : this.titlePattern,
      notePattern: data.notePattern.present
          ? data.notePattern.value
          : this.notePattern,
      tags: data.tags.present ? data.tags.value : this.tags,
      sortOrder: data.sortOrder.present ? data.sortOrder.value : this.sortOrder,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LearningTemplate(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('name: $name, ')
          ..write('titlePattern: $titlePattern, ')
          ..write('notePattern: $notePattern, ')
          ..write('tags: $tags, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    uuid,
    name,
    titlePattern,
    notePattern,
    tags,
    sortOrder,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LearningTemplate &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.name == this.name &&
          other.titlePattern == this.titlePattern &&
          other.notePattern == this.notePattern &&
          other.tags == this.tags &&
          other.sortOrder == this.sortOrder &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class LearningTemplatesCompanion extends UpdateCompanion<LearningTemplate> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<String> name;
  final Value<String> titlePattern;
  final Value<String?> notePattern;
  final Value<String> tags;
  final Value<int> sortOrder;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  const LearningTemplatesCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.name = const Value.absent(),
    this.titlePattern = const Value.absent(),
    this.notePattern = const Value.absent(),
    this.tags = const Value.absent(),
    this.sortOrder = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  LearningTemplatesCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required String name,
    required String titlePattern,
    this.notePattern = const Value.absent(),
    this.tags = const Value.absent(),
    this.sortOrder = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  }) : name = Value(name),
       titlePattern = Value(titlePattern);
  static Insertable<LearningTemplate> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<String>? name,
    Expression<String>? titlePattern,
    Expression<String>? notePattern,
    Expression<String>? tags,
    Expression<int>? sortOrder,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (name != null) 'name': name,
      if (titlePattern != null) 'title_pattern': titlePattern,
      if (notePattern != null) 'note_pattern': notePattern,
      if (tags != null) 'tags': tags,
      if (sortOrder != null) 'sort_order': sortOrder,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  LearningTemplatesCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<String>? name,
    Value<String>? titlePattern,
    Value<String?>? notePattern,
    Value<String>? tags,
    Value<int>? sortOrder,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
  }) {
    return LearningTemplatesCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      name: name ?? this.name,
      titlePattern: titlePattern ?? this.titlePattern,
      notePattern: notePattern ?? this.notePattern,
      tags: tags ?? this.tags,
      sortOrder: sortOrder ?? this.sortOrder,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (titlePattern.present) {
      map['title_pattern'] = Variable<String>(titlePattern.value);
    }
    if (notePattern.present) {
      map['note_pattern'] = Variable<String>(notePattern.value);
    }
    if (tags.present) {
      map['tags'] = Variable<String>(tags.value);
    }
    if (sortOrder.present) {
      map['sort_order'] = Variable<int>(sortOrder.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LearningTemplatesCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('name: $name, ')
          ..write('titlePattern: $titlePattern, ')
          ..write('notePattern: $notePattern, ')
          ..write('tags: $tags, ')
          ..write('sortOrder: $sortOrder, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $LearningTopicsTable extends LearningTopics
    with TableInfo<$LearningTopicsTable, LearningTopic> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LearningTopicsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _uuidMeta = const VerificationMeta('uuid');
  @override
  late final GeneratedColumn<String> uuid = GeneratedColumn<String>(
    'uuid',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 36,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    clientDefault: () => const Uuid().v4(),
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    additionalChecks: GeneratedColumn.checkTextLength(
      minTextLength: 1,
      maxTextLength: 50,
    ),
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    uuid,
    name,
    description,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'learning_topics';
  @override
  VerificationContext validateIntegrity(
    Insertable<LearningTopic> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('uuid')) {
      context.handle(
        _uuidMeta,
        uuid.isAcceptableOrUnknown(data['uuid']!, _uuidMeta),
      );
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LearningTopic map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LearningTopic(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      uuid: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}uuid'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
    );
  }

  @override
  $LearningTopicsTable createAlias(String alias) {
    return $LearningTopicsTable(attachedDatabase, alias);
  }
}

class LearningTopic extends DataClass implements Insertable<LearningTopic> {
  /// 主键 ID。
  final int id;

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份/恢复的合并去重（避免 id 冲突）
  /// - 迁移时会通过 SQL 为历史库补齐该列并回填为真实 UUID，再建立唯一索引
  final String uuid;

  /// 主题名称（必填，≤50）。
  final String name;

  /// 主题描述（可选）。
  final String? description;

  /// 创建时间。
  final DateTime createdAt;

  /// 更新时间（可空）。
  final DateTime? updatedAt;
  const LearningTopic({
    required this.id,
    required this.uuid,
    required this.name,
    this.description,
    required this.createdAt,
    this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['uuid'] = Variable<String>(uuid);
    map['name'] = Variable<String>(name);
    if (!nullToAbsent || description != null) {
      map['description'] = Variable<String>(description);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    return map;
  }

  LearningTopicsCompanion toCompanion(bool nullToAbsent) {
    return LearningTopicsCompanion(
      id: Value(id),
      uuid: Value(uuid),
      name: Value(name),
      description: description == null && nullToAbsent
          ? const Value.absent()
          : Value(description),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
    );
  }

  factory LearningTopic.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LearningTopic(
      id: serializer.fromJson<int>(json['id']),
      uuid: serializer.fromJson<String>(json['uuid']),
      name: serializer.fromJson<String>(json['name']),
      description: serializer.fromJson<String?>(json['description']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'uuid': serializer.toJson<String>(uuid),
      'name': serializer.toJson<String>(name),
      'description': serializer.toJson<String?>(description),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
    };
  }

  LearningTopic copyWith({
    int? id,
    String? uuid,
    String? name,
    Value<String?> description = const Value.absent(),
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
  }) => LearningTopic(
    id: id ?? this.id,
    uuid: uuid ?? this.uuid,
    name: name ?? this.name,
    description: description.present ? description.value : this.description,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
  );
  LearningTopic copyWithCompanion(LearningTopicsCompanion data) {
    return LearningTopic(
      id: data.id.present ? data.id.value : this.id,
      uuid: data.uuid.present ? data.uuid.value : this.uuid,
      name: data.name.present ? data.name.value : this.name,
      description: data.description.present
          ? data.description.value
          : this.description,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LearningTopic(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('name: $name, ')
          ..write('description: $description, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, uuid, name, description, createdAt, updatedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LearningTopic &&
          other.id == this.id &&
          other.uuid == this.uuid &&
          other.name == this.name &&
          other.description == this.description &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class LearningTopicsCompanion extends UpdateCompanion<LearningTopic> {
  final Value<int> id;
  final Value<String> uuid;
  final Value<String> name;
  final Value<String?> description;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  const LearningTopicsCompanion({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    this.name = const Value.absent(),
    this.description = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  });
  LearningTopicsCompanion.insert({
    this.id = const Value.absent(),
    this.uuid = const Value.absent(),
    required String name,
    this.description = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
  }) : name = Value(name);
  static Insertable<LearningTopic> custom({
    Expression<int>? id,
    Expression<String>? uuid,
    Expression<String>? name,
    Expression<String>? description,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (uuid != null) 'uuid': uuid,
      if (name != null) 'name': name,
      if (description != null) 'description': description,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
    });
  }

  LearningTopicsCompanion copyWith({
    Value<int>? id,
    Value<String>? uuid,
    Value<String>? name,
    Value<String?>? description,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
  }) {
    return LearningTopicsCompanion(
      id: id ?? this.id,
      uuid: uuid ?? this.uuid,
      name: name ?? this.name,
      description: description ?? this.description,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (uuid.present) {
      map['uuid'] = Variable<String>(uuid.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LearningTopicsCompanion(')
          ..write('id: $id, ')
          ..write('uuid: $uuid, ')
          ..write('name: $name, ')
          ..write('description: $description, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }
}

class $TopicItemRelationsTable extends TopicItemRelations
    with TableInfo<$TopicItemRelationsTable, TopicItemRelation> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TopicItemRelationsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _topicIdMeta = const VerificationMeta(
    'topicId',
  );
  @override
  late final GeneratedColumn<int> topicId = GeneratedColumn<int>(
    'topic_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES learning_topics (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _learningItemIdMeta = const VerificationMeta(
    'learningItemId',
  );
  @override
  late final GeneratedColumn<int> learningItemId = GeneratedColumn<int>(
    'learning_item_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'REFERENCES learning_items (id) ON DELETE CASCADE',
    ),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    topicId,
    learningItemId,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'topic_item_relations';
  @override
  VerificationContext validateIntegrity(
    Insertable<TopicItemRelation> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('topic_id')) {
      context.handle(
        _topicIdMeta,
        topicId.isAcceptableOrUnknown(data['topic_id']!, _topicIdMeta),
      );
    } else if (isInserting) {
      context.missing(_topicIdMeta);
    }
    if (data.containsKey('learning_item_id')) {
      context.handle(
        _learningItemIdMeta,
        learningItemId.isAcceptableOrUnknown(
          data['learning_item_id']!,
          _learningItemIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_learningItemIdMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {topicId, learningItemId},
  ];
  @override
  TopicItemRelation map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return TopicItemRelation(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      topicId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}topic_id'],
      )!,
      learningItemId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}learning_item_id'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $TopicItemRelationsTable createAlias(String alias) {
    return $TopicItemRelationsTable(attachedDatabase, alias);
  }
}

class TopicItemRelation extends DataClass
    implements Insertable<TopicItemRelation> {
  /// 主键 ID。
  final int id;

  /// 外键：主题 ID（删除主题时级联删除）。
  final int topicId;

  /// 外键：学习内容 ID（删除学习内容时级联删除）。
  final int learningItemId;

  /// 创建时间。
  final DateTime createdAt;
  const TopicItemRelation({
    required this.id,
    required this.topicId,
    required this.learningItemId,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['topic_id'] = Variable<int>(topicId);
    map['learning_item_id'] = Variable<int>(learningItemId);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  TopicItemRelationsCompanion toCompanion(bool nullToAbsent) {
    return TopicItemRelationsCompanion(
      id: Value(id),
      topicId: Value(topicId),
      learningItemId: Value(learningItemId),
      createdAt: Value(createdAt),
    );
  }

  factory TopicItemRelation.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return TopicItemRelation(
      id: serializer.fromJson<int>(json['id']),
      topicId: serializer.fromJson<int>(json['topicId']),
      learningItemId: serializer.fromJson<int>(json['learningItemId']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'topicId': serializer.toJson<int>(topicId),
      'learningItemId': serializer.toJson<int>(learningItemId),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  TopicItemRelation copyWith({
    int? id,
    int? topicId,
    int? learningItemId,
    DateTime? createdAt,
  }) => TopicItemRelation(
    id: id ?? this.id,
    topicId: topicId ?? this.topicId,
    learningItemId: learningItemId ?? this.learningItemId,
    createdAt: createdAt ?? this.createdAt,
  );
  TopicItemRelation copyWithCompanion(TopicItemRelationsCompanion data) {
    return TopicItemRelation(
      id: data.id.present ? data.id.value : this.id,
      topicId: data.topicId.present ? data.topicId.value : this.topicId,
      learningItemId: data.learningItemId.present
          ? data.learningItemId.value
          : this.learningItemId,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('TopicItemRelation(')
          ..write('id: $id, ')
          ..write('topicId: $topicId, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, topicId, learningItemId, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is TopicItemRelation &&
          other.id == this.id &&
          other.topicId == this.topicId &&
          other.learningItemId == this.learningItemId &&
          other.createdAt == this.createdAt);
}

class TopicItemRelationsCompanion extends UpdateCompanion<TopicItemRelation> {
  final Value<int> id;
  final Value<int> topicId;
  final Value<int> learningItemId;
  final Value<DateTime> createdAt;
  const TopicItemRelationsCompanion({
    this.id = const Value.absent(),
    this.topicId = const Value.absent(),
    this.learningItemId = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  TopicItemRelationsCompanion.insert({
    this.id = const Value.absent(),
    required int topicId,
    required int learningItemId,
    this.createdAt = const Value.absent(),
  }) : topicId = Value(topicId),
       learningItemId = Value(learningItemId);
  static Insertable<TopicItemRelation> custom({
    Expression<int>? id,
    Expression<int>? topicId,
    Expression<int>? learningItemId,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (topicId != null) 'topic_id': topicId,
      if (learningItemId != null) 'learning_item_id': learningItemId,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  TopicItemRelationsCompanion copyWith({
    Value<int>? id,
    Value<int>? topicId,
    Value<int>? learningItemId,
    Value<DateTime>? createdAt,
  }) {
    return TopicItemRelationsCompanion(
      id: id ?? this.id,
      topicId: topicId ?? this.topicId,
      learningItemId: learningItemId ?? this.learningItemId,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (topicId.present) {
      map['topic_id'] = Variable<int>(topicId.value);
    }
    if (learningItemId.present) {
      map['learning_item_id'] = Variable<int>(learningItemId.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TopicItemRelationsCompanion(')
          ..write('id: $id, ')
          ..write('topicId: $topicId, ')
          ..write('learningItemId: $learningItemId, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $SyncDevicesTable extends SyncDevices
    with TableInfo<$SyncDevicesTable, SyncDevice> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SyncDevicesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _deviceIdMeta = const VerificationMeta(
    'deviceId',
  );
  @override
  late final GeneratedColumn<String> deviceId = GeneratedColumn<String>(
    'device_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _deviceNameMeta = const VerificationMeta(
    'deviceName',
  );
  @override
  late final GeneratedColumn<String> deviceName = GeneratedColumn<String>(
    'device_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _deviceTypeMeta = const VerificationMeta(
    'deviceType',
  );
  @override
  late final GeneratedColumn<String> deviceType = GeneratedColumn<String>(
    'device_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _ipAddressMeta = const VerificationMeta(
    'ipAddress',
  );
  @override
  late final GeneratedColumn<String> ipAddress = GeneratedColumn<String>(
    'ip_address',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _authTokenMeta = const VerificationMeta(
    'authToken',
  );
  @override
  late final GeneratedColumn<String> authToken = GeneratedColumn<String>(
    'auth_token',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isMasterMeta = const VerificationMeta(
    'isMaster',
  );
  @override
  late final GeneratedColumn<bool> isMaster = GeneratedColumn<bool>(
    'is_master',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_master" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _lastSyncMsMeta = const VerificationMeta(
    'lastSyncMs',
  );
  @override
  late final GeneratedColumn<int> lastSyncMs = GeneratedColumn<int>(
    'last_sync_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastOutgoingMsMeta = const VerificationMeta(
    'lastOutgoingMs',
  );
  @override
  late final GeneratedColumn<int> lastOutgoingMs = GeneratedColumn<int>(
    'last_outgoing_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastIncomingMsMeta = const VerificationMeta(
    'lastIncomingMs',
  );
  @override
  late final GeneratedColumn<int> lastIncomingMs = GeneratedColumn<int>(
    'last_incoming_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    deviceId,
    deviceName,
    deviceType,
    ipAddress,
    authToken,
    isMaster,
    lastSyncMs,
    lastOutgoingMs,
    lastIncomingMs,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sync_devices';
  @override
  VerificationContext validateIntegrity(
    Insertable<SyncDevice> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('device_id')) {
      context.handle(
        _deviceIdMeta,
        deviceId.isAcceptableOrUnknown(data['device_id']!, _deviceIdMeta),
      );
    } else if (isInserting) {
      context.missing(_deviceIdMeta);
    }
    if (data.containsKey('device_name')) {
      context.handle(
        _deviceNameMeta,
        deviceName.isAcceptableOrUnknown(data['device_name']!, _deviceNameMeta),
      );
    } else if (isInserting) {
      context.missing(_deviceNameMeta);
    }
    if (data.containsKey('device_type')) {
      context.handle(
        _deviceTypeMeta,
        deviceType.isAcceptableOrUnknown(data['device_type']!, _deviceTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_deviceTypeMeta);
    }
    if (data.containsKey('ip_address')) {
      context.handle(
        _ipAddressMeta,
        ipAddress.isAcceptableOrUnknown(data['ip_address']!, _ipAddressMeta),
      );
    }
    if (data.containsKey('auth_token')) {
      context.handle(
        _authTokenMeta,
        authToken.isAcceptableOrUnknown(data['auth_token']!, _authTokenMeta),
      );
    }
    if (data.containsKey('is_master')) {
      context.handle(
        _isMasterMeta,
        isMaster.isAcceptableOrUnknown(data['is_master']!, _isMasterMeta),
      );
    }
    if (data.containsKey('last_sync_ms')) {
      context.handle(
        _lastSyncMsMeta,
        lastSyncMs.isAcceptableOrUnknown(
          data['last_sync_ms']!,
          _lastSyncMsMeta,
        ),
      );
    }
    if (data.containsKey('last_outgoing_ms')) {
      context.handle(
        _lastOutgoingMsMeta,
        lastOutgoingMs.isAcceptableOrUnknown(
          data['last_outgoing_ms']!,
          _lastOutgoingMsMeta,
        ),
      );
    }
    if (data.containsKey('last_incoming_ms')) {
      context.handle(
        _lastIncomingMsMeta,
        lastIncomingMs.isAcceptableOrUnknown(
          data['last_incoming_ms']!,
          _lastIncomingMsMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  SyncDevice map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SyncDevice(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      deviceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}device_id'],
      )!,
      deviceName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}device_name'],
      )!,
      deviceType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}device_type'],
      )!,
      ipAddress: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}ip_address'],
      ),
      authToken: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}auth_token'],
      ),
      isMaster: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_master'],
      )!,
      lastSyncMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_sync_ms'],
      ),
      lastOutgoingMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_outgoing_ms'],
      ),
      lastIncomingMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_incoming_ms'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $SyncDevicesTable createAlias(String alias) {
    return $SyncDevicesTable(attachedDatabase, alias);
  }
}

class SyncDevice extends DataClass implements Insertable<SyncDevice> {
  /// 主键 ID。
  final int id;

  /// 设备唯一标识（由本应用生成并持久化）。
  final String deviceId;

  /// 设备名称（用于 UI 展示）。
  final String deviceName;

  /// 设备类型：android/ios/windows/macos/linux/unknown。
  final String deviceType;

  /// IP 地址（用于局域网通信，可能会变化）。
  final String? ipAddress;

  /// 认证令牌（配对成功后生成）。
  final String? authToken;

  /// 是否为主机设备（用于客户端标记“主机”）。
  final bool isMaster;

  /// 最近一次成功同步的时间戳（毫秒）。
  final int? lastSyncMs;

  /// 最近一次成功“发送本地增量”的游标（毫秒）。
  final int? lastOutgoingMs;

  /// 最近一次成功“拉取远端增量”的游标（毫秒）。
  final int? lastIncomingMs;

  /// 创建时间。
  final DateTime createdAt;
  const SyncDevice({
    required this.id,
    required this.deviceId,
    required this.deviceName,
    required this.deviceType,
    this.ipAddress,
    this.authToken,
    required this.isMaster,
    this.lastSyncMs,
    this.lastOutgoingMs,
    this.lastIncomingMs,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['device_id'] = Variable<String>(deviceId);
    map['device_name'] = Variable<String>(deviceName);
    map['device_type'] = Variable<String>(deviceType);
    if (!nullToAbsent || ipAddress != null) {
      map['ip_address'] = Variable<String>(ipAddress);
    }
    if (!nullToAbsent || authToken != null) {
      map['auth_token'] = Variable<String>(authToken);
    }
    map['is_master'] = Variable<bool>(isMaster);
    if (!nullToAbsent || lastSyncMs != null) {
      map['last_sync_ms'] = Variable<int>(lastSyncMs);
    }
    if (!nullToAbsent || lastOutgoingMs != null) {
      map['last_outgoing_ms'] = Variable<int>(lastOutgoingMs);
    }
    if (!nullToAbsent || lastIncomingMs != null) {
      map['last_incoming_ms'] = Variable<int>(lastIncomingMs);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  SyncDevicesCompanion toCompanion(bool nullToAbsent) {
    return SyncDevicesCompanion(
      id: Value(id),
      deviceId: Value(deviceId),
      deviceName: Value(deviceName),
      deviceType: Value(deviceType),
      ipAddress: ipAddress == null && nullToAbsent
          ? const Value.absent()
          : Value(ipAddress),
      authToken: authToken == null && nullToAbsent
          ? const Value.absent()
          : Value(authToken),
      isMaster: Value(isMaster),
      lastSyncMs: lastSyncMs == null && nullToAbsent
          ? const Value.absent()
          : Value(lastSyncMs),
      lastOutgoingMs: lastOutgoingMs == null && nullToAbsent
          ? const Value.absent()
          : Value(lastOutgoingMs),
      lastIncomingMs: lastIncomingMs == null && nullToAbsent
          ? const Value.absent()
          : Value(lastIncomingMs),
      createdAt: Value(createdAt),
    );
  }

  factory SyncDevice.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SyncDevice(
      id: serializer.fromJson<int>(json['id']),
      deviceId: serializer.fromJson<String>(json['deviceId']),
      deviceName: serializer.fromJson<String>(json['deviceName']),
      deviceType: serializer.fromJson<String>(json['deviceType']),
      ipAddress: serializer.fromJson<String?>(json['ipAddress']),
      authToken: serializer.fromJson<String?>(json['authToken']),
      isMaster: serializer.fromJson<bool>(json['isMaster']),
      lastSyncMs: serializer.fromJson<int?>(json['lastSyncMs']),
      lastOutgoingMs: serializer.fromJson<int?>(json['lastOutgoingMs']),
      lastIncomingMs: serializer.fromJson<int?>(json['lastIncomingMs']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'deviceId': serializer.toJson<String>(deviceId),
      'deviceName': serializer.toJson<String>(deviceName),
      'deviceType': serializer.toJson<String>(deviceType),
      'ipAddress': serializer.toJson<String?>(ipAddress),
      'authToken': serializer.toJson<String?>(authToken),
      'isMaster': serializer.toJson<bool>(isMaster),
      'lastSyncMs': serializer.toJson<int?>(lastSyncMs),
      'lastOutgoingMs': serializer.toJson<int?>(lastOutgoingMs),
      'lastIncomingMs': serializer.toJson<int?>(lastIncomingMs),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  SyncDevice copyWith({
    int? id,
    String? deviceId,
    String? deviceName,
    String? deviceType,
    Value<String?> ipAddress = const Value.absent(),
    Value<String?> authToken = const Value.absent(),
    bool? isMaster,
    Value<int?> lastSyncMs = const Value.absent(),
    Value<int?> lastOutgoingMs = const Value.absent(),
    Value<int?> lastIncomingMs = const Value.absent(),
    DateTime? createdAt,
  }) => SyncDevice(
    id: id ?? this.id,
    deviceId: deviceId ?? this.deviceId,
    deviceName: deviceName ?? this.deviceName,
    deviceType: deviceType ?? this.deviceType,
    ipAddress: ipAddress.present ? ipAddress.value : this.ipAddress,
    authToken: authToken.present ? authToken.value : this.authToken,
    isMaster: isMaster ?? this.isMaster,
    lastSyncMs: lastSyncMs.present ? lastSyncMs.value : this.lastSyncMs,
    lastOutgoingMs: lastOutgoingMs.present
        ? lastOutgoingMs.value
        : this.lastOutgoingMs,
    lastIncomingMs: lastIncomingMs.present
        ? lastIncomingMs.value
        : this.lastIncomingMs,
    createdAt: createdAt ?? this.createdAt,
  );
  SyncDevice copyWithCompanion(SyncDevicesCompanion data) {
    return SyncDevice(
      id: data.id.present ? data.id.value : this.id,
      deviceId: data.deviceId.present ? data.deviceId.value : this.deviceId,
      deviceName: data.deviceName.present
          ? data.deviceName.value
          : this.deviceName,
      deviceType: data.deviceType.present
          ? data.deviceType.value
          : this.deviceType,
      ipAddress: data.ipAddress.present ? data.ipAddress.value : this.ipAddress,
      authToken: data.authToken.present ? data.authToken.value : this.authToken,
      isMaster: data.isMaster.present ? data.isMaster.value : this.isMaster,
      lastSyncMs: data.lastSyncMs.present
          ? data.lastSyncMs.value
          : this.lastSyncMs,
      lastOutgoingMs: data.lastOutgoingMs.present
          ? data.lastOutgoingMs.value
          : this.lastOutgoingMs,
      lastIncomingMs: data.lastIncomingMs.present
          ? data.lastIncomingMs.value
          : this.lastIncomingMs,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SyncDevice(')
          ..write('id: $id, ')
          ..write('deviceId: $deviceId, ')
          ..write('deviceName: $deviceName, ')
          ..write('deviceType: $deviceType, ')
          ..write('ipAddress: $ipAddress, ')
          ..write('authToken: $authToken, ')
          ..write('isMaster: $isMaster, ')
          ..write('lastSyncMs: $lastSyncMs, ')
          ..write('lastOutgoingMs: $lastOutgoingMs, ')
          ..write('lastIncomingMs: $lastIncomingMs, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    deviceId,
    deviceName,
    deviceType,
    ipAddress,
    authToken,
    isMaster,
    lastSyncMs,
    lastOutgoingMs,
    lastIncomingMs,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SyncDevice &&
          other.id == this.id &&
          other.deviceId == this.deviceId &&
          other.deviceName == this.deviceName &&
          other.deviceType == this.deviceType &&
          other.ipAddress == this.ipAddress &&
          other.authToken == this.authToken &&
          other.isMaster == this.isMaster &&
          other.lastSyncMs == this.lastSyncMs &&
          other.lastOutgoingMs == this.lastOutgoingMs &&
          other.lastIncomingMs == this.lastIncomingMs &&
          other.createdAt == this.createdAt);
}

class SyncDevicesCompanion extends UpdateCompanion<SyncDevice> {
  final Value<int> id;
  final Value<String> deviceId;
  final Value<String> deviceName;
  final Value<String> deviceType;
  final Value<String?> ipAddress;
  final Value<String?> authToken;
  final Value<bool> isMaster;
  final Value<int?> lastSyncMs;
  final Value<int?> lastOutgoingMs;
  final Value<int?> lastIncomingMs;
  final Value<DateTime> createdAt;
  const SyncDevicesCompanion({
    this.id = const Value.absent(),
    this.deviceId = const Value.absent(),
    this.deviceName = const Value.absent(),
    this.deviceType = const Value.absent(),
    this.ipAddress = const Value.absent(),
    this.authToken = const Value.absent(),
    this.isMaster = const Value.absent(),
    this.lastSyncMs = const Value.absent(),
    this.lastOutgoingMs = const Value.absent(),
    this.lastIncomingMs = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  SyncDevicesCompanion.insert({
    this.id = const Value.absent(),
    required String deviceId,
    required String deviceName,
    required String deviceType,
    this.ipAddress = const Value.absent(),
    this.authToken = const Value.absent(),
    this.isMaster = const Value.absent(),
    this.lastSyncMs = const Value.absent(),
    this.lastOutgoingMs = const Value.absent(),
    this.lastIncomingMs = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : deviceId = Value(deviceId),
       deviceName = Value(deviceName),
       deviceType = Value(deviceType);
  static Insertable<SyncDevice> custom({
    Expression<int>? id,
    Expression<String>? deviceId,
    Expression<String>? deviceName,
    Expression<String>? deviceType,
    Expression<String>? ipAddress,
    Expression<String>? authToken,
    Expression<bool>? isMaster,
    Expression<int>? lastSyncMs,
    Expression<int>? lastOutgoingMs,
    Expression<int>? lastIncomingMs,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (deviceId != null) 'device_id': deviceId,
      if (deviceName != null) 'device_name': deviceName,
      if (deviceType != null) 'device_type': deviceType,
      if (ipAddress != null) 'ip_address': ipAddress,
      if (authToken != null) 'auth_token': authToken,
      if (isMaster != null) 'is_master': isMaster,
      if (lastSyncMs != null) 'last_sync_ms': lastSyncMs,
      if (lastOutgoingMs != null) 'last_outgoing_ms': lastOutgoingMs,
      if (lastIncomingMs != null) 'last_incoming_ms': lastIncomingMs,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  SyncDevicesCompanion copyWith({
    Value<int>? id,
    Value<String>? deviceId,
    Value<String>? deviceName,
    Value<String>? deviceType,
    Value<String?>? ipAddress,
    Value<String?>? authToken,
    Value<bool>? isMaster,
    Value<int?>? lastSyncMs,
    Value<int?>? lastOutgoingMs,
    Value<int?>? lastIncomingMs,
    Value<DateTime>? createdAt,
  }) {
    return SyncDevicesCompanion(
      id: id ?? this.id,
      deviceId: deviceId ?? this.deviceId,
      deviceName: deviceName ?? this.deviceName,
      deviceType: deviceType ?? this.deviceType,
      ipAddress: ipAddress ?? this.ipAddress,
      authToken: authToken ?? this.authToken,
      isMaster: isMaster ?? this.isMaster,
      lastSyncMs: lastSyncMs ?? this.lastSyncMs,
      lastOutgoingMs: lastOutgoingMs ?? this.lastOutgoingMs,
      lastIncomingMs: lastIncomingMs ?? this.lastIncomingMs,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (deviceId.present) {
      map['device_id'] = Variable<String>(deviceId.value);
    }
    if (deviceName.present) {
      map['device_name'] = Variable<String>(deviceName.value);
    }
    if (deviceType.present) {
      map['device_type'] = Variable<String>(deviceType.value);
    }
    if (ipAddress.present) {
      map['ip_address'] = Variable<String>(ipAddress.value);
    }
    if (authToken.present) {
      map['auth_token'] = Variable<String>(authToken.value);
    }
    if (isMaster.present) {
      map['is_master'] = Variable<bool>(isMaster.value);
    }
    if (lastSyncMs.present) {
      map['last_sync_ms'] = Variable<int>(lastSyncMs.value);
    }
    if (lastOutgoingMs.present) {
      map['last_outgoing_ms'] = Variable<int>(lastOutgoingMs.value);
    }
    if (lastIncomingMs.present) {
      map['last_incoming_ms'] = Variable<int>(lastIncomingMs.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SyncDevicesCompanion(')
          ..write('id: $id, ')
          ..write('deviceId: $deviceId, ')
          ..write('deviceName: $deviceName, ')
          ..write('deviceType: $deviceType, ')
          ..write('ipAddress: $ipAddress, ')
          ..write('authToken: $authToken, ')
          ..write('isMaster: $isMaster, ')
          ..write('lastSyncMs: $lastSyncMs, ')
          ..write('lastOutgoingMs: $lastOutgoingMs, ')
          ..write('lastIncomingMs: $lastIncomingMs, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $SyncLogsTable extends SyncLogs with TableInfo<$SyncLogsTable, SyncLog> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SyncLogsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _deviceIdMeta = const VerificationMeta(
    'deviceId',
  );
  @override
  late final GeneratedColumn<String> deviceId = GeneratedColumn<String>(
    'device_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _entityTypeMeta = const VerificationMeta(
    'entityType',
  );
  @override
  late final GeneratedColumn<String> entityType = GeneratedColumn<String>(
    'entity_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _entityIdMeta = const VerificationMeta(
    'entityId',
  );
  @override
  late final GeneratedColumn<int> entityId = GeneratedColumn<int>(
    'entity_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _operationMeta = const VerificationMeta(
    'operation',
  );
  @override
  late final GeneratedColumn<String> operation = GeneratedColumn<String>(
    'operation',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dataMeta = const VerificationMeta('data');
  @override
  late final GeneratedColumn<String> data = GeneratedColumn<String>(
    'data',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _timestampMsMeta = const VerificationMeta(
    'timestampMs',
  );
  @override
  late final GeneratedColumn<int> timestampMs = GeneratedColumn<int>(
    'timestamp_ms',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _localVersionMeta = const VerificationMeta(
    'localVersion',
  );
  @override
  late final GeneratedColumn<int> localVersion = GeneratedColumn<int>(
    'local_version',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    deviceId,
    entityType,
    entityId,
    operation,
    data,
    timestampMs,
    localVersion,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sync_logs';
  @override
  VerificationContext validateIntegrity(
    Insertable<SyncLog> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('device_id')) {
      context.handle(
        _deviceIdMeta,
        deviceId.isAcceptableOrUnknown(data['device_id']!, _deviceIdMeta),
      );
    } else if (isInserting) {
      context.missing(_deviceIdMeta);
    }
    if (data.containsKey('entity_type')) {
      context.handle(
        _entityTypeMeta,
        entityType.isAcceptableOrUnknown(data['entity_type']!, _entityTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_entityTypeMeta);
    }
    if (data.containsKey('entity_id')) {
      context.handle(
        _entityIdMeta,
        entityId.isAcceptableOrUnknown(data['entity_id']!, _entityIdMeta),
      );
    } else if (isInserting) {
      context.missing(_entityIdMeta);
    }
    if (data.containsKey('operation')) {
      context.handle(
        _operationMeta,
        operation.isAcceptableOrUnknown(data['operation']!, _operationMeta),
      );
    } else if (isInserting) {
      context.missing(_operationMeta);
    }
    if (data.containsKey('data')) {
      context.handle(
        _dataMeta,
        this.data.isAcceptableOrUnknown(data['data']!, _dataMeta),
      );
    } else if (isInserting) {
      context.missing(_dataMeta);
    }
    if (data.containsKey('timestamp_ms')) {
      context.handle(
        _timestampMsMeta,
        timestampMs.isAcceptableOrUnknown(
          data['timestamp_ms']!,
          _timestampMsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_timestampMsMeta);
    }
    if (data.containsKey('local_version')) {
      context.handle(
        _localVersionMeta,
        localVersion.isAcceptableOrUnknown(
          data['local_version']!,
          _localVersionMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {deviceId, entityType, entityId, timestampMs, operation},
  ];
  @override
  SyncLog map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SyncLog(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      deviceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}device_id'],
      )!,
      entityType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entity_type'],
      )!,
      entityId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}entity_id'],
      )!,
      operation: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}operation'],
      )!,
      data: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}data'],
      )!,
      timestampMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}timestamp_ms'],
      )!,
      localVersion: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}local_version'],
      )!,
    );
  }

  @override
  $SyncLogsTable createAlias(String alias) {
    return $SyncLogsTable(attachedDatabase, alias);
  }
}

class SyncLog extends DataClass implements Insertable<SyncLog> {
  /// 主键 ID。
  final int id;

  /// 源设备 ID（同时也是该实体的 originDeviceId）。
  final String deviceId;

  /// 实体类型：learning_item/review_task/template/topic/topic_item_relation/settings/theme 等。
  final String entityType;

  /// 源设备上的实体 ID（originEntityId）。
  final int entityId;

  /// 操作类型：create/update/delete。
  final String operation;

  /// JSON 数据（create/update 用于携带字段；delete 可为空 JSON）。
  final String data;

  /// 事件时间戳（毫秒）。
  final int timestampMs;

  /// 本地版本号（预留字段，用于未来更严格的冲突解决策略）。
  final int localVersion;
  const SyncLog({
    required this.id,
    required this.deviceId,
    required this.entityType,
    required this.entityId,
    required this.operation,
    required this.data,
    required this.timestampMs,
    required this.localVersion,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['device_id'] = Variable<String>(deviceId);
    map['entity_type'] = Variable<String>(entityType);
    map['entity_id'] = Variable<int>(entityId);
    map['operation'] = Variable<String>(operation);
    map['data'] = Variable<String>(data);
    map['timestamp_ms'] = Variable<int>(timestampMs);
    map['local_version'] = Variable<int>(localVersion);
    return map;
  }

  SyncLogsCompanion toCompanion(bool nullToAbsent) {
    return SyncLogsCompanion(
      id: Value(id),
      deviceId: Value(deviceId),
      entityType: Value(entityType),
      entityId: Value(entityId),
      operation: Value(operation),
      data: Value(data),
      timestampMs: Value(timestampMs),
      localVersion: Value(localVersion),
    );
  }

  factory SyncLog.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SyncLog(
      id: serializer.fromJson<int>(json['id']),
      deviceId: serializer.fromJson<String>(json['deviceId']),
      entityType: serializer.fromJson<String>(json['entityType']),
      entityId: serializer.fromJson<int>(json['entityId']),
      operation: serializer.fromJson<String>(json['operation']),
      data: serializer.fromJson<String>(json['data']),
      timestampMs: serializer.fromJson<int>(json['timestampMs']),
      localVersion: serializer.fromJson<int>(json['localVersion']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'deviceId': serializer.toJson<String>(deviceId),
      'entityType': serializer.toJson<String>(entityType),
      'entityId': serializer.toJson<int>(entityId),
      'operation': serializer.toJson<String>(operation),
      'data': serializer.toJson<String>(data),
      'timestampMs': serializer.toJson<int>(timestampMs),
      'localVersion': serializer.toJson<int>(localVersion),
    };
  }

  SyncLog copyWith({
    int? id,
    String? deviceId,
    String? entityType,
    int? entityId,
    String? operation,
    String? data,
    int? timestampMs,
    int? localVersion,
  }) => SyncLog(
    id: id ?? this.id,
    deviceId: deviceId ?? this.deviceId,
    entityType: entityType ?? this.entityType,
    entityId: entityId ?? this.entityId,
    operation: operation ?? this.operation,
    data: data ?? this.data,
    timestampMs: timestampMs ?? this.timestampMs,
    localVersion: localVersion ?? this.localVersion,
  );
  SyncLog copyWithCompanion(SyncLogsCompanion data) {
    return SyncLog(
      id: data.id.present ? data.id.value : this.id,
      deviceId: data.deviceId.present ? data.deviceId.value : this.deviceId,
      entityType: data.entityType.present
          ? data.entityType.value
          : this.entityType,
      entityId: data.entityId.present ? data.entityId.value : this.entityId,
      operation: data.operation.present ? data.operation.value : this.operation,
      data: data.data.present ? data.data.value : this.data,
      timestampMs: data.timestampMs.present
          ? data.timestampMs.value
          : this.timestampMs,
      localVersion: data.localVersion.present
          ? data.localVersion.value
          : this.localVersion,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SyncLog(')
          ..write('id: $id, ')
          ..write('deviceId: $deviceId, ')
          ..write('entityType: $entityType, ')
          ..write('entityId: $entityId, ')
          ..write('operation: $operation, ')
          ..write('data: $data, ')
          ..write('timestampMs: $timestampMs, ')
          ..write('localVersion: $localVersion')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    deviceId,
    entityType,
    entityId,
    operation,
    data,
    timestampMs,
    localVersion,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SyncLog &&
          other.id == this.id &&
          other.deviceId == this.deviceId &&
          other.entityType == this.entityType &&
          other.entityId == this.entityId &&
          other.operation == this.operation &&
          other.data == this.data &&
          other.timestampMs == this.timestampMs &&
          other.localVersion == this.localVersion);
}

class SyncLogsCompanion extends UpdateCompanion<SyncLog> {
  final Value<int> id;
  final Value<String> deviceId;
  final Value<String> entityType;
  final Value<int> entityId;
  final Value<String> operation;
  final Value<String> data;
  final Value<int> timestampMs;
  final Value<int> localVersion;
  const SyncLogsCompanion({
    this.id = const Value.absent(),
    this.deviceId = const Value.absent(),
    this.entityType = const Value.absent(),
    this.entityId = const Value.absent(),
    this.operation = const Value.absent(),
    this.data = const Value.absent(),
    this.timestampMs = const Value.absent(),
    this.localVersion = const Value.absent(),
  });
  SyncLogsCompanion.insert({
    this.id = const Value.absent(),
    required String deviceId,
    required String entityType,
    required int entityId,
    required String operation,
    required String data,
    required int timestampMs,
    this.localVersion = const Value.absent(),
  }) : deviceId = Value(deviceId),
       entityType = Value(entityType),
       entityId = Value(entityId),
       operation = Value(operation),
       data = Value(data),
       timestampMs = Value(timestampMs);
  static Insertable<SyncLog> custom({
    Expression<int>? id,
    Expression<String>? deviceId,
    Expression<String>? entityType,
    Expression<int>? entityId,
    Expression<String>? operation,
    Expression<String>? data,
    Expression<int>? timestampMs,
    Expression<int>? localVersion,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (deviceId != null) 'device_id': deviceId,
      if (entityType != null) 'entity_type': entityType,
      if (entityId != null) 'entity_id': entityId,
      if (operation != null) 'operation': operation,
      if (data != null) 'data': data,
      if (timestampMs != null) 'timestamp_ms': timestampMs,
      if (localVersion != null) 'local_version': localVersion,
    });
  }

  SyncLogsCompanion copyWith({
    Value<int>? id,
    Value<String>? deviceId,
    Value<String>? entityType,
    Value<int>? entityId,
    Value<String>? operation,
    Value<String>? data,
    Value<int>? timestampMs,
    Value<int>? localVersion,
  }) {
    return SyncLogsCompanion(
      id: id ?? this.id,
      deviceId: deviceId ?? this.deviceId,
      entityType: entityType ?? this.entityType,
      entityId: entityId ?? this.entityId,
      operation: operation ?? this.operation,
      data: data ?? this.data,
      timestampMs: timestampMs ?? this.timestampMs,
      localVersion: localVersion ?? this.localVersion,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (deviceId.present) {
      map['device_id'] = Variable<String>(deviceId.value);
    }
    if (entityType.present) {
      map['entity_type'] = Variable<String>(entityType.value);
    }
    if (entityId.present) {
      map['entity_id'] = Variable<int>(entityId.value);
    }
    if (operation.present) {
      map['operation'] = Variable<String>(operation.value);
    }
    if (data.present) {
      map['data'] = Variable<String>(data.value);
    }
    if (timestampMs.present) {
      map['timestamp_ms'] = Variable<int>(timestampMs.value);
    }
    if (localVersion.present) {
      map['local_version'] = Variable<int>(localVersion.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SyncLogsCompanion(')
          ..write('id: $id, ')
          ..write('deviceId: $deviceId, ')
          ..write('entityType: $entityType, ')
          ..write('entityId: $entityId, ')
          ..write('operation: $operation, ')
          ..write('data: $data, ')
          ..write('timestampMs: $timestampMs, ')
          ..write('localVersion: $localVersion')
          ..write(')'))
        .toString();
  }
}

class $SyncEntityMappingsTable extends SyncEntityMappings
    with TableInfo<$SyncEntityMappingsTable, SyncEntityMapping> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SyncEntityMappingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _entityTypeMeta = const VerificationMeta(
    'entityType',
  );
  @override
  late final GeneratedColumn<String> entityType = GeneratedColumn<String>(
    'entity_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _originDeviceIdMeta = const VerificationMeta(
    'originDeviceId',
  );
  @override
  late final GeneratedColumn<String> originDeviceId = GeneratedColumn<String>(
    'origin_device_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _originEntityIdMeta = const VerificationMeta(
    'originEntityId',
  );
  @override
  late final GeneratedColumn<int> originEntityId = GeneratedColumn<int>(
    'origin_entity_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _localEntityIdMeta = const VerificationMeta(
    'localEntityId',
  );
  @override
  late final GeneratedColumn<int> localEntityId = GeneratedColumn<int>(
    'local_entity_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lastAppliedAtMsMeta = const VerificationMeta(
    'lastAppliedAtMs',
  );
  @override
  late final GeneratedColumn<int> lastAppliedAtMs = GeneratedColumn<int>(
    'last_applied_at_ms',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isDeletedMeta = const VerificationMeta(
    'isDeleted',
  );
  @override
  late final GeneratedColumn<bool> isDeleted = GeneratedColumn<bool>(
    'is_deleted',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_deleted" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    entityType,
    originDeviceId,
    originEntityId,
    localEntityId,
    lastAppliedAtMs,
    isDeleted,
    createdAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sync_entity_mappings';
  @override
  VerificationContext validateIntegrity(
    Insertable<SyncEntityMapping> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('entity_type')) {
      context.handle(
        _entityTypeMeta,
        entityType.isAcceptableOrUnknown(data['entity_type']!, _entityTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_entityTypeMeta);
    }
    if (data.containsKey('origin_device_id')) {
      context.handle(
        _originDeviceIdMeta,
        originDeviceId.isAcceptableOrUnknown(
          data['origin_device_id']!,
          _originDeviceIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_originDeviceIdMeta);
    }
    if (data.containsKey('origin_entity_id')) {
      context.handle(
        _originEntityIdMeta,
        originEntityId.isAcceptableOrUnknown(
          data['origin_entity_id']!,
          _originEntityIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_originEntityIdMeta);
    }
    if (data.containsKey('local_entity_id')) {
      context.handle(
        _localEntityIdMeta,
        localEntityId.isAcceptableOrUnknown(
          data['local_entity_id']!,
          _localEntityIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_localEntityIdMeta);
    }
    if (data.containsKey('last_applied_at_ms')) {
      context.handle(
        _lastAppliedAtMsMeta,
        lastAppliedAtMs.isAcceptableOrUnknown(
          data['last_applied_at_ms']!,
          _lastAppliedAtMsMeta,
        ),
      );
    }
    if (data.containsKey('is_deleted')) {
      context.handle(
        _isDeletedMeta,
        isDeleted.isAcceptableOrUnknown(data['is_deleted']!, _isDeletedMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  List<Set<GeneratedColumn>> get uniqueKeys => [
    {entityType, originDeviceId, originEntityId},
  ];
  @override
  SyncEntityMapping map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SyncEntityMapping(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      entityType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entity_type'],
      )!,
      originDeviceId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}origin_device_id'],
      )!,
      originEntityId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}origin_entity_id'],
      )!,
      localEntityId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}local_entity_id'],
      )!,
      lastAppliedAtMs: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}last_applied_at_ms'],
      ),
      isDeleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_deleted'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $SyncEntityMappingsTable createAlias(String alias) {
    return $SyncEntityMappingsTable(attachedDatabase, alias);
  }
}

class SyncEntityMapping extends DataClass
    implements Insertable<SyncEntityMapping> {
  /// 主键 ID。
  final int id;

  /// 实体类型（与 SyncLogs.entityType 保持一致）。
  final String entityType;

  /// 源设备 ID（originDeviceId）。
  final String originDeviceId;

  /// 源设备实体 ID（originEntityId）。
  final int originEntityId;

  /// 本地实体 ID（业务表主键）。
  final int localEntityId;

  /// 最近一次已应用事件时间戳（毫秒）。
  final int? lastAppliedAtMs;

  /// 是否已被删除（用于 tombstone，避免延迟事件“复活”已删数据）。
  final bool isDeleted;

  /// 创建时间。
  final DateTime createdAt;
  const SyncEntityMapping({
    required this.id,
    required this.entityType,
    required this.originDeviceId,
    required this.originEntityId,
    required this.localEntityId,
    this.lastAppliedAtMs,
    required this.isDeleted,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['entity_type'] = Variable<String>(entityType);
    map['origin_device_id'] = Variable<String>(originDeviceId);
    map['origin_entity_id'] = Variable<int>(originEntityId);
    map['local_entity_id'] = Variable<int>(localEntityId);
    if (!nullToAbsent || lastAppliedAtMs != null) {
      map['last_applied_at_ms'] = Variable<int>(lastAppliedAtMs);
    }
    map['is_deleted'] = Variable<bool>(isDeleted);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  SyncEntityMappingsCompanion toCompanion(bool nullToAbsent) {
    return SyncEntityMappingsCompanion(
      id: Value(id),
      entityType: Value(entityType),
      originDeviceId: Value(originDeviceId),
      originEntityId: Value(originEntityId),
      localEntityId: Value(localEntityId),
      lastAppliedAtMs: lastAppliedAtMs == null && nullToAbsent
          ? const Value.absent()
          : Value(lastAppliedAtMs),
      isDeleted: Value(isDeleted),
      createdAt: Value(createdAt),
    );
  }

  factory SyncEntityMapping.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SyncEntityMapping(
      id: serializer.fromJson<int>(json['id']),
      entityType: serializer.fromJson<String>(json['entityType']),
      originDeviceId: serializer.fromJson<String>(json['originDeviceId']),
      originEntityId: serializer.fromJson<int>(json['originEntityId']),
      localEntityId: serializer.fromJson<int>(json['localEntityId']),
      lastAppliedAtMs: serializer.fromJson<int?>(json['lastAppliedAtMs']),
      isDeleted: serializer.fromJson<bool>(json['isDeleted']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'entityType': serializer.toJson<String>(entityType),
      'originDeviceId': serializer.toJson<String>(originDeviceId),
      'originEntityId': serializer.toJson<int>(originEntityId),
      'localEntityId': serializer.toJson<int>(localEntityId),
      'lastAppliedAtMs': serializer.toJson<int?>(lastAppliedAtMs),
      'isDeleted': serializer.toJson<bool>(isDeleted),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  SyncEntityMapping copyWith({
    int? id,
    String? entityType,
    String? originDeviceId,
    int? originEntityId,
    int? localEntityId,
    Value<int?> lastAppliedAtMs = const Value.absent(),
    bool? isDeleted,
    DateTime? createdAt,
  }) => SyncEntityMapping(
    id: id ?? this.id,
    entityType: entityType ?? this.entityType,
    originDeviceId: originDeviceId ?? this.originDeviceId,
    originEntityId: originEntityId ?? this.originEntityId,
    localEntityId: localEntityId ?? this.localEntityId,
    lastAppliedAtMs: lastAppliedAtMs.present
        ? lastAppliedAtMs.value
        : this.lastAppliedAtMs,
    isDeleted: isDeleted ?? this.isDeleted,
    createdAt: createdAt ?? this.createdAt,
  );
  SyncEntityMapping copyWithCompanion(SyncEntityMappingsCompanion data) {
    return SyncEntityMapping(
      id: data.id.present ? data.id.value : this.id,
      entityType: data.entityType.present
          ? data.entityType.value
          : this.entityType,
      originDeviceId: data.originDeviceId.present
          ? data.originDeviceId.value
          : this.originDeviceId,
      originEntityId: data.originEntityId.present
          ? data.originEntityId.value
          : this.originEntityId,
      localEntityId: data.localEntityId.present
          ? data.localEntityId.value
          : this.localEntityId,
      lastAppliedAtMs: data.lastAppliedAtMs.present
          ? data.lastAppliedAtMs.value
          : this.lastAppliedAtMs,
      isDeleted: data.isDeleted.present ? data.isDeleted.value : this.isDeleted,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SyncEntityMapping(')
          ..write('id: $id, ')
          ..write('entityType: $entityType, ')
          ..write('originDeviceId: $originDeviceId, ')
          ..write('originEntityId: $originEntityId, ')
          ..write('localEntityId: $localEntityId, ')
          ..write('lastAppliedAtMs: $lastAppliedAtMs, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    entityType,
    originDeviceId,
    originEntityId,
    localEntityId,
    lastAppliedAtMs,
    isDeleted,
    createdAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SyncEntityMapping &&
          other.id == this.id &&
          other.entityType == this.entityType &&
          other.originDeviceId == this.originDeviceId &&
          other.originEntityId == this.originEntityId &&
          other.localEntityId == this.localEntityId &&
          other.lastAppliedAtMs == this.lastAppliedAtMs &&
          other.isDeleted == this.isDeleted &&
          other.createdAt == this.createdAt);
}

class SyncEntityMappingsCompanion extends UpdateCompanion<SyncEntityMapping> {
  final Value<int> id;
  final Value<String> entityType;
  final Value<String> originDeviceId;
  final Value<int> originEntityId;
  final Value<int> localEntityId;
  final Value<int?> lastAppliedAtMs;
  final Value<bool> isDeleted;
  final Value<DateTime> createdAt;
  const SyncEntityMappingsCompanion({
    this.id = const Value.absent(),
    this.entityType = const Value.absent(),
    this.originDeviceId = const Value.absent(),
    this.originEntityId = const Value.absent(),
    this.localEntityId = const Value.absent(),
    this.lastAppliedAtMs = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  SyncEntityMappingsCompanion.insert({
    this.id = const Value.absent(),
    required String entityType,
    required String originDeviceId,
    required int originEntityId,
    required int localEntityId,
    this.lastAppliedAtMs = const Value.absent(),
    this.isDeleted = const Value.absent(),
    this.createdAt = const Value.absent(),
  }) : entityType = Value(entityType),
       originDeviceId = Value(originDeviceId),
       originEntityId = Value(originEntityId),
       localEntityId = Value(localEntityId);
  static Insertable<SyncEntityMapping> custom({
    Expression<int>? id,
    Expression<String>? entityType,
    Expression<String>? originDeviceId,
    Expression<int>? originEntityId,
    Expression<int>? localEntityId,
    Expression<int>? lastAppliedAtMs,
    Expression<bool>? isDeleted,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (entityType != null) 'entity_type': entityType,
      if (originDeviceId != null) 'origin_device_id': originDeviceId,
      if (originEntityId != null) 'origin_entity_id': originEntityId,
      if (localEntityId != null) 'local_entity_id': localEntityId,
      if (lastAppliedAtMs != null) 'last_applied_at_ms': lastAppliedAtMs,
      if (isDeleted != null) 'is_deleted': isDeleted,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  SyncEntityMappingsCompanion copyWith({
    Value<int>? id,
    Value<String>? entityType,
    Value<String>? originDeviceId,
    Value<int>? originEntityId,
    Value<int>? localEntityId,
    Value<int?>? lastAppliedAtMs,
    Value<bool>? isDeleted,
    Value<DateTime>? createdAt,
  }) {
    return SyncEntityMappingsCompanion(
      id: id ?? this.id,
      entityType: entityType ?? this.entityType,
      originDeviceId: originDeviceId ?? this.originDeviceId,
      originEntityId: originEntityId ?? this.originEntityId,
      localEntityId: localEntityId ?? this.localEntityId,
      lastAppliedAtMs: lastAppliedAtMs ?? this.lastAppliedAtMs,
      isDeleted: isDeleted ?? this.isDeleted,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (entityType.present) {
      map['entity_type'] = Variable<String>(entityType.value);
    }
    if (originDeviceId.present) {
      map['origin_device_id'] = Variable<String>(originDeviceId.value);
    }
    if (originEntityId.present) {
      map['origin_entity_id'] = Variable<int>(originEntityId.value);
    }
    if (localEntityId.present) {
      map['local_entity_id'] = Variable<int>(localEntityId.value);
    }
    if (lastAppliedAtMs.present) {
      map['last_applied_at_ms'] = Variable<int>(lastAppliedAtMs.value);
    }
    if (isDeleted.present) {
      map['is_deleted'] = Variable<bool>(isDeleted.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SyncEntityMappingsCompanion(')
          ..write('id: $id, ')
          ..write('entityType: $entityType, ')
          ..write('originDeviceId: $originDeviceId, ')
          ..write('originEntityId: $originEntityId, ')
          ..write('localEntityId: $localEntityId, ')
          ..write('lastAppliedAtMs: $lastAppliedAtMs, ')
          ..write('isDeleted: $isDeleted, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $LearningItemsTable learningItems = $LearningItemsTable(this);
  late final $LearningSubtasksTable learningSubtasks = $LearningSubtasksTable(
    this,
  );
  late final $ReviewTasksTable reviewTasks = $ReviewTasksTable(this);
  late final $ReviewRecordsTable reviewRecords = $ReviewRecordsTable(this);
  late final $AppSettingsTableTable appSettingsTable = $AppSettingsTableTable(
    this,
  );
  late final $LearningTemplatesTable learningTemplates =
      $LearningTemplatesTable(this);
  late final $LearningTopicsTable learningTopics = $LearningTopicsTable(this);
  late final $TopicItemRelationsTable topicItemRelations =
      $TopicItemRelationsTable(this);
  late final $SyncDevicesTable syncDevices = $SyncDevicesTable(this);
  late final $SyncLogsTable syncLogs = $SyncLogsTable(this);
  late final $SyncEntityMappingsTable syncEntityMappings =
      $SyncEntityMappingsTable(this);
  late final Index idxLearningDate = Index(
    'idx_learning_date',
    'CREATE INDEX idx_learning_date ON learning_items (learning_date)',
  );
  late final Index idxLearningSubtasksItemOrder = Index(
    'idx_learning_subtasks_item_order',
    'CREATE INDEX idx_learning_subtasks_item_order ON learning_subtasks (learning_item_id, sort_order)',
  );
  late final Index idxScheduledDate = Index(
    'idx_scheduled_date',
    'CREATE INDEX idx_scheduled_date ON review_tasks (scheduled_date)',
  );
  late final Index idxStatus = Index(
    'idx_status',
    'CREATE INDEX idx_status ON review_tasks (status)',
  );
  late final Index idxLearningItemId = Index(
    'idx_learning_item_id',
    'CREATE INDEX idx_learning_item_id ON review_tasks (learning_item_id)',
  );
  late final Index idxCompletedAtStatus = Index(
    'idx_completed_at_status',
    'CREATE INDEX idx_completed_at_status ON review_tasks (completed_at, status)',
  );
  late final Index idxSkippedAtStatus = Index(
    'idx_skipped_at_status',
    'CREATE INDEX idx_skipped_at_status ON review_tasks (skipped_at, status)',
  );
  late final Index idxOccurredAtId = Index(
    'idx_occurred_at_id',
    'CREATE INDEX idx_occurred_at_id ON review_tasks (occurred_at, id)',
  );
  late final Index idxStatusOccurredAtId = Index(
    'idx_status_occurred_at_id',
    'CREATE INDEX idx_status_occurred_at_id ON review_tasks (status, occurred_at, id)',
  );
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    learningItems,
    learningSubtasks,
    reviewTasks,
    reviewRecords,
    appSettingsTable,
    learningTemplates,
    learningTopics,
    topicItemRelations,
    syncDevices,
    syncLogs,
    syncEntityMappings,
    idxLearningDate,
    idxLearningSubtasksItemOrder,
    idxScheduledDate,
    idxStatus,
    idxLearningItemId,
    idxCompletedAtStatus,
    idxSkippedAtStatus,
    idxOccurredAtId,
    idxStatusOccurredAtId,
  ];
  @override
  StreamQueryUpdateRules get streamUpdateRules => const StreamQueryUpdateRules([
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'learning_items',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('learning_subtasks', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'learning_items',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('review_tasks', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'review_tasks',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('review_records', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'learning_topics',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('topic_item_relations', kind: UpdateKind.delete)],
    ),
    WritePropagation(
      on: TableUpdateQuery.onTableName(
        'learning_items',
        limitUpdateKind: UpdateKind.delete,
      ),
      result: [TableUpdate('topic_item_relations', kind: UpdateKind.delete)],
    ),
  ]);
}

typedef $$LearningItemsTableCreateCompanionBuilder =
    LearningItemsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required String title,
      Value<String?> note,
      Value<String?> description,
      Value<String> tags,
      required DateTime learningDate,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isDeleted,
      Value<DateTime?> deletedAt,
      Value<bool> isMockData,
    });
typedef $$LearningItemsTableUpdateCompanionBuilder =
    LearningItemsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<String> title,
      Value<String?> note,
      Value<String?> description,
      Value<String> tags,
      Value<DateTime> learningDate,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isDeleted,
      Value<DateTime?> deletedAt,
      Value<bool> isMockData,
    });

final class $$LearningItemsTableReferences
    extends BaseReferences<_$AppDatabase, $LearningItemsTable, LearningItem> {
  $$LearningItemsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<$LearningSubtasksTable, List<LearningSubtask>>
  _learningSubtasksRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.learningSubtasks,
    aliasName: $_aliasNameGenerator(
      db.learningItems.id,
      db.learningSubtasks.learningItemId,
    ),
  );

  $$LearningSubtasksTableProcessedTableManager get learningSubtasksRefs {
    final manager = $$LearningSubtasksTableTableManager(
      $_db,
      $_db.learningSubtasks,
    ).filter((f) => f.learningItemId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _learningSubtasksRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$ReviewTasksTable, List<ReviewTask>>
  _reviewTasksRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.reviewTasks,
    aliasName: $_aliasNameGenerator(
      db.learningItems.id,
      db.reviewTasks.learningItemId,
    ),
  );

  $$ReviewTasksTableProcessedTableManager get reviewTasksRefs {
    final manager = $$ReviewTasksTableTableManager(
      $_db,
      $_db.reviewTasks,
    ).filter((f) => f.learningItemId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_reviewTasksRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }

  static MultiTypedResultKey<$TopicItemRelationsTable, List<TopicItemRelation>>
  _topicItemRelationsRefsTable(_$AppDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.topicItemRelations,
        aliasName: $_aliasNameGenerator(
          db.learningItems.id,
          db.topicItemRelations.learningItemId,
        ),
      );

  $$TopicItemRelationsTableProcessedTableManager get topicItemRelationsRefs {
    final manager = $$TopicItemRelationsTableTableManager(
      $_db,
      $_db.topicItemRelations,
    ).filter((f) => f.learningItemId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _topicItemRelationsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$LearningItemsTableFilterComposer
    extends Composer<_$AppDatabase, $LearningItemsTable> {
  $$LearningItemsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get learningDate => $composableBuilder(
    column: $table.learningDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> learningSubtasksRefs(
    Expression<bool> Function($$LearningSubtasksTableFilterComposer f) f,
  ) {
    final $$LearningSubtasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.learningSubtasks,
      getReferencedColumn: (t) => t.learningItemId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningSubtasksTableFilterComposer(
            $db: $db,
            $table: $db.learningSubtasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> reviewTasksRefs(
    Expression<bool> Function($$ReviewTasksTableFilterComposer f) f,
  ) {
    final $$ReviewTasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.reviewTasks,
      getReferencedColumn: (t) => t.learningItemId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewTasksTableFilterComposer(
            $db: $db,
            $table: $db.reviewTasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<bool> topicItemRelationsRefs(
    Expression<bool> Function($$TopicItemRelationsTableFilterComposer f) f,
  ) {
    final $$TopicItemRelationsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.topicItemRelations,
      getReferencedColumn: (t) => t.learningItemId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TopicItemRelationsTableFilterComposer(
            $db: $db,
            $table: $db.topicItemRelations,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$LearningItemsTableOrderingComposer
    extends Composer<_$AppDatabase, $LearningItemsTable> {
  $$LearningItemsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get learningDate => $composableBuilder(
    column: $table.learningDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LearningItemsTableAnnotationComposer
    extends Composer<_$AppDatabase, $LearningItemsTable> {
  $$LearningItemsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tags =>
      $composableBuilder(column: $table.tags, builder: (column) => column);

  GeneratedColumn<DateTime> get learningDate => $composableBuilder(
    column: $table.learningDate,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<bool> get isDeleted =>
      $composableBuilder(column: $table.isDeleted, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);

  GeneratedColumn<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => column,
  );

  Expression<T> learningSubtasksRefs<T extends Object>(
    Expression<T> Function($$LearningSubtasksTableAnnotationComposer a) f,
  ) {
    final $$LearningSubtasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.learningSubtasks,
      getReferencedColumn: (t) => t.learningItemId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningSubtasksTableAnnotationComposer(
            $db: $db,
            $table: $db.learningSubtasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> reviewTasksRefs<T extends Object>(
    Expression<T> Function($$ReviewTasksTableAnnotationComposer a) f,
  ) {
    final $$ReviewTasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.reviewTasks,
      getReferencedColumn: (t) => t.learningItemId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewTasksTableAnnotationComposer(
            $db: $db,
            $table: $db.reviewTasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }

  Expression<T> topicItemRelationsRefs<T extends Object>(
    Expression<T> Function($$TopicItemRelationsTableAnnotationComposer a) f,
  ) {
    final $$TopicItemRelationsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.id,
          referencedTable: $db.topicItemRelations,
          getReferencedColumn: (t) => t.learningItemId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$TopicItemRelationsTableAnnotationComposer(
                $db: $db,
                $table: $db.topicItemRelations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$LearningItemsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LearningItemsTable,
          LearningItem,
          $$LearningItemsTableFilterComposer,
          $$LearningItemsTableOrderingComposer,
          $$LearningItemsTableAnnotationComposer,
          $$LearningItemsTableCreateCompanionBuilder,
          $$LearningItemsTableUpdateCompanionBuilder,
          (LearningItem, $$LearningItemsTableReferences),
          LearningItem,
          PrefetchHooks Function({
            bool learningSubtasksRefs,
            bool reviewTasksRefs,
            bool topicItemRelationsRefs,
          })
        > {
  $$LearningItemsTableTableManager(_$AppDatabase db, $LearningItemsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LearningItemsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LearningItemsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LearningItemsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String?> note = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String> tags = const Value.absent(),
                Value<DateTime> learningDate = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => LearningItemsCompanion(
                id: id,
                uuid: uuid,
                title: title,
                note: note,
                description: description,
                tags: tags,
                learningDate: learningDate,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isDeleted: isDeleted,
                deletedAt: deletedAt,
                isMockData: isMockData,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required String title,
                Value<String?> note = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String> tags = const Value.absent(),
                required DateTime learningDate,
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => LearningItemsCompanion.insert(
                id: id,
                uuid: uuid,
                title: title,
                note: note,
                description: description,
                tags: tags,
                learningDate: learningDate,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isDeleted: isDeleted,
                deletedAt: deletedAt,
                isMockData: isMockData,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$LearningItemsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({
                learningSubtasksRefs = false,
                reviewTasksRefs = false,
                topicItemRelationsRefs = false,
              }) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (learningSubtasksRefs) db.learningSubtasks,
                    if (reviewTasksRefs) db.reviewTasks,
                    if (topicItemRelationsRefs) db.topicItemRelations,
                  ],
                  addJoins: null,
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (learningSubtasksRefs)
                        await $_getPrefetchedData<
                          LearningItem,
                          $LearningItemsTable,
                          LearningSubtask
                        >(
                          currentTable: table,
                          referencedTable: $$LearningItemsTableReferences
                              ._learningSubtasksRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$LearningItemsTableReferences(
                                db,
                                table,
                                p0,
                              ).learningSubtasksRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.learningItemId == item.id,
                              ),
                          typedResults: items,
                        ),
                      if (reviewTasksRefs)
                        await $_getPrefetchedData<
                          LearningItem,
                          $LearningItemsTable,
                          ReviewTask
                        >(
                          currentTable: table,
                          referencedTable: $$LearningItemsTableReferences
                              ._reviewTasksRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$LearningItemsTableReferences(
                                db,
                                table,
                                p0,
                              ).reviewTasksRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.learningItemId == item.id,
                              ),
                          typedResults: items,
                        ),
                      if (topicItemRelationsRefs)
                        await $_getPrefetchedData<
                          LearningItem,
                          $LearningItemsTable,
                          TopicItemRelation
                        >(
                          currentTable: table,
                          referencedTable: $$LearningItemsTableReferences
                              ._topicItemRelationsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$LearningItemsTableReferences(
                                db,
                                table,
                                p0,
                              ).topicItemRelationsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.learningItemId == item.id,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$LearningItemsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LearningItemsTable,
      LearningItem,
      $$LearningItemsTableFilterComposer,
      $$LearningItemsTableOrderingComposer,
      $$LearningItemsTableAnnotationComposer,
      $$LearningItemsTableCreateCompanionBuilder,
      $$LearningItemsTableUpdateCompanionBuilder,
      (LearningItem, $$LearningItemsTableReferences),
      LearningItem,
      PrefetchHooks Function({
        bool learningSubtasksRefs,
        bool reviewTasksRefs,
        bool topicItemRelationsRefs,
      })
    >;
typedef $$LearningSubtasksTableCreateCompanionBuilder =
    LearningSubtasksCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required int learningItemId,
      required String content,
      Value<int> sortOrder,
      required DateTime createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isMockData,
    });
typedef $$LearningSubtasksTableUpdateCompanionBuilder =
    LearningSubtasksCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<int> learningItemId,
      Value<String> content,
      Value<int> sortOrder,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isMockData,
    });

final class $$LearningSubtasksTableReferences
    extends
        BaseReferences<_$AppDatabase, $LearningSubtasksTable, LearningSubtask> {
  $$LearningSubtasksTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $LearningItemsTable _learningItemIdTable(_$AppDatabase db) =>
      db.learningItems.createAlias(
        $_aliasNameGenerator(
          db.learningSubtasks.learningItemId,
          db.learningItems.id,
        ),
      );

  $$LearningItemsTableProcessedTableManager get learningItemId {
    final $_column = $_itemColumn<int>('learning_item_id')!;

    final manager = $$LearningItemsTableTableManager(
      $_db,
      $_db.learningItems,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_learningItemIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$LearningSubtasksTableFilterComposer
    extends Composer<_$AppDatabase, $LearningSubtasksTable> {
  $$LearningSubtasksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnFilters(column),
  );

  $$LearningItemsTableFilterComposer get learningItemId {
    final $$LearningItemsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableFilterComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$LearningSubtasksTableOrderingComposer
    extends Composer<_$AppDatabase, $LearningSubtasksTable> {
  $$LearningSubtasksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get content => $composableBuilder(
    column: $table.content,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnOrderings(column),
  );

  $$LearningItemsTableOrderingComposer get learningItemId {
    final $$LearningItemsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableOrderingComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$LearningSubtasksTableAnnotationComposer
    extends Composer<_$AppDatabase, $LearningSubtasksTable> {
  $$LearningSubtasksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<String> get content =>
      $composableBuilder(column: $table.content, builder: (column) => column);

  GeneratedColumn<int> get sortOrder =>
      $composableBuilder(column: $table.sortOrder, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => column,
  );

  $$LearningItemsTableAnnotationComposer get learningItemId {
    final $$LearningItemsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableAnnotationComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$LearningSubtasksTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LearningSubtasksTable,
          LearningSubtask,
          $$LearningSubtasksTableFilterComposer,
          $$LearningSubtasksTableOrderingComposer,
          $$LearningSubtasksTableAnnotationComposer,
          $$LearningSubtasksTableCreateCompanionBuilder,
          $$LearningSubtasksTableUpdateCompanionBuilder,
          (LearningSubtask, $$LearningSubtasksTableReferences),
          LearningSubtask,
          PrefetchHooks Function({bool learningItemId})
        > {
  $$LearningSubtasksTableTableManager(
    _$AppDatabase db,
    $LearningSubtasksTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LearningSubtasksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LearningSubtasksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LearningSubtasksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<int> learningItemId = const Value.absent(),
                Value<String> content = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => LearningSubtasksCompanion(
                id: id,
                uuid: uuid,
                learningItemId: learningItemId,
                content: content,
                sortOrder: sortOrder,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isMockData: isMockData,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required int learningItemId,
                required String content,
                Value<int> sortOrder = const Value.absent(),
                required DateTime createdAt,
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => LearningSubtasksCompanion.insert(
                id: id,
                uuid: uuid,
                learningItemId: learningItemId,
                content: content,
                sortOrder: sortOrder,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isMockData: isMockData,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$LearningSubtasksTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({learningItemId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (learningItemId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.learningItemId,
                                referencedTable:
                                    $$LearningSubtasksTableReferences
                                        ._learningItemIdTable(db),
                                referencedColumn:
                                    $$LearningSubtasksTableReferences
                                        ._learningItemIdTable(db)
                                        .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$LearningSubtasksTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LearningSubtasksTable,
      LearningSubtask,
      $$LearningSubtasksTableFilterComposer,
      $$LearningSubtasksTableOrderingComposer,
      $$LearningSubtasksTableAnnotationComposer,
      $$LearningSubtasksTableCreateCompanionBuilder,
      $$LearningSubtasksTableUpdateCompanionBuilder,
      (LearningSubtask, $$LearningSubtasksTableReferences),
      LearningSubtask,
      PrefetchHooks Function({bool learningItemId})
    >;
typedef $$ReviewTasksTableCreateCompanionBuilder =
    ReviewTasksCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required int learningItemId,
      required int reviewRound,
      required DateTime scheduledDate,
      Value<DateTime?> occurredAt,
      Value<String> status,
      Value<DateTime?> completedAt,
      Value<DateTime?> skippedAt,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isMockData,
    });
typedef $$ReviewTasksTableUpdateCompanionBuilder =
    ReviewTasksCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<int> learningItemId,
      Value<int> reviewRound,
      Value<DateTime> scheduledDate,
      Value<DateTime?> occurredAt,
      Value<String> status,
      Value<DateTime?> completedAt,
      Value<DateTime?> skippedAt,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<bool> isMockData,
    });

final class $$ReviewTasksTableReferences
    extends BaseReferences<_$AppDatabase, $ReviewTasksTable, ReviewTask> {
  $$ReviewTasksTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $LearningItemsTable _learningItemIdTable(_$AppDatabase db) =>
      db.learningItems.createAlias(
        $_aliasNameGenerator(
          db.reviewTasks.learningItemId,
          db.learningItems.id,
        ),
      );

  $$LearningItemsTableProcessedTableManager get learningItemId {
    final $_column = $_itemColumn<int>('learning_item_id')!;

    final manager = $$LearningItemsTableTableManager(
      $_db,
      $_db.learningItems,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_learningItemIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static MultiTypedResultKey<$ReviewRecordsTable, List<ReviewRecord>>
  _reviewRecordsRefsTable(_$AppDatabase db) => MultiTypedResultKey.fromTable(
    db.reviewRecords,
    aliasName: $_aliasNameGenerator(
      db.reviewTasks.id,
      db.reviewRecords.reviewTaskId,
    ),
  );

  $$ReviewRecordsTableProcessedTableManager get reviewRecordsRefs {
    final manager = $$ReviewRecordsTableTableManager(
      $_db,
      $_db.reviewRecords,
    ).filter((f) => f.reviewTaskId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(_reviewRecordsRefsTable($_db));
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$ReviewTasksTableFilterComposer
    extends Composer<_$AppDatabase, $ReviewTasksTable> {
  $$ReviewTasksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get reviewRound => $composableBuilder(
    column: $table.reviewRound,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get scheduledDate => $composableBuilder(
    column: $table.scheduledDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get skippedAt => $composableBuilder(
    column: $table.skippedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnFilters(column),
  );

  $$LearningItemsTableFilterComposer get learningItemId {
    final $$LearningItemsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableFilterComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<bool> reviewRecordsRefs(
    Expression<bool> Function($$ReviewRecordsTableFilterComposer f) f,
  ) {
    final $$ReviewRecordsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.reviewRecords,
      getReferencedColumn: (t) => t.reviewTaskId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewRecordsTableFilterComposer(
            $db: $db,
            $table: $db.reviewRecords,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$ReviewTasksTableOrderingComposer
    extends Composer<_$AppDatabase, $ReviewTasksTable> {
  $$ReviewTasksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get reviewRound => $composableBuilder(
    column: $table.reviewRound,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get scheduledDate => $composableBuilder(
    column: $table.scheduledDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get skippedAt => $composableBuilder(
    column: $table.skippedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => ColumnOrderings(column),
  );

  $$LearningItemsTableOrderingComposer get learningItemId {
    final $$LearningItemsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableOrderingComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ReviewTasksTableAnnotationComposer
    extends Composer<_$AppDatabase, $ReviewTasksTable> {
  $$ReviewTasksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<int> get reviewRound => $composableBuilder(
    column: $table.reviewRound,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get scheduledDate => $composableBuilder(
    column: $table.scheduledDate,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<DateTime> get completedAt => $composableBuilder(
    column: $table.completedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get skippedAt =>
      $composableBuilder(column: $table.skippedAt, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<bool> get isMockData => $composableBuilder(
    column: $table.isMockData,
    builder: (column) => column,
  );

  $$LearningItemsTableAnnotationComposer get learningItemId {
    final $$LearningItemsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableAnnotationComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  Expression<T> reviewRecordsRefs<T extends Object>(
    Expression<T> Function($$ReviewRecordsTableAnnotationComposer a) f,
  ) {
    final $$ReviewRecordsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.reviewRecords,
      getReferencedColumn: (t) => t.reviewTaskId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewRecordsTableAnnotationComposer(
            $db: $db,
            $table: $db.reviewRecords,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$ReviewTasksTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $ReviewTasksTable,
          ReviewTask,
          $$ReviewTasksTableFilterComposer,
          $$ReviewTasksTableOrderingComposer,
          $$ReviewTasksTableAnnotationComposer,
          $$ReviewTasksTableCreateCompanionBuilder,
          $$ReviewTasksTableUpdateCompanionBuilder,
          (ReviewTask, $$ReviewTasksTableReferences),
          ReviewTask,
          PrefetchHooks Function({bool learningItemId, bool reviewRecordsRefs})
        > {
  $$ReviewTasksTableTableManager(_$AppDatabase db, $ReviewTasksTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ReviewTasksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ReviewTasksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ReviewTasksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<int> learningItemId = const Value.absent(),
                Value<int> reviewRound = const Value.absent(),
                Value<DateTime> scheduledDate = const Value.absent(),
                Value<DateTime?> occurredAt = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<DateTime?> skippedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => ReviewTasksCompanion(
                id: id,
                uuid: uuid,
                learningItemId: learningItemId,
                reviewRound: reviewRound,
                scheduledDate: scheduledDate,
                occurredAt: occurredAt,
                status: status,
                completedAt: completedAt,
                skippedAt: skippedAt,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isMockData: isMockData,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required int learningItemId,
                required int reviewRound,
                required DateTime scheduledDate,
                Value<DateTime?> occurredAt = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<DateTime?> completedAt = const Value.absent(),
                Value<DateTime?> skippedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<bool> isMockData = const Value.absent(),
              }) => ReviewTasksCompanion.insert(
                id: id,
                uuid: uuid,
                learningItemId: learningItemId,
                reviewRound: reviewRound,
                scheduledDate: scheduledDate,
                occurredAt: occurredAt,
                status: status,
                completedAt: completedAt,
                skippedAt: skippedAt,
                createdAt: createdAt,
                updatedAt: updatedAt,
                isMockData: isMockData,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$ReviewTasksTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback:
              ({learningItemId = false, reviewRecordsRefs = false}) {
                return PrefetchHooks(
                  db: db,
                  explicitlyWatchedTables: [
                    if (reviewRecordsRefs) db.reviewRecords,
                  ],
                  addJoins:
                      <
                        T extends TableManagerState<
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic,
                          dynamic
                        >
                      >(state) {
                        if (learningItemId) {
                          state =
                              state.withJoin(
                                    currentTable: table,
                                    currentColumn: table.learningItemId,
                                    referencedTable:
                                        $$ReviewTasksTableReferences
                                            ._learningItemIdTable(db),
                                    referencedColumn:
                                        $$ReviewTasksTableReferences
                                            ._learningItemIdTable(db)
                                            .id,
                                  )
                                  as T;
                        }

                        return state;
                      },
                  getPrefetchedDataCallback: (items) async {
                    return [
                      if (reviewRecordsRefs)
                        await $_getPrefetchedData<
                          ReviewTask,
                          $ReviewTasksTable,
                          ReviewRecord
                        >(
                          currentTable: table,
                          referencedTable: $$ReviewTasksTableReferences
                              ._reviewRecordsRefsTable(db),
                          managerFromTypedResult: (p0) =>
                              $$ReviewTasksTableReferences(
                                db,
                                table,
                                p0,
                              ).reviewRecordsRefs,
                          referencedItemsForCurrentItem:
                              (item, referencedItems) => referencedItems.where(
                                (e) => e.reviewTaskId == item.id,
                              ),
                          typedResults: items,
                        ),
                    ];
                  },
                );
              },
        ),
      );
}

typedef $$ReviewTasksTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $ReviewTasksTable,
      ReviewTask,
      $$ReviewTasksTableFilterComposer,
      $$ReviewTasksTableOrderingComposer,
      $$ReviewTasksTableAnnotationComposer,
      $$ReviewTasksTableCreateCompanionBuilder,
      $$ReviewTasksTableUpdateCompanionBuilder,
      (ReviewTask, $$ReviewTasksTableReferences),
      ReviewTask,
      PrefetchHooks Function({bool learningItemId, bool reviewRecordsRefs})
    >;
typedef $$ReviewRecordsTableCreateCompanionBuilder =
    ReviewRecordsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required int reviewTaskId,
      required String action,
      required DateTime occurredAt,
      Value<DateTime> createdAt,
    });
typedef $$ReviewRecordsTableUpdateCompanionBuilder =
    ReviewRecordsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<int> reviewTaskId,
      Value<String> action,
      Value<DateTime> occurredAt,
      Value<DateTime> createdAt,
    });

final class $$ReviewRecordsTableReferences
    extends BaseReferences<_$AppDatabase, $ReviewRecordsTable, ReviewRecord> {
  $$ReviewRecordsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $ReviewTasksTable _reviewTaskIdTable(_$AppDatabase db) =>
      db.reviewTasks.createAlias(
        $_aliasNameGenerator(db.reviewRecords.reviewTaskId, db.reviewTasks.id),
      );

  $$ReviewTasksTableProcessedTableManager get reviewTaskId {
    final $_column = $_itemColumn<int>('review_task_id')!;

    final manager = $$ReviewTasksTableTableManager(
      $_db,
      $_db.reviewTasks,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_reviewTaskIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$ReviewRecordsTableFilterComposer
    extends Composer<_$AppDatabase, $ReviewRecordsTable> {
  $$ReviewRecordsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get action => $composableBuilder(
    column: $table.action,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$ReviewTasksTableFilterComposer get reviewTaskId {
    final $$ReviewTasksTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.reviewTaskId,
      referencedTable: $db.reviewTasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewTasksTableFilterComposer(
            $db: $db,
            $table: $db.reviewTasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ReviewRecordsTableOrderingComposer
    extends Composer<_$AppDatabase, $ReviewRecordsTable> {
  $$ReviewRecordsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get action => $composableBuilder(
    column: $table.action,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$ReviewTasksTableOrderingComposer get reviewTaskId {
    final $$ReviewTasksTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.reviewTaskId,
      referencedTable: $db.reviewTasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewTasksTableOrderingComposer(
            $db: $db,
            $table: $db.reviewTasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ReviewRecordsTableAnnotationComposer
    extends Composer<_$AppDatabase, $ReviewRecordsTable> {
  $$ReviewRecordsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<String> get action =>
      $composableBuilder(column: $table.action, builder: (column) => column);

  GeneratedColumn<DateTime> get occurredAt => $composableBuilder(
    column: $table.occurredAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$ReviewTasksTableAnnotationComposer get reviewTaskId {
    final $$ReviewTasksTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.reviewTaskId,
      referencedTable: $db.reviewTasks,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$ReviewTasksTableAnnotationComposer(
            $db: $db,
            $table: $db.reviewTasks,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$ReviewRecordsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $ReviewRecordsTable,
          ReviewRecord,
          $$ReviewRecordsTableFilterComposer,
          $$ReviewRecordsTableOrderingComposer,
          $$ReviewRecordsTableAnnotationComposer,
          $$ReviewRecordsTableCreateCompanionBuilder,
          $$ReviewRecordsTableUpdateCompanionBuilder,
          (ReviewRecord, $$ReviewRecordsTableReferences),
          ReviewRecord,
          PrefetchHooks Function({bool reviewTaskId})
        > {
  $$ReviewRecordsTableTableManager(_$AppDatabase db, $ReviewRecordsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$ReviewRecordsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$ReviewRecordsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$ReviewRecordsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<int> reviewTaskId = const Value.absent(),
                Value<String> action = const Value.absent(),
                Value<DateTime> occurredAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => ReviewRecordsCompanion(
                id: id,
                uuid: uuid,
                reviewTaskId: reviewTaskId,
                action: action,
                occurredAt: occurredAt,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required int reviewTaskId,
                required String action,
                required DateTime occurredAt,
                Value<DateTime> createdAt = const Value.absent(),
              }) => ReviewRecordsCompanion.insert(
                id: id,
                uuid: uuid,
                reviewTaskId: reviewTaskId,
                action: action,
                occurredAt: occurredAt,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$ReviewRecordsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({reviewTaskId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (reviewTaskId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.reviewTaskId,
                                referencedTable: $$ReviewRecordsTableReferences
                                    ._reviewTaskIdTable(db),
                                referencedColumn: $$ReviewRecordsTableReferences
                                    ._reviewTaskIdTable(db)
                                    .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$ReviewRecordsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $ReviewRecordsTable,
      ReviewRecord,
      $$ReviewRecordsTableFilterComposer,
      $$ReviewRecordsTableOrderingComposer,
      $$ReviewRecordsTableAnnotationComposer,
      $$ReviewRecordsTableCreateCompanionBuilder,
      $$ReviewRecordsTableUpdateCompanionBuilder,
      (ReviewRecord, $$ReviewRecordsTableReferences),
      ReviewRecord,
      PrefetchHooks Function({bool reviewTaskId})
    >;
typedef $$AppSettingsTableTableCreateCompanionBuilder =
    AppSettingsTableCompanion Function({
      Value<int> id,
      required String key,
      required String value,
      Value<DateTime> updatedAt,
    });
typedef $$AppSettingsTableTableUpdateCompanionBuilder =
    AppSettingsTableCompanion Function({
      Value<int> id,
      Value<String> key,
      Value<String> value,
      Value<DateTime> updatedAt,
    });

class $$AppSettingsTableTableFilterComposer
    extends Composer<_$AppDatabase, $AppSettingsTableTable> {
  $$AppSettingsTableTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get key => $composableBuilder(
    column: $table.key,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AppSettingsTableTableOrderingComposer
    extends Composer<_$AppDatabase, $AppSettingsTableTable> {
  $$AppSettingsTableTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get key => $composableBuilder(
    column: $table.key,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get value => $composableBuilder(
    column: $table.value,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AppSettingsTableTableAnnotationComposer
    extends Composer<_$AppDatabase, $AppSettingsTableTable> {
  $$AppSettingsTableTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get key =>
      $composableBuilder(column: $table.key, builder: (column) => column);

  GeneratedColumn<String> get value =>
      $composableBuilder(column: $table.value, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$AppSettingsTableTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $AppSettingsTableTable,
          AppSettingsTableData,
          $$AppSettingsTableTableFilterComposer,
          $$AppSettingsTableTableOrderingComposer,
          $$AppSettingsTableTableAnnotationComposer,
          $$AppSettingsTableTableCreateCompanionBuilder,
          $$AppSettingsTableTableUpdateCompanionBuilder,
          (
            AppSettingsTableData,
            BaseReferences<
              _$AppDatabase,
              $AppSettingsTableTable,
              AppSettingsTableData
            >,
          ),
          AppSettingsTableData,
          PrefetchHooks Function()
        > {
  $$AppSettingsTableTableTableManager(
    _$AppDatabase db,
    $AppSettingsTableTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AppSettingsTableTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AppSettingsTableTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AppSettingsTableTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> key = const Value.absent(),
                Value<String> value = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
              }) => AppSettingsTableCompanion(
                id: id,
                key: key,
                value: value,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String key,
                required String value,
                Value<DateTime> updatedAt = const Value.absent(),
              }) => AppSettingsTableCompanion.insert(
                id: id,
                key: key,
                value: value,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AppSettingsTableTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $AppSettingsTableTable,
      AppSettingsTableData,
      $$AppSettingsTableTableFilterComposer,
      $$AppSettingsTableTableOrderingComposer,
      $$AppSettingsTableTableAnnotationComposer,
      $$AppSettingsTableTableCreateCompanionBuilder,
      $$AppSettingsTableTableUpdateCompanionBuilder,
      (
        AppSettingsTableData,
        BaseReferences<
          _$AppDatabase,
          $AppSettingsTableTable,
          AppSettingsTableData
        >,
      ),
      AppSettingsTableData,
      PrefetchHooks Function()
    >;
typedef $$LearningTemplatesTableCreateCompanionBuilder =
    LearningTemplatesCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required String name,
      required String titlePattern,
      Value<String?> notePattern,
      Value<String> tags,
      Value<int> sortOrder,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
    });
typedef $$LearningTemplatesTableUpdateCompanionBuilder =
    LearningTemplatesCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<String> name,
      Value<String> titlePattern,
      Value<String?> notePattern,
      Value<String> tags,
      Value<int> sortOrder,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
    });

class $$LearningTemplatesTableFilterComposer
    extends Composer<_$AppDatabase, $LearningTemplatesTable> {
  $$LearningTemplatesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get titlePattern => $composableBuilder(
    column: $table.titlePattern,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notePattern => $composableBuilder(
    column: $table.notePattern,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LearningTemplatesTableOrderingComposer
    extends Composer<_$AppDatabase, $LearningTemplatesTable> {
  $$LearningTemplatesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get titlePattern => $composableBuilder(
    column: $table.titlePattern,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notePattern => $composableBuilder(
    column: $table.notePattern,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tags => $composableBuilder(
    column: $table.tags,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get sortOrder => $composableBuilder(
    column: $table.sortOrder,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LearningTemplatesTableAnnotationComposer
    extends Composer<_$AppDatabase, $LearningTemplatesTable> {
  $$LearningTemplatesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get titlePattern => $composableBuilder(
    column: $table.titlePattern,
    builder: (column) => column,
  );

  GeneratedColumn<String> get notePattern => $composableBuilder(
    column: $table.notePattern,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tags =>
      $composableBuilder(column: $table.tags, builder: (column) => column);

  GeneratedColumn<int> get sortOrder =>
      $composableBuilder(column: $table.sortOrder, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$LearningTemplatesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LearningTemplatesTable,
          LearningTemplate,
          $$LearningTemplatesTableFilterComposer,
          $$LearningTemplatesTableOrderingComposer,
          $$LearningTemplatesTableAnnotationComposer,
          $$LearningTemplatesTableCreateCompanionBuilder,
          $$LearningTemplatesTableUpdateCompanionBuilder,
          (
            LearningTemplate,
            BaseReferences<
              _$AppDatabase,
              $LearningTemplatesTable,
              LearningTemplate
            >,
          ),
          LearningTemplate,
          PrefetchHooks Function()
        > {
  $$LearningTemplatesTableTableManager(
    _$AppDatabase db,
    $LearningTemplatesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LearningTemplatesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LearningTemplatesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LearningTemplatesTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> titlePattern = const Value.absent(),
                Value<String?> notePattern = const Value.absent(),
                Value<String> tags = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
              }) => LearningTemplatesCompanion(
                id: id,
                uuid: uuid,
                name: name,
                titlePattern: titlePattern,
                notePattern: notePattern,
                tags: tags,
                sortOrder: sortOrder,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required String name,
                required String titlePattern,
                Value<String?> notePattern = const Value.absent(),
                Value<String> tags = const Value.absent(),
                Value<int> sortOrder = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
              }) => LearningTemplatesCompanion.insert(
                id: id,
                uuid: uuid,
                name: name,
                titlePattern: titlePattern,
                notePattern: notePattern,
                tags: tags,
                sortOrder: sortOrder,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LearningTemplatesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LearningTemplatesTable,
      LearningTemplate,
      $$LearningTemplatesTableFilterComposer,
      $$LearningTemplatesTableOrderingComposer,
      $$LearningTemplatesTableAnnotationComposer,
      $$LearningTemplatesTableCreateCompanionBuilder,
      $$LearningTemplatesTableUpdateCompanionBuilder,
      (
        LearningTemplate,
        BaseReferences<
          _$AppDatabase,
          $LearningTemplatesTable,
          LearningTemplate
        >,
      ),
      LearningTemplate,
      PrefetchHooks Function()
    >;
typedef $$LearningTopicsTableCreateCompanionBuilder =
    LearningTopicsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      required String name,
      Value<String?> description,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
    });
typedef $$LearningTopicsTableUpdateCompanionBuilder =
    LearningTopicsCompanion Function({
      Value<int> id,
      Value<String> uuid,
      Value<String> name,
      Value<String?> description,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
    });

final class $$LearningTopicsTableReferences
    extends BaseReferences<_$AppDatabase, $LearningTopicsTable, LearningTopic> {
  $$LearningTopicsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static MultiTypedResultKey<$TopicItemRelationsTable, List<TopicItemRelation>>
  _topicItemRelationsRefsTable(_$AppDatabase db) =>
      MultiTypedResultKey.fromTable(
        db.topicItemRelations,
        aliasName: $_aliasNameGenerator(
          db.learningTopics.id,
          db.topicItemRelations.topicId,
        ),
      );

  $$TopicItemRelationsTableProcessedTableManager get topicItemRelationsRefs {
    final manager = $$TopicItemRelationsTableTableManager(
      $_db,
      $_db.topicItemRelations,
    ).filter((f) => f.topicId.id.sqlEquals($_itemColumn<int>('id')!));

    final cache = $_typedResult.readTableOrNull(
      _topicItemRelationsRefsTable($_db),
    );
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: cache),
    );
  }
}

class $$LearningTopicsTableFilterComposer
    extends Composer<_$AppDatabase, $LearningTopicsTable> {
  $$LearningTopicsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  Expression<bool> topicItemRelationsRefs(
    Expression<bool> Function($$TopicItemRelationsTableFilterComposer f) f,
  ) {
    final $$TopicItemRelationsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.id,
      referencedTable: $db.topicItemRelations,
      getReferencedColumn: (t) => t.topicId,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$TopicItemRelationsTableFilterComposer(
            $db: $db,
            $table: $db.topicItemRelations,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return f(composer);
  }
}

class $$LearningTopicsTableOrderingComposer
    extends Composer<_$AppDatabase, $LearningTopicsTable> {
  $$LearningTopicsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get uuid => $composableBuilder(
    column: $table.uuid,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LearningTopicsTableAnnotationComposer
    extends Composer<_$AppDatabase, $LearningTopicsTable> {
  $$LearningTopicsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get uuid =>
      $composableBuilder(column: $table.uuid, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  Expression<T> topicItemRelationsRefs<T extends Object>(
    Expression<T> Function($$TopicItemRelationsTableAnnotationComposer a) f,
  ) {
    final $$TopicItemRelationsTableAnnotationComposer composer =
        $composerBuilder(
          composer: this,
          getCurrentColumn: (t) => t.id,
          referencedTable: $db.topicItemRelations,
          getReferencedColumn: (t) => t.topicId,
          builder:
              (
                joinBuilder, {
                $addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer,
              }) => $$TopicItemRelationsTableAnnotationComposer(
                $db: $db,
                $table: $db.topicItemRelations,
                $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
                joinBuilder: joinBuilder,
                $removeJoinBuilderFromRootComposer:
                    $removeJoinBuilderFromRootComposer,
              ),
        );
    return f(composer);
  }
}

class $$LearningTopicsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LearningTopicsTable,
          LearningTopic,
          $$LearningTopicsTableFilterComposer,
          $$LearningTopicsTableOrderingComposer,
          $$LearningTopicsTableAnnotationComposer,
          $$LearningTopicsTableCreateCompanionBuilder,
          $$LearningTopicsTableUpdateCompanionBuilder,
          (LearningTopic, $$LearningTopicsTableReferences),
          LearningTopic,
          PrefetchHooks Function({bool topicItemRelationsRefs})
        > {
  $$LearningTopicsTableTableManager(
    _$AppDatabase db,
    $LearningTopicsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LearningTopicsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LearningTopicsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LearningTopicsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
              }) => LearningTopicsCompanion(
                id: id,
                uuid: uuid,
                name: name,
                description: description,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> uuid = const Value.absent(),
                required String name,
                Value<String?> description = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
              }) => LearningTopicsCompanion.insert(
                id: id,
                uuid: uuid,
                name: name,
                description: description,
                createdAt: createdAt,
                updatedAt: updatedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$LearningTopicsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({topicItemRelationsRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [
                if (topicItemRelationsRefs) db.topicItemRelations,
              ],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (topicItemRelationsRefs)
                    await $_getPrefetchedData<
                      LearningTopic,
                      $LearningTopicsTable,
                      TopicItemRelation
                    >(
                      currentTable: table,
                      referencedTable: $$LearningTopicsTableReferences
                          ._topicItemRelationsRefsTable(db),
                      managerFromTypedResult: (p0) =>
                          $$LearningTopicsTableReferences(
                            db,
                            table,
                            p0,
                          ).topicItemRelationsRefs,
                      referencedItemsForCurrentItem: (item, referencedItems) =>
                          referencedItems.where((e) => e.topicId == item.id),
                      typedResults: items,
                    ),
                ];
              },
            );
          },
        ),
      );
}

typedef $$LearningTopicsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LearningTopicsTable,
      LearningTopic,
      $$LearningTopicsTableFilterComposer,
      $$LearningTopicsTableOrderingComposer,
      $$LearningTopicsTableAnnotationComposer,
      $$LearningTopicsTableCreateCompanionBuilder,
      $$LearningTopicsTableUpdateCompanionBuilder,
      (LearningTopic, $$LearningTopicsTableReferences),
      LearningTopic,
      PrefetchHooks Function({bool topicItemRelationsRefs})
    >;
typedef $$TopicItemRelationsTableCreateCompanionBuilder =
    TopicItemRelationsCompanion Function({
      Value<int> id,
      required int topicId,
      required int learningItemId,
      Value<DateTime> createdAt,
    });
typedef $$TopicItemRelationsTableUpdateCompanionBuilder =
    TopicItemRelationsCompanion Function({
      Value<int> id,
      Value<int> topicId,
      Value<int> learningItemId,
      Value<DateTime> createdAt,
    });

final class $$TopicItemRelationsTableReferences
    extends
        BaseReferences<
          _$AppDatabase,
          $TopicItemRelationsTable,
          TopicItemRelation
        > {
  $$TopicItemRelationsTableReferences(
    super.$_db,
    super.$_table,
    super.$_typedResult,
  );

  static $LearningTopicsTable _topicIdTable(_$AppDatabase db) =>
      db.learningTopics.createAlias(
        $_aliasNameGenerator(
          db.topicItemRelations.topicId,
          db.learningTopics.id,
        ),
      );

  $$LearningTopicsTableProcessedTableManager get topicId {
    final $_column = $_itemColumn<int>('topic_id')!;

    final manager = $$LearningTopicsTableTableManager(
      $_db,
      $_db.learningTopics,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_topicIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }

  static $LearningItemsTable _learningItemIdTable(_$AppDatabase db) =>
      db.learningItems.createAlias(
        $_aliasNameGenerator(
          db.topicItemRelations.learningItemId,
          db.learningItems.id,
        ),
      );

  $$LearningItemsTableProcessedTableManager get learningItemId {
    final $_column = $_itemColumn<int>('learning_item_id')!;

    final manager = $$LearningItemsTableTableManager(
      $_db,
      $_db.learningItems,
    ).filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_learningItemIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
      manager.$state.copyWith(prefetchedData: [item]),
    );
  }
}

class $$TopicItemRelationsTableFilterComposer
    extends Composer<_$AppDatabase, $TopicItemRelationsTable> {
  $$TopicItemRelationsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  $$LearningTopicsTableFilterComposer get topicId {
    final $$LearningTopicsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.topicId,
      referencedTable: $db.learningTopics,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningTopicsTableFilterComposer(
            $db: $db,
            $table: $db.learningTopics,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$LearningItemsTableFilterComposer get learningItemId {
    final $$LearningItemsTableFilterComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableFilterComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TopicItemRelationsTableOrderingComposer
    extends Composer<_$AppDatabase, $TopicItemRelationsTable> {
  $$TopicItemRelationsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  $$LearningTopicsTableOrderingComposer get topicId {
    final $$LearningTopicsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.topicId,
      referencedTable: $db.learningTopics,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningTopicsTableOrderingComposer(
            $db: $db,
            $table: $db.learningTopics,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$LearningItemsTableOrderingComposer get learningItemId {
    final $$LearningItemsTableOrderingComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableOrderingComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TopicItemRelationsTableAnnotationComposer
    extends Composer<_$AppDatabase, $TopicItemRelationsTable> {
  $$TopicItemRelationsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  $$LearningTopicsTableAnnotationComposer get topicId {
    final $$LearningTopicsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.topicId,
      referencedTable: $db.learningTopics,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningTopicsTableAnnotationComposer(
            $db: $db,
            $table: $db.learningTopics,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }

  $$LearningItemsTableAnnotationComposer get learningItemId {
    final $$LearningItemsTableAnnotationComposer composer = $composerBuilder(
      composer: this,
      getCurrentColumn: (t) => t.learningItemId,
      referencedTable: $db.learningItems,
      getReferencedColumn: (t) => t.id,
      builder:
          (
            joinBuilder, {
            $addJoinBuilderToRootComposer,
            $removeJoinBuilderFromRootComposer,
          }) => $$LearningItemsTableAnnotationComposer(
            $db: $db,
            $table: $db.learningItems,
            $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
            joinBuilder: joinBuilder,
            $removeJoinBuilderFromRootComposer:
                $removeJoinBuilderFromRootComposer,
          ),
    );
    return composer;
  }
}

class $$TopicItemRelationsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TopicItemRelationsTable,
          TopicItemRelation,
          $$TopicItemRelationsTableFilterComposer,
          $$TopicItemRelationsTableOrderingComposer,
          $$TopicItemRelationsTableAnnotationComposer,
          $$TopicItemRelationsTableCreateCompanionBuilder,
          $$TopicItemRelationsTableUpdateCompanionBuilder,
          (TopicItemRelation, $$TopicItemRelationsTableReferences),
          TopicItemRelation,
          PrefetchHooks Function({bool topicId, bool learningItemId})
        > {
  $$TopicItemRelationsTableTableManager(
    _$AppDatabase db,
    $TopicItemRelationsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TopicItemRelationsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TopicItemRelationsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TopicItemRelationsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> topicId = const Value.absent(),
                Value<int> learningItemId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => TopicItemRelationsCompanion(
                id: id,
                topicId: topicId,
                learningItemId: learningItemId,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int topicId,
                required int learningItemId,
                Value<DateTime> createdAt = const Value.absent(),
              }) => TopicItemRelationsCompanion.insert(
                id: id,
                topicId: topicId,
                learningItemId: learningItemId,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map(
                (e) => (
                  e.readTable(table),
                  $$TopicItemRelationsTableReferences(db, table, e),
                ),
              )
              .toList(),
          prefetchHooksCallback: ({topicId = false, learningItemId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins:
                  <
                    T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic
                    >
                  >(state) {
                    if (topicId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.topicId,
                                referencedTable:
                                    $$TopicItemRelationsTableReferences
                                        ._topicIdTable(db),
                                referencedColumn:
                                    $$TopicItemRelationsTableReferences
                                        ._topicIdTable(db)
                                        .id,
                              )
                              as T;
                    }
                    if (learningItemId) {
                      state =
                          state.withJoin(
                                currentTable: table,
                                currentColumn: table.learningItemId,
                                referencedTable:
                                    $$TopicItemRelationsTableReferences
                                        ._learningItemIdTable(db),
                                referencedColumn:
                                    $$TopicItemRelationsTableReferences
                                        ._learningItemIdTable(db)
                                        .id,
                              )
                              as T;
                    }

                    return state;
                  },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ),
      );
}

typedef $$TopicItemRelationsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TopicItemRelationsTable,
      TopicItemRelation,
      $$TopicItemRelationsTableFilterComposer,
      $$TopicItemRelationsTableOrderingComposer,
      $$TopicItemRelationsTableAnnotationComposer,
      $$TopicItemRelationsTableCreateCompanionBuilder,
      $$TopicItemRelationsTableUpdateCompanionBuilder,
      (TopicItemRelation, $$TopicItemRelationsTableReferences),
      TopicItemRelation,
      PrefetchHooks Function({bool topicId, bool learningItemId})
    >;
typedef $$SyncDevicesTableCreateCompanionBuilder =
    SyncDevicesCompanion Function({
      Value<int> id,
      required String deviceId,
      required String deviceName,
      required String deviceType,
      Value<String?> ipAddress,
      Value<String?> authToken,
      Value<bool> isMaster,
      Value<int?> lastSyncMs,
      Value<int?> lastOutgoingMs,
      Value<int?> lastIncomingMs,
      Value<DateTime> createdAt,
    });
typedef $$SyncDevicesTableUpdateCompanionBuilder =
    SyncDevicesCompanion Function({
      Value<int> id,
      Value<String> deviceId,
      Value<String> deviceName,
      Value<String> deviceType,
      Value<String?> ipAddress,
      Value<String?> authToken,
      Value<bool> isMaster,
      Value<int?> lastSyncMs,
      Value<int?> lastOutgoingMs,
      Value<int?> lastIncomingMs,
      Value<DateTime> createdAt,
    });

class $$SyncDevicesTableFilterComposer
    extends Composer<_$AppDatabase, $SyncDevicesTable> {
  $$SyncDevicesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deviceId => $composableBuilder(
    column: $table.deviceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deviceName => $composableBuilder(
    column: $table.deviceName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deviceType => $composableBuilder(
    column: $table.deviceType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get ipAddress => $composableBuilder(
    column: $table.ipAddress,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get authToken => $composableBuilder(
    column: $table.authToken,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isMaster => $composableBuilder(
    column: $table.isMaster,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get lastSyncMs => $composableBuilder(
    column: $table.lastSyncMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get lastOutgoingMs => $composableBuilder(
    column: $table.lastOutgoingMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get lastIncomingMs => $composableBuilder(
    column: $table.lastIncomingMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SyncDevicesTableOrderingComposer
    extends Composer<_$AppDatabase, $SyncDevicesTable> {
  $$SyncDevicesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deviceId => $composableBuilder(
    column: $table.deviceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deviceName => $composableBuilder(
    column: $table.deviceName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deviceType => $composableBuilder(
    column: $table.deviceType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get ipAddress => $composableBuilder(
    column: $table.ipAddress,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get authToken => $composableBuilder(
    column: $table.authToken,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isMaster => $composableBuilder(
    column: $table.isMaster,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get lastSyncMs => $composableBuilder(
    column: $table.lastSyncMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get lastOutgoingMs => $composableBuilder(
    column: $table.lastOutgoingMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get lastIncomingMs => $composableBuilder(
    column: $table.lastIncomingMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SyncDevicesTableAnnotationComposer
    extends Composer<_$AppDatabase, $SyncDevicesTable> {
  $$SyncDevicesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get deviceId =>
      $composableBuilder(column: $table.deviceId, builder: (column) => column);

  GeneratedColumn<String> get deviceName => $composableBuilder(
    column: $table.deviceName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get deviceType => $composableBuilder(
    column: $table.deviceType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get ipAddress =>
      $composableBuilder(column: $table.ipAddress, builder: (column) => column);

  GeneratedColumn<String> get authToken =>
      $composableBuilder(column: $table.authToken, builder: (column) => column);

  GeneratedColumn<bool> get isMaster =>
      $composableBuilder(column: $table.isMaster, builder: (column) => column);

  GeneratedColumn<int> get lastSyncMs => $composableBuilder(
    column: $table.lastSyncMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get lastOutgoingMs => $composableBuilder(
    column: $table.lastOutgoingMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get lastIncomingMs => $composableBuilder(
    column: $table.lastIncomingMs,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$SyncDevicesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SyncDevicesTable,
          SyncDevice,
          $$SyncDevicesTableFilterComposer,
          $$SyncDevicesTableOrderingComposer,
          $$SyncDevicesTableAnnotationComposer,
          $$SyncDevicesTableCreateCompanionBuilder,
          $$SyncDevicesTableUpdateCompanionBuilder,
          (
            SyncDevice,
            BaseReferences<_$AppDatabase, $SyncDevicesTable, SyncDevice>,
          ),
          SyncDevice,
          PrefetchHooks Function()
        > {
  $$SyncDevicesTableTableManager(_$AppDatabase db, $SyncDevicesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SyncDevicesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SyncDevicesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SyncDevicesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> deviceId = const Value.absent(),
                Value<String> deviceName = const Value.absent(),
                Value<String> deviceType = const Value.absent(),
                Value<String?> ipAddress = const Value.absent(),
                Value<String?> authToken = const Value.absent(),
                Value<bool> isMaster = const Value.absent(),
                Value<int?> lastSyncMs = const Value.absent(),
                Value<int?> lastOutgoingMs = const Value.absent(),
                Value<int?> lastIncomingMs = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SyncDevicesCompanion(
                id: id,
                deviceId: deviceId,
                deviceName: deviceName,
                deviceType: deviceType,
                ipAddress: ipAddress,
                authToken: authToken,
                isMaster: isMaster,
                lastSyncMs: lastSyncMs,
                lastOutgoingMs: lastOutgoingMs,
                lastIncomingMs: lastIncomingMs,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String deviceId,
                required String deviceName,
                required String deviceType,
                Value<String?> ipAddress = const Value.absent(),
                Value<String?> authToken = const Value.absent(),
                Value<bool> isMaster = const Value.absent(),
                Value<int?> lastSyncMs = const Value.absent(),
                Value<int?> lastOutgoingMs = const Value.absent(),
                Value<int?> lastIncomingMs = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SyncDevicesCompanion.insert(
                id: id,
                deviceId: deviceId,
                deviceName: deviceName,
                deviceType: deviceType,
                ipAddress: ipAddress,
                authToken: authToken,
                isMaster: isMaster,
                lastSyncMs: lastSyncMs,
                lastOutgoingMs: lastOutgoingMs,
                lastIncomingMs: lastIncomingMs,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SyncDevicesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SyncDevicesTable,
      SyncDevice,
      $$SyncDevicesTableFilterComposer,
      $$SyncDevicesTableOrderingComposer,
      $$SyncDevicesTableAnnotationComposer,
      $$SyncDevicesTableCreateCompanionBuilder,
      $$SyncDevicesTableUpdateCompanionBuilder,
      (
        SyncDevice,
        BaseReferences<_$AppDatabase, $SyncDevicesTable, SyncDevice>,
      ),
      SyncDevice,
      PrefetchHooks Function()
    >;
typedef $$SyncLogsTableCreateCompanionBuilder =
    SyncLogsCompanion Function({
      Value<int> id,
      required String deviceId,
      required String entityType,
      required int entityId,
      required String operation,
      required String data,
      required int timestampMs,
      Value<int> localVersion,
    });
typedef $$SyncLogsTableUpdateCompanionBuilder =
    SyncLogsCompanion Function({
      Value<int> id,
      Value<String> deviceId,
      Value<String> entityType,
      Value<int> entityId,
      Value<String> operation,
      Value<String> data,
      Value<int> timestampMs,
      Value<int> localVersion,
    });

class $$SyncLogsTableFilterComposer
    extends Composer<_$AppDatabase, $SyncLogsTable> {
  $$SyncLogsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deviceId => $composableBuilder(
    column: $table.deviceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get entityId => $composableBuilder(
    column: $table.entityId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get operation => $composableBuilder(
    column: $table.operation,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get data => $composableBuilder(
    column: $table.data,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get timestampMs => $composableBuilder(
    column: $table.timestampMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get localVersion => $composableBuilder(
    column: $table.localVersion,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SyncLogsTableOrderingComposer
    extends Composer<_$AppDatabase, $SyncLogsTable> {
  $$SyncLogsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deviceId => $composableBuilder(
    column: $table.deviceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get entityId => $composableBuilder(
    column: $table.entityId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get operation => $composableBuilder(
    column: $table.operation,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get data => $composableBuilder(
    column: $table.data,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get timestampMs => $composableBuilder(
    column: $table.timestampMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get localVersion => $composableBuilder(
    column: $table.localVersion,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SyncLogsTableAnnotationComposer
    extends Composer<_$AppDatabase, $SyncLogsTable> {
  $$SyncLogsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get deviceId =>
      $composableBuilder(column: $table.deviceId, builder: (column) => column);

  GeneratedColumn<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => column,
  );

  GeneratedColumn<int> get entityId =>
      $composableBuilder(column: $table.entityId, builder: (column) => column);

  GeneratedColumn<String> get operation =>
      $composableBuilder(column: $table.operation, builder: (column) => column);

  GeneratedColumn<String> get data =>
      $composableBuilder(column: $table.data, builder: (column) => column);

  GeneratedColumn<int> get timestampMs => $composableBuilder(
    column: $table.timestampMs,
    builder: (column) => column,
  );

  GeneratedColumn<int> get localVersion => $composableBuilder(
    column: $table.localVersion,
    builder: (column) => column,
  );
}

class $$SyncLogsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SyncLogsTable,
          SyncLog,
          $$SyncLogsTableFilterComposer,
          $$SyncLogsTableOrderingComposer,
          $$SyncLogsTableAnnotationComposer,
          $$SyncLogsTableCreateCompanionBuilder,
          $$SyncLogsTableUpdateCompanionBuilder,
          (SyncLog, BaseReferences<_$AppDatabase, $SyncLogsTable, SyncLog>),
          SyncLog,
          PrefetchHooks Function()
        > {
  $$SyncLogsTableTableManager(_$AppDatabase db, $SyncLogsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SyncLogsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SyncLogsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SyncLogsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> deviceId = const Value.absent(),
                Value<String> entityType = const Value.absent(),
                Value<int> entityId = const Value.absent(),
                Value<String> operation = const Value.absent(),
                Value<String> data = const Value.absent(),
                Value<int> timestampMs = const Value.absent(),
                Value<int> localVersion = const Value.absent(),
              }) => SyncLogsCompanion(
                id: id,
                deviceId: deviceId,
                entityType: entityType,
                entityId: entityId,
                operation: operation,
                data: data,
                timestampMs: timestampMs,
                localVersion: localVersion,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String deviceId,
                required String entityType,
                required int entityId,
                required String operation,
                required String data,
                required int timestampMs,
                Value<int> localVersion = const Value.absent(),
              }) => SyncLogsCompanion.insert(
                id: id,
                deviceId: deviceId,
                entityType: entityType,
                entityId: entityId,
                operation: operation,
                data: data,
                timestampMs: timestampMs,
                localVersion: localVersion,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SyncLogsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SyncLogsTable,
      SyncLog,
      $$SyncLogsTableFilterComposer,
      $$SyncLogsTableOrderingComposer,
      $$SyncLogsTableAnnotationComposer,
      $$SyncLogsTableCreateCompanionBuilder,
      $$SyncLogsTableUpdateCompanionBuilder,
      (SyncLog, BaseReferences<_$AppDatabase, $SyncLogsTable, SyncLog>),
      SyncLog,
      PrefetchHooks Function()
    >;
typedef $$SyncEntityMappingsTableCreateCompanionBuilder =
    SyncEntityMappingsCompanion Function({
      Value<int> id,
      required String entityType,
      required String originDeviceId,
      required int originEntityId,
      required int localEntityId,
      Value<int?> lastAppliedAtMs,
      Value<bool> isDeleted,
      Value<DateTime> createdAt,
    });
typedef $$SyncEntityMappingsTableUpdateCompanionBuilder =
    SyncEntityMappingsCompanion Function({
      Value<int> id,
      Value<String> entityType,
      Value<String> originDeviceId,
      Value<int> originEntityId,
      Value<int> localEntityId,
      Value<int?> lastAppliedAtMs,
      Value<bool> isDeleted,
      Value<DateTime> createdAt,
    });

class $$SyncEntityMappingsTableFilterComposer
    extends Composer<_$AppDatabase, $SyncEntityMappingsTable> {
  $$SyncEntityMappingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get originDeviceId => $composableBuilder(
    column: $table.originDeviceId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get originEntityId => $composableBuilder(
    column: $table.originEntityId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get localEntityId => $composableBuilder(
    column: $table.localEntityId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get lastAppliedAtMs => $composableBuilder(
    column: $table.lastAppliedAtMs,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SyncEntityMappingsTableOrderingComposer
    extends Composer<_$AppDatabase, $SyncEntityMappingsTable> {
  $$SyncEntityMappingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get originDeviceId => $composableBuilder(
    column: $table.originDeviceId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get originEntityId => $composableBuilder(
    column: $table.originEntityId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get localEntityId => $composableBuilder(
    column: $table.localEntityId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get lastAppliedAtMs => $composableBuilder(
    column: $table.lastAppliedAtMs,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isDeleted => $composableBuilder(
    column: $table.isDeleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SyncEntityMappingsTableAnnotationComposer
    extends Composer<_$AppDatabase, $SyncEntityMappingsTable> {
  $$SyncEntityMappingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get originDeviceId => $composableBuilder(
    column: $table.originDeviceId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get originEntityId => $composableBuilder(
    column: $table.originEntityId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get localEntityId => $composableBuilder(
    column: $table.localEntityId,
    builder: (column) => column,
  );

  GeneratedColumn<int> get lastAppliedAtMs => $composableBuilder(
    column: $table.lastAppliedAtMs,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isDeleted =>
      $composableBuilder(column: $table.isDeleted, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$SyncEntityMappingsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SyncEntityMappingsTable,
          SyncEntityMapping,
          $$SyncEntityMappingsTableFilterComposer,
          $$SyncEntityMappingsTableOrderingComposer,
          $$SyncEntityMappingsTableAnnotationComposer,
          $$SyncEntityMappingsTableCreateCompanionBuilder,
          $$SyncEntityMappingsTableUpdateCompanionBuilder,
          (
            SyncEntityMapping,
            BaseReferences<
              _$AppDatabase,
              $SyncEntityMappingsTable,
              SyncEntityMapping
            >,
          ),
          SyncEntityMapping,
          PrefetchHooks Function()
        > {
  $$SyncEntityMappingsTableTableManager(
    _$AppDatabase db,
    $SyncEntityMappingsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SyncEntityMappingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SyncEntityMappingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SyncEntityMappingsTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> entityType = const Value.absent(),
                Value<String> originDeviceId = const Value.absent(),
                Value<int> originEntityId = const Value.absent(),
                Value<int> localEntityId = const Value.absent(),
                Value<int?> lastAppliedAtMs = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SyncEntityMappingsCompanion(
                id: id,
                entityType: entityType,
                originDeviceId: originDeviceId,
                originEntityId: originEntityId,
                localEntityId: localEntityId,
                lastAppliedAtMs: lastAppliedAtMs,
                isDeleted: isDeleted,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String entityType,
                required String originDeviceId,
                required int originEntityId,
                required int localEntityId,
                Value<int?> lastAppliedAtMs = const Value.absent(),
                Value<bool> isDeleted = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => SyncEntityMappingsCompanion.insert(
                id: id,
                entityType: entityType,
                originDeviceId: originDeviceId,
                originEntityId: originEntityId,
                localEntityId: localEntityId,
                lastAppliedAtMs: lastAppliedAtMs,
                isDeleted: isDeleted,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SyncEntityMappingsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SyncEntityMappingsTable,
      SyncEntityMapping,
      $$SyncEntityMappingsTableFilterComposer,
      $$SyncEntityMappingsTableOrderingComposer,
      $$SyncEntityMappingsTableAnnotationComposer,
      $$SyncEntityMappingsTableCreateCompanionBuilder,
      $$SyncEntityMappingsTableUpdateCompanionBuilder,
      (
        SyncEntityMapping,
        BaseReferences<
          _$AppDatabase,
          $SyncEntityMappingsTable,
          SyncEntityMapping
        >,
      ),
      SyncEntityMapping,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$LearningItemsTableTableManager get learningItems =>
      $$LearningItemsTableTableManager(_db, _db.learningItems);
  $$LearningSubtasksTableTableManager get learningSubtasks =>
      $$LearningSubtasksTableTableManager(_db, _db.learningSubtasks);
  $$ReviewTasksTableTableManager get reviewTasks =>
      $$ReviewTasksTableTableManager(_db, _db.reviewTasks);
  $$ReviewRecordsTableTableManager get reviewRecords =>
      $$ReviewRecordsTableTableManager(_db, _db.reviewRecords);
  $$AppSettingsTableTableTableManager get appSettingsTable =>
      $$AppSettingsTableTableTableManager(_db, _db.appSettingsTable);
  $$LearningTemplatesTableTableManager get learningTemplates =>
      $$LearningTemplatesTableTableManager(_db, _db.learningTemplates);
  $$LearningTopicsTableTableManager get learningTopics =>
      $$LearningTopicsTableTableManager(_db, _db.learningTopics);
  $$TopicItemRelationsTableTableManager get topicItemRelations =>
      $$TopicItemRelationsTableTableManager(_db, _db.topicItemRelations);
  $$SyncDevicesTableTableManager get syncDevices =>
      $$SyncDevicesTableTableManager(_db, _db.syncDevices);
  $$SyncLogsTableTableManager get syncLogs =>
      $$SyncLogsTableTableManager(_db, _db.syncLogs);
  $$SyncEntityMappingsTableTableManager get syncEntityMappings =>
      $$SyncEntityMappingsTableTableManager(_db, _db.syncEntityMappings);
}
