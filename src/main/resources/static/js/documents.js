/**
 * PersonaVault — Documents page.
 * Handles drag-drop upload and document list rendering.
 * No external dependencies.
 */

// --- DOM References ---
const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const uploadProgress = document.getElementById('upload-progress');
const docTableBody = document.querySelector('#doc-table tbody');

// --- Drag & Drop Setup ---

// Clicking the drop zone opens the file browser
dropZone.addEventListener('click', () => fileInput.click());

// Highlight when files are dragged over the zone
dropZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZone.classList.add('drag-over');
});

dropZone.addEventListener('dragleave', () => {
    dropZone.classList.remove('drag-over');
});

// Handle dropped files
dropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropZone.classList.remove('drag-over');
    const files = Array.from(e.dataTransfer.files);
    files.forEach(uploadFile);
});

// Handle file selection via click
fileInput.addEventListener('change', () => {
    const files = Array.from(fileInput.files);
    files.forEach(uploadFile);
    fileInput.value = ''; // Reset so same file can be re-selected
});

// --- Upload Logic ---

/**
 * Uploads a single file to the ingestion endpoint.
 * Shows per-file progress in the upload progress area.
 *
 * @param {File} file - The file to upload
 */
async function uploadFile(file) {
const category = document.getElementById('category-select').value;
    const row = document.createElement('div');
    row.className = 'progress-item';
    row.textContent = `⏳ Uploading: ${file.name}...`;
    uploadProgress.appendChild(row);

    try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('category', category);

        const res = await fetch('/api/documents/upload', {
            method: 'POST',
            body: formData
            // Note: do NOT set Content-Type header.
            // Browser sets it automatically with the correct boundary for FormData.
        });

        if (res.ok) {
            const data = await res.json();
            row.textContent = `✅ ${data.fileName} — ${data.chunkCount} chunks`;
            row.classList.add('success');
            loadDocuments(); // Refresh the table
        } else {
            const err = await res.json();
            row.textContent = `❌ ${file.name}: ${err.message}`;
            row.classList.add('error');
        }
    } catch (e) {
        row.textContent = `❌ ${file.name}: ${e.message}`;
        row.classList.add('error');
    }
}

// --- Document List ---

/**
 * Fetches the document list from the API and renders it in the table.
 * Called on page load and after each successful upload.
 */
async function loadDocuments() {
    try {
        const res = await fetch('/api/documents');
        if (!res.ok) return;
        const docs = await res.json();

        docTableBody.innerHTML = '';
        for (const doc of docs) {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td style="font-weight: 500;">${doc.fileName}</td>
                <td><span class="category-badge ${doc.category}">${formatCategory(doc.category)}</span></td>
                <td>${formatSize(doc.fileSizeBytes)}</td>
                <td>${doc.chunkCount}</td>
                <td>${new Date(doc.ingestedAt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</td>
                <td style="text-align: right;"><button class="btn-delete" data-id="${doc.id}" title="Delete document">🗑️</button></td>
            `;
            docTableBody.appendChild(tr);
        }
    } catch (e) {
        console.error('Failed to load documents:', e);
    }
}

docTableBody.addEventListener('click', async (e) => {
    const btn = e.target.closest('.btn-delete');
    if (!btn) {
        return;
    }
    const id = btn.dataset.id;
    if (!confirm('Delete this document? This cannot be undone.')) {
        return;
    }
    btn.disabled = true;
    btn.textContent = 'Deleting…';
    try {
        const res = await fetch(`/api/documents/${id}`, { method: 'DELETE' });
        if (res.ok) {
            loadDocuments();
        } else {
            const err = await res.json();
            alert(`Delete failed: ${err.message}`);
            btn.disabled = false;
            btn.textContent = 'Delete';
        }
    } catch (err) {
        alert(`Delete failed: ${err.message}`);
        btn.disabled = false;
        btn.textContent = 'Delete';
    }
});

/**
 * Formats a byte count into a human-readable string (KB, MB).
 * Uses metric units (1000-based) per project convention.
 *
 * @param {number} bytes - File size in bytes
 * @returns {string} Formatted size (e.g., "2.4 MB")
 */
function formatSize(bytes) {
    if (bytes < 1000) return bytes + ' B';
    if (bytes < 1_000_000) return (bytes / 1000).toFixed(1) + ' KB';
    return (bytes / 1_000_000).toFixed(1) + ' MB';
}

/**
 * Converts a category enum value to a display-friendly label.
 * e.g., "HEALTH_INSURANCE" → "Health Insurance"
 */
function formatCategory(cat) {
    if (!cat) {
        return 'Other';
    }
    return cat.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

// Initial load
loadDocuments();