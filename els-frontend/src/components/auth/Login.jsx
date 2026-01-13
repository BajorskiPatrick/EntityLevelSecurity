import React, { useState } from 'react';
import api from '../../api/axiosConfig';

const Login = ({ onLogin }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            // Backend expects { username, password }
            await api.post('/auth/login', { username, password });

            // Fetch user details to confirm session and get context
            const userRes = await api.get('/auth/me');
            onLogin(userRes.data); // Should be User object
        } catch (err) {
            console.error(err);
            setError('Invalid credentials');
        }
    };

    return (
        <div className="login-container">
            <form onSubmit={handleSubmit} className="card login-form">
                <h2>Login</h2>
                {error && <div className="alert error">{error}</div>}
                <div className="form-group">
                    <label>Username</label>
                    <input
                        type="text"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="admin / dr_house"
                    />
                </div>
                <div className="form-group">
                    <label>Password</label>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="password"
                    />
                </div>
                <button type="submit" className="btn-primary">Sign In</button>
            </form>
        </div>
    );
};

export default Login;
