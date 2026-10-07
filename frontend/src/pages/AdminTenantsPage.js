import React, { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { getTenants, setTenantEnabled, resetTenantPassword } from '../api/api';

export default function AdminTenantsPage() {
  const location = useLocation();
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [credentials, setCredentials] = useState(location.state?.credentials || null);
  const [copied, setCopied] = useState(false);

  const load = () =>
    getTenants()
      .then((res) => setTenants(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load tenants'))
      .finally(() => setLoading(false));

  useEffect(() => {
    load();
    // Clear the one-time credentials from the browser history so a refresh does not show them again
    if (location.state?.credentials) window.history.replaceState({}, document.title);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggle = async (t) => {
    const action = t.enabled ? 'disable' : 'enable';
    if (!window.confirm(`Do you want to ${action} "${t.name}"?`)) return;
    try {
      await setTenantEnabled(t.id, !t.enabled);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Action failed');
    }
  };

  const reset = async (t) => {
    if (!window.confirm(`Reset the password of "${t.name}" to its default? They must change it at next login.`)) return;
    try {
      const res = await resetTenantPassword(t.id);
      setCredentials(res.data);
      setCopied(false);
      window.scrollTo(0, 0);
    } catch (err) {
      alert(err.response?.data?.message || 'Could not reset the password');
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(
        `Username: ${credentials.username}\nPassword: ${credentials.defaultPassword}`
      );
      setCopied(true);
    } catch (e) {
      alert('Could not copy. Please select and copy the text manually.');
    }
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Tenants</h1>
          <p className="subtitle">Businesses that use the billing application</p>
        </div>
        <Link to="/admin/tenants/new" className="btn">+ New Tenant</Link>
      </div>

      {credentials && (
        <div className="card" style={{ borderLeft: '4px solid #16a34a' }}>
          <strong>Login details for {credentials.tenant.name}</strong>
          <p className="subtitle">Share these with the tenant now. The password is shown only once and must be changed at first login.</p>
          <p>Username: <code>{credentials.username}</code></p>
          <p>Password: <code>{credentials.defaultPassword}</code></p>
          <button className="btn secondary small" onClick={copy}>{copied ? 'Copied ✓' : 'Copy'}</button>{' '}
          <button className="btn secondary small" onClick={() => setCredentials(null)}>Dismiss</button>
        </div>
      )}

      <div className="card">
        {error && <p className="error-text">{error}</p>}
        {loading ? (
          <p className="empty">Loading…</p>
        ) : tenants.length === 0 ? (
          <p className="empty">No tenants yet. Click "New Tenant" to add the first one.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Tenant</th><th>Contact</th><th>Mobile</th><th>Username</th><th>Status</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {tenants.map((t) => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td>{t.contactName}</td>
                  <td>{t.mobile}</td>
                  <td>{t.username}</td>
                  <td><span className={`badge ${t.enabled ? 'PAID' : 'CANCELLED'}`}>{t.enabled ? 'ACTIVE' : 'DISABLED'}</span></td>
                  <td>
                    <Link to={`/admin/tenants/${t.id}/edit`} className="btn secondary small">Edit</Link>{' '}
                    <button className="btn secondary small" onClick={() => reset(t)}>Reset password</button>{' '}
                    <button className={`btn small ${t.enabled ? 'danger' : ''}`} onClick={() => toggle(t)}>
                      {t.enabled ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
