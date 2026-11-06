/// Core domain models shared across macOS, iOS, iPadOS, Windows, and Android backup schemas.
library;
class CmsDepot {
  final String name;
  final String url;
  const CmsDepot({
    required this.name,
    required this.url,
  });
  Map<String, dynamic> toJson() => {
        'name': name,
        'url': url,
      };
  factory CmsDepot.fromJson(Map<String, dynamic> json) {
    return CmsDepot(
      name: (json['name'] ?? '').toString().trim(),
      url: (json['url'] ?? '').toString().trim(),
    );
  }
}
class SubscriptionEntry {
  final String id;
  final String name;
  final String url;
  final String group;
  final String format;
  final bool isPrivate;
  final bool enabled;
  final String rawContent;
  final int siteCount;
  final int liveCount;
  final int updatedAt;
  const SubscriptionEntry({
    required this.id,
    required this.name,
    this.url = '',
    this.group = '默认分组',
    this.format = 'JSON',
    this.isPrivate = false,
    this.enabled = true,
    this.rawContent = '',
    this.siteCount = 0,
    this.liveCount = 0,
    this.updatedAt = 0,
  });
  SubscriptionEntry copyWith({
    String? id,
    String? name,
    String? url,
    String? group,
    String? format,
    bool? isPrivate,
    bool? enabled,
    String? rawContent,
    int? siteCount,
    int? liveCount,
    int? updatedAt,
  }) {
    return SubscriptionEntry(
      id: id ?? this.id,
      name: name ?? this.name,
      url: url ?? this.url,
      group: group ?? this.group,
      format: format ?? this.format,
      isPrivate: isPrivate ?? this.isPrivate,
      enabled: enabled ?? this.enabled,
      rawContent: rawContent ?? this.rawContent,
      siteCount: siteCount ?? this.siteCount,
      liveCount: liveCount ?? this.liveCount,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }
  bool get isEffectivelyPrivate =>
      isPrivate || group.contains('隐私') || group.contains('私密');
  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'url': url,
        'group': group,
        'format': format,
        'isPrivate': isEffectivelyPrivate,
        'enabled': enabled,
        'rawContent': rawContent,
        'siteCount': siteCount,
        'liveCount': liveCount,
        'updatedAt': updatedAt,
      };
  factory SubscriptionEntry.fromJson(Map<String, dynamic> json) {
    final g = (json['group'] ?? '').toString().trim();
    final f = (json['format'] ?? '').toString().trim();
    final cleanGroup = g.isEmpty ? '默认分组' : g;
    return SubscriptionEntry(
      id: (json['id'] ?? '').toString().trim(),
      name: (json['name'] ?? '订阅源').toString().trim(),
      url: (json['url'] ?? '').toString().trim(),
      group: cleanGroup,
      format: f.isEmpty ? 'JSON' : f,
      isPrivate: json['isPrivate'] == true ||
          cleanGroup.contains('隐私') ||
          cleanGroup.contains('私密'),
      enabled: json['enabled'] != false,
      rawContent: (json['rawContent'] ?? '').toString(),
      siteCount: (json['siteCount'] as num?)?.toInt() ?? 0,
      liveCount: (json['liveCount'] as num?)?.toInt() ?? 0,
      updatedAt: (json['updatedAt'] as num?)?.toInt() ?? 0,
    );
  }
}
class CmsFilter {
  final String primaryTab;
  final String categoryId;
  final String genreLabel;
  final String area;
  final String year;
  const CmsFilter({
    this.primaryTab = 'recommend',
    this.categoryId = '',
    this.genreLabel = '',
    this.area = '',
    this.year = '',
  });
  bool get hasSubFilter =>
      categoryId.isNotEmpty ||
      genreLabel.isNotEmpty ||
      area.isNotEmpty ||
      year.isNotEmpty;
  int get activeDimensionCount =>
      (categoryId.isNotEmpty || genreLabel.isNotEmpty ? 1 : 0) +
      (area.isNotEmpty ? 1 : 0) +
      (year.isNotEmpty ? 1 : 0);
  CmsFilter copyWith({
    String? primaryTab,
    String? categoryId,
    String? genreLabel,
    String? area,
    String? year,
  }) {
    return CmsFilter(
      primaryTab: primaryTab ?? this.primaryTab,
      categoryId: categoryId ?? this.categoryId,
      genreLabel: genreLabel ?? this.genreLabel,
      area: area ?? this.area,
      year: year ?? this.year,
    );
  }
}
class CmsSite {
  final String key;
  final String name;
  final int type;
  final String api;
  final String playUrl;
  final bool searchable;
  final String ext;
  final Map<String, String> headers;
  final String group;
  final bool isPrivate;
  final String subscriptionId;
  const CmsSite({
    required this.key,
    required this.name,
    required this.type,
    required this.api,
    this.playUrl = '',
    this.searchable = true,
    this.ext = '',
    this.headers = const {},
    this.group = '默认分组',
    this.isPrivate = false,
    this.subscriptionId = '',
  });
  CmsSite copyWith({
    String? key,
    String? name,
    int? type,
    String? api,
    String? playUrl,
    bool? searchable,
    String? ext,
    Map<String, String>? headers,
    String? group,
    bool? isPrivate,
    String? subscriptionId,
  }) {
    return CmsSite(
      key: key ?? this.key,
      name: name ?? this.name,
      type: type ?? this.type,
      api: api ?? this.api,
      playUrl: playUrl ?? this.playUrl,
      searchable: searchable ?? this.searchable,
      ext: ext ?? this.ext,
      headers: headers ?? this.headers,
      group: group ?? this.group,
      isPrivate: isPrivate ?? this.isPrivate,
      subscriptionId: subscriptionId ?? this.subscriptionId,
    );
  }
  bool get isHttpApi {
    final lower = api.trim().toLowerCase();
    return lower.startsWith('http://') || lower.startsWith('https://');
  }
  bool get isM3uVodApi {
    return api.trim().toLowerCase().startsWith('m3u_vod://');
  }
  bool get isScriptOrJarApi {
    final lower = api.trim().toLowerCase().split('?').first;
    return lower.startsWith('csp_') ||
        lower.endsWith('.js') ||
        lower.endsWith('.jar') ||
        lower.endsWith('.py');
  }
  /// Supports standard JSON/XML CMS (`type` 0 or 1), remote HTTP spider endpoints (`type` 4),
  /// M3U/TXT VOD playlists (`m3u_vod://`), and TVBox `csp_*` / `.js` spider sites via `CspBridge`.
  bool get canBrowse =>
      isM3uVodApi ||
      ((isHttpApi || isScriptOrJarApi) &&
          (type == 0 || type == 1 || type == 4 || type == 3 || type == -1));
  bool get isEffectivelyPrivate =>
      isPrivate || group.contains('隐私') || group.contains('私密');
  bool get requiresPluginRuntime => !canBrowse;
  bool get supported => canBrowse;
  String get unsupportedReason {
    return '非标准接口 (${api.trim()})';
  }
}
class LiveSource {
  final String name;
  final String url;
  final Map<String, String> headers;
  final String group;
  final bool isPrivate;
  final String subscriptionId;
  const LiveSource({
    required this.name,
    required this.url,
    this.headers = const {},
    this.group = '默认分组',
    this.isPrivate = false,
    this.subscriptionId = '',
  });
  bool get isEffectivelyPrivate =>
      isPrivate || group.contains('隐私') || group.contains('私密');
  LiveSource copyWith({
    String? name,
    String? url,
    Map<String, String>? headers,
    String? group,
    bool? isPrivate,
    String? subscriptionId,
  }) {
    return LiveSource(
      name: name ?? this.name,
      url: url ?? this.url,
      headers: headers ?? this.headers,
      group: group ?? this.group,
      isPrivate: isPrivate ?? this.isPrivate,
      subscriptionId: subscriptionId ?? this.subscriptionId,
    );
  }
}
class CmsConfig {
  final List<CmsSite> sites;
  final List<LiveSource> lives;
  final List<CmsDepot> depots;
  final String spider;
  const CmsConfig({
    this.sites = const [],
    this.lives = const [],
    this.depots = const [],
    this.spider = '',
  });
  List<CmsSite> get supportedSites =>
      sites.where((s) => s.supported).toList(growable: false);
  List<CmsSite> get publicSupportedSites =>
      sites
          .where((s) => s.supported && !s.isEffectivelyPrivate)
          .toList(growable: false);
  List<CmsSite> get privateSupportedSites =>
      sites
          .where((s) => s.supported && s.isEffectivelyPrivate)
          .toList(growable: false);
  List<LiveSource> get publicLives =>
      lives.where((l) => !l.isEffectivelyPrivate).toList(growable: false);
  List<LiveSource> get privateLives =>
      lives.where((l) => l.isEffectivelyPrivate).toList(growable: false);
  List<CmsSite> get pluginOnlySites =>
      sites.where((s) => !s.supported).toList(growable: false);
  bool get isMultiDepotOnly =>
      sites.isEmpty && lives.isEmpty && depots.isNotEmpty;
}
class CmsCategory {
  final String id;
  final String name;
  /// MacCMS `type_pid`; empty or `0` for top-level categories.
  final String parentId;
  const CmsCategory({required this.id, required this.name, this.parentId = ''});
  bool get isTopLevel => parentId.isEmpty || parentId == '0';
}
class CmsEpisode {
  final String name;
  final String url;
  final Map<String, String> headers;
  const CmsEpisode({
    required this.name,
    required this.url,
    this.headers = const {},
  });
  /// Whether [url] points to an already-downloaded local file (offline playback).
  bool get isLocalFile {
    final raw = url.trim();
    return raw.startsWith('/') ||
        RegExp(r'^[a-zA-Z]:[\\/]').hasMatch(raw) ||
        raw.toLowerCase().startsWith('file://');
  }
  bool get isDirectPlayable {
    if (isLocalFile) return true;
    final u = url.trim().toLowerCase();
    if (!u.startsWith('http://') &&
        !u.startsWith('https://') &&
        !u.startsWith('rtmp://') &&
        !u.startsWith('rtsp://')) {
      return false;
    }
    if (u.contains('tuxiaobei.com/play/') ||
        u.contains('youlai.cn/jijiu/article/') ||
        u.contains('api.bilibili.com/x/player/playurl')) {
      return true;
    }
    final pathOnly = u.split('?').first.split('#').first;
    if (pathOnly.endsWith('.html') ||
        pathOnly.endsWith('.htm') ||
        pathOnly.contains('/share/')) {
      return false;
    }
    return true;
  }
}
class CmsLine {
  final String name;
  final List<CmsEpisode> episodes;
  const CmsLine({required this.name, required this.episodes});
}
class CmsVideo {
  final String id;
  final String title;
  final String poster;
  final String remarks;
  final String year;
  final String area;
  final String actor;
  final String description;
  final String tag;
  final List<CmsLine> lines;
  const CmsVideo({
    required this.id,
    required this.title,
    this.poster = '',
    this.remarks = '',
    this.year = '',
    this.area = '',
    this.actor = '',
    this.description = '',
    this.tag = '',
    this.lines = const [],
  });
}
class CmsListing {
  final int page;
  final int pageCount;
  final List<CmsVideo> videos;
  const CmsListing({
    this.page = 1,
    this.pageCount = 1,
    this.videos = const [],
  });
}
class LiveChannel {
  final String name;
  final String group;
  final List<String> urls;
  final String logo;
  const LiveChannel({
    required this.name,
    required this.group,
    required this.urls,
    this.logo = '',
  });
}
class LiveGroup {
  final String name;
  final List<LiveChannel> channels;
  const LiveGroup({required this.name, required this.channels});
}
class WatchHistoryEntry {
  final String site;
  final String id;
  final String title;
  final String poster;
  final String line;
  final String episode;
  final int index;
  final int position;
  final int duration;
  final int updated;
  const WatchHistoryEntry({
    required this.site,
    required this.id,
    required this.title,
    this.poster = '',
    this.line = '',
    this.episode = '',
    this.index = 0,
    this.position = 0,
    this.duration = 0,
    this.updated = 0,
  });
  String get key => '$site\n$id';
  bool get complete =>
      duration >= 60000 &&
      position >= duration - (duration * 0.05).round().clamp(15000, 120000);
  int get resumePosition => complete ? 0 : (position < 0 ? 0 : position);
  double get progressFraction {
    if (duration <= 0) return 0.0;
    return (position / duration).clamp(0.0, 1.0);
  }
  Map<String, dynamic> toJson() => {
        'site': site,
        'id': id,
        'title': title,
        'poster': poster,
        'line': line,
        'episode': episode,
        'index': index,
        'position': position < 0 ? 0 : position,
        'duration': duration < 0 ? 0 : duration,
        'updated': updated,
      };
  factory WatchHistoryEntry.fromJson(Map<String, dynamic> json) {
    final pos = (json['position'] as num?)?.toInt() ?? 0;
    final dur = (json['duration'] as num?)?.toInt() ?? 0;
    return WatchHistoryEntry(
      site: (json['site'] ?? '').toString(),
      id: (json['id'] ?? '').toString(),
      title: (json['title'] ?? '').toString(),
      poster: (json['poster'] ?? '').toString(),
      line: (json['line'] ?? '').toString(),
      episode: (json['episode'] ?? '').toString(),
      index: (json['index'] as num?)?.toInt() ?? 0,
      position: pos < 0 ? 0 : pos,
      duration: dur < 0 ? 0 : dur,
      updated: (json['updated'] as num?)?.toInt() ?? 0,
    );
  }
}
class FavoriteEntry {
  final String site;
  final String id;
  final String title;
  final String poster;
  final String remarks;
  final int updated;
  const FavoriteEntry({
    required this.site,
    required this.id,
    required this.title,
    this.poster = '',
    this.remarks = '',
    this.updated = 0,
  });
  String get key => '$site\n$id';
  Map<String, dynamic> toJson() => {
        'site': site,
        'id': id,
        'title': title,
        'poster': poster,
        'remarks': remarks,
        'updated': updated,
      };
  factory FavoriteEntry.fromJson(Map<String, dynamic> json) {
    return FavoriteEntry(
      site: (json['site'] ?? '').toString(),
      id: (json['id'] ?? '').toString(),
      title: (json['title'] ?? '').toString(),
      poster: (json['poster'] ?? '').toString(),
      remarks: (json['remarks'] ?? '').toString(),
      updated: (json['updated'] as num?)?.toInt() ?? 0,
    );
  }
}
