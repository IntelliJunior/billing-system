import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getTenant, createTenant, updateTenant, getGstStates } from '../api/api';

const EMPTY = {
  name: '', contactName: '', mobile: '', email: '', address: '', username: '',
  accountHolder: '', bankName: '', accountNumber: '', ifsc: '', branch: '', upiId: '',
  gstin: '', stateCode: '', filingFrequency: 'MONTHLY', invoicePrefix: '',
};

const grid = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 };

const FEATURES = [
  { key: 'INVOICING', label: 'Invoicing', help: 'Customers, products, invoices, payments and PDF invoices' },
  { key: 'INVENTORY', label: 'Inventory', help: 'Stock tracking, stock in/out, stock history, suppliers and low-stock list' },
  { key: 'GST', label: 'GST', help: 'GST tax invoices (CGST/SGST/IGST, HSN) and GST reports. Needs Invoicing.' },
];

export default function AdminTenantFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY);
  const [bannerFile, setBannerFile] = useState(null);
  const [bannerPreview, setBannerPreview] = useState('');
  const [hasBanner, setHasBanner] = useState(false);
  const [removeBanner, setRemoveBanner] = useState(false);
  const [modules, setModules] = useState(['INVOICING']);
  const [states, setStates] = useState([]);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isEdit) return;
    getTenant(id)
      .then((res) => {
        const t = res.data;
        setForm({
          name: t.name || '', contactName: t.contactName || '', mobile: t.mobile || '',
          email: t.email || '', address: t.address || '', username: t.username || '',
          accountHolder: t.accountHolder || '', bankName: t.bankName || '',
          accountNumber: t.accountNumber || '', ifsc: t.ifsc || '', branch: t.branch || '',
          upiId: t.upiId || '',
          gstin: t.gstin || '', stateCode: t.stateCode || '',
          filingFrequency: t.filingFrequency || 'MONTHLY', invoicePrefix: t.invoicePrefix || '',
        });
        setHasBanner(t.hasBanner);
        setModules(t.modules && t.modules.length ? t.modules : ['INVOICING']);
      })
      .catch(() => setError('Could not load the tenant'))
      .finally(() => setLoading(false));
  }, [id, isEdit]);

  useEffect(() => {
    getGstStates().then((res) => setStates(res.data)).catch(() => {});
  }, []);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  // Typing the GSTIN fills in the state, because the first two digits are the state code
  const onGstinChange = (e) => {
    const value = e.target.value.toUpperCase().replace(/\s/g, '');
    const code = value.slice(0, 2);
    const known = states.some((s) => s.code === code);
    setForm({ ...form, gstin: value, stateCode: known ? code : form.stateCode });
  };

  const toggleModule = (key) =>
    setModules((current) => (current.includes(key) ? current.filter((m) => m !== key) : [...current, key]));

  const onBannerChange = (e) => {
    const file = e.target.files[0];
    setError('');
    if (!file) {
      setBannerFile(null);
      setBannerPreview('');
      return;
    }
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      setError('Banner must be a PNG or JPG image');
      e.target.value = '';
      return;
    }
    if (file.size > 1024 * 1024) {
      setError('Banner must be 1 MB or smaller');
      e.target.value = '';
      return;
    }
    setBannerFile(file);
    setBannerPreview(URL.createObjectURL(file));
    setRemoveBanner(false);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (modules.length === 0) {
      setError('Select at least one feature');
      return;
    }
    setSaving(true);

    const fd = new FormData();
    Object.entries(form).forEach(([key, value]) => {
      if (key === 'username' && isEdit) return;   // username cannot be changed
      fd.append(key, value ?? '');
    });
    modules.forEach((m) => fd.append('modules', m));
    fd.append('removeBanner', removeBanner ? 'true' : 'false');
    if (bannerFile) fd.append('banner', bannerFile);

    try {
      if (isEdit) {
        await updateTenant(id, fd);
        navigate('/admin');
      } else {
        const res = await createTenant(fd);
        navigate('/admin', { state: { credentials: res.data } });
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save the tenant');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className="empty">Loading…</p>;

  const showExistingBanner = isEdit && hasBanner && !removeBanner && !bannerPreview;

  return (
    <div>
      <div className="toolbar">
        <div>
          <h1>{isEdit ? 'Edit Tenant' : 'New Tenant'}</h1>
          <p className="subtitle">
            {isEdit
              ? 'Update the tenant details. The username cannot be changed.'
              : 'The tenant gets a login. Default password = contact first name + last 5 digits of mobile.'}
          </p>
        </div>
      </div>

      <form onSubmit={submit}>
        <div className="card">
          <strong>Tenant details</strong>
          <div style={{ ...grid, marginTop: 12 }}>
            <div className="form-row">
              <label>Tenant name *</label>
              <input value={form.name} onChange={set('name')} required />
            </div>
            <div className="form-row">
              <label>Contact person name * (first name is used in the default password)</label>
              <input value={form.contactName} onChange={set('contactName')} required />
            </div>
            <div className="form-row">
              <label>Mobile * (10 digits)</label>
              <input value={form.mobile} onChange={set('mobile')} pattern="[0-9]{10}" maxLength={10} required />
            </div>
            <div className="form-row">
              <label>Email</label>
              <input type="email" value={form.email} onChange={set('email')} />
            </div>
          </div>
          <div className="form-row">
            <label>Address</label>
            <input value={form.address} onChange={set('address')} />
          </div>
        </div>

        <div className="card">
          <strong>Login</strong>
          <div className="form-row" style={{ marginTop: 12, maxWidth: 360 }}>
            <label>Username * (4-30 characters, no spaces)</label>
            <input
              value={form.username}
              onChange={set('username')}
              pattern="[A-Za-z0-9._\-]{4,30}"
              disabled={isEdit}
              required={!isEdit}
            />
          </div>
        </div>

        <div className="card">
          <strong>Features</strong>
          <p className="subtitle">Choose what this tenant can use. You can change this later.</p>
          {FEATURES.map((f) => (
            <label key={f.key} style={{ display: 'block', marginBottom: 10 }}>
              <input
                type="checkbox"
                checked={modules.includes(f.key)}
                onChange={() => toggleModule(f.key)}
              />{' '}
              <strong>{f.label}</strong>
              <span className="subtitle"> — {f.help}</span>
            </label>
          ))}
        </div>

        {modules.includes('GST') && (
          <div className="card">
            <strong>GST details</strong>
            <p className="subtitle">Printed on tax invoices and used to work out CGST / SGST / IGST.</p>
            <div style={{ ...grid, marginTop: 12 }}>
              <div className="form-row">
                <label>GSTIN * (15 characters)</label>
                <input value={form.gstin} onChange={onGstinChange} maxLength={15} required />
              </div>
              <div className="form-row">
                <label>State *</label>
                <select value={form.stateCode} onChange={set('stateCode')} required>
                  <option value="">Select a state</option>
                  {states.map((s) => <option key={s.code} value={s.code}>{s.name} ({s.code})</option>)}
                </select>
              </div>
              <div className="form-row">
                <label>Invoice prefix * (2-4 letters, e.g. IJ gives IJ/26-27/0001)</label>
                <input
                  value={form.invoicePrefix}
                  onChange={(e) => setForm({ ...form, invoicePrefix: e.target.value.toUpperCase() })}
                  maxLength={4}
                  required
                />
              </div>
              <div className="form-row">
                <label>Return filing</label>
                <select value={form.filingFrequency} onChange={set('filingFrequency')}>
                  <option value="MONTHLY">Monthly</option>
                  <option value="QUARTERLY">Quarterly (QRMP)</option>
                </select>
              </div>
            </div>
          </div>
        )}

        <div className="card">
          <strong>Invoice banner (header)</strong>
          <p className="subtitle">PNG or JPG, up to 1 MB. A wide image works best.</p>
          {showExistingBanner && (
            <img
              src={`/api/admin/tenants/${id}/banner?v=${Date.now()}`}
              alt="Current banner"
              style={{ maxWidth: '100%', maxHeight: 120, display: 'block', marginBottom: 12 }}
            />
          )}
          {bannerPreview && (
            <img src={bannerPreview} alt="New banner" style={{ maxWidth: '100%', maxHeight: 120, display: 'block', marginBottom: 12 }} />
          )}
          <input type="file" accept="image/png,image/jpeg" onChange={onBannerChange} />
          {isEdit && hasBanner && (
            <label style={{ display: 'block', marginTop: 10 }}>
              <input
                type="checkbox"
                checked={removeBanner}
                onChange={(e) => setRemoveBanner(e.target.checked)}
              />{' '}
              Remove the current banner
            </label>
          )}
        </div>

        <div className="card">
          <strong>Bank details (invoice footer)</strong>
          <div style={{ ...grid, marginTop: 12 }}>
            <div className="form-row">
              <label>Account holder</label>
              <input value={form.accountHolder} onChange={set('accountHolder')} />
            </div>
            <div className="form-row">
              <label>Bank name</label>
              <input value={form.bankName} onChange={set('bankName')} />
            </div>
            <div className="form-row">
              <label>Account number</label>
              <input value={form.accountNumber} onChange={set('accountNumber')} />
            </div>
            <div className="form-row">
              <label>IFSC</label>
              <input value={form.ifsc} onChange={set('ifsc')} />
            </div>
            <div className="form-row">
              <label>Branch</label>
              <input value={form.branch} onChange={set('branch')} />
            </div>
            <div className="form-row">
              <label>UPI ID (optional)</label>
              <input value={form.upiId} onChange={set('upiId')} />
            </div>
          </div>
        </div>

        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="btn" disabled={saving}>
          {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create tenant'}
        </button>{' '}
        <Link to="/admin" className="btn secondary">Cancel</Link>
      </form>
    </div>
  );
}
