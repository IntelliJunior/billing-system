import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getInvoices } from '../api/api';

export default function InvoicesPage() {
  const [invoices, setInvoices] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');

  const load = (status) => getInvoices(status ? { status } : {}).then((res) => setInvoices(res.data));

  useEffect(() => { load(statusFilter); }, [statusFilter]);

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>Invoices</h1>
          <p className="subtitle">All invoices you've issued</p>
        </div>
        <Link to="/invoices/new" className="btn">+ New Invoice</Link>
      </div>

      <div className="card">
        <div className="form-row" style={{ maxWidth: 220, marginBottom: 16 }}>
          <label>Filter by status</label>
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All</option>
            <option value="UNPAID">Unpaid</option>
            <option value="PARTIALLY_PAID">Partially Paid</option>
            <option value="PAID">Paid</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        {invoices.length === 0 ? (
          <p className="empty">No invoices found.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Invoice #</th><th>Customer</th><th>Issue Date</th><th>Total</th><th>Balance Due</th><th>Status</th></tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id}>
                  <td><Link to={`/invoices/${inv.id}`}>{inv.invoiceNumber}</Link></td>
                  <td>{inv.customer?.name}</td>
                  <td>{inv.issueDate}</td>
                  <td>₹{Number(inv.totalAmount).toFixed(2)}</td>
                  <td>₹{(Number(inv.totalAmount) - Number(inv.amountPaid)).toFixed(2)}</td>
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
