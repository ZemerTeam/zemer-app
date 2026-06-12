// Strict-deserialization validator for the YouTube Music search response tree.
//
// WHY THIS EXISTS
// The app parses search responses with kotlinx.serialization configured (InnerTube.kt) as:
//     ignoreUnknownKeys = true   // unknown JSON keys are dropped
//     explicitNulls      = false // a *nullable* property absent in JSON decodes to null (optional)
//     // NO coerceInputValues       // a *non-null* property that is absent OR JSON-null THROWS
// So a Kotlin property that is non-null AND has no default value is REQUIRED: if YouTube stops
// sending it (or sends null), `response.body<SearchResponse>()` throws MissingFieldException and the
// ENTIRE response fails to parse. `YouTube.searchSummary()/search()` wrap that in runCatching and
// `.getOrNull()` swallows it to null -> the ViewModel shows "No results found" / "Search error".
// That is the highest-impact "search is broken" failure: one missing field kills every result.
//
// This module mirrors that exact rule. SCHEMA encodes, per renderer type reachable from a search
// response, which fields are REQUIRED (non-null, no Kotlin default) vs OPTIONAL (nullable or
// defaulted). validate() walks the LIVE JSON the same way kotlinx would and reports every field the
// app declares non-null that the server did not send -> each one is a whole-response crash.
//
// Field facts are transcribed from the Kotlin models (innertube/.../models/*). When a model's
// nullability changes, update the matching entry here. Types that cannot appear in a search response
// (musicQueueRenderer, gridRenderer, carousel/header shelves, continuationItemRenderer) are left
// unencoded on purpose; validate() records any such type it actually meets in `unencoded` so the
// report can warn that the strict sweep was not exhaustive there (no silent gaps).

// Descriptor helpers. child === null means a scalar (only presence/non-null is checked).
const req = (child = null, aliases = []) => ({ req: true, list: false, child, aliases });
const opt = (child = null, aliases = []) => ({ req: false, list: false, child, aliases });
const reqList = (child = null, aliases = []) => ({ req: true, list: true, child, aliases });
const optList = (child = null, aliases = []) => ({ req: false, list: true, child, aliases });

