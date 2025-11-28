// Color palette for participants
const participantColors = [
    '#E3F2FD', // Light Blue
    '#F3E5F5', // Light Purple
    '#E8F5E9', // Light Green
    '#FFF3E0', // Light Orange
    '#FCE4EC', // Light Pink
    '#E0F2F1', // Light Teal
    '#FFF9C4', // Light Yellow
    '#F1F8E9', // Light Lime
    '#E1F5FE', // Light Cyan
    '#FBE9E7', // Light Deep Orange
    '#EDE7F6', // Light Deep Purple
    '#E8EAF6', // Light Indigo
];

const lineColors = [
    '#2196F3', // Blue
    '#9C27B0', // Purple
    '#4CAF50', // Green
    '#FF9800', // Orange
    '#E91E63', // Pink
    '#009688', // Teal
    '#FDD835', // Yellow
    '#8BC34A', // Lime
    '#00BCD4', // Cyan
    '#FF5722', // Deep Orange
    '#673AB7', // Deep Purple
    '#3F51B5', // Indigo
];

function applyParticipantColors() {
    // Wait for Mermaid to render
    setTimeout(() => {
        const svg = document.querySelector('.mermaid svg');
        if (!svg) {
            console.log('SVG not found, retrying...');
            setTimeout(applyParticipantColors, 500);
            return;
        }
        console.log('Applying colors to diagram...');

        // Get all groups that contain a rect.actor (participant boxes)
        const allRootGroups = svg.querySelectorAll('g[id^="root-"]');
        console.log(`Found ${allRootGroups.length} root groups`);

        const colorMap = new Map();

        allRootGroups.forEach((group, index) => {
            const colorIndex = index % participantColors.length;
            const bgColor = participantColors[colorIndex];
            const lineColor = lineColors[colorIndex];

            // Color the participant box
            const rect = group.querySelector('rect.actor');
            if (rect) {
                rect.style.fill = bgColor;
                rect.style.stroke = lineColor;
                rect.style.strokeWidth = '2px';
            }

            // Get participant label for mapping
            const text = group.querySelector('text.actor');
            if (text) {
                const label = text.textContent.trim();
                colorMap.set(label, { bgColor, lineColor, index });
                console.log(`Mapped participant: "${label}" to color ${colorIndex}`);
            }
        });

        // Color the lifelines - they are siblings of participant groups
        const allGroups = svg.querySelectorAll('g');
        console.log(`Found ${allGroups.length} groups`);

        let coloredCount = 0;
        let verticalCount = 0;

        allGroups.forEach(group => {
            // Look for a vertical line in this group
            const line = group.querySelector(':scope > line');
            if (!line) return;

            const x1 = parseFloat(line.getAttribute('x1'));
            const x2 = parseFloat(line.getAttribute('x2'));
            const y1 = parseFloat(line.getAttribute('y1'));
            const y2 = parseFloat(line.getAttribute('y2'));

            const xDiff = Math.abs(x1 - x2);
            const yDiff = Math.abs(y1 - y2);
            const isVertical = (xDiff === 0 || xDiff < 1) && yDiff > 50;

            if (isVertical) {
                verticalCount++;

                // Look for participant rect in a sibling or child group
                const participantGroup = group.querySelector('g[id^="root-"]');
                if (participantGroup) {
                    const rect = participantGroup.querySelector('rect.actor');
                    const text = participantGroup.querySelector('text.actor');

                    if (rect && text) {
                        const label = text.textContent.trim();
                        const colorInfo = colorMap.get(label);

                        if (colorInfo) {
                            line.style.stroke = colorInfo.lineColor;
                            line.style.strokeWidth = '2px';
                            line.classList.add('lifeline-colored');
                            line.dataset.participant = label;
                            coloredCount++;
                            console.log(`✓ Colored lifeline for: ${label}`);
                        } else {
                            console.log(`No color mapping for: ${label}`);
                        }
                    }
                }
            }
        });

        console.log(`Found ${verticalCount} vertical lines`);
        console.log(`Colored ${coloredCount} lifelines`);

        // Add click/hover functionality for lifelines
        addLifelineInteraction();
        console.log('Color application complete!');
    }, 1000);
}

function addLifelineInteraction() {
    const tooltip = document.createElement('div');
    tooltip.className = 'participant-tooltip';
    document.body.appendChild(tooltip);

    const lifelines = document.querySelectorAll('.lifeline-colored');
    console.log(`Added interaction to ${lifelines.length} lifelines`);

    lifelines.forEach(line => {
        // Show tooltip on hover
        line.addEventListener('mouseenter', (e) => {
            const participant = line.dataset.participant;
            if (participant) {
                tooltip.textContent = participant;
                tooltip.style.display = 'block';
            }
        });

        line.addEventListener('mousemove', (e) => {
            tooltip.style.left = (e.pageX + 15) + 'px';
            tooltip.style.top = (e.pageY + 15) + 'px';
        });

        line.addEventListener('mouseleave', () => {
            tooltip.style.display = 'none';
        });

        // Show alert on click
        line.addEventListener('click', (e) => {
            const participant = line.dataset.participant;
            if (participant) {
                // Create a temporary prominent tooltip
                const clickTooltip = document.createElement('div');
                clickTooltip.style.position = 'fixed';
                clickTooltip.style.left = '50%';
                clickTooltip.style.top = '20px';
                clickTooltip.style.transform = 'translateX(-50%)';
                clickTooltip.style.background = '#2c3e50';
                clickTooltip.style.color = 'white';
                clickTooltip.style.padding = '15px 30px';
                clickTooltip.style.borderRadius = '8px';
                clickTooltip.style.fontSize = '16px';
                clickTooltip.style.fontWeight = 'bold';
                clickTooltip.style.boxShadow = '0 4px 12px rgba(0,0,0,0.3)';
                clickTooltip.style.zIndex = '10001';
                clickTooltip.textContent = participant;
                document.body.appendChild(clickTooltip);

                setTimeout(() => {
                    clickTooltip.remove();
                }, 2000);
            }
        });
    });
}

// Apply colors when page loads
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', applyParticipantColors);
} else {
    applyParticipantColors();
}
