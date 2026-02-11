import React, { useState, useEffect } from 'react';
import api from '../../api/axiosConfig';

const AdminPanel = ({ user }) => {
    const [activeTab, setActiveTab] = useState('permissions');
    const [groupedPermissions, setGroupedPermissions] = useState([]);
    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);
    const [toast, setToast] = useState(null);

    const isAdmin = user?.roles?.some(r => r.name === 'ADMIN') || user?.username === 'admin';

    // --- Forms State ---
    const [permForm, setPermForm] = useState({
        targetType: 'USER', username: '', roleId: '', entity: 'Patient', action: 'SELECT', accessType: 'WHITELIST', rowIds: ''
    });
    const [idsError, setIdsError] = useState('');
    const [userForm, setUserForm] = useState({ username: '', password: 'password', roleId: '' });
    const [roleForm, setRoleForm] = useState({ name: '', type: 'SIMPLE', parentId: '', childId: '' });

    useEffect(() => {
        refreshAll();
    }, []);

    const refreshAll = () => {
        api.get('/admin/permissions/grouped').then(res => setGroupedPermissions(res.data)).catch(() => { });
        api.get('/admin/users').then(res => setUsers(res.data)).catch(() => { });
        api.get('/admin/roles').then(res => setRoles(res.data)).catch(() => { });
    };

    const showToast = (msg, type = 'success') => {
        setToast({ msg, type });
        setTimeout(() => setToast(null), 3000);
    };

    // --- Validation ---
    const validateRowIds = (value) => {
        if (!value || value.trim() === '') return ''; // empty is OK (INSERT)
        const trimmed = value.trim();
        if (trimmed === '*') return '';

        const parts = trimmed.split(',');
        for (const raw of parts) {
            const part = raw.trim();
            if (part === '') continue;
            if (part === '*') continue;
            if (part.includes('-')) {
                const bounds = part.split('-');
                if (bounds.length !== 2 || bounds[0].trim() === '' || bounds[1].trim() === '') {
                    return `Invalid range: "${part}"`;
                }
                if (isNaN(bounds[0].trim()) || isNaN(bounds[1].trim())) {
                    return `Non-numeric range: "${part}"`;
                }
            } else {
                if (isNaN(part)) {
                    return `Invalid ID: "${part}" — must be a number`;
                }
            }
        }
        return '';
    };

    const handleRowIdsChange = (value) => {
        setPermForm({ ...permForm, rowIds: value });
        setIdsError(validateRowIds(value));
    };

    // --- Handlers ---

    const handleGrantPermission = async () => {
        const payload = {
            ...permForm,
            username: permForm.targetType === 'USER' ? permForm.username : null,
            roleId: permForm.targetType === 'ROLE' ? permForm.roleId : null,
            entityName: permForm.entity
        };
        try {
            await api.post('/admin/permissions', payload);
            showToast('Permission granted');
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    const handleRevokePermission = async (permissionIds) => {
        if (!confirm('Delete this entire permission rule?')) return;
        try {
            for (const id of permissionIds) {
                await api.delete(`/admin/permissions/${id}`);
            }
            showToast('Permission revoked');
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    const handleRemoveId = async (permissionIds, idToRemove) => {
        try {
            // Try removing from the first permission that has this ID
            for (const pid of permissionIds) {
                await api.delete(`/admin/permissions/${pid}/ids`, {
                    data: { idsToRemove: idToRemove }
                });
            }
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    const handleCreateUser = async () => {
        try {
            const res = await api.post('/admin/users', { username: userForm.username, password: userForm.password });
            const newUser = res.data;
            if (userForm.roleId) {
                await api.post(`/admin/users/${newUser.id}/roles/${userForm.roleId}`);
            }
            showToast('User created');
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    const handleCreateRole = async () => {
        try {
            const endpoint = roleForm.type === 'SIMPLE' ? '/admin/roles/simple' : '/admin/roles/composite';
            await api.post(`${endpoint}?name=${encodeURIComponent(roleForm.name)}`);
            showToast('Role created');
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    const handleLinkRoles = async () => {
        try {
            await api.post(`/admin/roles/${roleForm.parentId}/children/${roleForm.childId}`);
            showToast('Roles linked');
            refreshAll();
        } catch (e) {
            showToast(e.response?.data?.message || e.message, 'error');
        }
    };

    // --- ID Chips Renderer ---
    const renderIdChips = (group) => {
        const { ids, permissionIds } = group;
        if (!ids || ids.length === 0) {
            return <span className="id-chip id-chip-empty">—</span>;
        }
        if (ids.length === 1 && ids[0] === '*') {
            return <span className="id-chip id-chip-all">ALL ✱</span>;
        }
        return (
            <div className="id-chips-container">
                {ids.map(id => (
                    <span key={id} className="id-chip">
                        {id}
                        {isAdmin && (
                            <button
                                className="id-chip-remove"
                                onClick={() => handleRemoveId(permissionIds, id)}
                                title={`Remove ID ${id}`}
                            >×</button>
                        )}
                    </span>
                ))}
            </div>
        );
    };

    return (
        <div className="panel admin-panel">
            {toast && (
                <div className={`toast toast-${toast.type}`}>
                    <span>{toast.msg}</span>
                    <button className="toast-close" onClick={() => setToast(null)}>×</button>
                </div>
            )}

            <div className="panel-header">
                <h2>System Configuration</h2>
                <div className="tabs">
                    <button className={activeTab === 'permissions' ? 'active' : ''} onClick={() => setActiveTab('permissions')}>Permissions</button>
                    {isAdmin && (
                        <button className={activeTab === 'users' ? 'active' : ''} onClick={() => setActiveTab('users')}>Users & Roles</button>
                    )}
                </div>
            </div>

            <div className="content-area">

                {/* --- PERMISSIONS TAB --- */}
                {activeTab === 'permissions' && (
                    <div className={isAdmin ? "admin-grid" : "admin-grid-single"}>
                        {isAdmin && (
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

                                <input
                                    placeholder="IDs: 1,2,3 or 1-10 or *"
                                    value={permForm.rowIds}
                                    onChange={e => handleRowIdsChange(e.target.value)}
                                    className={idsError ? 'input-error' : ''}
                                />
                                {idsError ? (
                                    <span className="helper-text helper-error">{idsError}</span>
                                ) : (
                                    <span className="helper-text">
                                        Single: <code>1,2,3</code> · Range: <code>1-100</code> · All: <code>*</code> · Mix: <code>1,3-7,10</code> · Empty for INSERT
                                    </span>
                                )}

                                <button className="btn-primary" onClick={handleGrantPermission} disabled={!!idsError}>Grant</button>
                            </div>
                        )}

                        <div className="card list-card" style={{ gridColumn: isAdmin ? 'auto' : '1 / -1' }}>
                            <h3>Existing Permissions</h3>
                            <table>
                                <thead><tr><th>Who</th><th>What</th><th>How</th><th>IDs</th>{isAdmin && <th>Action</th>}</tr></thead>
                                <tbody>
                                    {groupedPermissions.map((g, idx) => (
                                        <tr key={idx}>
                                            <td>{g.who}</td>
                                            <td>{g.entityName}.{g.action}</td>
                                            <td>
                                                <span className={`access-badge ${g.accessType}`}>{g.accessType}</span>
                                            </td>
                                            <td>{renderIdChips(g)}</td>
                                            {isAdmin && (
                                                <td>
                                                    <button className="btn-danger small" onClick={() => handleRevokePermission(g.permissionIds)}>✕</button>
                                                </td>
                                            )}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {/* --- USERS & ROLES TAB --- */}
                {activeTab === 'users' && isAdmin && (
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

                        {/* Hierarchy Link */}
                        <div className="card form-card">
                            <h3>Link Roles</h3>
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

                        {/* Role Visualizer */}
                        <div className="card list-card" style={{ gridColumn: '1 / -1' }}>
                            <h3>Role Hierarchy & Structure</h3>
                            <div className="data-grid">
                                {roles.map(r => (
                                    <div key={r.id} className="card compact">
                                        <h4>{r.name} <span className="tag">{r.type}</span></h4>
                                        {r.children && r.children.length > 0 ? (
                                            <ul>
                                                {r.children.map(c => <li key={c.id}>Includes: {c.name}</li>)}
                                            </ul>
                                        ) : <p><i>No sub-roles</i></p>}
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AdminPanel;
