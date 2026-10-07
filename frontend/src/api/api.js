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
