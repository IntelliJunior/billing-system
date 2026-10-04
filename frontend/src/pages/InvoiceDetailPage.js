import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getInvoice, recordPayment, cancelInvoice } from '../api/api';

export default function InvoiceDetailPage() {
  const { id } = useParams();
  const [invoice, setInvoice] = useState(null);
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState('CASH');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const load = () => getInvoice(id).then((res) => setInvoice(res.data));

  useEffect(() => { load(); }, [id]);

  const submitPayment = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await recordPayment(id, { amount: Number(amount), method, referenceNote: note });
      setAmount(''); setNote('');
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to record payment');
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Cancel this invoice?')) return;
    try {
      await cancelInvoice(id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to cancel invoice');
    }
  };

  if (!invoice) return <p className="empty">Loading…</p>;

  const balanceDue = Number(invoice.totalAmount) - Number(invoice.amountPaid);

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>{invoice.invoiceNumber}</h1>
          <p className="subtitle">
            {invoice.customer?.name} · Issued {invoice.issueDate}{invoice.dueDate ? ` · Due ${invoice.dueDate}` : ''}
          </p>
        </div>
        <span className={`badge ${invoice.status}`}>{invoice.status.replace('_', ' ')}</span>
      </div>

      <div className="card">
        <strong>Line Items</strong>
        <table style={{ marginTop: 12 }}>
          <thead><tr><th>Description</th><th>Qty</th><th>Unit Price</th><th>Tax %</th><th>Line Total</th></tr></thead>
          <tbody>
            {invoice.items.map((it) => (
              <tr key={it.id}>
                <td>{it.description}</td>
                <td>{it.quantity}</td>
                <td>₹{Number(it.unitPrice).toFixed(2)}</td>
                <td>{it.taxPercent}%</td>
                <td>₹{Number(it.lineTotal).toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <div className="card">
          <div className="summary-line"><span>Subtotal</span><span>₹{Number(invoice.subtotal).toFixed(2)}</span></div>
          <div className="summary-line"><span>Tax</span><span>₹{Number(invoice.taxAmount).toFixed(2)}</span></div>
          <div className="summary-line"><span>Total</span><span>₹{Number(invoice.totalAmount).toFixed(2)}</span></div>
          <div className="summary-line"><span>Paid</span><span>₹{Number(invoice.amountPaid).toFixed(2)}</span></div>
          <div className="summary-line total"><span>Balance Due</span><span>₹{balanceDue.toFixed(2)}</span></div>

          {invoice.status !== 'CANCELLED' && Number(invoice.amountPaid) === 0 && (
            <button className="btn danger small" style={{ marginTop: 12 }} onClick={handleCancel}>Cancel Invoice</button>
          )}
        </div>

        <div className="card">
          <strong>Payments</strong>
          {invoice.payments.length === 0 ? (
            <p className="empty">No payments recorded yet.</p>
          ) : (
            <table style={{ marginTop: 12, marginBottom: 16 }}>
              <thead><tr><th>Amount</th><th>Method</th><th>Date</th></tr></thead>
              <tbody>
                {invoice.payments.map((p) => (
                  <tr key={p.id}>
                    <td>₹{Number(p.amount).toFixed(2)}</td>
                    <td>{p.method}</td>
                    <td>{new Date(p.paidAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {invoice.status !== 'PAID' && invoice.status !== 'CANCELLED' && (
            <form onSubmit={submitPayment}>
              <div className="form-row">
                <label>Amount (Balance due: ₹{balanceDue.toFixed(2)})</label>
                <input type="number" step="0.01" min="0.01" max={balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} required />
              </div>
              <div className="form-row">
                <label>Method</label>
                <select value={method} onChange={(e) => setMethod(e.target.value)}>
                  <option value="CASH">Cash</option>
                  <option value="CARD">Card</option>
                  <option value="BANK_TRANSFER">Bank Transfer</option>
                  <option value="UPI">UPI</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div className="form-row">
                <label>Note (optional)</label>
                <input value={note} onChange={(e) => setNote(e.target.value)} />
              </div>
              {error && <p className="error-text">{error}</p>}
              <button type="submit" className="btn">Record Payment</button>
            </form>
          )}
        </div>
      </div>

      <Link to="/invoices" className="btn secondary small">← Back to invoices</Link>
    </div>
  );
}
