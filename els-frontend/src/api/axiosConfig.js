import axios from 'axios';

const api = axios.create({
    baseURL: '/api', // Relative path for proxy (Vite or Nginx)
    withCredentials: true, // Crucial for JSESSIONID cookie
    headers: {
        'Content-Type': 'application/json',
    },
});

export default api;
