import axios from 'axios';

// Relative base URL: works both when the React build is served directly by
// Spring Boot (same origin, e.g. http://localhost:8181) and in `npm start`
// dev mode, where package.json's "proxy" field forwards /api to the backend.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Global handling of session problems
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || '';
    const isAuthCall = url.startsWith('/auth/');

    if (status === 401 && !isAuthCall) {
      // Session expired or account disabled: go to the login page
      window.location.assign('/login');
    } else if (status === 403 && error.response?.data?.code === 'PASSWORD_CHANGE_REQUIRED') {
      window.location.assign('/change-password');
    } else if (status === 403 && error.response?.data?.code === 'MODULE_NOT_ENABLED') {
      // The admin changed this account's features: reload so the menu is rebuilt
      if (window.location.pathname !== '/') window.location.assign('/');
    }
    return Promise.reject(error);
  }
);

// Auth
export const login = (username, password) =>
  api.post('/auth/login', new URLSearchParams({ username, password }), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
export const logout = () => api.post('/auth/logout');
export const getMe = () => api.get('/auth/me');
export const changePassword = (data) => api.post('/auth/change-password', data);

// Admin: tenants (multipart form data because of the banner image)
export const getTenants = () => api.get('/admin/tenants');
export const getTenant = (id) => api.get(`/admin/tenants/${id}`);
export const createTenant = (formData) =>
  api.post('/admin/tenants', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
export const updateTenant = (id, formData) =>
  api.post(`/admin/tenants/${id}`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
export const setTenantEnabled = (id, enabled) =>
  api.post(`/admin/tenants/${id}/${enabled ? 'enable' : 'disable'}`);
export const resetTenantPassword = (id) => api.post(`/admin/tenants/${id}/reset-password`);

// Customers
export const getCustomers = () => api.get('/customers');
export const getCustomer = (id) => api.get(`/customers/${id}`);
export const createCustomer = (data) => api.post('/customers', data);
export const updateCustomer = (id, data) => api.put(`/customers/${id}`, data);
export const deleteCustomer = (id) => api.delete(`/customers/${id}`);

// Products
export const getProducts = () => api.get('/products');
export const createProduct = (data) => api.post('/products', data);
export const updateProduct = (id, data) => api.put(`/products/${id}`, data);
export const deleteProduct = (id) => api.delete(`/products/${id}`);

// Inventory
export const getStock = (lowOnly = false) => api.get('/inventory/stock', { params: { lowOnly } });
export const recordMovement = (data) => api.post('/inventory/movements', data);
export const getMovements = (productId) =>
  api.get('/inventory/movements', { params: productId ? { productId } : {} });
export const getSuppliers = () => api.get('/inventory/suppliers');
export const createSupplier = (data) => api.post('/inventory/suppliers', data);
export const updateSupplier = (id, data) => api.put(`/inventory/suppliers/${id}`, data);
export const deleteSupplier = (id) => api.delete(`/inventory/suppliers/${id}`);

export const getInventorySummary = () => api.get('/inventory/summary');
export const getMovementReport = (params) => api.get('/inventory/reports/movements', { params });

// Downloads a CSV report through the signed-in session and saves it as a file
export const downloadCsv = async (path, params, filename) => {
  const res = await api.get(path, { params, responseType: 'blob' });
  const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => window.URL.revokeObjectURL(url), 60000);
};

// GST reference lists
export const getGstStates = () => api.get('/meta/gst-states');
export const getGstRates = () => api.get('/meta/gst-rates');

// GST reports (tenant)
export const getGstReport = (params) => api.get('/gst/report', { params });

// Downloads a file (Excel, PDF...) through the signed-in session and saves it
export const downloadFile = async (path, params, filename) => {
  const res = await api.get(path, { params, responseType: 'blob' });
  const url = window.URL.createObjectURL(new Blob([res.data]));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => window.URL.revokeObjectURL(url), 60000);
};

// GST rules (admin)
export const getAdminGstRates = () => api.get('/admin/gst/rates');
export const createGstRate = (data) => api.post('/admin/gst/rates', data);
export const updateGstRate = (id, data) => api.put(`/admin/gst/rates/${id}`, data);
export const setGstRateActive = (id, active) =>
  api.post(`/admin/gst/rates/${id}/${active ? 'activate' : 'deactivate'}`);
export const getGstSettings = () => api.get('/admin/gst/settings');
export const updateGstSettings = (data) => api.put('/admin/gst/settings', data);

// Invoices
export const getInvoices = (params) => api.get('/invoices', { params });
export const getInvoice = (id) => api.get(`/invoices/${id}`);
export const createInvoice = (data) => api.post('/invoices', data);
export const recordPayment = (id, data) => api.post(`/invoices/${id}/payments`, data);
export const cancelInvoice = (id) => api.post(`/invoices/${id}/cancel`);
export const deleteInvoice = (id) => api.delete(`/invoices/${id}`);
export const downloadInvoicePdf = (id) =>
  api.get(`/invoices/${id}/pdf`, { responseType: 'blob' });

export default api;
