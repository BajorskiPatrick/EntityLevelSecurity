// ... (UserSwitcher and helper code remains similar, focused on rewriting the main view)

const HospitalDashboard = ({ context }) => {
  const [activeTab, setActiveTab] = useState('departments');
  const [data, setData] = useState([]);
  const [error, setError] = useState(null);

  const fetchData = (endpoint) => {
    const headers = context.user ? { 'X-User': context.user } : {};
    axios.get(`${context.API_URL}/hospital/${endpoint}`, { headers })
      .then(res => {
        setData(res.data);
        setError(null);
      })
      .catch(err => {
        setData([]);
        setError(err.response?.status === 403 ? "Access Denied" : "Error fetching data");
      });
  };

  useEffect(() => {
    // Determine what to fetch based on tab
    if (activeTab === 'departments') fetchData('departments');
    if (activeTab === 'patients') fetchData('patients');
    if (activeTab === 'records') fetchData('records');
  }, [activeTab, context.user]);

  return (
    <div className="panel operational-panel">
      <h2>MediSec Hospital System</h2>
      <p className="subtitle">Logged in as: <strong>{context.user || 'Guest'}</strong></p>

      <div className="tabs">
        <button className={activeTab === 'departments' ? 'active' : ''} onClick={() => setActiveTab('departments')}>Departments</button>
        <button className={activeTab === 'patients' ? 'active' : ''} onClick={() => setActiveTab('patients')}>Patients</button>
        <button className={activeTab === 'records' ? 'active' : ''} onClick={() => setActiveTab('records')}>Medical Records</button>
      </div>

      {error && <div className="alert error">{error}</div>}

      <div className="data-grid">
        {data.length === 0 && !error && <div className="alert info">No records found.</div>}

        {activeTab === 'departments' && data.map(d => (
          <div key={d.id} className="card">
            <h3>{d.name}</h3>
            <div className="id-badge">ID: {d.id}</div>
          </div>
        ))}

        {activeTab === 'patients' && data.map(p => (
          <div key={p.id} className="card">
            <h3>{p.name}</h3>
            <p>SSN: {p.ssn || 'HIDDEN'}</p>
            <div className="tag">{p.department ? p.department.name : 'No Dept'}</div>
            <div className="id-badge">ID: {p.id}</div>
          </div>
        ))}

        {activeTab === 'records' && data.map(r => (
          <div key={r.id} className="card record-card">
            <h3>Diagnosis: {r.diagnosis}</h3>
            <p><strong>Patient:</strong> {r.patient ? r.patient.name : 'Unknown'}</p>
            <p><strong>Treatment:</strong> {r.treatment}</p>
            <div className="id-badge">ID: {r.id}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

function App() {
  const [user, setUser] = useState(null); // Current simulated user
  const [view, setView] = useState('hospital'); // 'hospital' or 'admin'

  // Global Context Provider
  const context = { user, setUser, API_URL };

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="logo">MediSec Hospital</div>
        <nav>
          <button className={view === 'hospital' ? 'active' : ''} onClick={() => setView('hospital')}>Hospital Panel</button>
          <button className={view === 'admin' ? 'active' : ''} onClick={() => setView('admin')}>Admin / Config</button>
        </nav>
        <UserSwitcher context={context} />
      </header>

      <main className="app-content">
        {view === 'hospital' && <HospitalDashboard context={context} />}
        {view === 'admin' && <AdminPanel context={context} />}
      </main>
    </div>
  )
}
// ... (UserSwitcher and AdminPanel logic remains exactly the same, they are generic)

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
            <select value={form.targetType} onChange={e => setForm({ ...form, targetType: e.target.value })}>
              <option value="USER">User</option>
              <option value="ROLE">Role (Composite)</option>
            </select>
          </div>

          {form.targetType === 'USER' ? (
            <div className="form-group">
              <label>User</label>
              <select value={form.username} onChange={e => setForm({ ...form, username: e.target.value })}>
                <option value="">-- Select User --</option>
                {users.map(u => <option key={u.id} value={u.username}>{u.username}</option>)}
              </select>
            </div>
          ) : (
            <div className="form-group">
              <label>Role</label>
              <select value={form.roleId} onChange={e => setForm({ ...form, roleId: e.target.value })}>
                <option value="">-- Select Role --</option>
                {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </div>
          )}

          <div className="form-group">
            <label>Action</label>
            <select value={form.action} onChange={e => setForm({ ...form, action: e.target.value })}>
              <option value="SELECT">SELECT</option>
              <option value="UPDATE">UPDATE</option>
            </select>
          </div>

          <div className="form-group">
            <label>Strategy</label>
            <select value={form.accessType} onChange={e => setForm({ ...form, accessType: e.target.value })}>
              <option value="WHITELIST">Whitelist (Allow)</option>
              <option value="BLACKLIST">Blacklist (Deny)</option>
            </select>
          </div>

          <div className="form-group">
            <label>Row IDs (comma separated)</label>
            <input type="text" placeholder="e.g. 1, 2, 3" value={form.rowIds} onChange={e => setForm({ ...form, rowIds: e.target.value })} />
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
