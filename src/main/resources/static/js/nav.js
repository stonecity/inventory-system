window.ImsNav = {
  TABS_KEY: 'ims_open_tabs',
  MSG_OPEN: 'ims:open-tab',

  links: [
    { href: 'dashboard.html', icon: '🏠', label: '首页' },
    { href: 'inventory.html', icon: '📊', label: '库存查询' },
    { href: 'movements.html', icon: '📋', label: '出入库单据' },
    { href: 'stocktake.html', icon: '🔍', label: '盘点管理' },
    { href: 'alerts.html', icon: '⚠️', label: '库存预警' },
    { href: 'reports.html', icon: '📈', label: '报表中心' },
    { href: 'products.html', icon: '🏷️', label: '商品 SPU' },
    { href: 'skus.html', icon: '📦', label: '商品 SKU' },
    { href: 'categories.html', icon: '🗂️', label: '商品分类' },
    { href: 'warehouses.html', icon: '🏭', label: '仓库管理' },
    { href: 'suppliers.html', icon: '🏢', label: '供应商' },
    { href: 'customers.html', icon: '👥', label: '客户' },
    { href: 'safety-stock.html', icon: '🛡️', label: '安全库存' },
    { href: 'users.html', icon: '👤', label: '用户管理' },
    { href: 'roles.html', icon: '🔐', label: '角色权限' },
    { href: 'settings.html', icon: '⚙️', label: '系统设置' }
  ],

  pageKey(href) {
    if (!href) return '';
    const path = String(href).split('#')[0].split('?')[0];
    return path.split('/').pop() || '';
  },

  normalize(href) {
    if (!href) return null;
    try {
      const url = new URL(href, location.origin);
      if (url.origin !== location.origin) return null;
      const file = url.pathname.split('/').pop();
      if (!file || !/\.html$/i.test(file)) return null;
      const name = file.toLowerCase();
      if (name === 'login.html') return null;
      if (name === 'index.html') return 'dashboard.html';
      return file + url.search;
    } catch (e) {
      return null;
    }
  },

  findLink(href) {
    const key = this.pageKey(href);
    return this.links.find(l => this.pageKey(l.href) === key) || null;
  },

  render(active) {
    const activeKey = this.pageKey(active);
    return this.links.map(l => {
      const current = this.pageKey(l.href) === activeKey ? ' active' : '';
      return `<a href="${l.href}" data-ims-page="${l.href}" class="nav-link${current}">${l.icon} ${l.label}</a>`;
    }).join('');
  },

  openPage(href) {
    const normalized = this.normalize(href);
    if (!normalized) return;
    if (window.self !== window.top) {
      window.parent.postMessage({ type: this.MSG_OPEN, href: normalized }, location.origin);
      return;
    }
    if (window.ImsShell && typeof window.ImsShell.openTab === 'function') {
      window.ImsShell.openTab(normalized);
      return;
    }
    location.href = normalized;
  },

  clearTabs() {
    try { sessionStorage.removeItem(this.TABS_KEY); } catch (e) { /* ignore */ }
  }
};

(function bootNav() {
  const file = ImsNav.pageKey(location.pathname) || 'index.html';
  if (file === 'login.html') return;

  if (file !== 'index.html' && window.self === window.top) {
    location.replace('/index.html#' + file + location.search);
    return;
  }

  if (window.self === window.top) return;

  document.documentElement.classList.add('ims-embedded');
  const style = document.createElement('style');
  style.id = 'ims-embedded-style';
  style.textContent = [
    'html.ims-embedded, html.ims-embedded body { height:100%; min-height:100%; background:#f8fafc; }',
    'html.ims-embedded #app > aside { display:none !important; }',
    'html.ims-embedded #app { min-height:100%; }'
  ].join('');
  (document.head || document.documentElement).appendChild(style);

  function hideShellDuplicates() {
    document.querySelectorAll('header button').forEach(btn => {
      const text = (btn.textContent || '').replace(/\s+/g, '');
      if ((text === '退出' || text === '退出登录') && !btn.hidden) btn.hidden = true;
    });
  }
  const observe = () => {
    hideShellDuplicates();
    if (!document.body) return;
    new MutationObserver(hideShellDuplicates).observe(document.body, { childList: true, subtree: true });
  };
  if (document.body) observe();
  else document.addEventListener('DOMContentLoaded', observe);

  document.addEventListener('click', function (e) {
    const a = e.target.closest && e.target.closest('a[href]');
    if (!a) return;
    const href = a.getAttribute('href');
    if (!href || /^(https?:|mailto:|javascript:|#)/i.test(href)) return;
    const normalized = ImsNav.normalize(href);
    if (!normalized) return;
    e.preventDefault();
    ImsNav.openPage(normalized);
  }, true);
})();
