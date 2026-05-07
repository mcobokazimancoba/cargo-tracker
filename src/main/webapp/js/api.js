const API_BASE  = '/Cargo_Tracker_System/api';
const TOKEN_KEY = 'cargo_tracker_token';
const USER_KEY  = 'cargo_tracker_user';

export const Auth = {
    setSession(authResponse) {
        // Normalize role to uppercase and strip any "ROLE_" prefix (Spring Security style)
        // so every comparison downstream is consistent regardless of what the backend sends.
        const role = (authResponse.role || '').toUpperCase().replace('ROLE_', '');
        localStorage.setItem(TOKEN_KEY, authResponse.token);
        localStorage.setItem(USER_KEY, JSON.stringify({
            username: authResponse.username,
            role
        }));
    },
    clearSession() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    },
    getToken()  { return localStorage.getItem(TOKEN_KEY); },
    getUser()   {
        const u = localStorage.getItem(USER_KEY);
        return u ? JSON.parse(u) : null;
    },
    isLoggedIn() { return !!this.getToken(); },
    hasRole(...roles) {
        const user = this.getUser();
        if (!user) return false;
        // Normalize both sides so "customer", "CUSTOMER", "ROLE_CUSTOMER" all match
        const userRole = (user.role || '').toUpperCase().replace('ROLE_', '');
        return roles.map(r => r.toUpperCase().replace('ROLE_', '')).includes(userRole);
    },
    requireAuth() {
        if (!this.isLoggedIn()) {
            window.location.href = '/Cargo_Tracker_System/';
        }
    },
    requireRole(...roles) {
        this.requireAuth();
        if (!this.hasRole(...roles)) {
            window.location.href = '/Cargo_Tracker_System/pages/access-denied.html';
        }
    }
};

export const Can = {
    updateShipment()  { return Auth.hasRole('ADMIN', 'OPERATOR'); },
    cancelShipment()  { return Auth.hasRole('ADMIN', 'OPERATOR'); },
    deletePermanent() { return Auth.hasRole('ADMIN'); },
    viewAllCargo()    { return Auth.hasRole('ADMIN', 'OPERATOR'); }
};

export class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.name   = 'ApiError';
        this.status = status;
    }
}

async function _fetch(path, options, requiresAuth) {
    const token = Auth.getToken();
    if (requiresAuth && !token) {
        throw new ApiError('Not logged in — please sign in again.', 401);
    }
    const headers = { 'Content-Type': 'application/json', ...options.headers };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let response;
    try {
        response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    } catch (networkError) {
        throw new ApiError('Network error — is the server running?', 0);
    }
    if (response.status === 204) return null;
    let body = null;
    try { body = await response.json(); } catch { body = null; }
    if (!response.ok) {
        const message = body?.message || body?.error || `Server error (HTTP ${response.status})`;
        console.warn(`[CargoTracker] API ${response.status} on ${path}:`, message);
        throw new ApiError(message, response.status);
    }
    return body;
}

async function request(path, options = {})      { return _fetch(path, options, true);  }
async function guestRequest(path, options = {}) { return _fetch(path, options, false); }

export const AuthApi = {
    login(credentials) {
        return guestRequest('/auth/login', { method: 'POST', body: JSON.stringify(credentials) });
    },
    register(userData) {
        return guestRequest('/auth/register', { method: 'POST', body: JSON.stringify(userData) });
    },
    forgotPassword(email) {
        return guestRequest('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
    },
    resetPassword(token, newPassword) {
        return guestRequest('/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, newPassword }) });
    }
};

export const LocationApi = {
    list(search) {
        return request(`/locations${search ? '?search=' + encodeURIComponent(search) : ''}`);
    }
};

export const CargoApi = {
    book(data) {
        return request('/cargos', { method: 'POST', body: JSON.stringify(data) });
    },
    getAll(page = 0, size = 20) {
        return request(`/cargos?page=${page}&size=${size}`);
    },
    getMyCargos(page = 0, size = 100) {
        return request(`/cargos/customer/mine?page=${page}&size=${size}`);
    },
    track(trackingNumber) {
        return guestRequest(`/cargos/${trackingNumber}`);
    },
    trackGuest(trackingNumber) {
        return guestRequest(`/cargos/${trackingNumber}`);
    },
    update(trackingNumber, data) {
        return request(`/cargos/${trackingNumber}`, { method: 'PUT', body: JSON.stringify(data) });
    },
    addEvent(trackingNumber, event) {
        return request(`/cargos/${trackingNumber}/events`, { method: 'POST', body: JSON.stringify(event) });
    },
    cancel(trackingNumber) {
        return request(`/cargos/${trackingNumber}`, { method: 'DELETE' });
    },
    deletePermanent(trackingNumber) {
        return request(`/cargos/${trackingNumber}/permanent`, { method: 'DELETE' });
    }
};

export function showAlert(containerId, message, type = 'info') {
    const box = document.getElementById(containerId);
    if (!box) return;
    const icon = type === 'success' ? '✓' : type === 'error' ? '⚠' : 'ℹ';
    box.innerHTML = `<div class="alert alert-${type}"><span>${icon}</span><span>${message}</span></div>`;
    if (type !== 'success') setTimeout(() => { box.innerHTML = ''; }, 6000);
}

export function initNav(activePage) {
    function applyNav() {
        const user = Auth.getUser();
        if (user) {
            const userEl = document.getElementById('nav-user');
            if (userEl) {
                userEl.textContent = `${user.username} (${user.role})`;
                userEl.style.display = 'inline-block';
            }
        }
        document.querySelectorAll('.nav-link').forEach(link => {
            if (link.dataset.page === activePage) link.classList.add('active');
        });
        // Hide staff-only nav links for anyone who isn't ADMIN or OPERATOR
        if (!Auth.hasRole('ADMIN', 'OPERATOR')) {
            const updateLink   = document.querySelector('[data-page="update"]');
            const allCargoLink = document.querySelector('[data-page="search"]');
            if (updateLink)   updateLink.style.display = 'none';
            if (allCargoLink) allCargoLink.style.display = 'none';
        }
        const logoutBtn = document.getElementById('btn-logout');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                Auth.clearSession();
                window.location.href = '/Cargo_Tracker_System/';
            });
        }
    }
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applyNav);
    } else {
        applyNav();
    }
}