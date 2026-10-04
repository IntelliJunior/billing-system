import React, { useEffect, useState } from 'react';
import { getProducts, createProduct, updateProduct, deleteProduct } from '../api/api';

const emptyForm = { name: '', description: '', unitPrice: '', taxPercent: 0 };

export default function ProductsPage() {
  const [products, setProducts] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  const load = () => getProducts().then((res) => setProducts(res.data));

  useEffect(() => { load(); }, []);

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (p) => {
    setForm({ name: p.name, description: p.description || '', unitPrice: p.unitPrice, taxPercent: p.taxPercent });
    setEditingId(p.id); setShowForm(true); setError('');
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const payload = { ...form, unitPrice: Number(form.unitPrice), taxPercent: Number(form.taxPercent) };
    try {
      if (editingId) await updateProduct(editingId, payload);
      else await createProduct(payload);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong');
    }
  };

  const remove = async (id) => {
    if (!window.confirm('Delete this product?')) return;
    await deleteProduct(id);
    load();
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Products & Services</h1>
          <p className="subtitle">Your billable item catalog</p>
        </div>
        <button className="btn" onClick={openNew}>+ New Product</button>
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
                <label>Unit Price (₹) *</label>
                <input type="number" step="0.01" min="0" value={form.unitPrice} onChange={(e) => setForm({ ...form, unitPrice: e.target.value })} required />
              </div>
              <div className="form-row">
                <label>Tax %</label>
                <input type="number" step="0.01" min="0" value={form.taxPercent} onChange={(e) => setForm({ ...form, taxPercent: e.target.value })} />
              </div>
              <div className="form-row">
                <label>Description</label>
                <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
            </div>
            {error && <p className="error-text">{error}</p>}
            <button type="submit" className="btn">{editingId ? 'Update' : 'Create'}</button>{' '}
            <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </form>
        </div>
      )}

      <div className="card">
        {products.length === 0 ? (
          <p className="empty">No products yet.</p>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Description</th><th>Unit Price</th><th>Tax %</th><th></th></tr></thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.description || '—'}</td>
                  <td>₹{Number(p.unitPrice).toFixed(2)}</td>
                  <td>{p.taxPercent}%</td>
                  <td>
                    <button className="btn secondary small" onClick={() => openEdit(p)}>Edit</button>{' '}
                    <button className="btn danger small" onClick={() => remove(p.id)}>Delete</button>
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
