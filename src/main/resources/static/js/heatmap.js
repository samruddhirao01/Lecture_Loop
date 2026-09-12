// Renders a smooth curve showing where doubts cluster in a lecture, similar
// to YouTube's "most replayed" graph. Shared between the teacher and student
// pages so both get the exact same visual and click-to-seek behavior.

function renderHeatmapCurve(containerId, doubts, videoDuration, onSeek) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!doubts || doubts.length === 0) {
        container.innerHTML = '<div class="empty-note" style="padding:8px;">No doubts yet - the graph will fill in as students ask questions.</div>';
        return;
    }

    const BUCKETS = 28;
    const W = 600, H = 70;

    // The x-axis must match the actual video timeline, not be guessed from
    // doubt timestamps -- otherwise a short clip gets stretched to look
    // longer than it is, and every doubt lands in the wrong spot on the
    // graph relative to where it really happened in the video.
    const axisMax = (videoDuration && isFinite(videoDuration) && videoDuration > 0)
        ? videoDuration
        : Math.max(...doubts.map(d => d.timestampSeconds), 60); // fallback only if duration isn't known yet

    const bucketSize = axisMax / BUCKETS;
    const counts = new Array(BUCKETS).fill(0);

    doubts.forEach(d => {
        let idx = Math.floor(d.timestampSeconds / bucketSize);
        if (idx >= BUCKETS) idx = BUCKETS - 1;
        if (idx < 0) idx = 0;
        counts[idx]++;
    });

    const maxCount = Math.max(...counts, 1);

    // Baseline sits a bit above the very bottom edge (not the middle) so flat
    // stretches have breathing room instead of hugging the container floor.
    const baseline = H - 12;
    const topPadding = 8;

    // Pad with a zero-value point at each end so the curve tapers smoothly
    // to the baseline instead of stopping abruptly mid-air.
    const points = [{ x: 0, y: baseline }];
    counts.forEach((count, i) => {
        const x = ((i + 0.5) / BUCKETS) * W;
        const y = baseline - (count / maxCount) * (baseline - topPadding);
        points.push({ x, y });
    });
    points.push({ x: W, y: baseline });

    const linePath = catmullRomToBezier(points);
    const fillPath = `${linePath} L ${W} ${H} L 0 ${H} Z`;
    const gradId = 'heatmapGrad' + Math.random().toString(36).slice(2, 8);

    container.innerHTML = `
        <svg class="heatmap-chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="height:70px;">
            <defs>
                <linearGradient id="${gradId}" x1="0" y1="0" x2="0" y2="1">
                    <stop class="grad-start" offset="0%"></stop>
                    <stop class="grad-end" offset="100%"></stop>
                </linearGradient>
            </defs>
            <path d="${fillPath}" fill="url(#${gradId})"></path>
            <path class="line-path" d="${linePath}"></path>
        </svg>
    `;

    const svg = container.querySelector('svg');
    svg.addEventListener('click', (e) => {
        const rect = svg.getBoundingClientRect();
        const fraction = (e.clientX - rect.left) / rect.width;
        const seconds = Math.round(fraction * axisMax);
        if (onSeek) onSeek(Math.max(0, seconds));
    });
}

// Converts a series of points into a smooth cubic-bezier SVG path
// (Catmull-Rom spline, converted to bezier control points).
function catmullRomToBezier(points) {
    let d = '';
    for (let i = 0; i < points.length - 1; i++) {
        const p0 = points[i === 0 ? 0 : i - 1];
        const p1 = points[i];
        const p2 = points[i + 1];
        const p3 = points[i + 2 < points.length ? i + 2 : i + 1];

        const cp1x = p1.x + (p2.x - p0.x) / 6;
        const cp1y = p1.y + (p2.y - p0.y) / 6;
        const cp2x = p2.x - (p3.x - p1.x) / 6;
        const cp2y = p2.y - (p3.y - p1.y) / 6;

        if (i === 0) d += `M ${p1.x} ${p1.y} `;
        d += `C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y} `;
    }
    return d;
}