export const SCHEMA = {
  // ---- response envelopes ------------------------------------------------
  SearchResponse: {
    contents: opt("SR_Contents"),
    continuationContents: opt("SR_ContinuationContents"),
  },
  SR_Contents: { tabbedSearchResultsRenderer: opt("Tabs") },
  SR_ContinuationContents: { musicShelfContinuation: req("MusicShelfContinuation") },
  MusicShelfContinuation: {
    contents: reqList("MSC_Content"),
    continuations: optList("Continuation"),
  },
  MSC_Content: { musicResponsiveListItemRenderer: req("MRLIR") },

  GetSearchSuggestionsResponse: { contents: optList("GSSR_Content") },
  GSSR_Content: { searchSuggestionsSectionRenderer: req("SearchSuggestionsSectionRenderer") },
  SearchSuggestionsSectionRenderer: { contents: reqList("SuggestionContent") },
  SuggestionContent: {
    searchSuggestionRenderer: opt("SearchSuggestionRenderer"),
    musicResponsiveListItemRenderer: opt("MRLIR"),
  },
  SearchSuggestionRenderer: { suggestion: req("Runs"), navigationEndpoint: req("NavigationEndpoint") },

  Tabs: { tabs: reqList("Tab") },
  Tab: { tabRenderer: req("TabRenderer") },
  TabRenderer: { title: opt(), content: opt("TabContent"), endpoint: opt("NavigationEndpoint") },
  TabContent: { sectionListRenderer: opt("SectionListRenderer") }, // musicQueueRenderer never in search

  SectionListRenderer: {
    header: opt("SLR_Header"),
    contents: optList("SLR_Content"),
    continuations: optList("Continuation"),
  },
  SLR_Header: { chipCloudRenderer: opt("ChipCloudRenderer") },
  SLR_Content: {
    musicShelfRenderer: opt("MusicShelfRenderer"),
    musicCardShelfRenderer: opt("MusicCardShelfRenderer"),
    itemSectionRenderer: opt("ItemSectionRenderer"),
    // carousel/grid/playlistShelf/descriptionShelf/responsiveHeader/editablePlaylistHeader:
    // declared nullable in Kotlin but never present in a search response -> intentionally unencoded.
  },
  MusicShelfRenderer: {
    title: opt("Runs"),
    contents: optList("MusicShelfContent"),
    continuations: optList("Continuation"),
    bottomEndpoint: opt("NavigationEndpoint"),
    moreContentButton: opt("Button"),
  },
  MusicShelfContent: { musicResponsiveListItemRenderer: opt("MRLIR") }, // continuationItemRenderer unencoded
  ItemSectionRenderer: { contents: optList("ISR_Content") }, // header unencoded (cosmetic)
  ISR_Content: { musicResponsiveListItemRenderer: opt("MRLIR") },

  // ---- the filter chip cloud (zemer declares isSelected + navigationEndpoint NON-NULL) ----
  ChipCloudRenderer: { chips: reqList("Chip") },
  Chip: { chipCloudChipRenderer: req("ChipCloudChipRenderer") },
  ChipCloudChipRenderer: {
    isSelected: req(),                         // <- zemer-strict (Metrolist made these nullable)
    navigationEndpoint: req("NavigationEndpoint"),
    onDeselectedCommand: opt("NavigationEndpoint"),
    text: opt("Runs"),
    uniqueId: opt(),
  },

  // ---- the core list item -------------------------------------------------
  MRLIR: {
    flexColumns: reqList("FlexColumn"),
    fixedColumns: optList("FlexColumn"),
    thumbnail: opt("ThumbnailRenderer"),
    menu: opt("Menu"),
    playlistItemData: opt("PlaylistItemData"),
    overlay: opt("Overlay"),
    navigationEndpoint: opt("NavigationEndpoint"),
    badges: optList("Badges"),
  },
  FlexColumn: {
    musicResponsiveListItemFlexColumnRenderer:
      req("FlexColumnInner", ["musicResponsiveListItemFixedColumnRenderer"]),
  },
  FlexColumnInner: { text: opt("Runs") },
  PlaylistItemData: { videoId: req(), playlistSetVideoId: opt() }, // videoId NON-NULL
  Overlay: { musicItemThumbnailOverlayRenderer: req("OverlayInner") },
  OverlayInner: { content: req("OverlayContent") },
  OverlayContent: { musicPlayButtonRenderer: req("MusicPlayButtonRenderer") },
  MusicPlayButtonRenderer: { playNavigationEndpoint: opt("NavigationEndpoint") },

  // ---- runs / endpoints ---------------------------------------------------
  Runs: { runs: optList("Run") },
  Run: { text: req(), navigationEndpoint: opt("NavigationEndpoint") }, // text NON-NULL
  NavigationEndpoint: {
    watchEndpoint: opt("WatchEndpoint"),
    watchPlaylistEndpoint: opt("WatchEndpoint"),
    browseEndpoint: opt("BrowseEndpoint"),
    searchEndpoint: opt("SearchEndpoint"),
    queueAddEndpoint: opt("QueueAddEndpoint"),
    shareEntityEndpoint: opt("ShareEntityEndpoint"),
    feedbackEndpoint: opt("FeedbackEndpoint"),
  },
  WatchEndpoint: { watchEndpointMusicSupportedConfigs: opt("WEMSC") }, // videoId etc. all defaulted
  WEMSC: { watchEndpointMusicConfig: req("WEMC") },
  WEMC: { musicVideoType: req() },
  BrowseEndpoint: { browseId: req(), browseEndpointContextSupportedConfigs: opt("BECSC") },
  BECSC: { browseEndpointContextMusicConfig: req("BECMC") },
  BECMC: { pageType: req() },
  SearchEndpoint: { query: req(), params: opt() },
  QueueAddEndpoint: { queueInsertPosition: req(), queueTarget: req("QueueTarget") },
  QueueTarget: { videoId: opt(), playlistId: opt() },
  ShareEntityEndpoint: { serializedShareEntity: req() },
  FeedbackEndpoint: { feedbackToken: req() },

  // ---- thumbnails ---------------------------------------------------------
  ThumbnailRenderer: {
    musicThumbnailRenderer: opt("MusicThumbnailRenderer", ["croppedSquareThumbnailRenderer"]),
    musicAnimatedThumbnailRenderer: opt("MusicAnimatedThumbnailRenderer"),
  },
  MusicThumbnailRenderer: { thumbnail: req("Thumbnails"), thumbnailCrop: opt(), thumbnailScale: opt() },
  MusicAnimatedThumbnailRenderer: { animatedThumbnail: req("Thumbnails"), backupRenderer: req("MusicThumbnailRenderer") },
  Thumbnails: { thumbnails: reqList("Thumbnail") },
  Thumbnail: { url: req(), width: opt(), height: opt() },

  // ---- menus / buttons / badges / icons ----------------------------------
  Menu: { menuRenderer: req("MenuRenderer") },
  MenuRenderer: { items: optList("MenuItem"), topLevelButtons: optList("TopLevelButton") },
  MenuItem: {
    menuNavigationItemRenderer: opt("MenuNavigationItemRenderer"),
    menuServiceItemRenderer: opt("MenuServiceItemRenderer"),
    toggleMenuServiceItemRenderer: opt("ToggleMenuServiceRenderer"),
  },
  MenuNavigationItemRenderer: { text: req("Runs"), icon: req("Icon"), navigationEndpoint: req("NavigationEndpoint") },
  MenuServiceItemRenderer: { text: req("Runs"), icon: req("Icon"), serviceEndpoint: req("NavigationEndpoint") },
  ToggleMenuServiceRenderer: {
    defaultIcon: req("Icon"),
    defaultServiceEndpoint: req("DefaultServiceEndpoint"),
    toggledServiceEndpoint: opt("ToggledServiceEndpoint"),
  },
  TopLevelButton: { buttonRenderer: opt("TopLevelButtonRenderer") },
  TopLevelButtonRenderer: { icon: req("Icon"), navigationEndpoint: req("NavigationEndpoint") },
  DefaultServiceEndpoint: { subscribeEndpoint: opt("SubscribeEndpoint"), feedbackEndpoint: opt("FeedbackEndpoint") },
  SubscribeEndpoint: { channelIds: reqList(), params: opt() },
  ToggledServiceEndpoint: { feedbackEndpoint: opt("FeedbackEndpoint") },
  Icon: { iconType: req() }, // NON-NULL
  Badges: { musicInlineBadgeRenderer: opt("MusicInlineBadgeRenderer") },
  MusicInlineBadgeRenderer: { icon: req("Icon") },
  Button: { buttonRenderer: req("ButtonRenderer") },
  ButtonRenderer: { text: req("Runs"), navigationEndpoint: opt("NavigationEndpoint"), command: opt("NavigationEndpoint"), icon: opt("Icon") },

  // ---- the top-result card -----------------------------------------------
  MusicCardShelfRenderer: {
    title: req("Runs"),
    subtitle: req("Runs"),
    thumbnail: req("ThumbnailRenderer"),
    header: opt("MusicCardShelfHeader"),
    contents: optList("CardContent"),
    buttons: reqList("Button"),
    onTap: req("NavigationEndpoint"),
    subtitleBadges: optList("Badges"),
  },
  MusicCardShelfHeader: { musicCardShelfHeaderBasicRenderer: req("MCSHBasic") },
  MCSHBasic: { title: req("Runs") },
  CardContent: { musicResponsiveListItemRenderer: opt("MRLIR") },

  // ---- continuations ------------------------------------------------------
  Continuation: { nextContinuationData: opt("NextContinuationData", ["nextRadioContinuationData"]) },
  NextContinuationData: { continuation: req() },
};

