import React, { useEffect, useState } from 'react';
import {
  getAdminGstRates, createGstRate, updateGstRate, setGstRateActive, getGstSettings, updateGstSettings,
} from '../api/api';

const emptyForm = { ratePercent: '', label: '', validFrom: '', validTo: '' };

export default function AdminGstRulesPage() {
  const [rates, setRates] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');
  const [threshold, setThreshold] = useState('');
  const [settingsMessage, setSettingsMessage] = useState('');
  const [settingsError, setSettingsError] = useState('');

  const load = () =>
    getAdminGstRates()
      .then((res) => setRates(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load the rates'));

  useEffect(() => {
    load();
    getGstSettings().then((res) => setThreshold(String(res.data.b2clThreshold))).catch(() => {});
  }, []);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (r) => {
    setForm({
      ratePercent: String(Number(r.ratePercent)),
      label: r.label || '',
      validFrom: r.validFrom || '',
      validTo: r.validTo || '',
    });
    setEditingId(r.id);
    setShowForm(true);
    setError('');
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const payload = {
      ratePercent: Number(form.ratePercent),
      label: form.label,
      validFrom: form.validFrom || null,
      validTo: form.validTo || null,
    };
    try {
      if (editingId) await updateGstRate(editingId, payload);
      else await createGstRate(payload);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save the rate');
    }
  };

  const toggle = async (r) => {
    try {
      await setGstRateActive(r.id, !r.active);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Action failed');
    }
  };

  const saveSettings = async (e) => {
    e.preventDefault();
    setSettingsMessage('');
    setSettingsError('');
    try {
      await updateGstSettings({ b2clThreshold: Number(threshold) });
      setSettingsMessage('Saved.');
    } catch (err) {
      setSettingsError(err.response?.data?.message || 'Could not save');
    }
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>GST Rules</h1>
          <p className="subtitle">The rates tenants can use on invoices, and the settings used by GST reports</p>
        </div>
        <button className="btn" onClick={openNew}>+ New rate</button>
      </div>

      {showForm && (
        <div className="card">
          <form onSubmit={submit}>
            <div className="form-grid">
              <div className="form-row">
                <label>Rate % *</label>
                <input type="number" step="0.01" min="0" max="100" value={form.ratePercent} onChange={set('ratePercent')} required />
              </div>
              <div className="form-row">
                <label>Label (shown in dropdowns)</label>
                <input value={form.label} onChange={set('label')} maxLength={100} />
              </div>
              <div className="form-row">
                <label>Valid from (empty = no start limit)</label>
                <input type="date" value={form.validFrom} onChange={set('validFrom')} />
              </div>
              <div className="form-row">
                <label>Valid until (empty = no end limit)</label>
                <input type="date" value={form.validTo} onChange={set('validTo')} />
              </div>
            </div>
            {error && <p className="error-text">{error}</p>}
            <button type="submit" className="btn">{editingId ? 'Update' : 'Add rate'}</button>{' '}
            <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </form>
        </div>
      )}

      <div className="card">
        {!showForm && error && <p className="error-text">{error}</p>}
        {rates.length === 0 ? (
          <p className="empty">No rates yet.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Rate</th><th>Label</th><th>Valid from</th><th>Valid until</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {rates.map((r) => (
                <tr key={r.id}>
                  <td>{Number(r.ratePercent)}%</td>
                  <td>{r.label || '—'}</td>
                  <td>{r.validFrom || 'No limit'}</td>
                  <td>{r.validTo || 'No limit'}</td>
                  <td><span className={`badge ${r.active ? 'PAID' : 'CANCELLED'}`}>{r.active ? 'ON' : 'OFF'}</span></td>
                  <td>
                    <button className="btn secondary small" onClick={() => openEdit(r)}>Edit</button>{' '}
                    <button className={`btn small ${r.active ? 'danger' : ''}`} onClick={() => toggle(r)}>
                      {r.active ? 'Switch off' : 'Switch on'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="subtitle" style={{ marginTop: 12 }}>
          Changing a rate here does not change invoices that already exist. A rate that is switched off, or whose dates
          do not cover the invoice date, cannot be used on new invoices.
        </p>
      </div>

      <div className="card">
        <strong>Report settings</strong>
        <form onSubmit={saveSettings} style={{ marginTop: 12 }}>
          <div className="form-row" style={{ maxWidth: 360 }}>
            <label>Large inter-state B2C invoice limit (₹)</label>
            <input type="number" min="0" step="1" value={threshold} onChange={(e) => setThreshold(e.target.value)} required />
          </div>
          <p className="subtitle">
            Inter-state sales to customers without a GSTIN above this value are listed invoice by invoice in the GST
            report. Check the current limit with your Chartered Accountant.
          </p>
          {settingsError && <p className="error-text">{settingsError}</p>}
          {settingsMessage && <p>{settingsMessage}</p>}
          <button type="submit" className="btn">Save</button>
        </form>
      </div>
    </div>
  );
}
