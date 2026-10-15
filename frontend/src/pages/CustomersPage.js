import React, { useEffect, useState } from 'react';
import { getCustomers, createCustomer, updateCustomer, deleteCustomer, getGstStates } from '../api/api';
import { useAuth } from '../auth/AuthContext';

const emptyForm = { name: '', email: '', phone: '', address: '', gstin: '', stateCode: '' };

export default function CustomersPage() {
  const { user } = useAuth();
  const hasGst = (user.modules || []).includes('GST');
  const [states, setStates] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  const load = () => getCustomers().then((res) => setCustomers(res.data));

  useEffect(() => {
    load();
    if (hasGst) getGstStates().then((res) => setStates(res.data)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (c) => { setForm({ name: c.name, email: c.email || '', phone: c.phone || '', address: c.address || '', gstin: c.gstin || '', stateCode: c.stateCode || '' }); setEditingId(c.id); setShowForm(true); setError(''); };

  // Typing the GSTIN fills in the state, because the first two digits are the state code
  const onGstinChange = (e) => {
    const value = e.target.value.toUpperCase().replace(/\s/g, '');
    const code = value.slice(0, 2);
    const known = states.some((s) => s.code === code);
    setForm({ ...form, gstin: value, stateCode: known ? code : form.stateCode });
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (editingId) {
        await updateCustomer(editingId, form);
      } else {
        await createCustomer(form);
      }
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong');
    }
  };

  const remove = async (id) => {
    if (!window.confirm('Delete this customer? This will also delete their invoices.')) return;
    await deleteCustomer(id);
    load();
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Customers</h1>
          <p className="subtitle">Manage your customer directory</p>
        </div>
        <button className="btn" onClick={openNew}>+ New Customer</button>
      </div>

      {showForm && (
        <div className="card">
          <form onSubmit={submit}>
            <div className="form-grid">
              <div className="form-row">
                <label>Name *</label>
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
              </div>
              <div className="form-row">
                <label>Email</label>
                <input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Phone</label>
                <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Address</label>
                <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
              </div>
              {hasGst && (
                <>
                  <div className="form-row">
                    <label>GSTIN (leave empty for customers without GST)</label>
                    <input value={form.gstin} onChange={onGstinChange} maxLength={15} />
                  </div>
                  <div className="form-row">
                    <label>State (decides CGST + SGST or IGST)</label>
                    <select value={form.stateCode} onChange={(e) => setForm({ ...form, stateCode: e.target.value })}>
                      <option value="">Same as my state</option>
                      {states.map((s) => <option key={s.code} value={s.code}>{s.name} ({s.code})</option>)}
                    </select>
                  </div>
                </>
              )}
            </div>
            {error && <p className="error-text">{error}</p>}
            <button type="submit" className="btn">{editingId ? 'Update' : 'Create'}</button>{' '}
            <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </form>
        </div>
      )}

      <div className="card">
        {customers.length === 0 ? (
          <p className="empty">No customers yet.</p>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Address</th>{hasGst && <th>GSTIN</th>}<th></th></tr></thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.email || '—'}</td>
                  <td>{c.phone || '—'}</td>
                  <td>{c.address || '—'}</td>
                  {hasGst && <td>{c.gstin || 'Unregistered'}</td>}
                  <td>
                    <button className="btn secondary small" onClick={() => openEdit(c)}>Edit</button>{' '}
                    <button className="btn danger small" onClick={() => remove(c.id)}>Delete</button>
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
