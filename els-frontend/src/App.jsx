import React, { useState, useEffect } from 'react';
import './App.css';
import Login from './components/auth/Login';
import HospitalDashboard from './components/hospital/HospitalDashboard';
import AdminPanel from './components/admin/AdminPanel';
import api from './api/axiosConfig';

function App() {
  const [user, setUser] = useState(null); // User Object { id, username, roles }
  const [view, setView] = useState('hospital');

  useEffect(() => {
    // Check if we have an active session on load
    api.get('/auth/me')
      .then(res => setUser(res.data))
      .catch(() => setUser(null));
  }, []);

  const handleLogout = async () => {
    try {
      await api.post('/auth/logout');
      setUser(null);
      setView('hospital');
    } catch (e) { console.error(e); }
  }

  if (!user) {
    return (
      <div className="app-container centered">
        <header className="app-header login-header">
          <div className="logo">ELS Demo System</div>
        </header>
        <div className="login-wrapper">
          <Login onLogin={setUser} />
        </div>
      </div>
    )
  }

  return (
    <div className="app-container">
      <header className="app-header">
        <div className="logo">MediSec Hospital</div>
        <nav>
          <button className={view === 'hospital' ? 'active' : ''} onClick={() => setView('hospital')}>Hospital Operations</button>
          <button className={view === 'admin' ? 'active' : ''} onClick={() => setView('admin')}>Admin / Security</button>
        </nav>
        <div className="user-profile">
          <span>{user.username}</span>
          <button className="btn-logout" onClick={handleLogout}>Logout</button>
        </div>
      </header>

      <main className="app-content">
        {view === 'hospital' && <HospitalDashboard user={user} />}
        {view === 'admin' && <AdminPanel user={user} />}
      </main>
    </div>
  )
}

export default App;
