/* Partial WebView shim. Load only on the app's explicitly allowlisted ARBPay hosts. */
(() => {
  if (window.__ARB_ANDROID_SHIM__) return;
  window.__ARB_ANDROID_SHIM__ = true;

  const native = window.ArbNativeBridge;
  const storageListeners = [];
  const messageListeners = [];
  const cache = Object.create(null);

  const parse = (raw, fallback) => {
    try { return JSON.parse(raw); } catch (_) { return fallback; }
  };

  const nativeRead = query => {
    if (!native || typeof native.getStorage !== "function") return {};
    return parse(native.getStorage(JSON.stringify(query ?? null)), {});
  };

  const keysFor = (keys) => {
    if (keys == null) return Object.keys(cache);
    if (typeof keys === "string") return [keys];
    if (Array.isArray(keys)) return keys;
    if (typeof keys === "object") return Object.keys(keys);
    return [];
  };

  const local = {
    get(keys, callback) {
      const result = nativeRead(keys);
      if (keys && typeof keys === "object" && !Array.isArray(keys)) {
        for (const [key, fallback] of Object.entries(keys)) {
          if (!(key in result)) result[key] = Object.prototype.hasOwnProperty.call(cache, key) ? cache[key] : fallback;
        }
      } else {
        for (const key of keysFor(keys)) {
          if (!(key in result) && Object.prototype.hasOwnProperty.call(cache, key)) result[key] = cache[key];
        }
      }
      if (typeof callback === "function") queueMicrotask(() => callback(result));
      return Promise.resolve(result);
    },
    set(items, callback) {
      const changes = {};
      for (const [key, value] of Object.entries(items || {})) {
        const oldValue = nativeRead([key])[key] ?? cache[key];
        cache[key] = value;
        changes[key] = { oldValue, newValue: value };
      }
      try { native?.setStorage?.(JSON.stringify(items || {})); } catch (_) {}
      if (Object.keys(changes).length) storageListeners.forEach(fn => { try { fn(changes, "local"); } catch (_) {} });
      if (typeof callback === "function") queueMicrotask(callback);
      return Promise.resolve();
    },
    remove(keys, callback) {
      const list = Array.isArray(keys) ? keys : [keys];
      const changes = {};
      for (const key of list) {
        const oldValue = nativeRead([key])[key] ?? cache[key];
        delete cache[key];
        changes[key] = { oldValue, newValue: undefined };
      }
      try { native?.removeStorage?.(JSON.stringify(list)); } catch (_) {}
      storageListeners.forEach(fn => { try { fn(changes, "local"); } catch (_) {} });
      if (typeof callback === "function") queueMicrotask(callback);
      return Promise.resolve();
    },
    clear(callback) {
      const all = nativeRead(null);
      const list = Object.keys(all);
      list.forEach(key => delete cache[key]);
      try { native?.removeStorage?.(JSON.stringify(list)); } catch (_) {}
      if (typeof callback === "function") queueMicrotask(callback);
      return Promise.resolve();
    }
  };

  const chromeApi = window.chrome || (window.chrome = {});
  chromeApi.storage = chromeApi.storage || {};
  chromeApi.storage.local = local;
  chromeApi.storage.onChanged = chromeApi.storage.onChanged || {};
  chromeApi.storage.onChanged.addListener = fn => { if (typeof fn === "function") storageListeners.push(fn); };

  chromeApi.runtime = chromeApi.runtime || {};
  chromeApi.runtime.lastError = undefined;
  chromeApi.runtime.getManifest = () => ({ name: "ARB Android Port", version: "0.1.0" });
  chromeApi.runtime.getURL = path => "file:///android_asset/arb-bot/" + String(path || "").replace(/^\/+/, "");
  chromeApi.runtime.onMessage = chromeApi.runtime.onMessage || {};
  chromeApi.runtime.onMessage.addListener = fn => { if (typeof fn === "function") messageListeners.push(fn); };
  chromeApi.runtime.sendMessage = (message, callback) => {
    let response = { ok: true };
    try {
      if (native && typeof native.sendMessage === "function") {
        response = parse(native.sendMessage(JSON.stringify(message || {})), { ok: true });
      }
    } catch (error) {
      response = { ok: false, error: String(error) };
    }
    for (const listener of messageListeners) {
      try { listener(message, { id: "android-webview" }, () => {}); } catch (_) {}
    }
    if (typeof callback === "function") queueMicrotask(() => callback(response));
    return Promise.resolve(response);
  };

  window.__ARB_ANDROID_STORAGE_CHANGED__ = changes => {
    storageListeners.forEach(fn => { try { fn(changes || {}, "local"); } catch (_) {} });
  };
})();
