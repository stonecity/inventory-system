window.ImsAuth = (function () {
  const TOKEN_KEY = 'ims_token';
  const USER_KEY = 'ims_user';
  const PERMS_KEY = 'ims_permissions';
  const API = '/api/v1';

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function getUser() {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  function getPermissions() {
    const raw = localStorage.getItem(PERMS_KEY);
    return raw ? JSON.parse(raw) : [];
  }

  function setSession(token, user, permissions) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    localStorage.setItem(PERMS_KEY, JSON.stringify(permissions || []));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(PERMS_KEY);
  }

  function hasPermission(code) {
    const user = getUser();
    if (user && user.roles && user.roles.includes('ROLE_ADMIN')) return true;
    return getPermissions().includes(code);
  }

  function navigateTop(url) {
    try {
      if (window.top && window.top !== window.self) {
        window.top.location.href = url;
        return;
      }
    } catch (e) { /* ignore */ }
    location.href = url;
  }

  function requireAuth() {
    if (!getToken()) {
      const redirect = encodeURIComponent('/index.html');
      navigateTop('/login.html?redirect=' + redirect);
      return false;
    }
    return true;
  }

  async function api(url, options) {
    options = options || {};
    const headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const res = await fetch(API + url, Object.assign({}, options, { headers }));
    const json = await res.json();

    if (json.code === 40100) {
      clearSession();
      try { sessionStorage.removeItem('ims_open_tabs'); } catch (e) { /* ignore */ }
      navigateTop('/login.html?redirect=' + encodeURIComponent('/index.html'));
      throw new Error('请重新登录');
    }
    if (json.code === 40300) {
      throw new Error(json.message || '无权限');
    }
    if (json.code !== 0) {
      const err = new Error(json.message || '请求失败');
      err.code = json.code;
      err.data = json.data;
      throw err;
    }
    return json.data;
  }

  async function download(url, filename) {
    const headers = {};
    const token = getToken();
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch(API + url, { headers });
    if (res.status === 401) {
      clearSession();
      try { sessionStorage.removeItem('ims_open_tabs'); } catch (e) { /* ignore */ }
      navigateTop('/login.html');
      throw new Error('请重新登录');
    }
    if (!res.ok) {
      throw new Error('导出失败');
    }
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(a.href);
  }

  async function logout() {
    try {
      await api('/auth/logout', { method: 'POST' });
    } catch (e) { /* ignore */ }
    clearSession();
    try { sessionStorage.removeItem('ims_open_tabs'); } catch (e) { /* ignore */ }
    navigateTop('/login.html');
  }

  return {
    getToken, getUser, getPermissions, setSession, clearSession,
    hasPermission, requireAuth, api, download, logout
  };
})();

window.ImsTime = (function () {
  const ZONE = 'Asia/Shanghai';

  function toDate(v) {
    if (v == null || v === '') return null;
    const d = typeof v === 'number' ? new Date(v > 1e12 ? v : v * 1000) : new Date(v);
    return Number.isNaN(d.getTime()) ? null : d;
  }

  function format(v, withSeconds) {
    const d = toDate(v);
    if (!d) return v == null || v === '' ? '-' : String(v);
    const opts = {
      timeZone: ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23'
    };
    if (withSeconds !== false) {
      opts.second = '2-digit';
    }
    return new Intl.DateTimeFormat('sv-SE', opts).format(d).replace('T', ' ');
  }

  function todayYmd() {
    return new Intl.DateTimeFormat('sv-SE', {
      timeZone: ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date());
  }

  function addDaysYmd(ymd, days) {
    const d = new Date(ymd + 'T12:00:00+08:00');
    d.setTime(d.getTime() + days * 86400000);
    return new Intl.DateTimeFormat('sv-SE', {
      timeZone: ZONE,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(d);
  }

  function dayStartIso(ymd) {
    return new Date(ymd + 'T00:00:00+08:00').toISOString();
  }

  function dayEndIso(ymd) {
    return new Date(ymd + 'T23:59:59.999+08:00').toISOString();
  }

  return { ZONE, format, todayYmd, addDaysYmd, dayStartIso, dayEndIso };
})();
