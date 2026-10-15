import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getCustomers, getProducts, createInvoice, getGstRates, getGstStates } from '../api/api';
import { useAuth } from '../auth/AuthContext';

const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });

const emptyItem = { productId: '', description: '', quantity: 1, unitPrice: '', taxPercent: 0, hsnCode: '' };

export default function NewInvoicePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const hasInventory = (user.modules || []).includes('INVENTORY');
  const hasGst = (user.modules || []).includes('GST');
  const [customers, setCustomers] = useState([]);
  const [products, setProducts] = useState([]);
  const [customerId, setCustomerId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [placeOfSupply, setPlaceOfSupply] = useState('');
  const [rates, setRates] = useState([]);
  const [states, setStates] = useState([]);
  const [items, setItems] = useState([{ ...emptyItem }]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getCustomers().then((res) => setCustomers(res.data));
    getProducts().then((res) => setProducts(res.data));
    if (hasGst) {
      getGstRates().then((res) => setRates(res.data)).catch(() => {});
      getGstStates().then((res) => setStates(res.data)).catch(() => {});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
        next[index].hsnCode = product.hsnCode || '';
      }
    }
    setItems(next);
  };

  const addItem = () => setItems([...items, { ...emptyItem }]);
  const removeItem = (index) => setItems(items.filter((_, i) => i !== index));

  // GST preview (the server does the final calculation)
  const today = new Date().toLocaleDateString('en-CA');
  const usableRates = rates.filter((r) => (!r.validFrom || r.validFrom <= today) && (!r.validTo || r.validTo >= today));
  const tenantState = user.tenantStateCode;
  const customer = customers.find((c) => String(c.id) === String(customerId));
  const supplyState = placeOfSupply || (customer && customer.stateCode) || tenantState;
  const interState = hasGst && supplyState !== tenantState;
  const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100;

  const totals = items.reduce(
    (acc, it) => {
      const qty = Number(it.quantity) || 0;
      const price = Number(it.unitPrice) || 0;
      const tax = Number(it.taxPercent) || 0;
      if (hasGst) {
        const taxable = round2(qty * price);
        if (interState) {
          acc.igst += round2((taxable * tax) / 100);
        } else {
          acc.cgst += round2((taxable * tax) / 200);
          acc.sgst += round2((taxable * tax) / 200);
        }
        acc.subtotal += taxable;
      } else {
        const lineSubtotal = qty * price;
        acc.subtotal += lineSubtotal;
        acc.tax += (lineSubtotal * tax) / 100;
      }
      return acc;
    },
    { subtotal: 0, tax: 0, cgst: 0, sgst: 0, igst: 0 }
  );
  if (hasGst) totals.tax = totals.cgst + totals.sgst + totals.igst;
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
        placeOfSupplyStateCode: hasGst && placeOfSupply ? placeOfSupply : null,
        items: items.map((it) => ({
          productId: it.productId ? Number(it.productId) : null,
          description: it.description,
          quantity: Number(it.quantity),
          unitPrice: Number(it.unitPrice),
          taxPercent: Number(it.taxPercent) || 0,
          hsnCode: it.hsnCode || null,
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
            {hasGst && (
              <div className="form-row">
                <label>Place of supply</label>
                <select value={placeOfSupply} onChange={(e) => setPlaceOfSupply(e.target.value)}>
                  <option value="">Customer's state (default)</option>
                  {states.map((st) => <option key={st.code} value={st.code}>{st.name} ({st.code})</option>)}
                </select>
              </div>
            )}
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
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}{hasInventory && p.trackStock ? ` — ${fmt(p.quantityOnHand)} ${p.unit || ''} in stock` : ''}
                      </option>
                    ))}
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
                {hasGst ? (
                  <div className="form-row">
                    <label>GST %</label>
                    <select value={item.taxPercent} onChange={(e) => updateItem(idx, 'taxPercent', e.target.value)}>
                      {usableRates.map((r) => (
                        <option key={r.id} value={Number(r.ratePercent)}>{Number(r.ratePercent)}%</option>
                      ))}
                    </select>
                  </div>
                ) : (
                  <div className="form-row">
                    <label>Tax %</label>
                    <input type="number" step="0.01" min="0" value={item.taxPercent} onChange={(e) => updateItem(idx, 'taxPercent', e.target.value)} />
                  </div>
                )}
                <button type="button" className="btn danger small" onClick={() => removeItem(idx)} disabled={items.length === 1}>Remove</button>
                {hasInventory && (() => {
                  const chosen = products.find((p) => String(p.id) === String(item.productId));
                  const need = Number(item.quantity) || 0;
                  return chosen && chosen.trackStock && need > Number(chosen.quantityOnHand ?? 0) ? (
                    <p className="error-text" style={{ gridColumn: '1 / -1', margin: 0 }}>
                      Only {fmt(chosen.quantityOnHand)} {chosen.unit || ''} of {chosen.name} in stock.
                    </p>
                  ) : null;
                })()}
                <div className="form-row" style={{ gridColumn: '1 / -1' }}>
                  <label>Description</label>
                  <input value={item.description} onChange={(e) => updateItem(idx, 'description', e.target.value)} placeholder="e.g. Consulting hours" required />
                </div>
                {hasGst && (
                  <div className="form-row" style={{ gridColumn: '1 / -1', maxWidth: 240 }}>
                    <label>HSN / SAC code</label>
                    <input value={item.hsnCode} onChange={(e) => updateItem(idx, 'hsnCode', e.target.value)} pattern="[0-9]{4,8}" maxLength={8} />
                  </div>
                )}
              </div>
            ))}
          </div>
          <button type="button" className="btn secondary small" onClick={addItem}>+ Add line item</button>
        </div>

        <div className="card" style={{ maxWidth: 320, marginLeft: 'auto' }}>
          {hasGst ? (
            <>
              <div className="summary-line"><span>Taxable value</span><span>₹{totals.subtotal.toFixed(2)}</span></div>
              {interState ? (
                <div className="summary-line"><span>IGST</span><span>₹{totals.igst.toFixed(2)}</span></div>
              ) : (
                <>
                  <div className="summary-line"><span>CGST</span><span>₹{totals.cgst.toFixed(2)}</span></div>
                  <div className="summary-line"><span>SGST</span><span>₹{totals.sgst.toFixed(2)}</span></div>
                </>
              )}
              <p className="subtitle" style={{ margin: '4px 0' }}>
                {interState ? 'Inter-state supply: IGST' : 'Intra-state supply: CGST + SGST'}
              </p>
            </>
          ) : (
            <>
              <div className="summary-line"><span>Subtotal</span><span>₹{totals.subtotal.toFixed(2)}</span></div>
              <div className="summary-line"><span>Tax</span><span>₹{totals.tax.toFixed(2)}</span></div>
            </>
          )}
          <div className="summary-line total"><span>Total</span><span>₹{total.toFixed(2)}</span></div>
        </div>

        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="btn" disabled={submitting}>{submitting ? 'Creating…' : 'Create Invoice'}</button>
      </form>
    </div>
  );
}
