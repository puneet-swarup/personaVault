/**
 * PersonaVault — Chat page.
 * Handles streaming Q&A via Server-Sent Events (SSE).
 *
 * Protocol:
 *   Client → POST /api/chat/stream (JSON: { question })
 *   Server → SSE stream: "data: <token>\n\n" frames
 *
 * We use fetch() + ReadableStream (not EventSource) because
 * EventSource only supports GET requests.
 */

const form = document.getElementById('chat-form');
const log = document.getElementById('chat-log');
const input = document.querySelector('[name=question]');
const sendBtn = document.getElementById('send-btn');

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const question = input.value.trim();
    if (!question) return;

    // Add user message to log
    appendMessage('user', question);
    input.value = '';
    sendBtn.disabled = true;

    // Prepare bot message container (will be filled by stream)
    const botEl = appendMessage('bot', '');

    try {
        const res = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        if (!res.ok) {
            const err = await res.json();
            botEl.querySelector('.message-text').textContent = `Error: ${err.message}`;
            return;
        }

        // Read the SSE stream frame by frame
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let sseBuffer = ''; // Holds incomplete SSE frame across reads
        let answer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            sseBuffer += decoder.decode(value, { stream: true });

            // SSE frames are separated by double newline: "data: text\n\n"
            const frames = sseBuffer.split('\n\n');
            sseBuffer = frames.pop(); // Last element is incomplete (or empty)

            for (const frame of frames) {
                // Strip "data: " prefix from each SSE frame
                const data = frame.replace(/^data:\s*/, '').trim();
                if (data) {
                    answer += data;
                    botEl.querySelector('.message-text').textContent = answer;
                    log.scrollTop = log.scrollHeight; // Auto-scroll
                }
            }
        }
    } catch (err) {
        botEl.querySelector('.message-text').textContent = `Connection error: ${err.message}`;
    } finally {
        sendBtn.disabled = false;
        input.focus();
    }
});

/**
 * Appends a message bubble to the chat log.
 *
 * @param {'user'|'bot'} role - Who sent the message
 * @param {string} text - Initial text (empty for bot, filled by stream)
 * @returns {HTMLElement} The message container element
 */
function appendMessage(role, text) {
    const div = document.createElement('div');
    div.className = `message ${role}`;
    const span = document.createElement('span');
    span.className = 'message-text';
    span.textContent = text;
    div.appendChild(span);
    log.appendChild(div);
    log.scrollTop = log.scrollHeight;
    return div;
}