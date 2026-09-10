window.ImsNav = {
  links: [
    { href: 'index.html', icon: '🏠', label: '首页' },
    { href: 'inventory.html', icon: '📊', label: '库存查询' },
    { href: 'movements.html', icon: '📋', label: '出入库单据' },
    { href: 'stocktake.html', icon: '🔍', label: '盘点管理' },
    { href: 'alerts.html', icon: '⚠️', label: '库存预警' },
    { href: 'categories.html', icon: '🗂️', label: '商品分类' },
    { href: 'skus.html', icon: '📦', label: '商品 SKU' },
    { href: 'warehouses.html', icon: '🏭', label: '仓库管理' },
    { href: 'suppliers.html', icon: '🏢', label: '供应商' },
    { href: 'customers.html', icon: '👥', label: '客户' }
  ],
  render(active) {
    return this.links.map(l =>
      `<a href="${l.href}" class="nav-link${l.href === active ? ' active' : ''}">${l.icon} ${l.label}</a>`
    ).join('');
  }
};
