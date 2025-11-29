import { useEffect, useState } from 'react';
import api from '../api/axiosConfig';
import { useAuth } from '../context/AuthContext';

const Dashboard = () => {
    const { user, logout } = useAuth();
    const [notes, setNotes] = useState([]);
    const [newNote, setNewNote] = useState({ title: '', content: '', category: 'Work' });

    const fetchNotes = async () => {
        try {
            const response = await api.get('/notes');
            setNotes(response.data);
        } catch (error) {
            console.error("Błąd pobierania notatek", error);
        }
    };

    useEffect(() => {
        fetchNotes();
    }, []);

    const handleCreate = async (e) => {
        e.preventDefault();
        try {
            await api.post('/notes', newNote);
            setNewNote({ title: '', content: '', category: 'Work' });
            fetchNotes();
        } catch (error) {
            alert('Błąd tworzenia notatki');
        }
    };

    const handleShare = async (noteId) => {
        const targetUserId = prompt("Podaj ID użytkownika, któremu chcesz udostępnić (np. 1, 2, 3):");
        if (!targetUserId) return;

        try {
            await api.post(`/notes/${noteId}/share?targetUser=${targetUserId}`);
            alert('Udostępniono pomyślnie!');
        } catch (error) {
            alert('Błąd udostępniania');
        }
    };

    return (
        <div className="container">
            {/* Header */}
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '3rem', paddingBottom: '1rem', borderBottom: '1px solid var(--border-color)' }}>
                <div>
                    <h1>Twoje Notatki</h1>
                    <p style={{ color: 'var(--text-secondary)', margin: 0 }}>
                        Zalogowany jako: <strong style={{ color: 'white' }}>{user?.username}</strong> (ID: {user?.userId})
                    </p>
                </div>
                <button onClick={logout} className="danger">
                    Wyloguj
                </button>
            </header>

            {/* Layout Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem' }}>

                {/* Kolumna 1: Formularz */}
                <aside>
                    <div className="card" style={{ position: 'sticky', top: '20px' }}>
                        <h3>📝 Nowa notatka</h3>
                        <form onSubmit={handleCreate}>
                            <div style={{ marginBottom: '1rem' }}>
                                <label>Tytuł</label>
                                <input
                                    value={newNote.title}
                                    onChange={e => setNewNote({...newNote, title: e.target.value})}
                                    placeholder="Wpisz tytuł..."
                                    required
                                />
                            </div>
                            <div style={{ marginBottom: '1rem' }}>
                                <label>Treść</label>
                                <textarea
                                    value={newNote.content}
                                    onChange={e => setNewNote({...newNote, content: e.target.value})}
                                    placeholder="O czym myślisz?"
                                    style={{ minHeight: '100px', resize: 'vertical' }}
                                    required
                                />
                            </div>
                            <div style={{ marginBottom: '1.5rem' }}>
                                <label>Kategoria</label>
                                <select
                                    value={newNote.category}
                                    onChange={e => setNewNote({...newNote, category: e.target.value})}
                                >
                                    <option value="Work">Praca (Work)</option>
                                    <option value="Home">Dom (Home)</option>
                                    <option value="Private">Prywatne</option>
                                </select>
                            </div>
                            <button type="submit" style={{ width: '100%' }}>Dodaj notatkę</button>
                        </form>
                    </div>
                </aside>

                {/* Kolumna 2: Lista notatek */}
                <main>
                    <h3 style={{ marginBottom: '1.5rem' }}>Ostatnie wpisy</h3>
                    {notes.length === 0 ? (
                        <div style={{ textAlign: 'center', color: 'var(--text-secondary)', marginTop: '2rem' }}>
                            <p>Nie masz jeszcze żadnych notatek.</p>
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                            {notes.map(note => (
                                <div key={note.id} className="card" style={{ transition: 'transform 0.2s' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
                                        <div>
                                            <h4 style={{ margin: '0 0 5px 0', fontSize: '1.2rem' }}>{note.title}</h4>
                                            <span style={{
                                                fontSize: '0.75rem',
                                                background: 'var(--surface-hover)',
                                                padding: '4px 8px',
                                                borderRadius: '4px',
                                                color: '#bbb'
                                            }}>
                                                {note.category}
                                            </span>
                                        </div>
                                        <button
                                            onClick={() => handleShare(note.id)}
                                            className="secondary"
                                            style={{ padding: '0.4rem 0.8rem', fontSize: '0.9rem' }}
                                        >
                                            🔗 Udostępnij
                                        </button>
                                    </div>
                                    <p style={{ color: 'var(--text-secondary)', whiteSpace: 'pre-wrap' }}>
                                        {note.content}
                                    </p>
                                </div>
                            ))}
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
};

export default Dashboard;