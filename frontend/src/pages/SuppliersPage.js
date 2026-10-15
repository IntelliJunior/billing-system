import React, { useEffect, useState } from 'react';
import { getSuppliers, createSupplier, updateSupplier, deleteSupplier } from '../api/api';

const emptyForm = { name: '', phone: '', email: '', address: '' };

export default function SuppliersPage() {
  const [suppliers, setSuppliers] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  const load = () => getSuppliers().then((res) => setSuppliers(res.data));

  useEffect(() => { load(); }, []);

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (s) => {
    setForm({ name: s.name, phone: s.phone || '', email: s.email || '', address: s.address || '' });
    setEditingId(s.id); setShowForm(true); setError('');
  };
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      if (editingId) await updateSupplier(editingId, form);
      else await createSupplier(form);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong');
    }
  };

  const remove = async (id) => {
    if (!window.confirm('Delete this supplier? Past stock history keeps the supplier name.')) return;
    try {
      await deleteSupplier(id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Could not delete this supplier');
    }
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Suppliers</h1>
          <p className="subtitle">Who you buy stock from</p>
        </div>
        <button className="btn" onClick={openNew}>+ New Supplier</button>
      </div>

      {showForm && (
        <div className="card">
          <form onSubmit={submit}>
            <div className="form-grid">
              <div className="form-row">
                <label>Name *</label>
                <input value={form.name} onChange={set('name')} required />
              </div>
              <div className="form-row">
                <label>Phone</label>
                <input value={form.phone} onChange={set('phone')} />
              </div>
              <div className="form-row">
                <label>Email</label>
                <input type="email" value={form.email} onChange={set('email')} />
              </div>
              <div className="form-row">
                <label>Address</label>
                <input value={form.address} onChange={set('address')} />
              </div>
            </div>
            {error && <p className="error-text">{error}</p>}
            <button type="submit" className="btn">{editingId ? 'Update' : 'Create'}</button>{' '}
            <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </form>
        </div>
      )}

      <div className="card">
        {suppliers.length === 0 ? (
          <p className="empty">No suppliers yet.</p>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Phone</th><th>Email</th><th>Address</th><th></th></tr></thead>
            <tbody>
              {suppliers.map((s) => (
                <tr key={s.id}>
                  <td>{s.name}</td>
                  <td>{s.phone || '—'}</td>
                  <td>{s.email || '—'}</td>
                  <td>{s.address || '—'}</td>
                  <td>
                    <button className="btn secondary small" onClick={() => openEdit(s)}>Edit</button>{' '}
                    <button className="btn danger small" onClick={() => remove(s.id)}>Delete</button>
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
