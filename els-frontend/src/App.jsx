import { useState, useEffect } from 'react'
import axios from 'axios'
import './App.css'

// Configuration
const API_URL = 'http://localhost:8080/api';

function App() {
  const [user, setUser] = useState(null); // Current simulated user (username)
  const [view, setView] = useState('products'); // 'products' or 'admin'

  // Global Context Provider (Simplified)
  const context = { user, setUser, API_URL };

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="logo">ELS Secure Retail</div>
        <nav>
          <button className={view === 'products' ? 'active' : ''} onClick={() => setView('products')}>Operational Panel</button>
          <button className={view === 'admin' ? 'active' : ''} onClick={() => setView('admin')}>Admin / Config</button>
        </nav>
        <UserSwitcher context={context} />
      </header>
      
      <main className="app-content">
        {view === 'products' && <ProductList context={context} />}
        {view === 'admin' && <AdminPanel context={context} />}
      </main>
    </div>
  )
}

// --- Components (Defining here for simplicity in one file first, can split later) ---

const UserSwitcher = ({ context }) => {
  const [users, setUsers] = useState([]);
  
  useEffect(() => {
    // Fetch users for the switcher (using admin API usually, or hardcoded for demo)
    axios.get(`${context.API_URL}/admin/users`)
      .then(res => setUsers(res.data))
      .catch(err => console.error("Failed to fetch users", err));
  }, []);

  return (
    <div className="user-switcher">
      <span>Simulate User: </span>
      <select value={context.user || ''} onChange={e => context.setUser(e.target.value)}>
        <option value="">-- Guest --</option>
        {users.map(u => (
          <option key={u.id} value={u.username}>{u.username} ({u.roles.map(r => r.name).join(', ') || 'No Role'})</option>
        ))}
      </select>
    </div>
  )
}

const ProductList = ({ context }) => {
  const [products, setProducts] = useState([]);
  const [error, setError] = useState(null);

  const fetchProducts = () => {
    const headers = context.user ? { 'X-User': context.user } : {};
    axios.get(`${context.API_URL}/products`, { headers })
      .then(res => {
        setProducts(res.data);
        setError(null);
      })
      .catch(err => {
        setProducts([]);
        setError(err.response?.status === 403 ? "Access Denied" : "Error fetching data");
      });
  }

  useEffect(() => {
    fetchProducts();
  }, [context.user]); // Refetch when user changes

  return (
    <div className="panel operational-panel">
      <h2>Products Inventory</h2>
      <p className="subtitle">View is filtered based on: <strong>{context.user || 'Guest'}</strong></p>
      
      {error && <div className="alert error">{error}</div>}
      
      <div className="product-grid">
        {products.map(p => (
          <div key={p.id} className="card product-card">
            <h3>{p.name}</h3>
            <span className="category">{p.category}</span>
            <span className="price">${p.price}</span>
            <div className="id-badge">ID: {p.id}</div>
          </div>
        ))}
        {products.length === 0 && !error && <div className="alert info">No products visible for this user.</div>}
      </div>
    </div>
  )
}

const AdminPanel = ({ context }) => {
  const [permissions, setPermissions] = useState([]);
  const [users, setUsers] = useState([]);
  const [roles, setRoles] = useState([]);
  
  // Form State
  const [form, setForm] = useState({
    targetType: 'USER', // USER or ROLE
    username: '',
    roleId: '',
    entityName: 'Product',
    action: 'SELECT',
    accessType: 'WHITELIST',
    rowIds: ''
  });

  const fetchData = () => {
    axios.get(`${context.API_URL}/admin/permissions`).then(res => setPermissions(res.data));
    axios.get(`${context.API_URL}/admin/users`).then(res => setUsers(res.data));
    axios.get(`${context.API_URL}/admin/roles`).then(res => setRoles(res.data));
  };

  useEffect(() => fetchData(), []);

  const handleGrant = () => {
    const payload = {
       ...form,
       username: form.targetType === 'USER' ? form.username : null,
       roleId: form.targetType === 'ROLE' ? form.roleId : null
    };
    
    axios.post(`${context.API_URL}/admin/permissions`, payload)
      .then(() => {
        fetchData();
        alert("Permission Granted!");
      })
      .catch(err => alert("Error: " + err.message));
  };
  
  const handleDelete = (id) => {
      axios.delete(`${context.API_URL}/admin/permissions/${id}`)
        .then(() => fetchData());
  }

  return (
    <div className="panel admin-panel">
      <h2>Configuration Panel</h2>
      
      <div className="admin-grid">
        <div className="card form-card">
            <h3>Grant Permission</h3>
            
            <div className="form-group">
                <label>Target Type</label>
                <select value={form.targetType} onChange={e => setForm({...form, targetType: e.target.value})}>
                    <option value="USER">User</option>
                    <option value="ROLE">Role (Composite)</option>
                </select>
            </div>
            
            {form.targetType === 'USER' ? (
                 <div className="form-group">
                    <label>User</label>
                    <select value={form.username} onChange={e => setForm({...form, username: e.target.value})}>
                        <option value="">-- Select User --</option>
                        {users.map(u => <option key={u.id} value={u.username}>{u.username}</option>)}
                    </select>
                </div>
            ) : (
                <div className="form-group">
                    <label>Role</label>
                    <select value={form.roleId} onChange={e => setForm({...form, roleId: e.target.value})}>
                        <option value="">-- Select Role --</option>
                        {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                    </select>
                </div>
            )}
            
            <div className="form-group">
                <label>Action</label>
                <select value={form.action} onChange={e => setForm({...form, action: e.target.value})}>
                    <option value="SELECT">SELECT</option>
                    <option value="UPDATE">UPDATE</option>
                </select>
            </div>
            
            <div className="form-group">
                <label>Strategy</label>
                <select value={form.accessType} onChange={e => setForm({...form, accessType: e.target.value})}>
                    <option value="WHITELIST">Whitelist (Allow)</option>
                    <option value="BLACKLIST">Blacklist (Deny)</option>
                </select>
            </div>
            
            <div className="form-group">
                <label>Row IDs (comma separated)</label>
                <input type="text" placeholder="e.g. 1, 2, 3" value={form.rowIds} onChange={e => setForm({...form, rowIds: e.target.value})} />
            </div>
            
            <button className="btn-primary" onClick={handleGrant}>Grant Permission</button>
        </div>

        <div className="card list-card">
            <h3>Active Permissions</h3>
            <table>
                <thead>
                    <tr>
                        <th>Grantee</th>
                        <th>Entity</th>
                        <th>Action</th>
                        <th>Type</th>
                        <th>IDs</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    {permissions.map(p => (
                        <tr key={p.id}>
                            <td>
                                {p.user ? `User: ${p.user.username}` : `Role: ${p.role?.name}`}
                            </td>
                            <td>{p.entityName}</td>
                            <td>{p.action}</td>
                            <td><span className={`badge ${p.accessType}`}>{p.accessType}</span></td>
                            <td>{p.rowIds}</td>
                            <td><button className="btn-danger small" onClick={() => handleDelete(p.id)}>Revoke</button></td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
      </div>
    </div>
  )
}

export default App
