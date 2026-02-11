import React, { useState, useEffect } from 'react';
import api from '../../api/axiosConfig';

const HospitalDashboard = ({ user }) => {
    const [activeTab, setActiveTab] = useState('patients');
    const [data, setData] = useState([]);
    const [departments, setDepartments] = useState([]);
    const [patients, setPatients] = useState([]); // [NEW] State for patients dropdown
    const [error, setError] = useState(null);
    const [toast, setToast] = useState(null);

    // Forms State
    const [newDept, setNewDept] = useState({ name: '' });
    const [newPatient, setNewPatient] = useState({ name: '', departmentId: '' });
    const [newRecord, setNewRecord] = useState({ diagnosis: '', treatment: '', patientId: '' });

    // Edit State
    const [editingDept, setEditingDept] = useState(null);
    const [editingPatient, setEditingPatient] = useState(null);
    const [editingRecord, setEditingRecord] = useState(null);

    useEffect(() => {
        fetchData();
        fetchDepartments();
        fetchPatients(); // [NEW] Fetch patients for dropdowns
    }, [activeTab]);

    const fetchDepartments = () => {
        api.get('/hospital/departments').then(res => setDepartments(res.data)).catch(() => { });
    };

    const fetchPatients = () => {
        api.get('/hospital/patients').then(res => setPatients(res.data)).catch(() => { });
    };

    const fetchData = async () => {
        setError(null);
        try {
            const endpoints = {
                departments: '/hospital/departments',
                patients: '/hospital/patients',
                records: '/hospital/records'
            };
            const res = await api.get(endpoints[activeTab]);
            setData(res.data);
        } catch (err) {
            setData([]);
            setError(err.response?.status === 403
                ? `Access Denied – you don't have SELECT permission for this entity.`
                : "Error fetching data");
        }
    };

    const showToast = (message, type = 'error') => {
        setToast({ message, type });
        setTimeout(() => setToast(null), 4000);
    };

    const handleError = (err, operation) => {
        const status = err.response?.status;
        const msg = err.response?.data?.message;
        if (status === 403) {
            showToast(`🔒 Access Denied: ${msg || `No ${operation} permission.`}`);
        } else if (status === 409) {
            showToast(`⚠️ Conflict: ${msg || 'Dependent records exist.'}`);
        } else {
            showToast(`❌ ${operation} failed: ${msg || err.message}`);
        }
    };

    // ===========================================
    // DEPARTMENT CRUD
    // ===========================================
    const handleAddDept = async (e) => {
        e.preventDefault();
        try {
            await api.post('/hospital/departments', { name: newDept.name });
            setNewDept({ name: '' });
            fetchData();
            fetchDepartments();
        } catch (err) { handleError(err, 'INSERT Department'); }
    };

    const handleUpdateDept = async (e) => {
        e.preventDefault();
        try {
            await api.put(`/hospital/departments/${editingDept.id}`, { name: editingDept.name });
            setEditingDept(null);
            fetchData();
            fetchDepartments();
        } catch (err) { handleError(err, 'UPDATE Department'); }
    };

    const handleDeleteDept = async (id) => {
        if (!confirm('Delete this department? Patients in this department may be affected.')) return;
        try {
            await api.delete(`/hospital/departments/${id}`);
            fetchData();
            fetchDepartments();
        } catch (err) { handleError(err, 'DELETE Department'); }
    };

    // ===========================================
    // PATIENT CRUD
    // ===========================================
    const handleAddPatient = async (e) => {
        e.preventDefault();
        try {
            await api.post('/hospital/patients', {
                name: newPatient.name,
                department: { id: parseInt(newPatient.departmentId) }
            });
            setNewPatient({ name: '', departmentId: '' });
            fetchData();
            // Refresh patients list since a new one was added
            fetchPatients();
        } catch (err) { handleError(err, 'INSERT Patient'); }
    };

    const handleUpdatePatient = async (e) => {
        e.preventDefault();
        try {
            await api.put(`/hospital/patients/${editingPatient.id}`, {
                name: editingPatient.name,
                department: { id: parseInt(editingPatient.departmentId) }
            });
            setEditingPatient(null);
            fetchData();
            fetchPatients();
        } catch (err) { handleError(err, 'UPDATE Patient'); }
    };

    const handleDeletePatient = async (id) => {
        if (!confirm('Delete this patient and all their medical records?')) return;
        try {
            await api.delete(`/hospital/patients/${id}`);
            fetchData();
            fetchPatients();
        } catch (err) { handleError(err, 'DELETE Patient'); }
    };

    // ===========================================
    // RECORD CRUD
    // ===========================================
    const handleAddRecord = async (e) => {
        e.preventDefault();
        try {
            await api.post('/hospital/records', {
                diagnosis: newRecord.diagnosis,
                treatment: newRecord.treatment,
                patient: { id: parseInt(newRecord.patientId) }
            });
            setNewRecord({ diagnosis: '', treatment: '', patientId: '' });
            fetchData();
        } catch (err) { handleError(err, 'INSERT MedicalRecord'); }
    };

    const handleUpdateRecord = async (e) => {
        e.preventDefault();
        try {
            await api.put(`/hospital/records/${editingRecord.id}`, {
                diagnosis: editingRecord.diagnosis,
                treatment: editingRecord.treatment,
                patient: { id: parseInt(editingRecord.patientId) }
            });
            setEditingRecord(null);
            fetchData();
        } catch (err) { handleError(err, 'UPDATE MedicalRecord'); }
    };

    const handleDeleteRecord = async (id) => {
        if (!confirm('Delete this medical record?')) return;
        try {
            await api.delete(`/hospital/records/${id}`);
            fetchData();
        } catch (err) { handleError(err, 'DELETE MedicalRecord'); }
    };

    // ===========================================
    // RENDER
    // ===========================================
    return (
        <div className="panel operational-panel">
            {/* Toast notification */}
            {toast && (
                <div className={`toast toast-${toast.type}`}>
                    <span>{toast.message}</span>
                    <button className="toast-close" onClick={() => setToast(null)}>×</button>
                </div>
            )}

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

                {/* ========== DEPARTMENTS TAB ========== */}
                {activeTab === 'departments' && (
                    <>
                        <div className="card form-card compact">
                            <h4>Add Department</h4>
                            <form onSubmit={handleAddDept} className="form-row">
                                <div className="form-field" style={{ flex: 1 }}>
                                    <label>Name</label>
                                    <input type="text" placeholder="e.g. Radiology" value={newDept.name}
                                        onChange={e => setNewDept({ name: e.target.value })} required />
                                </div>
                                <button type="submit" className="btn-success">Add</button>
                            </form>
                        </div>

                        {editingDept && (
                            <div className="card form-card compact edit-form">
                                <h4>Edit Department (ID: {editingDept.id})</h4>
                                <form onSubmit={handleUpdateDept} className="form-row">
                                    <div className="form-field" style={{ flex: 1 }}>
                                        <label>Name</label>
                                        <input type="text" value={editingDept.name}
                                            onChange={e => setEditingDept({ ...editingDept, name: e.target.value })} required />
                                    </div>
                                    <button type="submit" className="btn-primary">Save</button>
                                    <button type="button" className="btn-secondary" onClick={() => setEditingDept(null)}>Cancel</button>
                                </form>
                            </div>
                        )}
                    </>
                )}

                {/* ========== PATIENTS TAB ========== */}
                {activeTab === 'patients' && (
                    <>
                        <div className="card form-card compact">
                            <h4>Add Patient</h4>
                            <form onSubmit={handleAddPatient} className="form-row">
                                <div className="form-field" style={{ flex: 2 }}>
                                    <label>Name</label>
                                    <input type="text" placeholder="Full name" value={newPatient.name}
                                        onChange={e => setNewPatient({ ...newPatient, name: e.target.value })} required />
                                </div>
                                <div className="form-field" style={{ flex: 1 }}>
                                    <label>Department</label>
                                    <select value={newPatient.departmentId}
                                        onChange={e => setNewPatient({ ...newPatient, departmentId: e.target.value })} required>
                                        <option value="">Select…</option>
                                        {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                    </select>
                                </div>
                                <button type="submit" className="btn-success">Add</button>
                            </form>
                        </div>

                        {editingPatient && (
                            <div className="card form-card compact edit-form">
                                <h4>Edit Patient (ID: {editingPatient.id})</h4>
                                <form onSubmit={handleUpdatePatient} className="form-row">
                                    <div className="form-field" style={{ flex: 2 }}>
                                        <label>Name</label>
                                        <input type="text" value={editingPatient.name}
                                            onChange={e => setEditingPatient({ ...editingPatient, name: e.target.value })} required />
                                    </div>
                                    <div className="form-field" style={{ flex: 1 }}>
                                        <label>Department</label>
                                        <select value={editingPatient.departmentId}
                                            onChange={e => setEditingPatient({ ...editingPatient, departmentId: e.target.value })} required>
                                            <option value="">Select…</option>
                                            {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
                                        </select>
                                    </div>
                                    <button type="submit" className="btn-primary">Save</button>
                                    <button type="button" className="btn-secondary" onClick={() => setEditingPatient(null)}>Cancel</button>
                                </form>
                            </div>
                        )}
                    </>
                )}

                {/* ========== RECORDS TAB ========== */}
                {activeTab === 'records' && (
                    <>
                        <div className="card form-card compact">
                            <h4>Add Medical Record</h4>
                            <form onSubmit={handleAddRecord} className="form-row">
                                <div className="form-field" style={{ flex: 2 }}>
                                    <label>Diagnosis</label>
                                    <input type="text" placeholder="e.g. Fractured tibia" value={newRecord.diagnosis}
                                        onChange={e => setNewRecord({ ...newRecord, diagnosis: e.target.value })} required />
                                </div>
                                <div className="form-field" style={{ flex: 2 }}>
                                    <label>Treatment</label>
                                    <input type="text" placeholder="e.g. Cast applied" value={newRecord.treatment}
                                        onChange={e => setNewRecord({ ...newRecord, treatment: e.target.value })} required />
                                </div>
                                <div className="form-field" style={{ flex: 1 }}>
                                    <label>Patient</label>
                                    <select value={newRecord.patientId}
                                        onChange={e => setNewRecord({ ...newRecord, patientId: e.target.value })} required>
                                        <option value="">Select…</option>
                                        {patients.map(p => <option key={p.id} value={p.id}>{p.name} (ID: {p.id})</option>)}
                                    </select>
                                </div>
                                <button type="submit" className="btn-success">Add</button>
                            </form>
                        </div>

                        {editingRecord && (
                            <div className="card form-card compact edit-form">
                                <h4>Edit Record (ID: {editingRecord.id})</h4>
                                <form onSubmit={handleUpdateRecord} className="form-row">
                                    <div className="form-field" style={{ flex: 2 }}>
                                        <label>Diagnosis</label>
                                        <input type="text" value={editingRecord.diagnosis}
                                            onChange={e => setEditingRecord({ ...editingRecord, diagnosis: e.target.value })} required />
                                    </div>
                                    <div className="form-field" style={{ flex: 2 }}>
                                        <label>Treatment</label>
                                        <input type="text" value={editingRecord.treatment}
                                            onChange={e => setEditingRecord({ ...editingRecord, treatment: e.target.value })} required />
                                    </div>
                                    <div className="form-field" style={{ flex: 1 }}>
                                        <label>Patient</label>
                                        <select value={editingRecord.patientId}
                                            onChange={e => setEditingRecord({ ...editingRecord, patientId: e.target.value })} required>
                                            <option value="">Select…</option>
                                            {patients.map(p => <option key={p.id} value={p.id}>{p.name} (ID: {p.id})</option>)}
                                        </select>
                                    </div>
                                    <button type="submit" className="btn-primary">Save</button>
                                    <button type="button" className="btn-secondary" onClick={() => setEditingRecord(null)}>Cancel</button>
                                </form>
                            </div>
                        )}
                    </>
                )}

                {/* ========== DATA GRID ========== */}
                <div className="data-grid">
                    {data.length === 0 && !error && <div className="alert info">No records found.</div>}

                    {activeTab === 'departments' && data.map(d => (
                        <div key={d.id} className="card">
                            <h3>{d.name}</h3>
                            <div className="id-badge">ID: {d.id}</div>
                            <div className="card-actions">
                                <button className="btn-edit small" onClick={() => setEditingDept({ id: d.id, name: d.name })}>Edit</button>
                                <button className="btn-danger small" onClick={() => handleDeleteDept(d.id)}>Delete</button>
                            </div>
                        </div>
                    ))}

                    {activeTab === 'patients' && data.map(p => (
                        <div key={p.id} className="card">
                            <h3>{p.name}</h3>
                            <div className="tag">{p.department ? p.department.name : 'No Dept'}</div>
                            <div className="id-badge">ID: {p.id}</div>
                            <div className="card-actions">
                                <button className="btn-edit small" onClick={() => setEditingPatient({
                                    id: p.id,
                                    name: p.name,
                                    departmentId: p.department?.id || ''
                                })}>Edit</button>
                                <button className="btn-danger small" onClick={() => handleDeletePatient(p.id)}>Delete</button>
                            </div>
                        </div>
                    ))}

                    {activeTab === 'records' && data.map(r => (
                        <div key={r.id} className="card record-card">
                            <h3>{r.diagnosis}</h3>
                            <p><strong>Patient:</strong> {r.patient ? r.patient.name : 'Unknown'} (ID: {r.patient?.id})</p>
                            <p><strong>Treatment:</strong> {r.treatment}</p>
                            <div className="id-badge">ID: {r.id}</div>
                            <div className="card-actions">
                                <button className="btn-edit small" onClick={() => setEditingRecord({
                                    id: r.id,
                                    diagnosis: r.diagnosis,
                                    treatment: r.treatment,
                                    patientId: r.patient?.id || ''
                                })}>Edit</button>
                                <button className="btn-danger small" onClick={() => handleDeleteRecord(r.id)}>Delete</button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default HospitalDashboard;
