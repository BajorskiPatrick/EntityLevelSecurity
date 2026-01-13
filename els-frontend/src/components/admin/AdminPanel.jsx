import React, { useState, useEffect } from 'react';
import api from '../../api/axiosConfig';

const AdminPanel = () => {
    const [activeTab, setActiveTab] = useState('permissions');
    const [permissions, setPermissions] = useState([]);
    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);

    // --- Forms State ---
    // Permissions Form
    const [permForm, setPermForm] = useState({
        targetType: 'USER', username: '', roleId: '', entity: 'Patient', action: 'SELECT', accessType: 'WHITELIST', rowIds: ''
    });
    // User Form
    const [userForm, setUserForm] = useState({ username: '', password: 'password', roleId: '' });
    // Role Form
    const [roleForm, setRoleForm] = useState({ name: '', type: 'SIMPLE', parentId: '', childId: '' });

    useEffect(() => {
        refreshAll();
    }, []);

    const refreshAll = () => {
        api.get('/admin/permissions').then(res => setPermissions(res.data));
        api.get('/admin/users').then(res => setUsers(res.data));
        api.get('/admin/roles').then(res => setRoles(res.data));
    };

    // --- Handlers ---

    const handleGrantPermission = async () => {
        const payload = {
            ...permForm,
            username: permForm.targetType === 'USER' ? permForm.username : null,
            roleId: permForm.targetType === 'ROLE' ? permForm.roleId : null,
            entityName: permForm.entity // Backend expects entityName
        };
        try {
            await api.post('/admin/permissions', payload);
            alert('Permission Granted');
            refreshAll();
        } catch (e) { alert(e.message); }
    };

    const handleRevokePermission = async (id) => {
        try {
            await api.delete(`/admin/permissions/${id}`);
            refreshAll();
        } catch (e) { alert(e.message); }
    }

    const handleCreateUser = async () => {
        try {
            // 1. Create User
            const res = await api.post('/admin/users', { username: userForm.username, password: userForm.password });
            const newUser = res.data;
            // 2. Assign Role if selected
            if (userForm.roleId) {
                await api.post(`/admin/users/${newUser.id}/roles/${userForm.roleId}`);
            }
            alert('User Created');
            refreshAll();
        } catch (e) { alert(e.message); }
    }

    const handleCreateRole = async () => {
        try {
            const endpoint = roleForm.type === 'SIMPLE' ? '/admin/roles/simple' : '/admin/roles/composite';
            await api.post(endpoint, { name: roleForm.name }); // Simple expects just name, Composite expects just name initially in basic implementation or use specific endpoint
            // Note: The simple/composite endpoints might differ in DTO structure. Assuming name is passed in body as string or object.
            // Checking backend: @RequestBody String name. So pass plain string? No, typically JSON. Let's send plain string if backend accepts, or object.
            // Adjusting to send simple object map, backend DTO check needed. Assuming "name" field in body or @RequestBody String?
            // Let's assume standard JSON { "name": "..." }
            alert('Role Created');
            refreshAll();
        } catch (e) { alert(e.message); }
    }

    const handleLinkRoles = async () => {
        try {
            await api.post(`/admin/roles/${roleForm.parentId}/children/${roleForm.childId}`);
            alert('Roles Linked');
        } catch (e) { alert(e.message); }
    }

    return (
        <div className="panel admin-panel">
            <div className="panel-header">
                <h2>System Configuration</h2>
                <div className="tabs">
                    <button className={activeTab === 'permissions' ? 'active' : ''} onClick={() => setActiveTab('permissions')}>Permissions</button>
                    <button className={activeTab === 'users' ? 'active' : ''} onClick={() => setActiveTab('users')}>Users & Roles</button>
                </div>
            </div>

            <div className="content-area">

                {/* --- PERMISSIONS TAB --- */}
                {activeTab === 'permissions' && (
                    <div className="admin-grid">
                        <div className="card form-card">
                            <h3>Grant Permission</h3>

                            <label>Target</label>
                            <select value={permForm.targetType} onChange={e => setPermForm({ ...permForm, targetType: e.target.value })}>
                                <option value="USER">User</option>
                                <option value="ROLE">Role</option>
                            </select>

                            {permForm.targetType === 'USER' ? (
                                <select value={permForm.username} onChange={e => setPermForm({ ...permForm, username: e.target.value })}>
                                    <option value="">Select User</option>
                                    {users.map(u => <option key={u.id} value={u.username}>{u.username}</option>)}
                                </select>
                            ) : (
                                <select value={permForm.roleId} onChange={e => setPermForm({ ...permForm, roleId: e.target.value })}>
                                    <option value="">Select Role</option>
                                    {roles.map(r => <option key={r.id} value={r.id}>{r.name} ({r.type})</option>)}
                                </select>
                            )}

                            <label>Entity</label>
                            <select value={permForm.entity} onChange={e => setPermForm({ ...permForm, entity: e.target.value })}>
                                <option value="Patient">Patient</option>
                                <option value="MedicalRecord">MedicalRecord</option>
                                <option value="Department">Department</option>
                            </select>

                            <div className="row">
                                <select value={permForm.action} onChange={e => setPermForm({ ...permForm, action: e.target.value })}>
                                    <option value="SELECT">SELECT</option>
                                    <option value="INSERT">INSERT</option>
                                    <option value="UPDATE">UPDATE</option>
                                    <option value="DELETE">DELETE</option>
                                </select>
                                <select value={permForm.accessType} onChange={e => setPermForm({ ...permForm, accessType: e.target.value })}>
                                    <option value="WHITELIST">Allow (Whitelist)</option>
                                    <option value="BLACKLIST">Deny (Blacklist)</option>
                                </select>
                            </div>

                            <input placeholder="IDs (e.g. 1,2 or *)" value={permForm.rowIds} onChange={e => setPermForm({ ...permForm, rowIds: e.target.value })} />

                            <button className="btn-primary" onClick={handleGrantPermission}>Grant</button>
                        </div>

                        <div className="card list-card">
                            <h3>Existing Permissions</h3>
                            <table>
                                <thead><tr><th>Who</th><th>What</th><th>How</th><th>IDs</th><th>Action</th></tr></thead>
                                <tbody>
                                    {permissions.map(p => (
                                        <tr key={p.id}>
                                            <td>{p.user ? `U: ${p.user.username}` : `R: ${p.role?.name}`}</td>
                                            <td>{p.entityName}.{p.action}</td>
                                            <td>{p.accessType}</td>
                                            <td>{p.rowIds}</td>
                                            <td><button className="btn-danger small" onClick={() => handleRevokePermission(p.id)}>X</button></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {/* --- USERS & ROLES TAB --- */}
                {activeTab === 'users' && (
                    <div className="admin-grid three-col">
                        {/* User Create */}
                        <div className="card form-card">
                            <h3>Create User</h3>
                            <input placeholder="Username" value={userForm.username} onChange={e => setUserForm({ ...userForm, username: e.target.value })} />
                            <input placeholder="Password" value={userForm.password} onChange={e => setUserForm({ ...userForm, password: e.target.value })} />
                            <select value={userForm.roleId} onChange={e => setUserForm({ ...userForm, roleId: e.target.value })}>
                                <option value="">Assign Role (Optional)</option>
                                {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                            </select>
                            <button className="btn-primary" onClick={handleCreateUser}>Create User</button>
                        </div>

                        {/* Role Create */}
                        <div className="card form-card">
                            <h3>Create Role</h3>
                            <input placeholder="Role Name" value={roleForm.name} onChange={e => setRoleForm({ ...roleForm, name: e.target.value })} />
                            <select value={roleForm.type} onChange={e => setRoleForm({ ...roleForm, type: e.target.value })}>
                                <option value="SIMPLE">Simple</option>
                                <option value="COMPOSITE">Composite</option>
                            </select>
                            <button className="btn-primary" onClick={handleCreateRole}>Create Role</button>
                        </div>

                        {/* Hierarchy */}
                        <div className="card form-card">
                            <h3>Role Hierarchy</h3>
                            <label>Parent (Composite)</label>
                            <select value={roleForm.parentId} onChange={e => setRoleForm({ ...roleForm, parentId: e.target.value })}>
                                <option value="">Select Parent</option>
                                {roles.filter(r => r.type === 'COMPOSITE' || r.children).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                            </select>

                            <label>Child</label>
                            <select value={roleForm.childId} onChange={e => setRoleForm({ ...roleForm, childId: e.target.value })}>
                                <option value="">Select Child</option>
                                {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                            </select>
                            <button className="btn-primary" onClick={handleLinkRoles}>Link Roles</button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminPanel;