// Walk `node` as Kotlin type `type`, recording every REQUIRED field the live JSON omits or nulls —
// each is a whole-response MissingFieldException in the app. Mirrors kotlinx ignoreUnknownKeys
// (unknown JSON keys ignored) + explicitNulls=false (nullable-absent is fine) + no coerceInputValues
// (non-null-absent / non-null-null throws).
export function validate(node, type, { path = type, ctx } = {}) {
  ctx = ctx || { violations: [], unencoded: new Set(), visited: 0 };
  walk(node, type, path, ctx);
  return ctx;
}

function lookup(node, key, aliases) {
  if (Object.prototype.hasOwnProperty.call(node, key)) return { present: true, val: node[key], used: key };
  for (const a of aliases) {
    if (Object.prototype.hasOwnProperty.call(node, a)) return { present: true, val: node[a], used: a };
  }
  return { present: false, val: undefined, used: key };
}

function walk(node, type, path, ctx) {
  const spec = SCHEMA[type];
  if (!spec) { ctx.unencoded.add(type); return; }
  if (node === null || typeof node !== "object" || Array.isArray(node)) {
    ctx.violations.push({ path, type, field: "(self)", why: node === null ? "null" : "not-object" });
    return;
  }
  ctx.visited++;
  for (const [key, d] of Object.entries(spec)) {
    const { present, val } = lookup(node, key, d.aliases || []);
    if (d.req && (!present || val === null)) {
      ctx.violations.push({ path: `${path}.${key}`, type, field: key, why: present ? "null" : "missing" });
      continue;
    }
    if (!present || val === null) continue;
    if (d.list) {
      if (!Array.isArray(val)) { ctx.violations.push({ path: `${path}.${key}`, type, field: key, why: "not-array" }); continue; }
      val.forEach((el, i) => {
        if (el === null) { ctx.violations.push({ path: `${path}.${key}[${i}]`, type, field: key, why: "null-element" }); return; }
        if (d.child) walk(el, d.child, `${path}.${key}[${i}]`, ctx);
      });
    } else if (d.child) {
      walk(val, d.child, `${path}.${key}`, ctx);
    }
  }
}
