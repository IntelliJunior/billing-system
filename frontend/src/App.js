import React from 'react';
import { BrowserRouter, Routes, Route, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import AdminTenantsPage from './pages/AdminTenantsPage';
import AdminTenantFormPage from './pages/AdminTenantFormPage';
import CustomersPage from './pages/CustomersPage';
import ProductsPage from './pages/ProductsPage';
import InvoicesPage from './pages/InvoicesPage';
import InvoiceDetailPage from './pages/InvoiceDetailPage';
import NewInvoicePage from './pages/NewInvoicePage';
import DashboardPage from './pages/DashboardPage';
import StockPage from './pages/StockPage';
import InventoryDashboardPage from './pages/InventoryDashboardPage';
import InventoryReportsPage from './pages/InventoryReportsPage';
import MovementsPage from './pages/MovementsPage';
import SuppliersPage from './pages/SuppliersPage';
import GstReportsPage from './pages/GstReportsPage';
import AdminGstRulesPage from './pages/AdminGstRulesPage';

// Lets a page through only if the user is signed in (and has one of the allowed roles)
function RequireAuth({ roles, children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <p className="empty">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />;
  }
  return children;
}

// Lets a tenant page through only if the admin enabled one of the listed features
function RequireModule({ any, children }) {
  const { user } = useAuth();
  const modules = user.modules || [];
  if (!any.some((m) => modules.includes(m))) return <Navigate to="/" replace />;
  return children;
}

// Landing page depends on the enabled features
function Home() {
  const { user } = useAuth();
  const modules = user.modules || [];
  if (modules.includes('INVOICING')) return <DashboardPage />;
  if (modules.includes('INVENTORY')) return <Navigate to="/inventory" replace />;
  return <p className="empty">No features are enabled for your account. Please contact your administrator.</p>;
}

const sectionLabel = { color: '#9ca3af', fontSize: 11, letterSpacing: 1, margin: '14px 12px 4px' };

// Sidebar + content area, with a menu that depends on the role and the enabled features
function Shell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user.role === 'ADMIN';
  const modules = user.modules || [];
  const hasInvoicing = modules.includes('INVOICING');
  const hasInventory = modules.includes('INVENTORY');
  const hasGst = modules.includes('GST');

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <h2>💳 Billing</h2>
        <p style={{ color: '#9ca3af', fontSize: 12, margin: '0 12px 12px' }}>
          {isAdmin ? 'Administrator' : user.tenantName}
        </p>
        {isAdmin ? (
          <>
            <NavLink to="/admin" end>Tenants</NavLink>
            <NavLink to="/admin/gst-rules">GST Rules</NavLink>
          </>
        ) : (
          <>
            {hasInvoicing && <NavLink to="/" end>Dashboard</NavLink>}
            {hasInvoicing && <NavLink to="/customers">Customers</NavLink>}
            <NavLink to="/products">Products</NavLink>
            {hasInvoicing && <NavLink to="/invoices" end>Invoices</NavLink>}
            {hasInvoicing && <NavLink to="/invoices/new">New Invoice</NavLink>}
            {hasGst && <NavLink to="/gst">GST Reports</NavLink>}
            {hasInventory && (
              <>
                <p style={sectionLabel}>INVENTORY</p>
                <NavLink to="/inventory" end>Overview</NavLink>
                <NavLink to="/inventory/stock">Stock</NavLink>
                <NavLink to="/inventory/movements">Stock History</NavLink>
                <NavLink to="/inventory/reports">Reports</NavLink>
                <NavLink to="/inventory/suppliers">Suppliers</NavLink>
              </>
            )}
          </>
        )}
        <NavLink to="/change-password">Change Password</NavLink>
        <button className="btn secondary small" style={{ margin: '16px 12px' }} onClick={handleLogout}>
          Logout
        </button>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}

const INVOICING = ['INVOICING'];
const INVENTORY = ['INVENTORY'];
const GST = ['GST'];
const EITHER = ['INVOICING', 'INVENTORY'];

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/change-password" element={<RequireAuth><ChangePasswordPage /></RequireAuth>} />

          {/* Admin area */}
          <Route element={<RequireAuth roles={['ADMIN']}><Shell /></RequireAuth>}>
            <Route path="/admin" element={<AdminTenantsPage />} />
            <Route path="/admin/tenants/new" element={<AdminTenantFormPage />} />
            <Route path="/admin/tenants/:id/edit" element={<AdminTenantFormPage />} />
            <Route path="/admin/gst-rules" element={<AdminGstRulesPage />} />
          </Route>

          {/* Tenant area */}
          <Route element={<RequireAuth roles={['TENANT']}><Shell /></RequireAuth>}>
            <Route path="/" element={<Home />} />

            {/* Invoicing */}
            <Route path="/customers" element={<RequireModule any={INVOICING}><CustomersPage /></RequireModule>} />
            <Route path="/invoices" element={<RequireModule any={INVOICING}><InvoicesPage /></RequireModule>} />
            <Route path="/invoices/new" element={<RequireModule any={INVOICING}><NewInvoicePage /></RequireModule>} />
            <Route path="/invoices/:id" element={<RequireModule any={INVOICING}><InvoiceDetailPage /></RequireModule>} />

            {/* Shared by both features */}
            <Route path="/products" element={<RequireModule any={EITHER}><ProductsPage /></RequireModule>} />

            {/* GST */}
            <Route path="/gst" element={<RequireModule any={GST}><GstReportsPage /></RequireModule>} />

            {/* Inventory */}
            <Route path="/inventory" element={<RequireModule any={INVENTORY}><InventoryDashboardPage /></RequireModule>} />
            <Route path="/inventory/stock" element={<RequireModule any={INVENTORY}><StockPage /></RequireModule>} />
            <Route path="/inventory/reports" element={<RequireModule any={INVENTORY}><InventoryReportsPage /></RequireModule>} />
            <Route path="/inventory/movements" element={<RequireModule any={INVENTORY}><MovementsPage /></RequireModule>} />
            <Route path="/inventory/suppliers" element={<RequireModule any={INVENTORY}><SuppliersPage /></RequireModule>} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
