import React, { useEffect, useState } from 'react';
import { getProducts, createProduct, updateProduct, deleteProduct, getGstRates } from '../api/api';
import { useAuth } from '../auth/AuthContext';

const emptyForm = {
  name: '', description: '', unitPrice: '', taxPercent: 0, hsnCode: '',
  sku: '', unit: 'pcs', costPrice: '', trackStock: false, reorderLevel: '', openingStock: '',
};

const toNumberOrNull = (v) => (v === '' || v === null || v === undefined ? null : Number(v));
const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });

export default function ProductsPage() {
  const { user } = useAuth();
  const hasInventory = (user.modules || []).includes('INVENTORY');
  const hasGst = (user.modules || []).includes('GST');
  const [rates, setRates] = useState([]);

  const [products, setProducts] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');

  const load = () => getProducts().then((res) => setProducts(res.data));

  useEffect(() => {
    load();
    if (hasGst) getGstRates().then((res) => setRates(res.data)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Only rates that may be used on an invoice dated today
  const today = new Date().toLocaleDateString('en-CA');
  const usableRates = rates.filter((r) => (!r.validFrom || r.validFrom <= today) && (!r.validTo || r.validTo >= today));

  const openNew = () => { setForm(emptyForm); setEditingId(null); setShowForm(true); setError(''); };
  const openEdit = (p) => {
    setForm({
      name: p.name,
      description: p.description || '',
      unitPrice: p.unitPrice,
      taxPercent: p.taxPercent,
      hsnCode: p.hsnCode || '',
      sku: p.sku || '',
      unit: p.unit || 'pcs',
      costPrice: p.costPrice ?? '',
      trackStock: !!p.trackStock,
      reorderLevel: p.reorderLevel ?? '',
      openingStock: '',
    });
    setEditingId(p.id); setShowForm(true); setError('');
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    const payload = {
      name: form.name,
      description: form.description,
      unitPrice: Number(form.unitPrice),
      taxPercent: Number(form.taxPercent),
    };
    if (hasGst) payload.hsnCode = form.hsnCode;
    if (hasInventory) {
      payload.sku = form.sku;
      payload.unit = form.unit;
      payload.costPrice = toNumberOrNull(form.costPrice);
      payload.trackStock = form.trackStock;
      payload.reorderLevel = toNumberOrNull(form.reorderLevel);
      if (!editingId && form.trackStock) payload.openingStock = toNumberOrNull(form.openingStock);
    }
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
    try {
      await deleteProduct(id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Could not delete this product');
    }
  };

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Products & Services</h1>
          <p className="subtitle">{hasInventory ? 'Your item catalog and stock settings' : 'Your billable item catalog'}</p>
        </div>
        <button className="btn" onClick={openNew}>+ New Product</button>
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
                <label>Unit Price (₹) *</label>
                <input type="number" step="0.01" min="0" value={form.unitPrice} onChange={set('unitPrice')} required />
              </div>
              {hasGst ? (
                <>
                  <div className="form-row">
                    <label>GST rate *</label>
                    <select value={form.taxPercent} onChange={set('taxPercent')} required>
                      {usableRates.map((r) => (
                        <option key={r.id} value={Number(r.ratePercent)}>{r.label || `${Number(r.ratePercent)}%`}</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-row">
                    <label>HSN / SAC code (4-8 digits)</label>
                    <input value={form.hsnCode} onChange={set('hsnCode')} pattern="[0-9]{4,8}" maxLength={8} />
                  </div>
                </>
              ) : (
                <div className="form-row">
                  <label>Tax %</label>
                  <input type="number" step="0.01" min="0" value={form.taxPercent} onChange={set('taxPercent')} />
                </div>
              )}
              <div className="form-row">
                <label>Description</label>
                <input value={form.description} onChange={set('description')} />
              </div>

              {hasInventory && (
                <>
                  <div className="form-row">
                    <label>SKU / Code</label>
                    <input value={form.sku} onChange={set('sku')} />
                  </div>
                  <div className="form-row">
                    <label>Unit (pcs, kg, litre…)</label>
                    <input value={form.unit} onChange={set('unit')} />
                  </div>
                  <div className="form-row">
                    <label>Cost price (₹)</label>
                    <input type="number" step="0.01" min="0" value={form.costPrice} onChange={set('costPrice')} />
                  </div>
                  <div className="form-row">
                    <label>Reorder level (alert when stock is at or below)</label>
                    <input type="number" step="0.001" min="0" value={form.reorderLevel} onChange={set('reorderLevel')} />
                  </div>
                  <div className="form-row">
                    <label>
                      <input
                        type="checkbox"
                        checked={form.trackStock}
                        onChange={(e) => setForm({ ...form, trackStock: e.target.checked })}
                      />{' '}
                      Track stock for this product
                    </label>
                  </div>
                  {form.trackStock && !editingId && (
                    <div className="form-row">
                      <label>Opening stock (optional)</label>
                      <input type="number" step="0.001" min="0" value={form.openingStock} onChange={set('openingStock')} />
                    </div>
                  )}
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
        {products.length === 0 ? (
          <p className="empty">No products yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                {hasInventory && <th>SKU</th>}
                <th>Description</th>
                <th>Unit Price</th>
                <th>{hasGst ? 'GST %' : 'Tax %'}</th>
                {hasGst && <th>HSN/SAC</th>}
                {hasInventory && <th>Stock</th>}
                <th></th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  {hasInventory && <td>{p.sku || '—'}</td>}
                  <td>{p.description || '—'}</td>
                  <td>₹{Number(p.unitPrice).toFixed(2)}</td>
                  <td>{p.taxPercent}%</td>
                  {hasGst && <td>{p.hsnCode || '—'}</td>}
                  {hasInventory && (
                    <td>{p.trackStock ? `${fmt(p.quantityOnHand)} ${p.unit || ''}` : 'Not tracked'}</td>
                  )}
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
