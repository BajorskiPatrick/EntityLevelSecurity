import { useState } from 'react';
import api from '../api/axiosConfig';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const Login = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const { login } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            const response = await api.post('/auth/login', { username, password });
            login(response.data);
            navigate('/dashboard');
        } catch (err) {
            setError('Nieprawidłowe dane logowania');
        }
    };

    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
            <div className="card" style={{ width: '100%', maxWidth: '400px' }}>
                <h2 style={{ textAlign: 'center', marginBottom: '1.5rem' }}>Zaloguj się</h2>

                {error && (
                    <div style={{
                        background: 'rgba(207, 102, 121, 0.2)',
                        color: '#cf6679',
                        padding: '10px',
                        borderRadius: '4px',
                        marginBottom: '1rem',
                        textAlign: 'center'
                    }}>
                        {error}
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div style={{ marginBottom: '1.2rem' }}>
                        <label>Nazwa użytkownika</label>
                        <input
                            type="text"
                            placeholder="np. jan, admin"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                        />
                    </div>
                    <div style={{ marginBottom: '1.5rem' }}>
                        <label>Hasło</label>
                        <input
                            type="password"
                            placeholder="Wpisz hasło"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                        />
                    </div>
                    <button type="submit" style={{ width: '100%' }}>
                        Zaloguj się
                    </button>
                </form>
            </div>
        </div>
    );
};

export default Login;