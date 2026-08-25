# Kept Community Android v0.3 UI integration

This version is driven by the responsive audit of upstream Kept at 320x568, 360x800, 393x852, 412x915, 600x960, 768x1024, and 800x360.

Observed before v0.3:
- no horizontal overflow at any tested size;
- 393-412px portrait phone layouts were strong;
- 600px triggered a 280px persistent sidebar and made note cards narrower than on a phone;
- 800x360 landscape behaved like desktop despite compact height;
- the v0.2 native 48dp toolbar duplicated Kept navigation and consumed useful height;
- several top-level mobile controls were smaller than Android touch-target guidance;
- the pinned-to-other gap was disproportionately large on phones.

v0.3 response:
- WebView is full-content; no permanent native toolbar;
- compact presentation when width <840dp OR height <480dp;
- medium-width Android windows use the Kept drawer/mobile composition;
- <340dp uses a single note column;
- app-only settings are injected into the Kept side drawer;
- PWA install UI is hidden because the app is already installed;
- important mobile navigation targets are enlarged;
- pinned spacing is reduced on compact windows;
- Android system bars follow the Kept light/dark theme.

These changes affect presentation only. They do not fake Capacitor platform/plugin availability, so unpublished KeptGeofence/KeptSmartCapture implementations are still not claimed.
