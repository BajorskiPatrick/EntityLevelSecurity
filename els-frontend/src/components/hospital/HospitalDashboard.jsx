import React, { useState, useEffect } from 'react';
import api from '../../api/axiosConfig';

const HospitalDashboard = ({ user }) => {
    const [activeTab, setActiveTab] = useState('patients');
    const [data, setData] = useState([]);
    const [departments, setDepartments] = useState([]); // Needed for creating patients
    const [error, setError] = useState(null);

    // Forms State
    const [newPatient, setNewPatient] = useState({ name: '', departmentId: '' });
    const [newRecord, setNewRecord] = useState({ diagnosis: '', treatment: '', patientId: '' });

    useEffect(() => {
        fetchData();
        if (activeTab === 'patients' || activeTab === 'records') {
            fetchDepartments(); // Need dept list for dropdowns
        }
    }, [activeTab]);

    const fetchDepartments = () => {
        api.get('/hospital/departments').then(res => setDepartments(res.data)).catch(() => { });
    }

    const fetchData = async () => {
        setError(null);
        try {
            let endpoint = '';
            if (activeTab === 'departments') endpoint = '/hospital/departments';
            if (activeTab === 'patients') endpoint = '/hospital/patients';
            if (activeTab === 'records') endpoint = '/hospital/records';

            const res = await api.get(endpoint);
            setData(res.data);
        } catch (err) {
            setData([]);
            setError(err.response?.status === 403 ? "Access Denied (403)" : "Error fetching data");
        }
    };

    const handleAddPatient = async (e) => {
        e.preventDefault();
        try {
            await api.post('/hospital/patients', {
                name: newPatient.name,
                department: { id: newPatient.departmentId }
            });
            fetchData();
            setNewPatient({ name: '', departmentId: '' });
            alert('Patient Added');
        } catch (err) {
            alert("Failed: " + (err.response?.status === 403 ? "Access Denied" : err.message));
        }
    }

    const handleAddRecord = async (e) => {
        e.preventDefault();
        try {
            await api.post('/hospital/records', {
                diagnosis: newRecord.diagnosis,
                treatment: newRecord.treatment,
                patient: { id: newRecord.patientId }
            });
            fetchData();
            setNewRecord({ diagnosis: '', treatment: '', patientId: '' });
            alert('Record Added');
        } catch (err) {
            alert("Failed: " + (err.response?.status === 403 ? "Access Denied" : err.message));
        }
    }

    return (
        <div className="panel operational-panel">
            <div className="panel-header">
                <h2>Hospital Operations</h2>
                <div className="tabs">
                    <button className={activeTab === 'departments' ? 'active' : ''} onClick={() => setActiveTab('departments')}>Departments</button>
                    <button className={activeTab === 'patients' ? 'active' : ''} onClick={() => setActiveTab('patients')}>Patients</button>
                    <button className={activeTab === 'records' ? 'active' : ''} onClick={() => setActiveTab('records')}>Medical Records</button>
                </div>
            </div>

            {error && <div className="alert error">{error}</div>}

            <div className="content-area">
                {/* Action Bar (Forms) */}
                {activeTab === 'patients' && (
                    <div className="card form-card compact">
                        <h4>New Patient</h4>
                        <form onSubmit={handleAddPatient} className="inline-form">
                            <input placeholder="Name" value={newPatient.name} onChange={e => setNewPatient({ ...newPatient, name: e.target.value })} required />
                            <select value={newPatient.departmentId} onChange={e => setNewPatient({ ...newPatient, departmentId: e.target.value })} required>
                                <option value="">Select Dept</option>
                                {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                            </select>
                            <button type="submit" className="btn-success">Add</button>
                        </form>
                    </div>
                )}

                {activeTab === 'records' && (
                    <div className="card form-card compact">
                        <h4>New Record</h4>
                        <form onSubmit={handleAddRecord} className="inline-form">
                            <input placeholder="Diagnosis" value={newRecord.diagnosis} onChange={e => setNewRecord({ ...newRecord, diagnosis: e.target.value })} required />
                            <input placeholder="Treatment" value={newRecord.treatment} onChange={e => setNewRecord({ ...newRecord, treatment: e.target.value })} required />
                            <input placeholder="Patient ID" value={newRecord.patientId} onChange={e => setNewRecord({ ...newRecord, patientId: e.target.value })} required style={{ width: '80px' }} />
                            <button type="submit" className="btn-success">Add</button>
                        </form>
                    </div>
                )}

                {/* Data Grid */}
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
                            <div className="tag">{p.department ? p.department.name : 'No Dept'}</div>
                            <div className="id-badge">ID: {p.id}</div>
                        </div>
                    ))}

                    {activeTab === 'records' && data.map(r => (
                        <div key={r.id} className="card record-card">
                            <h3>{r.diagnosis}</h3>
                            <p><strong>Patient:</strong> {r.patient ? r.patient.name : 'Unknown'}</p>
                            <p><strong>Treatment:</strong> {r.treatment}</p>
                            <div className="id-badge">ID: {r.id}</div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default HospitalDashboard;
