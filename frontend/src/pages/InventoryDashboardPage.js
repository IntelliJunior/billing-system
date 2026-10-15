import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getInventorySummary } from '../api/api';

const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });
const money = (n) => `₹${Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
const BADGE = { IN: 'PAID', OUT: 'CANCELLED', ADJUSTMENT: 'PARTIALLY_PAID' };

function Tile({ label, value, hint, color }) {
  return (
    <div className="card" style={{ marginBottom: 0 }}>
      <div className="subtitle">{label}</div>
      <div style={{ fontSize: 28, fontWeight: 700, color: color || 'inherit' }}>{value}</div>
      {hint && <div className="subtitle">{hint}</div>}
    </div>
  );
}

export default function InventoryDashboardPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getInventorySummary()
      .then((res) => setData(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Could not load the overview'));
  }, []);

  if (error) return <p className="error-text">{error}</p>;
  if (!data) return <p className="empty">Loading…</p>;

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Inventory Overview</h1>
          <p className="subtitle">Where your stock stands today</p>
        </div>
        <Link to="/inventory/stock" className="btn">Manage stock</Link>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 16, marginBottom: 20 }}>
        <Tile label="Tracked products" value={data.trackedProducts} />
        <Tile label="Low stock" value={data.lowStockCount} color={data.lowStockCount ? '#b45309' : undefined} hint="at or below reorder level" />
        <Tile label="Out of stock" value={data.outOfStockCount} color={data.outOfStockCount ? '#b91c1c' : undefined} />
        <Tile label="Stock value (cost)" value={money(data.stockValueAtCost)} hint={`Selling value ${money(data.stockValueAtPrice)}`} />
      </div>

      {data.trackedProducts === 0 && (
        <div className="card">
          <p className="empty">
            Nothing is tracked yet. Open <Link to="/products">Products</Link> and switch on "Track stock"
            for the items you want to count.
          </p>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <div className="card">
          <strong>Needs attention</strong>
          {data.attentionItems.length === 0 ? (
            <p className="empty">Everything is above its reorder level.</p>
          ) : (
            <table style={{ marginTop: 12 }}>
              <thead><tr><th>Product</th><th>On hand</th><th>Reorder at</th><th></th></tr></thead>
              <tbody>
                {data.attentionItems.map((p) => {
                  const out = Number(p.quantityOnHand ?? 0) <= 0;
                  return (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>{fmt(p.quantityOnHand)} {p.unit || ''}</td>
                      <td>{p.reorderLevel !== null && p.reorderLevel !== undefined ? fmt(p.reorderLevel) : '—'}</td>
                      <td><span className={`badge ${out ? 'CANCELLED' : 'PARTIALLY_PAID'}`}>{out ? 'OUT' : 'LOW'}</span></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <strong>Recent stock changes</strong>
          {data.recentMovements.length === 0 ? (
            <p className="empty">No stock changes yet.</p>
          ) : (
            <table style={{ marginTop: 12 }}>
              <thead><tr><th>When</th><th>Product</th><th>Type</th><th>Change</th></tr></thead>
              <tbody>
                {data.recentMovements.map((m) => {
                  const change = Number(m.quantityChange);
                  return (
                    <tr key={m.id}>
                      <td>{new Date(m.createdAt).toLocaleString()}</td>
                      <td>{m.productName}</td>
                      <td><span className={`badge ${BADGE[m.type]}`}>{m.type}</span></td>
                      <td style={{ color: change < 0 ? '#b91c1c' : '#15803d', fontWeight: 600 }}>
                        {change > 0 ? '+' : ''}{fmt(change)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          <p style={{ marginTop: 12 }}><Link to="/inventory/movements">See full history →</Link></p>
        </div>
      </div>
    </div>
  );
}
