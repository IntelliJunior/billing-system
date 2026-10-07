import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getTenant, createTenant, updateTenant } from '../api/api';

const EMPTY = {
  name: '', contactName: '', mobile: '', email: '', address: '', username: '',
  accountHolder: '', bankName: '', accountNumber: '', ifsc: '', branch: '', upiId: '',
};

const grid = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 };

export default function AdminTenantFormPage() {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [form, setForm] = useState(EMPTY);
  const [bannerFile, setBannerFile] = useState(null);
  const [bannerPreview, setBannerPreview] = useState('');
  const [hasBanner, setHasBanner] = useState(false);
  const [removeBanner, setRemoveBanner] = useState(false);
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
        });
        setHasBanner(t.hasBanner);
      })
      .catch(() => setError('Could not load the tenant'))
      .finally(() => setLoading(false));
  }, [id, isEdit]);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

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
    setSaving(true);

    const fd = new FormData();
    Object.entries(form).forEach(([key, value]) => {
      if (key === 'username' && isEdit) return;   // username cannot be changed
      fd.append(key, value ?? '');
    });
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
