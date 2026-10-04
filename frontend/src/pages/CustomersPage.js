import React, { useEffect, useState } from 'react';
import { getCustomers, createCustomer, updateCustomer, deleteCustomer } from '../api/api';

const emptyForm = { name: '', email: '', phone: '', address: '' };

export default function CustomersPage() {
  const [customers, setCustomers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  const load = () => getCustomers().then((res) => setCustomers(res.data));

  useEffect(() => { load(); }, []);

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (c) => { setForm({ name: c.name, email: c.email || '', phone: c.phone || '', address: c.address || '' }); setEditingId(c.id); setShowForm(true); setError(''); };

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
            <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Address</th><th></th></tr></thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.email || '—'}</td>
                  <td>{c.phone || '—'}</td>
                  <td>{c.address || '—'}</td>
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
