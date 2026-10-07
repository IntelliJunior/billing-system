import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { changePassword } from '../api/api';
import { useAuth } from '../auth/AuthContext';

export default function ChangePasswordPage() {
  const { user, refresh, logout } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const forced = user.mustChangePassword;
  const home = user.role === 'ADMIN' ? '/admin' : '/';

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters');
      return;
    }
    if (newPassword !== confirm) {
      setError('New password and confirmation do not match');
      return;
    }
    setBusy(true);
    try {
      await changePassword({ currentPassword, newPassword });
      await refresh();
      navigate(home, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not change the password');
    } finally {
      setBusy(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div style={{ maxWidth: 420, margin: '10vh auto', padding: '0 16px' }}>
      <div className="card">
        <h1 style={{ marginTop: 0 }}>Change password</h1>
        <p className="subtitle">
          {forced
            ? 'You must set a new password before you continue.'
            : `Signed in as ${user.username}`}
        </p>
        <form onSubmit={submit}>
          <div className="form-row">
            <label>Current password</label>
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required />
          </div>
          <div className="form-row">
            <label>New password (min 8 characters)</label>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
          </div>
          <div className="form-row">
            <label>Confirm new password</label>
            <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
          </div>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="btn" disabled={busy}>{busy ? 'Saving…' : 'Change password'}</button>
          {' '}
          {forced ? (
            <button type="button" className="btn secondary" onClick={handleLogout}>Logout</button>
          ) : (
            <Link to={home} className="btn secondary">Cancel</Link>
          )}
        </form>
      </div>
    </div>
  );
}
