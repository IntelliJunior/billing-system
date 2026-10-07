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

// Sidebar + content area, with a menu that depends on the role
function Shell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user.role === 'ADMIN';

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
              <NavLink to="/admin" end>Tenants</NavLink>
          ) : (
              <>
                <NavLink to="/" end>Dashboard</NavLink>
                <NavLink to="/customers">Customers</NavLink>
                <NavLink to="/products">Products</NavLink>
                <NavLink to="/invoices" end>Invoices</NavLink>
                <NavLink to="/invoices/new">New Invoice</NavLink>
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
            </Route>

            {/* Tenant area (the billing application) */}
            <Route element={<RequireAuth roles={['TENANT']}><Shell /></RequireAuth>}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/customers" element={<CustomersPage />} />
              <Route path="/products" element={<ProductsPage />} />
              <Route path="/invoices" element={<InvoicesPage />} />
              <Route path="/invoices/new" element={<NewInvoicePage />} />
              <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
  );
}

export default App;
