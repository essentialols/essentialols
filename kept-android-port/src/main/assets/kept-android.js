(function () {
  var root = document.documentElement;
  if (!root) return;
  root.classList.add('kept-community-android');

  function syncTheme() {
    try {
      var light = !!(document.body && document.body.classList.contains('light-theme'));
      if (window.KeptNative) KeptNative.setTheme(light ? 'light' : 'dark');
    } catch (_) {}
  }

  function ensureMenu() {
    var bottom = document.querySelector('app-sidenav .sidenav-bottom');
    if (!bottom || bottom.querySelector('.kept-community-app-menu')) return;
    var item = document.createElement('div');
    item.className = 'item kept-community-app-menu';
    item.setAttribute('role', 'button');
    item.setAttribute('tabindex', '0');
    item.innerHTML = '<span class="material-pic material-symbols-outlined" aria-hidden="true">tune</span><span class="text">Android app settings</span>';
    var open = function () { if (window.KeptNative) KeptNative.openMenu(); };
    item.addEventListener('click', open);
    item.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); }
    });
    bottom.appendChild(item);
  }

  function sidebarBackdrop(compact) {
    var sidebar = document.querySelector('app-sidenav .main-sidebar');
    var own = document.querySelector('.kept-community-sidebar-backdrop');
    var upstream = document.querySelector('app-sidenav .sidebar-backdrop');
    var open = !!(compact && sidebar && !sidebar.classList.contains('close'));
    if (open && !upstream && !own) {
      own = document.createElement('div');
      own.className = 'kept-community-sidebar-backdrop';
      own.addEventListener('click', function () {
        var button = document.querySelector('app-navbar .pic-container');
        if (button) button.click();
      });
      document.body.appendChild(own);
    }
    if ((!open || upstream) && own) own.remove();
  }

  function observeSidebar() {
    var sidebar = document.querySelector('app-sidenav .main-sidebar');
    if (!sidebar || sidebar.__keptCommunityObserved) return;
    sidebar.__keptCommunityObserved = true;
    new MutationObserver(function () {
      sidebarBackdrop(root.classList.contains('kept-community-compact'));
    }).observe(sidebar, { attributes: true, attributeFilter: ['class'] });
  }

  function apply() {
    var compact = innerWidth < 840 || innerHeight < 480;
    var tiny = innerWidth < 340;
    var previous = root.__keptCommunityCompact;
    root.classList.toggle('kept-community-compact', compact);
    root.classList.toggle('kept-community-tiny', tiny);

    document.querySelectorAll('.main-section, app-notes .main-container').forEach(function (el) {
      el.classList.toggle('native-phone-layout', compact);
    });

    var sidebar = document.querySelector('app-sidenav .main-sidebar');
    if (sidebar && compact && !sidebar.classList.contains('close') && !root.__keptCommunityAutoClosed) {
      var button = document.querySelector('app-navbar .pic-container');
      if (button) { button.click(); root.__keptCommunityAutoClosed = true; }
    }
    if (sidebar && !compact && root.__keptCommunityAutoClosed && sidebar.classList.contains('close')) {
      var button2 = document.querySelector('app-navbar .pic-container');
      if (button2) button2.click();
      root.__keptCommunityAutoClosed = false;
    }

    ensureMenu();
    observeSidebar();
    sidebarBackdrop(compact);
    syncTheme();

    if (previous !== undefined && previous !== compact) {
      setTimeout(function () { window.dispatchEvent(new Event('resize')); }, 40);
    }
    root.__keptCommunityCompact = compact;
  }

  if (!window.__keptCommunityUiInstalled) {
    window.__keptCommunityUiInstalled = true;
    var pending = 0;
    var schedule = function () {
      if (pending) return;
      pending = requestAnimationFrame(function () { pending = 0; apply(); });
    };
    new MutationObserver(schedule).observe(document.documentElement, { subtree: true, childList: true });
    window.addEventListener('resize', schedule);
    if (document.body) {
      new MutationObserver(syncTheme).observe(document.body, { attributes: true, attributeFilter: ['class'] });
    }
  }

  apply();
  setTimeout(apply, 350);
  setTimeout(apply, 1200);
})();
