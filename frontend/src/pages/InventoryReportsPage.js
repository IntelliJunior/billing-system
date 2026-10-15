import React, { useEffect, useState } from 'react';
import { downloadCsv, getMovementReport, getStock } from '../api/api';

const fmt = (n) => Number(n ?? 0).toLocaleString('en-IN', { maximumFractionDigits: 3 });
const BADGE = { IN: 'PAID', OUT: 'CANCELLED', ADJUSTMENT: 'PARTIALLY_PAID' };
const SHOW_ROWS = 500;

const toInput = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export default function InventoryReportsPage() {
  const today = new Date();
  const monthAgo = new Date();
  monthAgo.setDate(today.getDate() - 30);

  const [products, setProducts] = useState([]);
  const [filters, setFilters] = useState({ from: toInput(monthAgo), to: toInput(today), productId: '', type: '' });
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getStock(false).then((res) => setProducts(res.data)).catch(() => {});
  }, []);

  const set = (field) => (e) => setFilters({ ...filters, [field]: e.target.value });

  const params = () => {
    const p = { from: filters.from, to: filters.to };
    if (filters.productId) p.productId = filters.productId;
    if (filters.type) p.type = filters.type;
    return p;
  };

  const run = async (e) => {
    if (e) e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await getMovementReport(params());
      setReport(res.data);
    } catch (err) {
      setReport(null);
      setError(err.response?.data?.message || 'Could not build the report');
    } finally {
      setLoading(false);
    }
  };

  const download = async (path, name) => {
    setError('');
    try {
      await downloadCsv(path, path.includes('movements') ? params() : undefined, name);
    } catch (err) {
      setError('Could not download the file');
    }
  };

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Inventory Reports</h1>
          <p className="subtitle">Download stock figures or review what moved in a period</p>
        </div>
      </div>

      <div className="card">
        <strong>Stock summary</strong>
        <p className="subtitle">Current quantity, value and status of every tracked product.</p>
        <button className="btn secondary" onClick={() => download('/inventory/reports/stock.csv', 'stock-summary.csv')}>
          ⬇ Download CSV
        </button>
      </div>

      <div className="card">
        <strong>Stock movement report</strong>
        <form onSubmit={run} style={{ marginTop: 12 }}>
          <div className="form-grid">
            <div className="form-row">
              <label>From</label>
              <input type="date" value={filters.from} onChange={set('from')} required />
            </div>
            <div className="form-row">
              <label>To</label>
              <input type="date" value={filters.to} onChange={set('to')} required />
            </div>
            <div className="form-row">
              <label>Product</label>
              <select value={filters.productId} onChange={set('productId')}>
                <option value="">All products</option>
                {products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
            <div className="form-row">
              <label>Type</label>
              <select value={filters.type} onChange={set('type')}>
                <option value="">All types</option>
                <option value="IN">Stock In</option>
                <option value="OUT">Stock Out</option>
                <option value="ADJUSTMENT">Adjustments</option>
              </select>
            </div>
          </div>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="btn" disabled={loading}>{loading ? 'Running…' : 'Run report'}</button>{' '}
          <button
            type="button"
            className="btn secondary"
            onClick={() => download('/inventory/reports/movements.csv', 'stock-movements.csv')}
          >
            ⬇ Download CSV
          </button>
        </form>
      </div>

      {report && (
        <>
          <div className="card">
            <strong>Totals by product</strong>
            <p className="subtitle">
              {report.from} to {report.to} · {report.rowCount} stock change(s)
              {report.truncated ? ' (the CSV and the table below show only the newest 5,000)' : ''}
            </p>
            {report.byProduct.length === 0 ? (
              <p className="empty">No stock changes in this period.</p>
            ) : (
              <table>
                <thead><tr><th>Product</th><th>Received / added</th><th>Removed / sold</th><th>Net change</th></tr></thead>
                <tbody>
                  {report.byProduct.map((s) => {
                    const net = Number(s.netChange);
                    return (
                      <tr key={s.productId}>
                        <td>{s.productName}</td>
                        <td>{fmt(s.quantityIn)}</td>
                        <td>{fmt(s.quantityOut)}</td>
                        <td style={{ color: net < 0 ? '#b91c1c' : '#15803d', fontWeight: 600 }}>
                          {net > 0 ? '+' : ''}{fmt(net)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>

          {report.rows.length > 0 && (
            <div className="card">
              <strong>Details</strong>
              {report.rows.length > SHOW_ROWS && (
                <p className="subtitle">Showing the newest {SHOW_ROWS}. Download the CSV for all rows.</p>
              )}
              <table style={{ marginTop: 12 }}>
                <thead>
                  <tr><th>Date</th><th>Product</th><th>Type</th><th>Change</th><th>Balance</th><th>Reason / Note</th><th>Reference</th></tr>
                </thead>
                <tbody>
                  {report.rows.slice(0, SHOW_ROWS).map((m) => {
                    const change = Number(m.quantityChange);
                    return (
                      <tr key={m.id}>
                        <td>{new Date(m.createdAt).toLocaleString()}</td>
                        <td>{m.productName}</td>
                        <td><span className={`badge ${BADGE[m.type]}`}>{m.type}</span></td>
                        <td style={{ color: change < 0 ? '#b91c1c' : '#15803d', fontWeight: 600 }}>
                          {change > 0 ? '+' : ''}{fmt(change)}
                        </td>
                        <td>{fmt(m.quantityAfter)}</td>
                        <td>{[m.reason, m.note].filter(Boolean).join(' · ') || '—'}</td>
                        <td>{m.reference || '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}
