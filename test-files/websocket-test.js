/* WebSocket Test Client JavaScript */

let ws = null;

async function fetchBooking(inputId) {
    const apiUrl = document.getElementById(inputId).value.trim();
    const resultBox = document.getElementById('apiResult');

    if (!apiUrl) {
        resultBox.textContent = 'Please enter an API URL';
        return;
    }

    resultBox.textContent = 'Fetching booking data...';

    try {
        const startTime = performance.now();
        const response = await fetch(apiUrl);
        const endTime = performance.now();
        const responseTime = (endTime - startTime).toFixed(2);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();

        // Format the result with response time
        let resultText = `Response Time: ${responseTime}ms\n`;
        resultText += `Status: ${response.status} ${response.statusText}\n`;
        resultText += `URL: ${apiUrl}\n`;
        resultText += '\n' + JSON.stringify(data, null, 2);

        resultBox.textContent = resultText;

        // Also log to WebSocket messages if connected
        if (ws && ws.readyState === WebSocket.OPEN) {
            addMessage(`API Response received from: ${apiUrl} (${responseTime}ms)`, 'success');
        }
    } catch (error) {
        resultBox.textContent = `Error: ${error.message}`;
        addMessage('API request failed: ' + error.message, 'error');
    }
}

function connect() {
    const url = document.getElementById('wsUrl').value;

    try {
        addMessage('Connecting to ' + url + '...', 'info');
        ws = new WebSocket(url);

        ws.onopen = function () {
            addMessage('Connected successfully!', 'success');
            updateStatus(true);
        };

        ws.onmessage = function (event) {
            try {
                const data = JSON.parse(event.data);
                addMessage('Received: ' + JSON.stringify(data, null, 2), 'message');
            } catch (e) {
                addMessage('Received: ' + event.data, 'message');
            }
        };

        ws.onerror = function (error) {
            addMessage('WebSocket error occurred', 'error');
            console.error('WebSocket error:', error);
        };

        ws.onclose = function () {
            addMessage('Connection closed', 'info');
            updateStatus(false);
        };

    } catch (error) {
        addMessage('Failed to connect: ' + error.message, 'error');
    }
}

function disconnect() {
    if (ws) {
        ws.close();
        ws = null;
        addMessage('Disconnected by user', 'info');
    }
}

function updateStatus(connected) {
    const statusDiv = document.getElementById('status');
    const connectBtn = document.getElementById('connectBtn');
    const disconnectBtn = document.getElementById('disconnectBtn');

    if (connected) {
        statusDiv.textContent = 'Connected';
        statusDiv.className = 'status connected';
        connectBtn.disabled = true;
        disconnectBtn.disabled = false;
    } else {
        statusDiv.textContent = 'Disconnected';
        statusDiv.className = 'status disconnected';
        connectBtn.disabled = false;
        disconnectBtn.disabled = true;
    }
}

function addMessage(text, type) {
    const messagesDiv = document.getElementById('messages');
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message' + (type === 'error' ? ' error' : '');

    const timestamp = new Date().toLocaleTimeString();
    messageDiv.innerHTML = `<span class="timestamp">[${timestamp}]</span><br/>${text}`;

    messagesDiv.appendChild(messageDiv);
    messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function clearMessages() {
    document.getElementById('messages').innerHTML = '';
    addMessage('Messages cleared', 'info');
}
