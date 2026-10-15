import React, { useEffect, useState } from 'react';
import { getMovements, getStock } from '../api/api';

const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });
const BADGE = { IN: 'PAID', OUT: 'CANCELLED', ADJUSTMENT: 'PARTIALLY_PAID' };
const LABEL = { IN: 'IN', OUT: 'OUT', ADJUSTMENT: 'ADJUSTED' };

export default function MovementsPage() {
  const [rows, setRows] = useState([]);
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getStock(false).then((res) => setProducts(res.data)).catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    getMovements(productId || null)
      .then((res) => setRows(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load the history'))
      .finally(() => setLoading(false));
  }, [productId]);

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Stock History</h1>
          <p className="subtitle">Every stock change, newest first (last 300 entries)</p>
        </div>
      </div>

      <div className="card">
        <div className="form-row" style={{ maxWidth: 320 }}>
          <label>Product</label>
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            <option value="">All products</option>
            {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </div>
        {error && <p className="error-text">{error}</p>}

        {loading ? (
          <p className="empty">Loading…</p>
        ) : rows.length === 0 ? (
          <p className="empty">No stock changes recorded yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Date</th><th>Product</th><th>Type</th><th>Change</th><th>Balance</th>
                <th>Reason / Note</th><th>Supplier</th><th>Reference</th><th>By</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((m) => {
                const change = Number(m.quantityChange);
                return (
                  <tr key={m.id}>
                    <td>{new Date(m.createdAt).toLocaleString()}</td>
                    <td>{m.productName}</td>
                    <td><span className={`badge ${BADGE[m.type]}`}>{LABEL[m.type]}</span></td>
                    <td style={{ color: change < 0 ? '#b91c1c' : '#15803d', fontWeight: 600 }}>
                      {change > 0 ? '+' : ''}{fmt(change)}
                    </td>
                    <td>{fmt(m.quantityAfter)}</td>
                    <td>{[m.reason, m.note].filter(Boolean).join(' · ') || '—'}</td>
                    <td>{m.supplierName || '—'}</td>
                    <td>{m.reference || '—'}</td>
                    <td>{m.createdBy || '—'}</td>
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
