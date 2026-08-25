(function () {
  var root = document.documentElement;
  if (!root) return;
  root.classList.add('kept-community-android');

  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
  }

  function rgbToHex(value) {
    var m = String(value || '').match(/rgba?\(\s*(\d+)\D+(\d+)\D+(\d+)/i);
    if (!m) return null;
    return '#' + [m[1], m[2], m[3]].map(function (x) {
      return Math.max(0, Math.min(255, Number(x))).toString(16).padStart(2, '0');
    }).join('');
  }

  function activeEditor() {
    var candidates = Array.prototype.slice.call(document.querySelectorAll(
      'app-input .mobile-compose-mode .note-main, app-input .drawing-fullscreen, app-notes .modal-container app-input .note-main'
    ));
    return candidates.find(visible) || null;
  }

  function syncChrome() {
    try {
      var editor = root.classList.contains('kept-community-compact') ? activeEditor() : null;
      if (editor) {
        root.classList.add('kept-community-editor-active');
        var color = rgbToHex(getComputedStyle(editor).backgroundColor);
        if (window.KeptNative && color) KeptNative.setNoteChrome(color);
        return;
      }
      root.classList.remove('kept-community-editor-active');
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

  function ensureSmartFab(compact) {
    var own = document.querySelector('.kept-community-smart-fab');
    var upstream = Array.prototype.slice.call(document.querySelectorAll('.smart-capture-fab')).find(visible);
    if (!compact || upstream) {
      if (own) own.remove();
      return;
    }
    if (own) return;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'kept-community-smart-fab';
    button.setAttribute('aria-label', 'Smart Capture');
    button.title = 'Smart Capture';
    button.innerHTML = '<span class="material-symbols-outlined">mic</span>';
    button.addEventListener('click', function () {
      if (window.KeptNative) KeptNative.smartCaptureUnavailable();
    });
    document.body.appendChild(button);
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
    ensureSmartFab(compact);
    observeSidebar();
    sidebarBackdrop(compact);
    syncChrome();

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
    new MutationObserver(schedule).observe(document.documentElement, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'style'] });
    window.addEventListener('resize', schedule);
    window.addEventListener('focus', schedule);
    if (document.body) {
      new MutationObserver(syncChrome).observe(document.body, { attributes: true, attributeFilter: ['class'] });
    }
  }

  apply();
  setTimeout(apply, 350);
  setTimeout(apply, 1200);
})();
