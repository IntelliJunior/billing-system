import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getStock, getSuppliers, recordMovement } from '../api/api';

const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });

const REASONS = {
  IN: ['Purchase', 'Return from customer', 'Other'],
  OUT: ['Used / Sold', 'Damaged', 'Expired', 'Lost', 'Other'],
  ADJUSTMENT: ['Stock count', 'Correction'],
};
const TYPE_LABEL = { IN: 'Stock In', OUT: 'Stock Out', ADJUSTMENT: 'Adjust stock' };
const emptyForm = { productId: '', type: 'IN', quantity: '', supplierId: '', unitCost: '', reason: 'Purchase', note: '' };

function statusOf(p) {
  const qty = Number(p.quantityOnHand ?? 0);
  if (qty <= 0) return 'OUT';
  if (p.reorderLevel !== null && p.reorderLevel !== undefined && qty <= Number(p.reorderLevel)) return 'LOW';
  return 'OK';
}
const BADGE = { OUT: 'CANCELLED', LOW: 'PARTIALLY_PAID', OK: 'PAID' };
const LABEL = { OUT: 'OUT OF STOCK', LOW: 'LOW', OK: 'OK' };

export default function StockPage() {
  const [items, setItems] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [lowOnly, setLowOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () =>
    getStock(false)
      .then((res) => setItems(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load stock'))
      .finally(() => setLoading(false));

  useEffect(() => {
    load();
    getSuppliers().then((res) => setSuppliers(res.data)).catch(() => {});
  }, []);

  const openForm = (product, type) => {
    setForm({ ...emptyForm, productId: product ? String(product.id) : '', type, reason: REASONS[type][0] });
    setShowForm(true);
    setError('');
    setMessage('');
    window.scrollTo(0, 0);
  };

  const changeType = (type) => setForm({ ...form, type, reason: REASONS[type][0], supplierId: '', unitCost: '' });
  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const selected = items.find((i) => String(i.id) === form.productId);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    const payload = {
      productId: Number(form.productId),
      type: form.type,
      quantity: Number(form.quantity),
      reason: form.reason,
      note: form.note,
    };
    if (form.type === 'IN') {
      if (form.supplierId) payload.supplierId = Number(form.supplierId);
      if (form.unitCost !== '') payload.unitCost = Number(form.unitCost);
    }
    try {
      const res = await recordMovement(payload);
      setMessage(`${TYPE_LABEL[form.type]} saved. ${res.data.productName} now has ${fmt(res.data.quantityAfter)} in stock.`);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save the stock change');
    } finally {
      setBusy(false);
    }
  };

  const lowCount = items.filter((p) => statusOf(p) !== 'OK').length;
  const stockValue = items.reduce((sum, p) => sum + Number(p.quantityOnHand ?? 0) * Number(p.costPrice ?? 0), 0);
  const visible = lowOnly ? items.filter((p) => statusOf(p) !== 'OK') : items;

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Stock</h1>
          <p className="subtitle">
            {items.length} tracked product(s) · {lowCount} low or out of stock · Stock value at cost: ₹{fmt(stockValue)}
          </p>
        </div>
        <button className="btn" onClick={() => openForm(null, 'IN')}>+ Record stock change</button>
      </div>

      {message && <div className="card" style={{ borderLeft: '4px solid #16a34a' }}>{message}</div>}

      {showForm && (
        <div className="card">
          <strong>{TYPE_LABEL[form.type]}</strong>
          <form onSubmit={submit} style={{ marginTop: 12 }}>
            <div className="form-grid">
              <div className="form-row">
                <label>Product *</label>
                <select value={form.productId} onChange={set('productId')} required>
                  <option value="">Select a product</option>
                  {items.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}{p.sku ? ` (${p.sku})` : ''}</option>
                  ))}
                </select>
              </div>
              <div className="form-row">
                <label>Type *</label>
                <select value={form.type} onChange={(e) => changeType(e.target.value)}>
                  <option value="IN">Stock In (received)</option>
                  <option value="OUT">Stock Out (used, sold, damaged)</option>
                  <option value="ADJUSTMENT">Adjust (set counted quantity)</option>
                </select>
              </div>
              <div className="form-row">
                <label>
                  {form.type === 'ADJUSTMENT' ? 'Counted quantity now on the shelf *' : 'Quantity *'}
                  {selected ? ` (current: ${fmt(selected.quantityOnHand)} ${selected.unit || ''})` : ''}
                </label>
                <input type="number" step="0.001" min="0" value={form.quantity} onChange={set('quantity')} required />
              </div>
              <div className="form-row">
                <label>Reason</label>
                <select value={form.reason} onChange={set('reason')}>
                  {REASONS[form.type].map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
              {form.type === 'IN' && (
                <>
                  <div className="form-row">
                    <label>Supplier (optional)</label>
                    <select value={form.supplierId} onChange={set('supplierId')}>
                      <option value="">—</option>
                      {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                  </div>
                  <div className="form-row">
                    <label>Unit cost (₹, optional; updates the cost price)</label>
                    <input type="number" step="0.01" min="0" value={form.unitCost} onChange={set('unitCost')} />
                  </div>
                </>
              )}
              <div className="form-row">
                <label>Note (optional)</label>
                <input value={form.note} onChange={set('note')} maxLength={500} />
              </div>
            </div>
            {error && <p className="error-text">{error}</p>}
            <button type="submit" className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>{' '}
            <button type="button" className="btn secondary" onClick={() => setShowForm(false)}>Cancel</button>
          </form>
        </div>
      )}

      <div className="card">
        <label>
          <input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />{' '}
          Show only low or out-of-stock items
        </label>
        {!showForm && error && <p className="error-text">{error}</p>}

        {loading ? (
          <p className="empty">Loading…</p>
        ) : items.length === 0 ? (
          <p className="empty">
            No products with stock tracking yet. Open <Link to="/products">Products</Link> and switch on
            "Track stock" for the items you want to count.
          </p>
        ) : visible.length === 0 ? (
          <p className="empty">Nothing is low on stock.</p>
        ) : (
          <table style={{ marginTop: 12 }}>
            <thead>
              <tr>
                <th>SKU</th><th>Product</th><th>On hand</th><th>Reorder level</th>
                <th>Cost</th><th>Value</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              {visible.map((p) => {
                const st = statusOf(p);
                const value = Number(p.quantityOnHand ?? 0) * Number(p.costPrice ?? 0);
                return (
                  <tr key={p.id}>
                    <td>{p.sku || '—'}</td>
                    <td>{p.name}</td>
                    <td>{fmt(p.quantityOnHand)} {p.unit || ''}</td>
                    <td>{p.reorderLevel !== null && p.reorderLevel !== undefined ? fmt(p.reorderLevel) : '—'}</td>
                    <td>{p.costPrice !== null && p.costPrice !== undefined ? `₹${Number(p.costPrice).toFixed(2)}` : '—'}</td>
                    <td>₹{fmt(value)}</td>
                    <td><span className={`badge ${BADGE[st]}`}>{LABEL[st]}</span></td>
                    <td>
                      <button className="btn secondary small" onClick={() => openForm(p, 'IN')}>+ In</button>{' '}
                      <button className="btn secondary small" onClick={() => openForm(p, 'OUT')}>− Out</button>{' '}
                      <button className="btn secondary small" onClick={() => openForm(p, 'ADJUSTMENT')}>Adjust</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
