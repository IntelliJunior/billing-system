import axios from 'axios';

// Relative base URL: works both when the React build is served directly by
// Spring Boot (same origin, e.g. http://localhost:8080) and in `npm start`
// dev mode, where package.json's "proxy" field forwards /api to the backend.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

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

export default api;
