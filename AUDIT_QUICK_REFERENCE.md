# Zemer Audit - Quick Reference Guide

## Critical Issues (Fix Immediately)

### 1. Release Signing Configuration
- **File:** `app/build.gradle.kts:60`
- **Issue:** Release builds use `persistentDebug` signing instead of `release`
- **Fix:** Change `signingConfig = signingConfigs.getByName("persistentDebug")` to `getByName("release")`
- **Time:** 5 minutes

## High Priority Issues (Fix Before Release)

### 2. Service Unbinding Error
- **File:** `MainActivity.kt:325`
- **Issue:** `unbindService()` can throw IllegalArgumentException if service never bound
- **Fix:** Wrap in try-catch
- **Time:** 10 minutes

### 3-4. Unsafe .first() Calls
- **Files:** `HomeScreen.kt`, `LibraryVideosScreen.kt`
- **Issue:** `.first()` without checking if list is empty
- **Fix:** Replace with `.firstOrNull()?.let { ... }` pattern
- **Time:** 20 minutes (both)

### 5. Missing Error Handling in Seek Operations
- **File:** `PlayerConnection.kt:114-124`
- **Issue:** No validation before `seekToNext()` and `seekToPrevious()`
- **Fix:** Add timeline validation and try-catch
- **Time:** 15 minutes

### 6. Missing Notification Permission Verification
- **File:** `MainActivity.kt:297` and `MusicService.kt:onCreate()`
- **Issue:** No verification that POST_NOTIFICATIONS permission granted
- **Fix:** Add ContextCompat.checkSelfPermission check in MusicService
- **Time:** 15 minutes

### 7. Race Condition in Search Screen
- **File:** `OnlineSearchScreen.kt`
- **Issue:** `.first()` calls after `isEmpty()` check vulnerable to race condition
- **Fix:** Use `.firstOrNull()` directly
- **Time:** 10 minutes

### 8. Inconsistent LocalPlayerConnection Null Checks
- **Files:** Multiple screen files
- **Issue:** Not all screens check for null PlayerConnection
- **Fix:** Audit all screens using LocalPlayerConnection and add early returns
- **Time:** 20 minutes

---

## Medium Priority Issues (Polish & Stability)

### 9. Missing Playback Error UI
- Add snackbar/toast for PlaybackException
- Display `playerConnection.error` state

### 10-11. Database Migration Error Handling
- Add try-catch for destructive migration fallback
- Log migration failures clearly

### 12. Coil Cache Size Configuration
- Reduce default from 512MB to 256MB
- Add reasonable upper limit (1GB)

### 13. Service Cleanup in onDestroy
- Always dispose PlayerConnection
- Use try-finally pattern

### 14-15. LaunchedEffect Improvements
- Add timeout to image loading (5 seconds)
- Use async preference collection for search history

### 16. Accessibility - Content Descriptions
- Add `stringResource()` for all icon button descriptions
- Search for `contentDescription = ""`

---

## Low Priority Issues (Code Quality)

### 17. Update Deprecated Imports
- Change `androidx.hilt.navigation.compose.hiltViewModel`
- To `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
- Files: YouTubeArtistMenu.kt, LibraryVideosScreen.kt

### 18-25. Documentation & Logging Improvements
- Add error context to deep link logging
- Add KDoc to CompositionLocals
- Improve theme extraction error handling
- Add Timber initialization verification
- Remove unused variable suppressions
- Simplify skip button logic with comments

---

## Testing Checklist

### Before Release:
- [ ] Test release build signing with environment variables
- [ ] Test service binding/unbinding on rotation
- [ ] Test with POST_NOTIFICATIONS permission denied
- [ ] Test empty library screens
- [ ] Test playback error scenarios (no network, bad URL)
- [ ] Test on low storage devices (< 1GB)
- [ ] Test accessibility with screen reader
- [ ] Test deep links (albums, playlists, artists)

### Regression Testing:
- [ ] App starts without ANR
- [ ] Music playback works normally
- [ ] Navigation drawer functions properly
- [ ] Search and quick picks work
- [ ] Theme extraction from artwork
- [ ] Database migrations on update

---

## File Locations Reference

| Component | Location |
|-----------|----------|
| Main Activity | `app/src/main/kotlin/com/jtech/zemer/MainActivity.kt` |
| Music Service | `app/src/main/kotlin/com/jtech/zemer/playback/MusicService.kt` |
| Player Connection | `app/src/main/kotlin/com/jtech/zemer/playback/PlayerConnection.kt` |
| Database | `app/src/main/kotlin/com/jtech/zemer/db/MusicDatabase.kt` |
| Build Config | `app/build.gradle.kts` |
| Manifest | `app/src/main/AndroidManifest.xml` |
| ProGuard Rules | `app/proguard-rules.pro` |
| App Class | `app/src/main/kotlin/com/jtech/zemer/App.kt` |

---

## Issue Severity Summary

```
Critical:      1 issue (Release signing)
High:          8 issues (Crashes, permissions, error handling)
Medium:        9 issues (Polish, edge cases, resource management)
Low:           9 issues (Code quality, documentation)
────────────────────────────
Total:        27 issues
```

**Estimated Fix Time:** 18-43 hours
**Recommended Priority:** Fix all Critical & High before release

---

## Key Statistics

- **Total Kotlin Files:** 244
- **Database Version:** 28
- **ProGuard Rules:** Comprehensive (184 lines)
- **Lint Errors:** 0
- **Build Warnings:** 2 (deprecated imports)
- **Composable Screens:** 81
- **ViewModels:** 27+

---

## Quick Wins (High Impact, Low Effort)

1. Fix release signing (5 min) - CRITICAL
2. Update deprecated imports (10 min)
3. Add service unbinding error handling (10 min)
4. Add .firstOrNull() safety checks (20 min)
5. Add timber initialization verification (5 min)

**Total Time for Quick Wins: 50 minutes**

These 5 changes will significantly improve release readiness.

