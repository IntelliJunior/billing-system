import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getInvoices, getCustomers } from '../api/api';

export default function DashboardPage() {
  const [invoices, setInvoices] = useState([]);
  const [customerCount, setCustomerCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getInvoices(), getCustomers()])
      .then(([invRes, custRes]) => {
        setInvoices(invRes.data);
        setCustomerCount(custRes.data.length);
      })
      .finally(() => setLoading(false));
  }, []);

  const totalBilled = invoices.reduce((sum, i) => sum + Number(i.totalAmount), 0);
  const totalCollected = invoices.reduce((sum, i) => sum + Number(i.amountPaid), 0);
  const totalOutstanding = totalBilled - totalCollected;
  const unpaidCount = invoices.filter((i) => i.status !== 'PAID' && i.status !== 'CANCELLED').length;

  if (loading) return <p className="empty">Loading dashboard…</p>;

  return (
    <div>
      <h1>Dashboard</h1>
      <p className="subtitle">Overview of your billing activity</p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 24 }}>
        <StatCard label="Customers" value={customerCount} />
        <StatCard label="Total Billed" value={`₹${totalBilled.toFixed(2)}`} />
        <StatCard label="Collected" value={`₹${totalCollected.toFixed(2)}`} />
        <StatCard label="Outstanding" value={`₹${totalOutstanding.toFixed(2)}`} highlight={totalOutstanding > 0} />
      </div>

      <div className="card">
        <div className="toolbar">
          <strong>Recent Invoices</strong>
          <Link to="/invoices" className="btn secondary small">View all</Link>
        </div>
        {invoices.length === 0 ? (
          <p className="empty">No invoices yet. {unpaidCount === 0 ? '' : ''}
            <Link to="/invoices/new">Create your first invoice</Link>.
          </p>
        ) : (
          <table>
            <thead>
              <tr><th>Invoice #</th><th>Customer</th><th>Total</th><th>Status</th></tr>
            </thead>
            <tbody>
              {invoices.slice(0, 5).map((inv) => (
                <tr key={inv.id}>
                  <td><Link to={`/invoices/${inv.id}`}>{inv.invoiceNumber}</Link></td>
                  <td>{inv.customer?.name}</td>
                  <td>₹{Number(inv.totalAmount).toFixed(2)}</td>
                  <td><span className={`badge ${inv.status}`}>{inv.status.replace('_', ' ')}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, highlight }) {
  return (
    <div className="card" style={{ marginBottom: 0 }}>
      <div style={{ fontSize: 12, color: '#64748b', textTransform: 'uppercase', marginBottom: 6 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 700, color: highlight ? '#dc2626' : '#1e293b' }}>{value}</div>
    </div>
  );
}
