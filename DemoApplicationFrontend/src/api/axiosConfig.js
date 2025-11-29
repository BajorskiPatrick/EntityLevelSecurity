import axios from 'axios';

// Adres twojego backendu
const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

// Interceptor: Przed wysłaniem każdego zapytania sprawdź, czy mamy usera
api.interceptors.request.use((config) => {
    const user = JSON.parse(localStorage.getItem('user'));

    if (user && user.userId) {
        // Kluczowe dla Twojego backendu: dodanie nagłówka X-User-Id
        config.headers['X-User-Id'] = user.userId;
    }
    return config;
}, (error) => {
    return Promise.reject(error);
});

export default api;