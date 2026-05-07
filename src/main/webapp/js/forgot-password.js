/**
 * forgot-password.js
 *
 * Handles the "Forgot Password" form.
 * POSTs the email to POST /api/auth/forgot-password and shows feedback.
 *
 * The server always returns 200 regardless of whether the address is
 * registered, so we simply display the static success message from the response.
 */

const BASE_URL = '/api/auth';

const form  = document.getElementById('forgot-form');
const alert = document.getElementById('forgot-alert');

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAlert();

    const email = document.getElementById('email').value.trim();

    try {
        const res = await fetch(`${BASE_URL}/forgot-password`, {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ email }),
        });

        const data = await res.json();

        if (res.ok) {
            showAlert(data.message, 'success');
            form.reset();
        } else {
            // Validation error (400) — e.g. blank or invalid email format.
            showAlert(data.message ?? 'Something went wrong. Please try again.', 'error');
        }
    } catch {
        showAlert('Network error — please check your connection and try again.', 'error');
    }
});

// ── Helpers ───────────────────────────────────────────────────────────────────

function showAlert(message, type) {
    alert.textContent  = message;
    alert.className    = `alert alert-${type}`;   // style via styles.css
}

function clearAlert() {
    alert.textContent = '';
    alert.className   = '';
}