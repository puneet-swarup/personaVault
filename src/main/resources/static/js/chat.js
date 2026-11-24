/**
 * PersonaVault — Chat page.
 * Handles streaming Q&A via Server-Sent Events (SSE).
 *
 * Protocol:
 *   Client → POST /api/chat/stream (JSON: { question })
 *   Server → SSE stream:
 *     event:citation  data:{"tier":"WIKI","slug":"...","title":"..."}   (once, first)
 *     data: <token>                                                     (many)
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
    botEl.querySelector('.message-text').textContent = 'Searching your documents…';
    botEl.classList.add('thinking');

    try {
        const res = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                question,
                category: document.getElementById('chat-category').value || null
            })
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
                const parsed = parseFrame(frame);
                if (parsed.event === 'citation') {
                    showCitation(botEl, parsed.data);
                    continue;
                }
                if (parsed.data) {
                    if (answer === '') {
                        // First token received — remove thinking indicator
                        botEl.classList.remove('thinking');
                        botEl.querySelector('.message-text').textContent = '';
                    }
                    answer += parsed.data;
                    botEl.querySelector('.message-text').textContent = answer;
                    log.scrollTop = log.scrollHeight;
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
/**
 * Parses one SSE frame into its event name and data payload.
 *
 * Handles unnamed frames ("data: token") and named ones, which is how the
 * server sends provenance: "event:citation" followed by a JSON "data:" line.
 *
 * @param {string} frame - one raw SSE frame, without the trailing blank line
 * @returns {{event: string, data: string}}
 */
function parseFrame(frame) {
    let event = 'message';
    const data = [];
    for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) {
            event = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
            data.push(line.slice(5).replace(/^ /, ''));
        }
    }
    return { event, data: data.join('\n') };
}

/**
 * Renders which tier answered, above the answer text.
 *
 * @param {HTMLElement} botEl - the message bubble being filled
 * @param {string} payload - JSON citation frame from the server
 */
function showCitation(botEl, payload) {
    let citation;
    try {
        citation = JSON.parse(payload);
    } catch (e) {
        return;
    }
    const chip = document.createElement('div');
    chip.className = 'citation-chip';
    if (citation.tier === 'RAG') {
        chip.textContent = 'Answered from your documents';
    } else {
        chip.textContent = 'Answered from wiki page: ' + (citation.title || citation.slug);
    }
    botEl.insertBefore(chip, botEl.firstChild);
}
