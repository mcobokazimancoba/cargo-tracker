const BASE_URL    = '/Cargo_Tracker_System/api/auth';
const LOGIN_PAGE  = '/Cargo_Tracker_System/index.html';
const FORGOT_PAGE = '/Cargo_Tracker_System/pages/forgot-password.html';

const form          = document.getElementById('reset-form');
const alertBox      = document.getElementById('reset-alert');
const newPasswordEl = document.getElementById('newPassword');
const confirmEl     = document.getElementById('confirmPassword');

const token = new URLSearchParams(window.location.search).get('token');

if (!token) {
    showAlert(
        'No reset token found. Please request a new password reset link.',
        'error',
        FORGOT_PAGE,
        'Request new link'
    );
    form.style.display = 'none';
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAlert();

    const newPassword     = newPasswordEl.value;
    const confirmPassword = confirmEl.value;

    if (newPassword !== confirmPassword) {
        showAlert('Passwords do not match.', 'error');
        return;
    }
    if (newPassword.length < 8) {
        showAlert('Password must be at least 8 characters.', 'error');
        return;
    }

    try {
        const res = await fetch(`${BASE_URL}/reset-password`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ token, newPassword }),
        });
        const data = await res.json();
        if (res.ok) {
            showAlert(data.message, 'success');
            form.style.display = 'none';
            setTimeout(() => { window.location.href = LOGIN_PAGE; }, 2500);
        } else {
            showAlert(
                data.message ?? 'This link is invalid or has expired.',
                'error',
                FORGOT_PAGE,
                'Request a new link'
            );
            form.style.display = 'none';
        }
    } catch {
        showAlert('Network error — please check your connection and try again.', 'error');
    }
});

function showAlert(message, type, linkHref, linkText) {
    let html = `<span>${message}</span>`;
    if (linkHref) {
        html += ` <a href="${linkHref}">${linkText}</a>`;
    }
    alertBox.innerHTML = html;
    alertBox.className = `alert alert-${type}`;
}

function clearAlert() {
    alertBox.innerHTML = '';
    alertBox.className = '';
}