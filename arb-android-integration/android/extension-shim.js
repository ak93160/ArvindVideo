/* Android compatibility shim for the extension's in-page HUD and background logic. */
(function () {
  if (window.__ARB_ANDROID_SHIM__) return;
  window.__ARB_ANDROID_SHIM__ = true;
  const native = window.ArbNativeBridge;
  const listeners = { runtime: [], startup: [], installed: [], alarms: [], storage: [], tabUpdated: [], tabRemoved: [], notificationClicked: [] };
  const alarmTimers = Object.create(null);
  const alarmData = Object.create(null);
  let nextTabId = 1;
  const parse = (raw, fallback) => { try { return JSON.parse(raw); } catch (_) { return fallback; } };
  const event = list => ({
    addListener(fn) { if (typeof fn === 'function' && !list.includes(fn)) list.push(fn); },
    removeListener(fn) { const i = list.indexOf(fn); if (i >= 0) list.splice(i, 1); },
    hasListener(fn) { return list.includes(fn); }
  });
  const dispatch = (list, args) => list.slice().forEach(fn => { try { fn.apply(null, args); } catch (e) { console.error('[ARB event]', e); } });
  const localGet = keys => {
    if (!native) return {};
    const result = parse(native.getStorage(JSON.stringify(keys == null ? null : keys)), {});
    if (keys && typeof keys === 'object' && !Array.isArray(keys)) {
      for (const [k, v] of Object.entries(keys)) if (!(k in result)) result[k] = v;
    }
    return result;
  };
  const local = {
    get(keys, callback) { const result = localGet(keys); if (callback) queueMicrotask(() => callback(result)); return Promise.resolve(result); },
    set(items, callback) {
      const old = localGet(Object.keys(items || {}));
      native && native.setStorage(JSON.stringify(items || {}));
      const changes = {};
      for (const [k, v] of Object.entries(items || {})) changes[k] = {oldValue: old[k], newValue: v};
      dispatch(listeners.storage, [changes, 'local']);
      if (callback) queueMicrotask(callback);
      return Promise.resolve();
    },
    remove(keys, callback) {
      const list = Array.isArray(keys) ? keys : [keys];
      const old = localGet(list);
      native && native.removeStorage(JSON.stringify(list));
      const changes = {};
      list.forEach(k => changes[k] = {oldValue: old[k], newValue: undefined});
      dispatch(listeners.storage, [changes, 'local']);
      if (callback) queueMicrotask(callback);
      return Promise.resolve();
    },
    clear(callback) {
      const all = localGet(null), keys = Object.keys(all);
      native && native.removeStorage(JSON.stringify(keys));
      const changes = {};
      keys.forEach(k => changes[k] = {oldValue: all[k], newValue: undefined});
      dispatch(listeners.storage, [changes, 'local']);
      if (callback) queueMicrotask(callback);
      return Promise.resolve();
    }
  };
  const runtime = {
    id: 'arb-android-webview', lastError: undefined,
    getManifest: () => ({name: 'Arvind Bot', version: '4.5.1'}),
    getURL: path => native && native.assetDataUrl(String(path || '').replace(/^\/+/, '')) || '',
    onMessage: event(listeners.runtime),
    onStartup: event(listeners.startup),
    onInstalled: event(listeners.installed),
    __fireStartup() { dispatch(listeners.startup, []); },
    __fireInstalled() { dispatch(listeners.installed, [{reason: 'install'}]); },
    __fireTabUpdated() { dispatch(listeners.tabUpdated, [1, {status: 'complete'}, {id: 1, url: native ? native.currentUrl() : ''}]); },
    sendMessage(message, callback) {
      let replied = false;
      let wantsAsync = false;
      const pending = [];
      let resolvePromise;
      const promise = new Promise(resolve => { resolvePromise = resolve; });
      const finish = value => {
        if (replied) return;
        replied = true;
        resolvePromise(value);
        if (typeof callback === 'function') callback(value);
      };
      listeners.runtime.slice().forEach(fn => {
        try {
          const result = fn(message, {id: 'arb-android-webview', tab: {id: 1, url: native ? native.currentUrl() : ''}}, finish);
          if (result === true) wantsAsync = true;
          else if (result && typeof result.then === 'function') pending.push(result.then(finish).catch(e => console.warn('[ARB async message]', e)));
        } catch (e) { console.error('[ARB message]', e); }
      });
      if (!pending.length && !wantsAsync && !replied) queueMicrotask(() => finish(undefined));
      if (wantsAsync || pending.length) setTimeout(() => finish(undefined), 8000);
      return promise;
    }
  };
  const tabs = {
    query(queryInfo, callback) {
      const result = [{id: 1, active: true, currentWindow: true, url: native ? native.currentUrl() : ''}];
      if (callback) callback(result); return Promise.resolve(result);
    },
    get(id, callback) {
      const result = {id: 1, url: native ? native.currentUrl() : ''};
      if (callback) callback(result); return Promise.resolve(result);
    },
    update(id, props, callback) {
      if (props && props.url && native) native.navigate(props.url);
      const result = {id: 1, url: props && props.url || (native ? native.currentUrl() : '')};
      if (callback) callback(result); return Promise.resolve(result);
    },
    create(props, callback) {
      if (props && props.url && native) native.navigate(props.url);
      const result = {id: nextTabId++, url: props && props.url || ''};
      if (callback) callback(result); return Promise.resolve(result);
    },
    sendMessage(id, message, callback) { return runtime.sendMessage(message, callback); },
    onUpdated: event(listeners.tabUpdated),
    onRemoved: event(listeners.tabRemoved)
  };
  const alarms = {
    onAlarm: event(listeners.alarms),
    create(name, info) {
      if (typeof name === 'object') { info = name; name = info.name; }
      name = String(name || 'arb-alarm'); info = info || {};
      if (alarmTimers[name]) { clearTimeout(alarmTimers[name]); clearInterval(alarmTimers[name]); }
      alarmData[name] = {name, periodInMinutes: Number(info.periodInMinutes || 0), delayInMinutes: Number(info.delayInMinutes || 0)};
      const delay = Math.max(1000, (alarmData[name].delayInMinutes || alarmData[name].periodInMinutes || 1) * 60000);
      const fire = () => dispatch(listeners.alarms, [{name, scheduledTime: Date.now()}]);
      if (alarmData[name].periodInMinutes > 0) alarmTimers[name] = setInterval(fire, Math.max(delay, alarmData[name].periodInMinutes * 60000));
      else alarmTimers[name] = setTimeout(fire, delay);
    },
    clear(name, callback) {
      name = String(name); const existed = !!alarmTimers[name];
      if (alarmTimers[name]) { clearTimeout(alarmTimers[name]); clearInterval(alarmTimers[name]); delete alarmTimers[name]; delete alarmData[name]; }
      if (callback) callback(existed); return Promise.resolve(existed);
    },
    get(name, callback) { const result = alarmData[name] || null; if (callback) callback(result); return Promise.resolve(result); }
  };
  const notifications = {
    onClicked: event(listeners.notificationClicked),
    create(id, options, callback) {
      if (typeof id === 'object') { callback = options; options = id; id = 'arb-' + Date.now(); }
      options = options || {};
      native && native.notify(String(id), options.title || 'ARB Bot', options.message || options.contextMessage || '');
      if (callback) callback(String(id)); return Promise.resolve(String(id));
    },
    clear(id, callback) { if (callback) callback(true); return Promise.resolve(true); }
  };
  const chrome = window.chrome || (window.chrome = {});
  chrome.storage = chrome.storage || {};
  chrome.storage.local = local;
  chrome.storage.onChanged = event(listeners.storage);
  chrome.runtime = Object.assign(chrome.runtime || {}, runtime);
  chrome.tabs = Object.assign(chrome.tabs || {}, tabs);
  chrome.alarms = Object.assign(chrome.alarms || {}, alarms);
  chrome.notifications = Object.assign(chrome.notifications || {}, notifications);
  chrome.action = chrome.action || {setBadgeText() {}, setBadgeBackgroundColor() {}, setTitle() {}, setIcon() {}};
  chrome.browserAction = chrome.browserAction || chrome.action;
  window.__ARB_ANDROID_SHIM_READY__ = true;
})();
