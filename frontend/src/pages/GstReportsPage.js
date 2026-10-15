import React, { useState } from 'react';
import { downloadFile, getGstReport } from '../api/api';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const pad = (n) => String(n).padStart(2, '0');
const iso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const lastDay = (y, m) => new Date(y, m, 0).getDate();          // m is 1 to 12
const fyLabel = (s) => `${s}-${String(s + 1).slice(2)}`;
const inr = (n) => Number(n ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = (r) => `${Number(r)}%`;
const day = (d) => (d ? new Date(d + 'T00:00:00').toLocaleDateString('en-IN') : '');

const now = new Date();
const thisMonth = now.getMonth() + 1;
const currentFyStart = thisMonth >= 4 ? now.getFullYear() : now.getFullYear() - 1;
const currentQuarter = thisMonth >= 4 && thisMonth <= 6 ? 1 : thisMonth >= 7 && thisMonth <= 9 ? 2 : thisMonth >= 10 ? 3 : 4;

const QUARTERS = {
  1: { name: 'Apr-Jun', from: (s) => [s, 4, 1], to: (s) => [s, 6, 30] },
  2: { name: 'Jul-Sep', from: (s) => [s, 7, 1], to: (s) => [s, 9, 30] },
  3: { name: 'Oct-Dec', from: (s) => [s, 10, 1], to: (s) => [s, 12, 31] },
  4: { name: 'Jan-Mar', from: (s) => [s + 1, 1, 1], to: (s) => [s + 1, 3, 31] },
};

// Turns the picker choices into start date, end date and a readable name
function computeRange(f) {
  if (f.type === 'MONTH') {
    if (!f.month) return null;
    const [y, m] = f.month.split('-').map(Number);
    return { from: iso(y, m, 1), to: iso(y, m, lastDay(y, m)), label: `${MONTHS[m - 1]} ${y}` };
  }
  if (f.type === 'QUARTER') {
    const s = Number(f.fy);
    const q = QUARTERS[f.quarter];
    const [fy, fm, fd] = q.from(s);
    const [ty, tm, td] = q.to(s);
    return { from: iso(fy, fm, fd), to: iso(ty, tm, td), label: `Q${f.quarter} FY ${fyLabel(s)} (${q.name})` };
  }
  if (f.type === 'FY') {
    const s = Number(f.fy);
    return { from: iso(s, 4, 1), to: iso(s + 1, 3, 31), label: `FY ${fyLabel(s)}` };
  }
  if (f.type === 'CY') {
    const y = Number(f.cy);
    return { from: iso(y, 1, 1), to: iso(y, 12, 31), label: `Calendar year ${y}` };
  }
  if (!f.from || !f.to) return null;
  return { from: f.from, to: f.to, label: `${day(f.from)} to ${day(f.to)}` };
}

function Tile({ label, value }) {
  return (
    <div className="card" style={{ marginBottom: 0 }}>
      <div className="subtitle">{label}</div>
      <div style={{ fontSize: 20, fontWeight: 700 }}>{value}</div>
    </div>
  );
}

// columns: [{ label, key, num, render }]
function Table({ columns, rows, empty }) {
  if (!rows || rows.length === 0) return <p className="empty">{empty || 'Nothing to show.'}</p>;
  return (
    <div style={{ overflowX: 'auto' }}>
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.label} style={c.num ? { textAlign: 'right' } : undefined}>{c.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {columns.map((c) => (
                <td key={c.label} style={c.num ? { textAlign: 'right' } : undefined}>
                  {c.render ? c.render(r) : r[c.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const money = (key) => ({ key, num: true, render: (r) => inr(r[key]) });
const amountCols = [
  { label: 'Taxable value', ...money('taxable') },
  { label: 'CGST', ...money('cgst') },
  { label: 'SGST', ...money('sgst') },
  { label: 'IGST', ...money('igst') },
];

export default function GstReportsPage() {
  const [filters, setFilters] = useState({
    type: 'MONTH',
    month: `${now.getFullYear()}-${pad(thisMonth)}`,
    fy: currentFyStart,
    quarter: currentQuarter,
    cy: now.getFullYear(),
    from: '',
    to: '',
  });
  const [report, setReport] = useState(null);
  const [tab, setTab] = useState('summary');
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState('');
  const [error, setError] = useState('');

  const range = computeRange(filters);
  const set = (field) => (e) => setFilters({ ...filters, [field]: e.target.value });
  const fyOptions = Array.from({ length: 6 }, (_, i) => currentFyStart - i);
  const cyOptions = Array.from({ length: 6 }, (_, i) => now.getFullYear() - i);

  const run = async (e) => {
    if (e) e.preventDefault();
    if (!range) {
      setError('Choose the period first');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const res = await getGstReport(range);
      setReport(res.data);
      setTab('summary');
    } catch (err) {
      setReport(null);
      setError(err.response?.data?.message || 'Could not build the report');
    } finally {
      setLoading(false);
    }
  };

  const download = async (kind) => {
    if (!range) return;
    setError('');
    setDownloading(kind);
    try {
      await downloadFile(`/gst/report.${kind}`, range, `GST-Report-${range.from}-to-${range.to}.${kind}`);
    } catch (err) {
      setError('Could not download the file. Please try again.');
    } finally {
      setDownloading('');
    }
  };

  const tabs = report
    ? [
        ['summary', 'Summary'],
        ['register', 'Sales register'],
        ['b2b', 'B2B'],
        ['b2c', 'B2C'],
        ['hsn', 'HSN summary'],
        ['docs', 'Documents'],
        ['excluded', `Not included (${report.notIncluded.length})`],
      ]
    : [];

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>GST Reports</h1>
          <p className="subtitle">Pick a period, view the figures, and download them as Excel or PDF</p>
        </div>
      </div>

      <div className="card">
        <form onSubmit={run}>
          <div className="form-grid">
            <div className="form-row">
              <label>Period</label>
              <select value={filters.type} onChange={set('type')}>
                <option value="MONTH">Month</option>
                <option value="QUARTER">Quarter (financial year)</option>
                <option value="FY">Financial year (April to March)</option>
                <option value="CY">Calendar year</option>
                <option value="CUSTOM">Custom dates</option>
              </select>
            </div>

            {filters.type === 'MONTH' && (
              <div className="form-row">
                <label>Month</label>
                <input type="month" value={filters.month} onChange={set('month')} required />
              </div>
            )}
            {(filters.type === 'QUARTER' || filters.type === 'FY') && (
              <div className="form-row">
                <label>Financial year</label>
                <select value={filters.fy} onChange={set('fy')}>
                  {fyOptions.map((s) => <option key={s} value={s}>{fyLabel(s)}</option>)}
                </select>
              </div>
            )}
            {filters.type === 'QUARTER' && (
              <div className="form-row">
                <label>Quarter</label>
                <select value={filters.quarter} onChange={set('quarter')}>
                  {[1, 2, 3, 4].map((q) => <option key={q} value={q}>Q{q} ({QUARTERS[q].name})</option>)}
                </select>
              </div>
            )}
            {filters.type === 'CY' && (
              <div className="form-row">
                <label>Year</label>
                <select value={filters.cy} onChange={set('cy')}>
                  {cyOptions.map((y) => <option key={y} value={y}>{y}</option>)}
                </select>
              </div>
            )}
            {filters.type === 'CUSTOM' && (
              <>
                <div className="form-row">
                  <label>From</label>
                  <input type="date" value={filters.from} onChange={set('from')} required />
                </div>
                <div className="form-row">
                  <label>To</label>
                  <input type="date" value={filters.to} onChange={set('to')} required />
                </div>
              </>
            )}
          </div>

          {range && <p className="subtitle">{range.label}: {day(range.from)} to {day(range.to)}</p>}
          {error && <p className="error-text">{error}</p>}

          <button type="submit" className="btn" disabled={loading}>{loading ? 'Building…' : 'Show report'}</button>{' '}
          <button type="button" className="btn secondary" disabled={!range || downloading !== ''} onClick={() => download('xlsx')}>
            {downloading === 'xlsx' ? 'Preparing…' : '⬇ Excel'}
          </button>{' '}
          <button type="button" className="btn secondary" disabled={!range || downloading !== ''} onClick={() => download('pdf')}>
            {downloading === 'pdf' ? 'Preparing…' : '⬇ PDF'}
          </button>
        </form>
      </div>

      {report && (
        <>
          {report.notIncluded.length > 0 && (
            <div className="card" style={{ borderLeft: '4px solid #d97706' }}>
              {report.notIncluded.length} invoice(s) are not in these figures because they are cancelled or were made
              before GST was switched on. See the "Not included" tab.
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12, marginBottom: 20 }}>
            <Tile label="Invoices" value={report.totals.count} />
            <Tile label="Taxable value" value={`₹${inr(report.totals.taxable)}`} />
            <Tile label="CGST" value={`₹${inr(report.totals.cgst)}`} />
            <Tile label="SGST" value={`₹${inr(report.totals.sgst)}`} />
            <Tile label="IGST" value={`₹${inr(report.totals.igst)}`} />
            <Tile label="Total tax" value={`₹${inr(report.totals.tax)}`} />
            <Tile label="Invoice value" value={`₹${inr(report.totals.value)}`} />
          </div>

          <div style={{ marginBottom: 12 }}>
            {tabs.map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={`btn small ${tab === key ? '' : 'secondary'}`}
                style={{ marginRight: 6, marginBottom: 6 }}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </div>

          {tab === 'summary' && (
            <>
              <div className="card">
                <strong>By customer type and supply</strong>
                <Table
                  columns={[
                    { label: 'Type', render: (r) => r.label },
                    { label: 'Invoices', num: true, render: (r) => r.amounts.count },
                    { label: 'Taxable value', num: true, render: (r) => inr(r.amounts.taxable) },
                    { label: 'CGST', num: true, render: (r) => inr(r.amounts.cgst) },
                    { label: 'SGST', num: true, render: (r) => inr(r.amounts.sgst) },
                    { label: 'IGST', num: true, render: (r) => inr(r.amounts.igst) },
                    { label: 'Invoice value', num: true, render: (r) => inr(r.amounts.value) },
                  ]}
                  rows={report.segments}
                />
              </div>
              <div className="card">
                <strong>By GST rate</strong>
                <Table
                  columns={[
                    { label: 'Rate', render: (r) => pct(r.rate) },
                    { label: 'Lines', num: true, render: (r) => r.amounts.count },
                    { label: 'Taxable value', num: true, render: (r) => inr(r.amounts.taxable) },
                    { label: 'CGST', num: true, render: (r) => inr(r.amounts.cgst) },
                    { label: 'SGST', num: true, render: (r) => inr(r.amounts.sgst) },
                    { label: 'IGST', num: true, render: (r) => inr(r.amounts.igst) },
                    { label: 'Total tax', num: true, render: (r) => inr(r.amounts.tax) },
                  ]}
                  rows={report.byRate}
                  empty="No GST invoices in this period."
                />
              </div>
              <div className="card">
                <strong>Outward supplies (GSTR-3B table 3.1 style)</strong>
                <Table
                  columns={[
                    { label: 'Nature of supply', key: 'name' },
                    { label: 'Taxable value', ...money('taxable') },
                    { label: 'IGST', ...money('igst') },
                    { label: 'CGST', ...money('cgst') },
                    { label: 'SGST', ...money('sgst') },
                  ]}
                  rows={[
                    { name: '(a) Outward taxable supplies (rate above 0%)', ...report.outwardTaxable },
                    { name: '(c) Other outward supplies (0% rate: nil rated or exempt)', taxable: report.nilExemptTaxable, igst: null, cgst: null, sgst: null },
                  ]}
                />
                <p className="subtitle">Input tax credit is not included because purchases are not recorded.</p>
              </div>
              <div className="card">
                <strong>Inter-state supplies to unregistered persons, state by state</strong>
                <Table
                  columns={[
                    { label: 'Place of supply', key: 'state' },
                    { label: 'Taxable value', ...money('taxable') },
                    { label: 'IGST', ...money('igst') },
                  ]}
                  rows={report.unregisteredInterState}
                  empty="None in this period."
                />
              </div>
            </>
          )}

          {tab === 'register' && (
            <div className="card">
              <Table
                columns={[
                  { label: 'Date', render: (r) => day(r.date) },
                  { label: 'Invoice no', key: 'invoiceNumber' },
                  { label: 'Customer', key: 'customer' },
                  { label: 'GSTIN', render: (r) => r.gstin || '' },
                  { label: 'Place of supply', key: 'placeOfSupply' },
                  { label: 'Type', key: 'type' },
                  ...amountCols,
                  { label: 'Total tax', ...money('tax') },
                  { label: 'Invoice value', ...money('value') },
                  { label: 'Status', render: (r) => r.status.replace('_', ' ') },
                ]}
                rows={report.register}
                empty="No GST invoices in this period."
              />
            </div>
          )}

          {tab === 'b2b' && (
            <div className="card">
              <strong>B2B invoices (customer has a GSTIN), rate by rate</strong>
              <Table
                columns={[
                  { label: 'Customer GSTIN', key: 'gstin' },
                  { label: 'Customer', key: 'customer' },
                  { label: 'Invoice no', key: 'invoiceNumber' },
                  { label: 'Date', render: (r) => day(r.date) },
                  { label: 'Invoice value', ...money('invoiceValue') },
                  { label: 'Place of supply', key: 'placeOfSupply' },
                  { label: 'Rate', render: (r) => pct(r.rate) },
                  { label: 'Taxable value', ...money('taxable') },
                  { label: 'IGST', ...money('igst') },
                  { label: 'CGST', ...money('cgst') },
                  { label: 'SGST', ...money('sgst') },
                ]}
                rows={report.b2b}
                empty="No B2B invoices in this period."
              />
            </div>
          )}

          {tab === 'b2c' && (
            <>
              <div className="card">
                <strong>Large inter-state B2C invoices (above ₹{inr(report.b2clThreshold)})</strong>
                <Table
                  columns={[
                    { label: 'Invoice no', key: 'invoiceNumber' },
                    { label: 'Date', render: (r) => day(r.date) },
                    { label: 'Invoice value', ...money('invoiceValue') },
                    { label: 'Place of supply', key: 'placeOfSupply' },
                    { label: 'Rate', render: (r) => pct(r.rate) },
                    { label: 'Taxable value', ...money('taxable') },
                    { label: 'IGST', ...money('igst') },
                  ]}
                  rows={report.b2cLarge}
                  empty="None in this period."
                />
              </div>
              <div className="card">
                <strong>Other B2C supplies, totalled by state and rate</strong>
                <Table
                  columns={[
                    { label: 'Place of supply', key: 'placeOfSupply' },
                    { label: 'Supply', key: 'supply' },
                    { label: 'Rate', render: (r) => pct(r.rate) },
                    { label: 'Taxable value', ...money('taxable') },
                    { label: 'IGST', ...money('igst') },
                    { label: 'CGST', ...money('cgst') },
                    { label: 'SGST', ...money('sgst') },
                  ]}
                  rows={report.b2cSmall}
                  empty="None in this period."
                />
              </div>
            </>
          )}

          {tab === 'hsn' && (
            <div className="card">
              <Table
                columns={[
                  { label: 'HSN / SAC', key: 'hsn' },
                  { label: 'Rate', render: (r) => pct(r.rate) },
                  { label: 'Quantity', num: true, render: (r) => Number(r.quantity) },
                  { label: 'Taxable value', ...money('taxable') },
                  { label: 'IGST', ...money('igst') },
                  { label: 'CGST', ...money('cgst') },
                  { label: 'SGST', ...money('sgst') },
                  { label: 'Total value', ...money('value') },
                ]}
                rows={report.hsn}
                empty="No items in this period."
              />
            </div>
          )}

          {tab === 'docs' && (
            <div className="card">
              <strong>Invoices issued in the period</strong>
              <Table
                columns={[
                  { label: 'Series', key: 'series' },
                  { label: 'From', key: 'from' },
                  { label: 'To', key: 'to' },
                  { label: 'Total issued', num: true, key: 'total' },
                  { label: 'Cancelled', num: true, key: 'cancelled' },
                  { label: 'Net issued', num: true, key: 'net' },
                ]}
                rows={report.documents}
                empty="No GST invoices in this period."
              />
            </div>
          )}

          {tab === 'excluded' && (
            <div className="card">
              <strong>Invoices left out of the figures</strong>
              <Table
                columns={[
                  { label: 'Invoice no', key: 'invoiceNumber' },
                  { label: 'Date', render: (r) => day(r.date) },
                  { label: 'Customer', key: 'customer' },
                  { label: 'Invoice value', ...money('value') },
                  { label: 'Reason', key: 'reason' },
                ]}
                rows={report.notIncluded}
                empty="Every invoice in this period is included."
              />
            </div>
          )}

          <p className="subtitle">
            This report is a calculation aid. Please check it with your Chartered Accountant before filing a return.
          </p>
        </>
      )}
    </div>
  );
}
