import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getCustomers, getProducts, createInvoice } from '../api/api';

const emptyItem = { productId: '', description: '', quantity: 1, unitPrice: '', taxPercent: 0 };

export default function NewInvoicePage() {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState([]);
  const [products, setProducts] = useState([]);
  const [customerId, setCustomerId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [items, setItems] = useState([{ ...emptyItem }]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getCustomers().then((res) => setCustomers(res.data));
    getProducts().then((res) => setProducts(res.data));
  }, []);

  const updateItem = (index, field, value) => {
    const next = [...items];
    next[index] = { ...next[index], [field]: value };

    if (field === 'productId' && value) {
      const product = products.find((p) => String(p.id) === String(value));
      if (product) {
        next[index].description = product.name;
        next[index].unitPrice = product.unitPrice;
        next[index].taxPercent = product.taxPercent;
      }
    }
    setItems(next);
  };

  const addItem = () => setItems([...items, { ...emptyItem }]);
  const removeItem = (index) => setItems(items.filter((_, i) => i !== index));

  const totals = items.reduce(
    (acc, it) => {
      const qty = Number(it.quantity) || 0;
      const price = Number(it.unitPrice) || 0;
      const tax = Number(it.taxPercent) || 0;
      const lineSubtotal = qty * price;
      const lineTax = (lineSubtotal * tax) / 100;
      acc.subtotal += lineSubtotal;
      acc.tax += lineTax;
      return acc;
    },
    { subtotal: 0, tax: 0 }
  );
  const total = totals.subtotal + totals.tax;

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (!customerId) { setError('Please select a customer'); return; }
    if (items.some((it) => !it.description || !it.unitPrice)) {
      setError('Every line item needs a description and unit price');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        customerId: Number(customerId),
        dueDate: dueDate || null,
        items: items.map((it) => ({
          productId: it.productId ? Number(it.productId) : null,
          description: it.description,
          quantity: Number(it.quantity),
          unitPrice: Number(it.unitPrice),
          taxPercent: Number(it.taxPercent) || 0,
        })),
      };
      const res = await createInvoice(payload);
      navigate(`/invoices/${res.data.id}`);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create invoice');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h1>New Invoice</h1>
      <p className="subtitle">Create and send a new bill to a customer</p>

      <form onSubmit={submit}>
        <div className="card">
          <div className="form-grid">
            <div className="form-row">
              <label>Customer *</label>
              <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
                <option value="">Select a customer…</option>
                {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="form-row">
              <label>Due Date</label>
              <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            </div>
          </div>
        </div>

        <div className="card">
          <strong>Line Items</strong>
          <div style={{ marginTop: 12 }}>
            {items.map((item, idx) => (
              <div className="item-row" key={idx}>
                <div className="form-row">
                  <label>Product (optional)</label>
                  <select value={item.productId} onChange={(e) => updateItem(idx, 'productId', e.target.value)}>
                    <option value="">Custom item</option>
                    {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                </div>
                <div className="form-row">
                  <label>Qty</label>
                  <input type="number" min="1" value={item.quantity} onChange={(e) => updateItem(idx, 'quantity', e.target.value)} />
                </div>
                <div className="form-row">
                  <label>Unit Price</label>
                  <input type="number" step="0.01" min="0" value={item.unitPrice} onChange={(e) => updateItem(idx, 'unitPrice', e.target.value)} />
                </div>
                <div className="form-row">
                  <label>Tax %</label>
                  <input type="number" step="0.01" min="0" value={item.taxPercent} onChange={(e) => updateItem(idx, 'taxPercent', e.target.value)} />
                </div>
                <button type="button" className="btn danger small" onClick={() => removeItem(idx)} disabled={items.length === 1}>Remove</button>
                <div className="form-row" style={{ gridColumn: '1 / -1' }}>
                  <label>Description</label>
                  <input value={item.description} onChange={(e) => updateItem(idx, 'description', e.target.value)} placeholder="e.g. Consulting hours" required />
                </div>
              </div>
            ))}
          </div>
          <button type="button" className="btn secondary small" onClick={addItem}>+ Add line item</button>
        </div>

        <div className="card" style={{ maxWidth: 320, marginLeft: 'auto' }}>
          <div className="summary-line"><span>Subtotal</span><span>₹{totals.subtotal.toFixed(2)}</span></div>
          <div className="summary-line"><span>Tax</span><span>₹{totals.tax.toFixed(2)}</span></div>
          <div className="summary-line total"><span>Total</span><span>₹{total.toFixed(2)}</span></div>
        </div>

        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="btn" disabled={submitting}>{submitting ? 'Creating…' : 'Create Invoice'}</button>
      </form>
    </div>
  );
}
